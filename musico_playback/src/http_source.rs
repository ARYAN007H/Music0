//! Pure-stdlib loopback HTTP/1.1 range `MediaSource` for Symphonia.
//!
//! Enables Desktop progressive stream-while-download playback against
//! `musico_core`'s local loopback server (`http://127.0.0.1:<port>/stream/<token>`)
//! with zero extra crate dependencies.
//!
//! Security boundary: strictly rejects any URL whose host is not `127.0.0.1`
//! (no DNS, no external hosts, no userinfo, no header-injection characters).

use std::io::{self, BufRead, BufReader, ErrorKind, Read, Seek, SeekFrom, Write};
use std::net::{Ipv4Addr, SocketAddrV4, TcpStream};
use std::sync::atomic::{AtomicBool, AtomicU64, Ordering};
use std::sync::Arc;
use std::time::{Duration, Instant};

use symphonia::core::io::MediaSource;

/// Connect timeout to local `127.0.0.1` — loopback connects in < 1 ms, so 2 s
/// is generous while still failing promptly if a session vanished.
const CONNECT_TIMEOUT: Duration = Duration::from_secs(2);
/// Socket read poll interval so a cancelled track switch or Stop unblocks in
/// ≤ 120 ms even while the upstream internet link is stalled.
const READ_POLL_TIMEOUT: Duration = Duration::from_millis(120);
/// Wait threshold before raising the live buffering indicator during a slow
/// mid-stream chunk read.
const BUFFERING_STALL_THRESHOLD: Duration = Duration::from_millis(200);
/// Hard cap on waiting for a single slow chunk before declaring the stream dead.
const MAX_STALL_WAIT: Duration = Duration::from_secs(30);
/// Forward seeks within this window drain the open TCP stream instead of
/// tearing down and reconnecting (MP4/WebM probes frequently skip small boxes).
const INLINE_SKIP_LIMIT: u64 = 64 * 1024;
/// Maximum HTTP response header lines accepted from loopback.
const MAX_RESPONSE_HEADERS: usize = 64;

#[derive(Debug, Clone, PartialEq, Eq)]
struct ParsedLoopbackUrl {
    port: u16,
    path: String,
}

/// Strictly validates and parses `http://127.0.0.1:<port>/<path>`.
fn parse_loopback_url(url: &str) -> Option<ParsedLoopbackUrl> {
    let rest = url.strip_prefix("http://127.0.0.1:")?;
    let slash = rest.find('/')?;
    let port_str = &rest[..slash];
    let path = &rest[slash..];
    if port_str.is_empty() || path.is_empty() {
        return None;
    }
    let port: u16 = port_str.parse().ok()?;
    if port == 0 {
        return None;
    }
    // Reject CR/LF, whitespace, or control chars in the request target.
    if path.bytes().any(|b| b <= 0x20 || b == 0x7f) {
        return None;
    }
    Some(ParsedLoopbackUrl {
        port,
        path: path.to_string(),
    })
}

/// Maps an HTTP `Content-Type` header to a Symphonia extension hint.
pub(crate) fn extension_hint_for_mime(mime: &str) -> Option<&'static str> {
    let base = mime.split(';').next().unwrap_or("").trim().to_ascii_lowercase();
    match base.as_str() {
        "audio/mp4" | "audio/x-m4a" | "audio/m4a" | "audio/aac" => Some("m4a"),
        "audio/webm" => Some("webm"),
        "audio/mpeg" | "audio/mp3" => Some("mp3"),
        "audio/ogg" | "application/ogg" => Some("ogg"),
        "audio/flac" | "audio/x-flac" => Some("flac"),
        "audio/wav" | "audio/x-wav" => Some("wav"),
        _ => None,
    }
}

/// Symphonia `MediaSource` streaming directly from a local `127.0.0.1` HTTP/1.1
/// range server as the audio file downloads in the background.
pub struct LoopbackHttpMediaSource {
    port: u16,
    path: String,
    total_len: Option<u64>,
    content_type: Option<String>,
    accepts_ranges: bool,
    pos: u64,
    reader: Option<BufReader<TcpStream>>,
    stream_gen: Arc<AtomicU64>,
    expected_gen: u64,
    buffering_stall: Arc<AtomicBool>,
}

impl LoopbackHttpMediaSource {
    /// Returns `true` when `input` looks like an HTTP loopback stream URL.
    pub fn is_loopback_url(input: &str) -> bool {
        input.starts_with("http://127.0.0.1:")
    }

    /// Opens a progressive loopback stream starting at byte 0 in a single HTTP
    /// GET round-trip (no separate HEAD probe).
    pub fn open(
        url: &str,
        stream_gen: Arc<AtomicU64>,
        expected_gen: u64,
        buffering_stall: Arc<AtomicBool>,
    ) -> io::Result<Self> {
        let parsed = parse_loopback_url(url).ok_or_else(|| {
            io::Error::new(
                ErrorKind::InvalidInput,
                "only http://127.0.0.1:<port>/... loopback URLs are permitted",
            )
        })?;
        let mut src = Self {
            port: parsed.port,
            path: parsed.path,
            total_len: None,
            content_type: None,
            accepts_ranges: false,
            pos: 0,
            reader: None,
            stream_gen,
            expected_gen,
            buffering_stall,
        };
        src.connect_at(0, false)?;
        Ok(src)
    }

    /// Returns the server-declared `Content-Type` (e.g. `audio/mp4`), if any.
    pub fn content_type(&self) -> Option<&str> {
        self.content_type.as_deref()
    }

    /// Returns the Symphonia extension hint derived from `Content-Type`.
    pub fn extension_hint(&self) -> Option<&'static str> {
        self.content_type
            .as_deref()
            .and_then(extension_hint_for_mime)
    }

    fn is_cancelled(&self) -> bool {
        self.stream_gen.load(Ordering::Acquire) != self.expected_gen
    }

    fn clear_buffering(&self) {
        self.buffering_stall.store(false, Ordering::Release);
    }

    fn connect_at(&mut self, offset: u64, use_range: bool) -> io::Result<()> {
        if self.is_cancelled() {
            self.clear_buffering();
            return Err(io::Error::new(ErrorKind::Interrupted, "stream cancelled"));
        }

        let addr = SocketAddrV4::new(Ipv4Addr::LOCALHOST, self.port);
        let mut sock = TcpStream::connect_timeout(&addr.into(), CONNECT_TIMEOUT)?;
        let _ = sock.set_nodelay(true);
        sock.set_read_timeout(Some(READ_POLL_TIMEOUT))?;
        sock.set_write_timeout(Some(Duration::from_secs(5)))?;

        let mut req = format!(
            "GET {} HTTP/1.1\r\nHost: 127.0.0.1:{}\r\n",
            self.path, self.port
        );
        if use_range || offset > 0 {
            req.push_str(&format!("Range: bytes={offset}-\r\n"));
        }
        req.push_str("Connection: close\r\n\r\n");
        sock.write_all(req.as_bytes())?;
        sock.flush()?;

        let mut reader = BufReader::with_capacity(16 * 1024, sock);

        // Read HTTP status line (tolerating READ_POLL_TIMEOUT while checking cancel).
        let status_line = self.read_header_line(&mut reader)?;
        let status_code: u16 = status_line
            .split_whitespace()
            .nth(1)
            .and_then(|s| s.parse().ok())
            .ok_or_else(|| {
                io::Error::new(
                    ErrorKind::InvalidData,
                    format!("malformed loopback HTTP status: {}", status_line.trim()),
                )
            })?;

        if status_code == 416 {
            return Err(io::Error::new(
                ErrorKind::UnexpectedEof,
                "range not satisfiable",
            ));
        }
        if status_code != 200 && status_code != 206 {
            return Err(io::Error::new(
                ErrorKind::Other,
                format!("loopback HTTP {status_code}"),
            ));
        }
        if offset > 0 && status_code != 206 {
            return Err(io::Error::new(
                ErrorKind::Unsupported,
                "loopback server did not honor Range request",
            ));
        }

        let mut content_length: Option<u64> = None;
        let mut content_range_total: Option<u64> = None;
        let mut content_type: Option<String> = None;
        let mut accepts_ranges = status_code == 206;

        for _ in 0..MAX_RESPONSE_HEADERS {
            let line = self.read_header_line(&mut reader)?;
            let trimmed = line.trim();
            if trimmed.is_empty() {
                break;
            }
            if let Some((name, value)) = trimmed.split_once(':') {
                let k = name.trim().to_ascii_lowercase();
                let v = value.trim();
                match k.as_str() {
                    "content-length" => {
                        content_length = v.parse::<u64>().ok();
                    }
                    "content-range" => {
                        // Format: "bytes START-END/TOTAL" or "bytes START-END/*"
                        if let Some((_, total_str)) = v.rsplit_once('/') {
                            if let Ok(total) = total_str.trim().parse::<u64>() {
                                content_range_total = Some(total);
                            }
                        }
                    }
                    "content-type" => {
                        let base = v.split(';').next().unwrap_or("").trim();
                        if !base.is_empty() {
                            content_type = Some(base.to_string());
                        }
                    }
                    "accept-ranges" => {
                        if v.eq_ignore_ascii_case("bytes") {
                            accepts_ranges = true;
                        }
                    }
                    _ => {}
                }
            }
        }

        if let Some(total) = content_range_total {
            self.total_len = Some(total);
        } else if status_code == 200 {
            if let Some(cl) = content_length {
                self.total_len = Some(cl);
            }
        }
        if self.content_type.is_none() {
            self.content_type = content_type;
        }
        if accepts_ranges {
            self.accepts_ranges = true;
        }
        self.pos = offset;
        self.reader = Some(reader);
        self.clear_buffering();
        Ok(())
    }

    fn read_header_line(&self, reader: &mut BufReader<TcpStream>) -> io::Result<String> {
        let mut line = String::new();
        let wait_started = Instant::now();
        loop {
            if self.is_cancelled() {
                self.clear_buffering();
                return Err(io::Error::new(ErrorKind::Interrupted, "stream cancelled"));
            }
            match reader.read_line(&mut line) {
                Ok(0) => {
                    self.clear_buffering();
                    return Err(io::Error::new(
                        ErrorKind::UnexpectedEof,
                        "loopback closed before headers completed",
                    ));
                }
                Ok(_) => {
                    self.clear_buffering();
                    return Ok(line);
                }
                Err(e) if matches!(e.kind(), ErrorKind::WouldBlock | ErrorKind::TimedOut) => {
                    if wait_started.elapsed() >= BUFFERING_STALL_THRESHOLD {
                        self.buffering_stall.store(true, Ordering::Release);
                    }
                    if wait_started.elapsed() >= MAX_STALL_WAIT {
                        self.clear_buffering();
                        return Err(io::Error::new(
                            ErrorKind::TimedOut,
                            "loopback header read timed out",
                        ));
                    }
                    continue;
                }
                Err(e) if e.kind() == ErrorKind::Interrupted => continue,
                Err(e) => {
                    self.clear_buffering();
                    return Err(e);
                }
            }
        }
    }
}

impl Drop for LoopbackHttpMediaSource {
    fn drop(&mut self) {
        self.clear_buffering();
    }
}

impl Read for LoopbackHttpMediaSource {
    fn read(&mut self, buf: &mut [u8]) -> io::Result<usize> {
        if buf.is_empty() {
            return Ok(0);
        }
        if let Some(total) = self.total_len {
            if self.pos >= total {
                self.clear_buffering();
                return Ok(0);
            }
        }
        if self.reader.is_none() {
            let offset = self.pos;
            self.connect_at(offset, offset > 0)?;
        }

        let wait_started = Instant::now();
        loop {
            if self.is_cancelled() {
                self.clear_buffering();
                return Err(io::Error::new(ErrorKind::Interrupted, "stream cancelled"));
            }
            let Some(reader) = self.reader.as_mut() else {
                self.clear_buffering();
                return Ok(0);
            };
            match reader.read(buf) {
                Ok(0) => {
                    self.clear_buffering();
                    return Ok(0);
                }
                Ok(n) => {
                    self.clear_buffering();
                    self.pos = self.pos.saturating_add(n as u64);
                    return Ok(n);
                }
                Err(e) if matches!(e.kind(), ErrorKind::WouldBlock | ErrorKind::TimedOut) => {
                    if wait_started.elapsed() >= BUFFERING_STALL_THRESHOLD {
                        self.buffering_stall.store(true, Ordering::Release);
                    }
                    if wait_started.elapsed() >= MAX_STALL_WAIT {
                        self.clear_buffering();
                        return Err(io::Error::new(
                            ErrorKind::TimedOut,
                            "loopback stream stalled waiting for upstream audio",
                        ));
                    }
                    continue;
                }
                Err(e) if e.kind() == ErrorKind::Interrupted => continue,
                Err(e) => {
                    self.clear_buffering();
                    return Err(e);
                }
            }
        }
    }
}

impl Seek for LoopbackHttpMediaSource {
    fn seek(&mut self, style: SeekFrom) -> io::Result<u64> {
        let target: u64 = match style {
            SeekFrom::Start(offset) => offset,
            SeekFrom::Current(delta) => {
                if delta >= 0 {
                    self.pos.saturating_add(delta as u64)
                } else {
                    self.pos
                        .checked_sub(delta.unsigned_abs())
                        .ok_or_else(|| {
                            io::Error::new(ErrorKind::InvalidInput, "seek before start of stream")
                        })?
                }
            }
            SeekFrom::End(delta) => {
                let total = self.total_len.ok_or_else(|| {
                    io::Error::new(
                        ErrorKind::Unsupported,
                        "cannot seek from end when total length is unknown",
                    )
                })?;
                if delta >= 0 {
                    total.saturating_add(delta as u64)
                } else {
                    total
                        .checked_sub(delta.unsigned_abs())
                        .ok_or_else(|| {
                            io::Error::new(ErrorKind::InvalidInput, "seek before start of stream")
                        })?
                }
            }
        };

        if target == self.pos {
            return Ok(self.pos);
        }

        // Fast path: small forward skip on an already-open stream avoids TCP
        // reconnect overhead while Symphonia skips small container atoms.
        if target > self.pos && (target - self.pos) <= INLINE_SKIP_LIMIT && self.reader.is_some() {
            let mut remaining = target - self.pos;
            let mut scratch = [0u8; 4096];
            while remaining > 0 {
                let want = (remaining as usize).min(scratch.len());
                let n = self.read(&mut scratch[..want])?;
                if n == 0 {
                    break;
                }
                remaining -= n as u64;
            }
            return Ok(self.pos);
        }

        if !self.accepts_ranges && target != 0 {
            return Err(io::Error::new(
                ErrorKind::Unsupported,
                "loopback stream does not support range seeking",
            ));
        }

        // Lazy reconnect at `target` on the next `read()`.
        self.reader = None;
        self.pos = target;
        Ok(self.pos)
    }
}

impl MediaSource for LoopbackHttpMediaSource {
    fn is_seekable(&self) -> bool {
        self.accepts_ranges && self.total_len.is_some()
    }

    fn byte_len(&self) -> Option<u64> {
        self.total_len
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::net::TcpListener;
    use std::sync::atomic::AtomicUsize;

    #[test]
    fn rejects_non_loopback_and_malformed_urls() {
        let gen = Arc::new(AtomicU64::new(0));
        let buf = Arc::new(AtomicBool::new(false));
        for bad in [
            "https://127.0.0.1:8080/stream/abc",
            "http://example.com:8080/stream/abc",
            "http://localhost:8080/stream/abc",
            "http://127.0.0.1:0/stream/abc",
            "http://127.0.0.1:8080",
            "http://127.0.0.1:8080/stream/abc\r\nX-Injected: 1",
            "http://127.0.0.1:8080/stream/has space",
            "/tmp/local.m4a",
        ] {
            assert!(
                LoopbackHttpMediaSource::open(bad, Arc::clone(&gen), 0, Arc::clone(&buf)).is_err(),
                "must reject URL: {bad}"
            );
        }
    }

    #[test]
    fn streams_live_before_eof_and_signals_buffering_on_slow_chunk() {
        let listener = TcpListener::bind("127.0.0.1:0").expect("bind mock server");
        let port = listener.local_addr().unwrap().port();
        let chunk1 = vec![0xAAu8; 8 * 1024];
        let chunk2 = vec![0xBBu8; 8 * 1024];
        let total = chunk1.len() + chunk2.len();
        let c1 = chunk1.clone();
        let c2 = chunk2.clone();

        let srv = std::thread::spawn(move || {
            let (mut sock, _) = listener.accept().expect("accept");
            let mut req_buf = [0u8; 512];
            let _ = sock.read(&mut req_buf);
            let hdr = format!(
                "HTTP/1.1 200 OK\r\nContent-Length: {total}\r\nContent-Type: audio/mp4\r\nAccept-Ranges: bytes\r\nConnection: close\r\n\r\n"
            );
            sock.write_all(hdr.as_bytes()).unwrap();
            sock.write_all(&c1).unwrap();
            sock.flush().unwrap();
            // Simulate slow network pause longer than BUFFERING_STALL_THRESHOLD (200 ms).
            std::thread::sleep(Duration::from_millis(360));
            sock.write_all(&c2).unwrap();
            sock.flush().unwrap();
        });

        let gen = Arc::new(AtomicU64::new(1));
        let buffering = Arc::new(AtomicBool::new(false));
        let url = format!("http://127.0.0.1:{port}/stream/live01");
        let mut src = LoopbackHttpMediaSource::open(&url, Arc::clone(&gen), 1, Arc::clone(&buffering))
            .expect("open loopback stream");

        assert_eq!(src.content_type(), Some("audio/mp4"));
        assert_eq!(src.extension_hint(), Some("m4a"));
        assert_eq!(src.byte_len(), Some(total as u64));
        assert!(src.is_seekable());

        // Chunk 1 must arrive immediately — long before chunk 2 is emitted.
        let t0 = Instant::now();
        let mut first = vec![0u8; chunk1.len()];
        src.read_exact(&mut first).expect("read chunk1");
        assert_eq!(first, chunk1);
        assert!(
            t0.elapsed() < Duration::from_millis(200),
            "first chunk should arrive before slow tail: {:?}",
            t0.elapsed()
        );

        // Observe buffering flag rising while blocked waiting on chunk 2.
        let observed_stall = Arc::new(AtomicBool::new(false));
        let obs = Arc::clone(&observed_stall);
        let buf_watch = Arc::clone(&buffering);
        let watcher = std::thread::spawn(move || {
            for _ in 0..40 {
                if buf_watch.load(Ordering::Acquire) {
                    obs.store(true, Ordering::Release);
                    break;
                }
                std::thread::sleep(Duration::from_millis(20));
            }
        });

        let mut second = vec![0u8; chunk2.len()];
        src.read_exact(&mut second).expect("read chunk2");
        assert_eq!(second, chunk2);
        assert!(!buffering.load(Ordering::Acquire), "buffering flag clears once bytes arrive");

        // Next read hits EOF cleanly.
        let mut eof_buf = [0u8; 16];
        assert_eq!(src.read(&mut eof_buf).expect("eof"), 0);

        watcher.join().unwrap();
        srv.join().unwrap();
        assert!(
            observed_stall.load(Ordering::Acquire),
            "buffering stall indicator must activate during slow chunk wait"
        );
    }

    #[test]
    fn seeks_with_inline_forward_skip_and_range_reconnect() {
        let payload: Vec<u8> = (0..128 * 1024).map(|i| (i % 251) as u8).collect();
        let listener = TcpListener::bind("127.0.0.1:0").expect("bind range server");
        let port = listener.local_addr().unwrap().port();
        let connections = Arc::new(AtomicUsize::new(0));
        let conn_count = Arc::clone(&connections);
        let data = Arc::new(payload.clone());

        let _srv = std::thread::spawn(move || {
            for incoming in listener.incoming().take(3) {
                let Ok(mut sock) = incoming else { break };
                conn_count.fetch_add(1, Ordering::SeqCst);
                let mut reader = BufReader::new(sock.try_clone().unwrap());
                let mut range_start = 0usize;
                let mut has_range = false;
                loop {
                    let mut line = String::new();
                    if reader.read_line(&mut line).unwrap_or(0) == 0 {
                        break;
                    }
                    let t = line.trim();
                    if t.is_empty() {
                        break;
                    }
                    if let Some(rest) = t
                        .strip_prefix("Range: bytes=")
                        .or_else(|| t.strip_prefix("range: bytes="))
                    {
                        if let Some((start_s, _)) = rest.split_once('-') {
                            if let Ok(s) = start_s.parse::<usize>() {
                                range_start = s;
                                has_range = true;
                            }
                        }
                    }
                }
                let slice = &data[range_start.min(data.len())..];
                let hdr = if has_range {
                    format!(
                        "HTTP/1.1 206 Partial Content\r\nContent-Range: bytes {}-{}/{}\r\nContent-Length: {}\r\nContent-Type: audio/webm\r\nAccept-Ranges: bytes\r\nConnection: close\r\n\r\n",
                        range_start,
                        data.len() - 1,
                        data.len(),
                        slice.len()
                    )
                } else {
                    format!(
                        "HTTP/1.1 200 OK\r\nContent-Length: {}\r\nContent-Type: audio/webm\r\nAccept-Ranges: bytes\r\nConnection: close\r\n\r\n",
                        data.len()
                    )
                };
                let _ = sock.write_all(hdr.as_bytes());
                let _ = sock.write_all(slice);
                let _ = sock.flush();
            }
        });

        let gen = Arc::new(AtomicU64::new(5));
        let buf = Arc::new(AtomicBool::new(false));
        let url = format!("http://127.0.0.1:{port}/stream/seek01");
        let mut src = LoopbackHttpMediaSource::open(&url, gen, 5, buf).expect("open");
        assert_eq!(src.extension_hint(), Some("webm"));

        // Small forward seek (1 KiB) drains inline without opening a second TCP connection.
        assert_eq!(src.seek(SeekFrom::Start(1024)).unwrap(), 1024);
        let mut sample = [0u8; 32];
        src.read_exact(&mut sample).unwrap();
        assert_eq!(&sample, &payload[1024..1056]);
        assert_eq!(
            connections.load(Ordering::SeqCst),
            1,
            "small forward skip must reuse open stream"
        );

        // Large forward seek (100 KiB) triggers a 206 Range reconnect.
        assert_eq!(src.seek(SeekFrom::Start(100 * 1024)).unwrap(), 100 * 1024);
        src.read_exact(&mut sample).unwrap();
        assert_eq!(&sample, &payload[100 * 1024..100 * 1024 + 32]);
        assert_eq!(connections.load(Ordering::SeqCst), 2);

        // Backward seek to byte 16 triggers a 206 Range reconnect.
        assert_eq!(src.seek(SeekFrom::Start(16)).unwrap(), 16);
        src.read_exact(&mut sample).unwrap();
        assert_eq!(&sample, &payload[16..48]);
        assert_eq!(connections.load(Ordering::SeqCst), 3);
    }

    #[test]
    fn cancel_gen_unblocks_stalled_read_promptly() {
        let listener = TcpListener::bind("127.0.0.1:0").expect("bind stall server");
        let port = listener.local_addr().unwrap().port();
        let (done_tx, done_rx) = std::sync::mpsc::channel::<()>();

        let srv = std::thread::spawn(move || {
            let (mut sock, _) = listener.accept().expect("accept");
            let mut req_buf = [0u8; 512];
            let _ = sock.read(&mut req_buf);
            let hdr = "HTTP/1.1 200 OK\r\nContent-Length: 65536\r\nContent-Type: audio/mp4\r\nAccept-Ranges: bytes\r\nConnection: close\r\n\r\n";
            let _ = sock.write_all(hdr.as_bytes());
            let _ = sock.flush();
            // Hold connection open with zero body bytes until test finishes.
            let _ = done_rx.recv_timeout(Duration::from_secs(3));
        });

        let gen = Arc::new(AtomicU64::new(7));
        let buf = Arc::new(AtomicBool::new(false));
        let url = format!("http://127.0.0.1:{port}/stream/stall01");
        let mut src = LoopbackHttpMediaSource::open(&url, Arc::clone(&gen), 7, buf).expect("open");

        let gen_trip = Arc::clone(&gen);
        std::thread::spawn(move || {
            std::thread::sleep(Duration::from_millis(80));
            gen_trip.fetch_add(1, Ordering::SeqCst);
        });

        let t0 = Instant::now();
        let mut out = [0u8; 64];
        let err = src.read(&mut out).expect_err("cancelled read must error");
        assert_eq!(err.kind(), ErrorKind::Interrupted);
        assert!(
            t0.elapsed() < Duration::from_millis(500),
            "cancelled read must unblock promptly, took {:?}",
            t0.elapsed()
        );

        let _ = done_tx.send(());
        srv.join().unwrap();
    }
}
