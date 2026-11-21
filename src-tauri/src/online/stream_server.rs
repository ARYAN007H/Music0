//! Local loopback HTTP/1.1 range-request server for stream-while-download playback.
//!
//! Mobile playback (`track.resolve` with `allowStream = true`) opens an upstream
//! audio body, begins writing it into `{stem}.m4a.part` on a background thread,
//! and returns a `http://127.0.0.1:<port>/stream/<token>` URL as soon as the
//! first [`INITIAL_READY_BYTES`] land (typically < 1–2 s).
//!
//! Media3 / ExoPlayer requests `GET` / `HEAD` with `Range: bytes=...` headers
//! against this loopback server. Reader threads block on a `Condvar` whenever
//! they catch up to `bytes_written` and wake on every incoming chunk, seamlessly
//! following the atomic `.part` → final cache file rename once the download
//! passes [`super::stream::MIN_FILE_BYTES`] verification.

use std::collections::HashMap;
use std::fs::File;
use std::io::{BufRead, BufReader, ErrorKind, Read, Seek, SeekFrom, Write};
use std::net::{TcpListener, TcpStream};
use std::path::{Path, PathBuf};
use std::sync::atomic::{AtomicU64, Ordering};
use std::sync::{Arc, Condvar, LazyLock, Mutex};
use std::time::{Duration, Instant};

/// Bytes buffered into `.part` before `start_session` hands the loopback URL
/// back to the player. Small enough to start audio in < 1–2 s while catching
/// immediate upstream 403 / empty-body failures before playback begins.
pub(crate) const INITIAL_READY_BYTES: u64 = 64 * 1024;
/// How long `start_session` waits for the initial ready threshold before
/// declaring the upstream stalled and failing over.
pub(crate) const INITIAL_READY_TIMEOUT: Duration = Duration::from_secs(12);
/// Upper bound on active/completed sessions retained in the lookup map.
const MAX_RETAINED_SESSIONS: usize = 24;
/// Read chunk size for both producer and HTTP range reader.
const IO_CHUNK_BYTES: usize = 32 * 1024;
/// Maximum HTTP header lines accepted on a single loopback connection.
const MAX_HEADER_LINES: usize = 64;

#[derive(Debug, Clone, PartialEq, Eq)]
pub(crate) enum RangeParse {
    Full,
    Range { start: u64, end: Option<u64> },
    Unsatisfiable,
}

/// Parses an RFC 7233 `Range` header value (e.g. `bytes=0-1023`, `bytes=512-`,
/// `bytes=-256`) against an optional known total byte length.
pub(crate) fn parse_range_header(header: &str, total: Option<u64>) -> RangeParse {
    let trimmed = header.trim();
    let Some(spec) = trimmed
        .strip_prefix("bytes=")
        .or_else(|| trimmed.strip_prefix("BYTES="))
    else {
        return RangeParse::Full;
    };
    // Only the first byte-range-spec is handled (Media3 never sends multi-range).
    let first = spec.split(',').next().unwrap_or("").trim();
    let Some((start_str, end_str)) = first.split_once('-') else {
        return RangeParse::Unsatisfiable;
    };
    let start_str = start_str.trim();
    let end_str = end_str.trim();

    if start_str.is_empty() {
        // Suffix range: bytes=-N
        let Ok(suffix) = end_str.parse::<u64>() else {
            return RangeParse::Unsatisfiable;
        };
        if suffix == 0 {
            return RangeParse::Unsatisfiable;
        }
        let Some(t) = total else {
            return RangeParse::Unsatisfiable;
        };
        if t == 0 {
            return RangeParse::Unsatisfiable;
        }
        let start = t.saturating_sub(suffix);
        return RangeParse::Range {
            start,
            end: Some(t - 1),
        };
    }

    let Ok(start) = start_str.parse::<u64>() else {
        return RangeParse::Unsatisfiable;
    };
    if let Some(t) = total {
        if t == 0 || start >= t {
            return RangeParse::Unsatisfiable;
        }
    }

    if end_str.is_empty() {
        // Open-ended range: bytes=START-
        let end = total.map(|t| t.saturating_sub(1));
        return RangeParse::Range { start, end };
    }

    let Ok(raw_end) = end_str.parse::<u64>() else {
        return RangeParse::Unsatisfiable;
    };
    if raw_end < start {
        return RangeParse::Unsatisfiable;
    }
    let clamped_end = match total {
        Some(t) => raw_end.min(t.saturating_sub(1)),
        None => raw_end,
    };
    RangeParse::Range {
        start,
        end: Some(clamped_end),
    }
}

struct SessionState {
    bytes_written: u64,
    total_bytes: Option<u64>,
    finalized_path: Option<PathBuf>,
    done: bool,
    error: Option<String>,
}

pub(crate) struct StreamSession {
    part_path: PathBuf,
    final_path: PathBuf,
    content_type: String,
    state: Mutex<SessionState>,
    cv: Condvar,
    created_at: Instant,
}

impl StreamSession {
    fn new(
        part_path: PathBuf,
        final_path: PathBuf,
        content_type: String,
        expected_total: Option<u64>,
    ) -> Self {
        Self {
            part_path,
            final_path,
            content_type,
            state: Mutex::new(SessionState {
                bytes_written: 0,
                total_bytes: expected_total,
                finalized_path: None,
                done: false,
                error: None,
            }),
            cv: Condvar::new(),
            created_at: Instant::now(),
        }
    }

    fn wait_initial_ready(&self, ready_bytes: u64, timeout: Duration) -> Result<(), String> {
        let deadline = Instant::now() + timeout;
        let mut guard = self.state.lock().unwrap_or_else(|e| e.into_inner());
        loop {
            if let Some(err) = &guard.error {
                return Err(err.clone());
            }
            if guard.done {
                return if guard.finalized_path.is_some() {
                    Ok(())
                } else {
                    Err("stream ended before finalizing".into())
                };
            }
            if guard.bytes_written >= ready_bytes {
                return Ok(());
            }
            let Some(rem) = deadline.checked_duration_since(Instant::now()) else {
                if guard.bytes_written > 0 {
                    // Slow link that already delivered audio header bytes: let playback begin.
                    return Ok(());
                }
                return Err("stream timed out waiting for initial audio bytes".into());
            };
            let (g, _) = self
                .cv
                .wait_timeout(guard, rem.min(Duration::from_millis(250)))
                .unwrap_or_else(|e| e.into_inner());
            guard = g;
        }
    }
}

/// Configuration for a streaming-while-downloading session.
pub(crate) struct StreamSessionConfig {
    pub part_path: PathBuf,
    pub final_path: PathBuf,
    pub content_type: String,
    pub expected_total: Option<u64>,
    pub min_file_bytes: u64,
    pub max_file_bytes: u64,
    pub initial_ready_bytes: u64,
    pub initial_ready_timeout: Duration,
}

pub(crate) struct LoopbackStreamServer {
    port: u16,
    sessions: Arc<Mutex<HashMap<String, Arc<StreamSession>>>>,
    next_id: AtomicU64,
}

impl LoopbackStreamServer {
    /// Starts a new loopback HTTP/1.1 range server on `127.0.0.1:0`.
    pub(crate) fn start() -> Result<Self, String> {
        let listener =
            TcpListener::bind("127.0.0.1:0").map_err(|e| format!("bind loopback server: {e}"))?;
        let port = listener
            .local_addr()
            .map_err(|e| format!("loopback local_addr: {e}"))?
            .port();
        let sessions: Arc<Mutex<HashMap<String, Arc<StreamSession>>>> =
            Arc::new(Mutex::new(HashMap::new()));
        let accept_sessions = sessions.clone();

        std::thread::Builder::new()
            .name("musico-stream-srv".into())
            .spawn(move || {
                for incoming in listener.incoming() {
                    let Ok(stream) = incoming else { continue };
                    let map = accept_sessions.clone();
                    let _ = std::thread::Builder::new()
                        .name("musico-stream-conn".into())
                        .spawn(move || handle_connection(stream, &map));
                }
            })
            .map_err(|e| format!("spawn stream server thread: {e}"))?;

        log::info!("loopback stream server listening on 127.0.0.1:{port}");
        Ok(Self {
            port,
            sessions,
            next_id: AtomicU64::new(1),
        })
    }

    pub(crate) fn port(&self) -> u16 {
        self.port
    }

    /// Registers a streaming session, starts pumping `reader` into `cfg.part_path`,
    /// waits until `cfg.initial_ready_bytes` (or EOF) are available, and returns
    /// the local playback URL (`http://127.0.0.1:<port>/stream/<token>`).
    ///
    /// On completion, the producer verifies `min_file_bytes` + `expected_total`,
    /// atomically renames `part_path` → `final_path`, and runs `on_complete`.
    pub(crate) fn start_session<R, F>(
        &self,
        mut reader: R,
        cfg: StreamSessionConfig,
        on_complete: F,
    ) -> Result<String, String>
    where
        R: Read + Send + 'static,
        F: FnOnce(PathBuf) + Send + 'static,
    {
        if let Some(parent) = cfg.part_path.parent() {
            std::fs::create_dir_all(parent).map_err(|e| format!("create cache dir: {e}"))?;
        }
        let token = format!(
            "s{}_{}",
            self.next_id.fetch_add(1, Ordering::Relaxed),
            cfg.final_path
                .file_stem()
                .and_then(|s| s.to_str())
                .unwrap_or("track")
                .chars()
                .filter(|c| c.is_ascii_alphanumeric() || *c == '-' || *c == '_')
                .take(48)
                .collect::<String>()
        );

        let session = Arc::new(StreamSession::new(
            cfg.part_path.clone(),
            cfg.final_path.clone(),
            cfg.content_type.clone(),
            cfg.expected_total,
        ));

        {
            let mut map = self.sessions.lock().unwrap_or_else(|e| e.into_inner());
            // Evict oldest completed sessions when map exceeds cap.
            while map.len() >= MAX_RETAINED_SESSIONS {
                let oldest_done = map
                    .iter()
                    .filter(|(_, s)| {
                        s.state
                            .lock()
                            .map(|g| g.done)
                            .unwrap_or(true)
                    })
                    .min_by_key(|(_, s)| s.created_at)
                    .map(|(k, _)| k.clone());
                match oldest_done {
                    Some(k) => {
                        map.remove(&k);
                    }
                    None => break,
                }
            }
            map.insert(token.clone(), session.clone());
        }

        let producer_session = session.clone();
        let expected_total = cfg.expected_total;
        let min_bytes = cfg.min_file_bytes;
        let max_bytes = cfg.max_file_bytes;

        std::thread::Builder::new()
            .name("musico-stream-pump".into())
            .spawn(move || {
                let result = pump_into_part(
                    &mut reader,
                    &producer_session,
                    expected_total,
                    min_bytes,
                    max_bytes,
                );
                match result {
                    Ok(final_path) => {
                        on_complete(final_path);
                    }
                    Err(e) => {
                        let _ = std::fs::remove_file(&producer_session.part_path);
                        let mut g = producer_session
                            .state
                            .lock()
                            .unwrap_or_else(|e| e.into_inner());
                        g.error = Some(e);
                        g.done = true;
                        producer_session.cv.notify_all();
                    }
                }
            })
            .map_err(|e| format!("spawn stream pump thread: {e}"))?;

        if let Err(e) = session.wait_initial_ready(cfg.initial_ready_bytes, cfg.initial_ready_timeout)
        {
            let mut map = self.sessions.lock().unwrap_or_else(|e| e.into_inner());
            map.remove(&token);
            return Err(e);
        }

        Ok(format!("http://127.0.0.1:{}/stream/{token}", self.port))
    }
}

fn pump_into_part<R: Read>(
    reader: &mut R,
    session: &StreamSession,
    expected_total: Option<u64>,
    min_bytes: u64,
    max_bytes: u64,
) -> Result<PathBuf, String> {
    let mut file = File::create(&session.part_path).map_err(|e| format!("create .part: {e}"))?;
    let mut buf = vec![0u8; IO_CHUNK_BYTES];
    let mut total_written: u64 = 0;

    loop {
        let n = reader.read(&mut buf).map_err(|e| format!("upstream read: {e}"))?;
        if n == 0 {
            break;
        }
        total_written = total_written.saturating_add(n as u64);
        if total_written > max_bytes {
            return Err(format!("stream exceeds the {max_bytes} B cap"));
        }
        file.write_all(&buf[..n])
            .map_err(|e| format!("write .part: {e}"))?;
        file.flush().map_err(|e| format!("flush .part: {e}"))?;

        let mut g = session.state.lock().unwrap_or_else(|e| e.into_inner());
        g.bytes_written = total_written;
        session.cv.notify_all();
    }

    file.sync_all().map_err(|e| format!("sync .part: {e}"))?;
    drop(file);

    if total_written < min_bytes {
        return Err(format!(
            "downloaded file suspiciously small ({total_written} B)"
        ));
    }
    if let Some(expected) = expected_total {
        if expected > 0 && total_written < expected {
            return Err(format!(
                "truncated stream ({total_written} B of expected {expected} B)"
            ));
        }
    }

    std::fs::rename(&session.part_path, &session.final_path)
        .map_err(|e| format!("atomic rename .part -> cache: {e}"))?;

    {
        let mut g = session.state.lock().unwrap_or_else(|e| e.into_inner());
        g.bytes_written = total_written;
        g.total_bytes = Some(total_written);
        g.finalized_path = Some(session.final_path.clone());
        g.done = true;
        session.cv.notify_all();
    }

    Ok(session.final_path.clone())
}

fn handle_connection(
    mut stream: TcpStream,
    sessions: &Arc<Mutex<HashMap<String, Arc<StreamSession>>>>,
) {
    let _ = stream.set_read_timeout(Some(Duration::from_secs(10)));
    let _ = stream.set_write_timeout(Some(Duration::from_secs(30)));
    let _ = stream.set_nodelay(true);

    let Ok(read_clone) = stream.try_clone() else {
        return;
    };
    let mut reader = BufReader::new(read_clone);

    let mut request_line = String::new();
    if reader.read_line(&mut request_line).unwrap_or(0) == 0 {
        return;
    }
    let mut parts = request_line.split_whitespace();
    let method = parts.next().unwrap_or("");
    let path = parts.next().unwrap_or("");
    if method != "GET" && method != "HEAD" {
        let _ = write_simple_response(&mut stream, "405 Method Not Allowed", "method not allowed");
        return;
    }

    let mut range_header: Option<String> = None;
    for _ in 0..MAX_HEADER_LINES {
        let mut line = String::new();
        if reader.read_line(&mut line).unwrap_or(0) == 0 {
            break;
        }
        let trimmed = line.trim();
        if trimmed.is_empty() {
            break;
        }
        if let Some((name, value)) = trimmed.split_once(':') {
            if name.trim().eq_ignore_ascii_case("range") {
                range_header = Some(value.trim().to_string());
            }
        }
    }

    let clean_path = path.split('?').next().unwrap_or(path);
    let Some(token) = clean_path.strip_prefix("/stream/") else {
        let _ = write_simple_response(&mut stream, "404 Not Found", "not found");
        return;
    };

    let session = {
        let map = sessions.lock().unwrap_or_else(|e| e.into_inner());
        map.get(token).cloned()
    };
    let Some(session) = session else {
        let _ = write_simple_response(&mut stream, "404 Not Found", "session expired");
        return;
    };

    // Snapshot known total length. If unknown and caller requests a non-zero
    // or suffix range, wait for download completion so Content-Range is exact.
    let mut known_total = {
        let g = session.state.lock().unwrap_or_else(|e| e.into_inner());
        g.total_bytes
            .or_else(|| if g.done { Some(g.bytes_written) } else { None })
    };

    if known_total.is_none() {
        if let Some(ref rh) = range_header {
            let needs_total = match parse_range_header(rh, None) {
                RangeParse::Range { start: 0, end: None } => false,
                RangeParse::Range { end: Some(_), .. } => false,
                RangeParse::Full => false,
                _ => true,
            };
            if needs_total {
                let mut g = session.state.lock().unwrap_or_else(|e| e.into_inner());
                while !g.done && g.total_bytes.is_none() {
                    g = session.cv.wait(g).unwrap_or_else(|e| e.into_inner());
                }
                known_total = g
                    .total_bytes
                    .or_else(|| if g.done { Some(g.bytes_written) } else { None });
            }
        }
    }

    let parsed_range = match range_header.as_deref() {
        Some(h) => parse_range_header(h, known_total),
        None => RangeParse::Full,
    };

    let (start, end, is_partial) = match parsed_range {
        RangeParse::Unsatisfiable => {
            let total_str = known_total
                .map(|t| t.to_string())
                .unwrap_or_else(|| "*".into());
            let resp = format!(
                "HTTP/1.1 416 Range Not Satisfiable\r\nContent-Range: bytes */{total_str}\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
            );
            let _ = stream.write_all(resp.as_bytes());
            return;
        }
        RangeParse::Full => (0u64, known_total.map(|t| t.saturating_sub(1)), false),
        RangeParse::Range { start, end } => {
            if start == 0 && end.is_none() && known_total.is_none() {
                (0u64, None, false)
            } else {
                (start, end, true)
            }
        }
    };

    // Build and send response headers.
    let mut headers = String::new();
    if is_partial {
        headers.push_str("HTTP/1.1 206 Partial Content\r\n");
        let end_val = end.unwrap_or(start);
        let total_str = known_total
            .map(|t| t.to_string())
            .unwrap_or_else(|| "*".into());
        let len = end_val.saturating_sub(start).saturating_add(1);
        headers.push_str(&format!(
            "Content-Range: bytes {start}-{end_val}/{total_str}\r\n"
        ));
        headers.push_str(&format!("Content-Length: {len}\r\n"));
    } else {
        headers.push_str("HTTP/1.1 200 OK\r\n");
        if let Some(total) = known_total {
            headers.push_str(&format!("Content-Length: {total}\r\n"));
        }
    }
    headers.push_str(&format!("Content-Type: {}\r\n", session.content_type));
    headers.push_str("Accept-Ranges: bytes\r\n");
    headers.push_str("Connection: close\r\n\r\n");

    if stream.write_all(headers.as_bytes()).is_err() {
        return;
    }
    if method == "HEAD" {
        let _ = stream.flush();
        return;
    }

    // Stream body from backing `.part` or finalized cache file.
    let mut cursor = start;
    let mut open_file: Option<(PathBuf, File)> = None;
    let mut buf = vec![0u8; IO_CHUNK_BYTES];

    loop {
        if let Some(end_incl) = end {
            if cursor > end_incl {
                break;
            }
        }

        // Wait until at least one byte beyond `cursor` exists, or producer finishes.
        let (available, backing_path, finished, failed) = {
            let mut g = session.state.lock().unwrap_or_else(|e| e.into_inner());
            while g.bytes_written <= cursor && !g.done && g.error.is_none() {
                g = session.cv.wait(g).unwrap_or_else(|e| e.into_inner());
            }
            let path = g
                .finalized_path
                .clone()
                .unwrap_or_else(|| session.part_path.clone());
            (g.bytes_written, path, g.done, g.error.is_some())
        };

        if available <= cursor {
            if failed || finished {
                break;
            }
            continue;
        }

        let max_by_range = match end {
            Some(end_incl) => end_incl.saturating_sub(cursor).saturating_add(1),
            None => u64::MAX,
        };
        let to_read = (available - cursor)
            .min(max_by_range)
            .min(buf.len() as u64) as usize;
        if to_read == 0 {
            break;
        }

        let file = match ensure_backing_file(&mut open_file, &backing_path, &session) {
            Some(f) => f,
            None => break,
        };
        if file.seek(SeekFrom::Start(cursor)).is_err() {
            open_file = None;
            continue;
        }
        match file.read(&mut buf[..to_read]) {
            Ok(0) => {
                // Backing file may have just been renamed from .part -> final_path.
                open_file = None;
                continue;
            }
            Ok(n) => {
                if stream.write_all(&buf[..n]).is_err() {
                    break;
                }
                cursor = cursor.saturating_add(n as u64);
            }
            Err(_) => {
                open_file = None;
                break;
            }
        }
    }
    let _ = stream.flush();
}

fn ensure_backing_file<'a>(
    open_file: &'a mut Option<(PathBuf, File)>,
    preferred_path: &Path,
    session: &StreamSession,
) -> Option<&'a mut File> {
    let need_reopen = match open_file {
        Some((p, _)) => p != preferred_path,
        None => true,
    };
    if need_reopen {
        let opened = match File::open(preferred_path) {
            Ok(f) => Some((preferred_path.to_path_buf(), f)),
            Err(e) if e.kind() == ErrorKind::NotFound => {
                // Handle atomic rename window (.part vanished -> finalized_path exists).
                let fallback = session
                    .state
                    .lock()
                    .ok()
                    .and_then(|g| g.finalized_path.clone())
                    .unwrap_or_else(|| session.final_path.clone());
                File::open(&fallback).ok().map(|f| (fallback, f))
            }
            Err(_) => None,
        };
        *open_file = opened;
    }
    open_file.as_mut().map(|(_, f)| f)
}

fn write_simple_response(stream: &mut TcpStream, status: &str, body: &str) -> std::io::Result<()> {
    let resp = format!(
        "HTTP/1.1 {status}\r\nContent-Type: text/plain\r\nContent-Length: {}\r\nConnection: close\r\n\r\n{body}",
        body.len()
    );
    stream.write_all(resp.as_bytes())
}

/// Process-wide singleton loopback server initialized lazily on first stream.
#[allow(clippy::incompatible_msrv)]
static GLOBAL_SERVER: LazyLock<Result<LoopbackStreamServer, String>> =
    LazyLock::new(LoopbackStreamServer::start);

pub(crate) fn global_server() -> Result<&'static LoopbackStreamServer, String> {
    GLOBAL_SERVER
        .as_ref()
        .map_err(|e| format!("loopback stream server unavailable: {e}"))
}

// ---------------------------------------------------------------------------
// Hermetic tests (no network — loopback 127.0.0.1 + mock Readers only)
// ---------------------------------------------------------------------------

#[cfg(test)]
mod tests {
    use super::*;
    use std::io::Cursor;
    use std::sync::mpsc;

    fn http_get(port: u16, path: &str, range: Option<&str>) -> (String, HashMap<String, String>, Vec<u8>) {
        http_request(port, "GET", path, range)
    }

    fn http_request(
        port: u16,
        method: &str,
        path: &str,
        range: Option<&str>,
    ) -> (String, HashMap<String, String>, Vec<u8>) {
        let mut sock = TcpStream::connect(("127.0.0.1", port)).expect("connect loopback");
        sock.set_read_timeout(Some(Duration::from_secs(5))).unwrap();
        let mut req = format!("{method} {path} HTTP/1.1\r\nHost: 127.0.0.1:{port}\r\n");
        if let Some(r) = range {
            req.push_str(&format!("Range: {r}\r\n"));
        }
        req.push_str("Connection: close\r\n\r\n");
        sock.write_all(req.as_bytes()).unwrap();

        let mut raw = Vec::new();
        sock.read_to_end(&mut raw).unwrap();
        let sep = raw
            .windows(4)
            .position(|w| w == b"\r\n\r\n")
            .expect("HTTP header separator");
        let head = String::from_utf8_lossy(&raw[..sep]);
        let mut lines = head.lines();
        let status = lines.next().unwrap_or("").to_string();
        let mut headers = HashMap::new();
        for line in lines {
            if let Some((k, v)) = line.split_once(':') {
                headers.insert(k.trim().to_ascii_lowercase(), v.trim().to_string());
            }
        }
        let body = raw[sep + 4..].to_vec();
        (status, headers, body)
    }

    /// Mock reader that emits chunks controlled by a channel so tests can
    /// verify live streaming BEFORE the download finishes.
    struct ChannelReader {
        rx: mpsc::Receiver<Result<Vec<u8>, String>>,
        current: Vec<u8>,
        offset: usize,
    }

    impl Read for ChannelReader {
        fn read(&mut self, out: &mut [u8]) -> std::io::Result<usize> {
            while self.offset >= self.current.len() {
                match self.rx.recv() {
                    Ok(Ok(chunk)) if chunk.is_empty() => continue,
                    Ok(Ok(chunk)) => {
                        self.current = chunk;
                        self.offset = 0;
                    }
                    Ok(Err(msg)) => {
                        return Err(std::io::Error::new(ErrorKind::BrokenPipe, msg));
                    }
                    Err(_) => return Ok(0),
                }
            }
            let avail = self.current.len() - self.offset;
            let n = avail.min(out.len());
            out[..n].copy_from_slice(&self.current[self.offset..self.offset + n]);
            self.offset += n;
            Ok(n)
        }
    }

    #[test]
    fn range_header_parser_handles_all_rfc7233_forms() {
        assert_eq!(
            parse_range_header("bytes=0-99", Some(1000)),
            RangeParse::Range {
                start: 0,
                end: Some(99)
            }
        );
        // End past EOF clamps to total - 1.
        assert_eq!(
            parse_range_header("bytes=900-5000", Some(1000)),
            RangeParse::Range {
                start: 900,
                end: Some(999)
            }
        );
        // Open-ended range.
        assert_eq!(
            parse_range_header("bytes=500-", Some(1000)),
            RangeParse::Range {
                start: 500,
                end: Some(999)
            }
        );
        // Suffix range.
        assert_eq!(
            parse_range_header("bytes=-128", Some(1000)),
            RangeParse::Range {
                start: 872,
                end: Some(999)
            }
        );
        // Unsatisfiable ranges.
        assert_eq!(
            parse_range_header("bytes=1000-", Some(1000)),
            RangeParse::Unsatisfiable
        );
        assert_eq!(
            parse_range_header("bytes=500-100", Some(1000)),
            RangeParse::Unsatisfiable
        );
        assert_eq!(
            parse_range_header("bytes=-0", Some(1000)),
            RangeParse::Unsatisfiable
        );
        assert_eq!(
            parse_range_header("bytes=bad", Some(1000)),
            RangeParse::Unsatisfiable
        );
    }

    #[test]
    fn loopback_server_streams_live_while_part_grows_and_atomically_renames() {
        let srv = LoopbackStreamServer::start().expect("server starts");
        let tmp = tempfile::tempdir().unwrap();
        let part_path = tmp.path().join("abc12345678.m4a.part");
        let final_path = tmp.path().join("abc12345678.m4a");

        let chunk1: Vec<u8> = (0..70_000).map(|i| (i % 251) as u8).collect();
        let chunk2: Vec<u8> = (70_000..150_000).map(|i| (i % 251) as u8).collect();
        let mut expected = Vec::with_capacity(150_000);
        expected.extend_from_slice(&chunk1);
        expected.extend_from_slice(&chunk2);

        let (tx, rx) = mpsc::channel();
        tx.send(Ok(chunk1.clone())).unwrap();

        let completed_flag = Arc::new(Mutex::new(None::<PathBuf>));
        let completed_clone = completed_flag.clone();

        let url = srv
            .start_session(
                ChannelReader {
                    rx,
                    current: Vec::new(),
                    offset: 0,
                },
                StreamSessionConfig {
                    part_path: part_path.clone(),
                    final_path: final_path.clone(),
                    content_type: "audio/mp4".into(),
                    expected_total: Some(expected.len() as u64),
                    min_file_bytes: 32 * 1024,
                    max_file_bytes: 64 * 1024 * 1024,
                    initial_ready_bytes: 64 * 1024,
                    initial_ready_timeout: Duration::from_secs(5),
                },
                move |p| {
                    *completed_clone.lock().unwrap() = Some(p);
                },
            )
            .expect("session ready as soon as chunk1 arrives");

        // Before chunk2 is sent, the final cache file must NOT exist yet —
        // proving start_session returned while the download is still live!
        assert!(!final_path.exists(), "must return before download finishes");
        assert!(part_path.exists(), ".part exists while streaming");

        let path = url.strip_prefix(&format!("http://127.0.0.1:{}", srv.port())).unwrap().to_string();
        let port = srv.port();

        // Start a full HTTP GET while chunk2 hasn't arrived yet.
        let getter = std::thread::spawn(move || http_get(port, &path, None));

        // Feed the remaining audio bytes and close the channel (EOF).
        std::thread::sleep(Duration::from_millis(60));
        tx.send(Ok(chunk2)).unwrap();
        drop(tx);

        let (status, headers, body) = getter.join().unwrap();
        assert!(status.starts_with("HTTP/1.1 200"), "got status {status}");
        assert_eq!(headers.get("accept-ranges").map(String::as_str), Some("bytes"));
        assert_eq!(headers.get("content-type").map(String::as_str), Some("audio/mp4"));
        assert_eq!(body.len(), expected.len());
        assert_eq!(body, expected);

        // Wait briefly for on_complete callback after rename.
        for _ in 0..40 {
            if completed_flag.lock().unwrap().is_some() {
                break;
            }
            std::thread::sleep(Duration::from_millis(15));
        }
        assert!(final_path.exists(), "finalized cache file landed");
        assert!(!part_path.exists(), ".part cleaned up after atomic rename");
        assert_eq!(std::fs::read(&final_path).unwrap(), expected);
        assert_eq!(completed_flag.lock().unwrap().as_ref(), Some(&final_path));
    }

    #[test]
    fn loopback_server_serves_206_range_and_waits_for_tail_bytes() {
        let srv = LoopbackStreamServer::start().expect("server starts");
        let tmp = tempfile::tempdir().unwrap();
        let part_path = tmp.path().join("tailseek.m4a.part");
        let final_path = tmp.path().join("tailseek.m4a");

        let chunk1: Vec<u8> = (0..80_000).map(|i| ((i * 7) % 256) as u8).collect();
        let chunk2: Vec<u8> = (80_000..160_000).map(|i| ((i * 7) % 256) as u8).collect();
        let mut full = Vec::with_capacity(160_000);
        full.extend_from_slice(&chunk1);
        full.extend_from_slice(&chunk2);

        let (tx, rx) = mpsc::channel();
        tx.send(Ok(chunk1)).unwrap();

        let url = srv
            .start_session(
                ChannelReader {
                    rx,
                    current: Vec::new(),
                    offset: 0,
                },
                StreamSessionConfig {
                    part_path,
                    final_path,
                    content_type: "audio/mp4".into(),
                    expected_total: Some(160_000),
                    min_file_bytes: 32 * 1024,
                    max_file_bytes: 64 * 1024 * 1024,
                    initial_ready_bytes: 64 * 1024,
                    initial_ready_timeout: Duration::from_secs(5),
                },
                |_| {},
            )
            .expect("session starts");

        let path = url.strip_prefix(&format!("http://127.0.0.1:{}", srv.port())).unwrap().to_string();
        let port = srv.port();

        // Request bytes at the very end (simulating MP4 moov-at-tail probe) BEFORE chunk2 arrives.
        let range_path = path.clone();
        let tail_req = std::thread::spawn(move || {
            http_get(port, &range_path, Some("bytes=150000-159999"))
        });

        std::thread::sleep(Duration::from_millis(50));
        tx.send(Ok(chunk2)).unwrap();
        drop(tx);

        let (status, headers, body) = tail_req.join().unwrap();
        assert!(status.starts_with("HTTP/1.1 206"), "expected 206, got {status}");
        assert_eq!(
            headers.get("content-range").map(String::as_str),
            Some("bytes 150000-159999/160000")
        );
        assert_eq!(headers.get("content-length").map(String::as_str), Some("10000"));
        assert_eq!(body, full[150_000..160_000]);

        // HEAD and 416 checks on the now-finalized session.
        let (head_status, head_hdrs, head_body) = http_request(port, "HEAD", &path, None);
        assert!(head_status.starts_with("HTTP/1.1 200"));
        assert_eq!(head_hdrs.get("content-length").map(String::as_str), Some("160000"));
        assert!(head_body.is_empty());

        let (bad_status, _, _) = http_get(port, &path, Some("bytes=200000-"));
        assert!(bad_status.starts_with("HTTP/1.1 416"));

        let (missing_status, _, _) = http_get(port, "/stream/does-not-exist", None);
        assert!(missing_status.starts_with("HTTP/1.1 404"));
    }

    #[test]
    fn loopback_server_surfaces_upstream_error_and_cleans_part() {
        let srv = LoopbackStreamServer::start().expect("server starts");
        let tmp = tempfile::tempdir().unwrap();
        let part_path = tmp.path().join("broken.m4a.part");
        let final_path = tmp.path().join("broken.m4a");

        // Too-small body fails MIN_FILE_BYTES check before session becomes ready.
        let tiny = Cursor::new(vec![0x42u8; 512]);
        let err = srv
            .start_session(
                tiny,
                StreamSessionConfig {
                    part_path: part_path.clone(),
                    final_path: final_path.clone(),
                    content_type: "audio/mp4".into(),
                    expected_total: Some(512),
                    min_file_bytes: 32 * 1024,
                    max_file_bytes: 64 * 1024 * 1024,
                    initial_ready_bytes: 64 * 1024,
                    initial_ready_timeout: Duration::from_secs(3),
                },
                |_| {},
            )
            .unwrap_err();
        assert!(err.contains("suspiciously small"), "got: {err}");
        assert!(!part_path.exists(), "failed .part removed");
        assert!(!final_path.exists(), "no corrupt final file");
    }
}
