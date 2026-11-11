/* ══════════════════════════════════════════════════════════════════════════════
   MUSICO ONLINE — Browse (YouTube Music via InnerTube) + fullscreen vinyl
   Depends on: Bridge, App (for artFor/showToast/navigateTo). Loaded after app.js.

   Robust/advanced behaviour:
   - live search (350 ms debounce) + Enter; '/' focuses the search box
   - Songs/Videos filter pill
   - paginated results ("Load more" via InnerTube continuations)
   - per-row add-to-queue (background stream resolve) + cached badge
   - search history chips (localStorage, capped at 6)
   - stale-response guards for racing searches
   ══════════════════════════════════════════════════════════════════════════════ */

const Online = (() => {
  // videoId → OnlineTrack, filled by search; lets us decorate the now-playing
  // UI with the real thumbnail when the engine reports a `yt:<id>` song.
  const trackMap = new Map();
  let searchSeq = 0;        // guard: only the newest search may render
  let nextContinuation = null;
  let lastQuery = '';
  let lastVideos = false;
  let loadingMore = false;
  let debounceTimer = null;

  const HISTORY_KEY = 'musico-search-history';
  const DEBOUNCE_MS = 350;
  // Typeahead (#59): suggestions fire faster than live results and render
  // in a separate popover, so they get their own timer + sequence guard.
  const SUGGEST_DEBOUNCE_MS = 200;
  let suggestTimer = null;
  let suggestSeq = 0;
  let suggestIndex = -1;

  // ─── Search ─────────────────────────────────────────────────────────────────
  async function search(query, { videos = false, continuation = null, append = false } = {}) {
    const seq = ++searchSeq;
    lastQuery = query;
    lastVideos = videos;

    if (!append) {
      nextContinuation = null;
      setStatus('Searching YouTube Music…');
      renderSkeleton();
    } else {
      setLoadMoreState(true);
    }

    try {
      const page = await Bridge.invoke('online_search', { query, videos, continuation });
      if (seq !== searchSeq) return; // a newer search superseded this one

      // null is the IPC-failure convention (Bridge swallows rejections) —
      // Airplane Mode and network errors both land here.
      if (page === null) {
        if (append) setLoadMoreState(false);
        else { setStatus('Search failed — check your connection (Airplane Mode?).'); renderResults([]); }
        return;
      }

      const tracks = page?.tracks || [];
      nextContinuation = page?.next_continuation || null;

      if (append) {
        appendResults(tracks);
      } else {
        tracks.forEach((t) => trackMap.set(t.video_id, t));
        renderResults(tracks);
        setStatus(tracks.length ? `${tracks.length} results` : `No results for “${query}”.`);
      }
      if (tracks.length) pushHistory(query);
      setLoadMoreState(false);
    } catch (e) {
      if (seq !== searchSeq) return;
      if (append) setLoadMoreState(false);
      else { setStatus(`Search failed: ${e}`); renderResults([]); }
    }
  }

  // ─── Typeahead (#59): InnerTube suggestions in a popover ────────────────────
  // API failure = Bridge returns null → popover stays closed, never an error.
  async function fetchSuggestions(q) {
    const seq = ++suggestSeq;
    const items = await Bridge.invoke('online_search_suggestions', { query: q });
    if (seq !== suggestSeq) return; // a newer keystroke superseded this one
    renderSuggestions(Array.isArray(items) ? items : []);
  }

  function renderSuggestions(items) {
    const box = document.getElementById('browse-suggest');
    if (!box) return;
    suggestIndex = -1;
    document.getElementById('browse-input')?.setAttribute('aria-expanded', 'false');
    if (!items.length) {
      box.hidden = true;
      box.innerHTML = '';
      return;
    }
    box.innerHTML = items.map((q, i) =>
      `<div class="suggest-item" role="option" id="suggest-opt-${i}" data-q="${escapeAttr(q)}" data-i="${i}">` +
      '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" class="suggest-icon"><circle cx="11" cy="11" r="8"/><path d="M21 21l-4.35-4.35"/></svg>' +
      `<span>${escapeHtml(q)}</span></div>`
    ).join('');
    box.hidden = false;
    // Combobox contract (#68 review nit): the input owns the list and
    // announces it as expanded once options exist.
    document.getElementById('browse-input')?.setAttribute('aria-expanded', 'true');
  }

  function closeSuggestions() {
    // Bump the seq: a suggestion response already in flight is stale the
    // moment the popover is dismissed (Escape/outside/commit) — without
    // this it would re-open the closed popover on arrival (#68 review).
    suggestSeq++;
    const box = document.getElementById('browse-suggest');
    if (box) {
      box.hidden = true;
      box.innerHTML = '';
    }
    suggestIndex = -1;
    const input = document.getElementById('browse-input');
    input?.setAttribute('aria-expanded', 'false');
    input?.removeAttribute('aria-activedescendant');
  }

  // ↑/↓ walks -1 (the input row) .. n-1 and wraps; returns false when there
  // is nothing to walk so the keypress falls through untouched.
  function moveSuggestion(delta) {
    const n = document.querySelectorAll('#browse-suggest .suggest-item').length;
    if (!n) return false;
    const pos = ((suggestIndex + 1 + delta) % (n + 1) + (n + 1)) % (n + 1);
    suggestIndex = pos - 1;
    const rows = document.querySelectorAll('#browse-suggest .suggest-item');
    rows.forEach((r, i) => r.setAttribute('aria-selected', String(i === suggestIndex)));
    // Combobox contract: the input points at the highlighted option.
    const input = document.getElementById('browse-input');
    if (suggestIndex >= 0 && rows[suggestIndex]?.id) {
      input?.setAttribute('aria-activedescendant', rows[suggestIndex].id);
    } else {
      input?.removeAttribute('aria-activedescendant');
    }
    return true;
  }

  function activeSuggestionQuery() {
    if (suggestIndex < 0) return null;
    const row = document.querySelectorAll('#browse-suggest .suggest-item')[suggestIndex];
    return row ? row.dataset.q : null;
  }

  function setStatus(text) {
    const el = document.getElementById('browse-status');
    if (el) el.textContent = text || '';
  }

  // ─── Search history (localStorage) ──────────────────────────────────────────
  function loadHistory() {
    try { return JSON.parse(localStorage.getItem(HISTORY_KEY) || '[]'); }
    catch (e) { return []; }
  }

  function pushHistory(query) {
    const q = query.trim();
    if (!q) return;
    let hist = loadHistory().filter((h) => h.toLowerCase() !== q.toLowerCase());
    hist.unshift(q);
    hist = hist.slice(0, 6);
    try { localStorage.setItem(HISTORY_KEY, JSON.stringify(hist)); } catch (e) {}
    renderHistoryChips();
  }

  function renderHistoryChips() {
    const wrap = document.getElementById('browse-hints');
    if (!wrap) return;
    const hist = loadHistory();
    const defaults = ['lofi beats', 'synthwave 80s', 'acoustic covers'];
    const queries = [...new Set([...hist, ...defaults])].slice(0, 7);
    wrap.innerHTML =
      '<span class="browse-hint-label">' + (hist.length ? 'Recent' : 'Try') + '</span>' +
      queries.map((q) =>
        `<button type="button" class="browse-chip" data-q="${escapeAttr(q)}">${escapeHtml(q)}</button>`
      ).join('');
    wrap.querySelectorAll('.browse-chip').forEach((chip) => {
      chip.addEventListener('click', () => {
        const input = document.getElementById('browse-input');
        input.value = chip.dataset.q;
        search(chip.dataset.q, { videos: currentVideos() });
      });
    });
  }

  function currentVideos() {
    const toggle = document.getElementById('browse-videos-toggle');
    return !!(toggle && toggle.classList.contains('active'));
  }

  // ─── Results rendering ──────────────────────────────────────────────────────
  function fmtTime(secs) {
    if (!secs && secs !== 0) return '';
    const m = Math.floor(secs / 60);
    const s = String(Math.floor(secs % 60)).padStart(2, '0');
    return `${m}:${s}`;
  }

  function rowHtml(t, i) {
    const dur = t.duration_secs ? fmtTime(t.duration_secs) : '';
    const sub = [t.artist, t.album, dur].filter(Boolean).join(' · ');
    const thumb = t.thumbnail
      ? `<img class="browse-thumb" src="${escapeAttr(t.thumbnail)}" alt="" loading="lazy" decoding="async"/>`
      : `<div class="browse-thumb browse-thumb-fallback">${svgMusic()}</div>`;
    const badge = t.cached ? '<span class="browse-cached-badge" title="Already downloaded">cached</span>' : '';
    return `
      <div class="browse-row" role="button" tabindex="0" data-video-id="${escapeAttr(t.video_id)}" data-idx="${i}"
           data-ctx="song" data-title="${escapeAttr(t.title)}" data-artist="${escapeAttr(t.artist || '')}"
           aria-label="Play ${escapeAttr(t.title)} by ${escapeAttr(t.artist)}">
        ${thumb}
        <div class="browse-meta">
          <div class="browse-row-title">${escapeHtml(t.title)} ${badge}</div>
          <div class="browse-row-sub">${escapeHtml(sub)}</div>
        </div>
        <button class="browse-enqueue" title="Add to queue (downloads in background)"
                aria-label="Add ${escapeAttr(t.title)} to queue">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 5v14M5 12h14"/></svg>
        </button>
        <button class="browse-play" title="Play now" aria-label="Play ${escapeAttr(t.title)}">
          <svg viewBox="0 0 24 24" fill="currentColor"><polygon points="8,5 20,12 8,19"/></svg>
        </button>
      </div>`;
  }

  function bindRows(scope) {
    scope.querySelectorAll('.browse-row').forEach((row) => {
      const track = trackMap.get(row.dataset.videoId);
      // Broken thumbnails fall back to the note placeholder — bound in JS
      // (an inline onerror attribute would need quote-safe SVG embedding).
      row.querySelector('img.browse-thumb')?.addEventListener('error', (e) => {
        e.currentTarget.outerHTML = `<div class="browse-thumb browse-thumb-fallback">${svgMusic()}</div>`;
      });
      const enqueueBtn = row.querySelector('.browse-enqueue');
      enqueueBtn?.addEventListener('click', (e) => {
        e.stopPropagation();
        enqueueTrack(track, enqueueBtn);
      });
      const activate = () => playTrack(track, row);
      row.addEventListener('click', activate);
      row.addEventListener('keydown', (e) => {
        if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); activate(); }
      });
    });
  }

  function renderResults(tracks) {
    const wrap = document.getElementById('browse-results');
    if (!wrap) return;
    if (!tracks.length) { wrap.innerHTML = ''; updateLoadMore(); return; }
    wrap.innerHTML = tracks.map((t, i) => rowHtml(t, i)).join('');
    bindRows(wrap);
    updateLoadMore();
  }

  function appendResults(tracks) {
    const wrap = document.getElementById('browse-results');
    if (!wrap) return;
    tracks.forEach((t) => trackMap.set(t.video_id, t));
    const frag = document.createElement('template');
    const base = wrap.querySelectorAll('.browse-row').length;
    frag.innerHTML = tracks.map((t, i) => rowHtml(t, base + i)).join('');
    wrap.appendChild(frag.content);
    bindRows(wrap);
    if (tracks.length) setStatus(`${base + tracks.length} results`);
    updateLoadMore();
  }

  function renderSkeleton() {
    const wrap = document.getElementById('browse-results');
    if (!wrap) return;
    wrap.innerHTML = Array.from({ length: 6 }, () => `
      <div class="browse-row skeleton">
        <div class="browse-thumb skel-box"></div>
        <div class="browse-meta">
          <div class="skel-box skel-line"></div>
          <div class="skel-box skel-line short"></div>
        </div>
      </div>`).join('');
    updateLoadMore();
  }

  function updateLoadMore() {
    const btn = document.getElementById('browse-load-more');
    if (!btn) return;
    btn.style.display = nextContinuation ? 'inline-flex' : 'none';
  }

  function setLoadMoreState(loading) {
    loadingMore = loading;
    const btn = document.getElementById('browse-load-more');
    if (btn) {
      btn.disabled = loading;
      btn.textContent = loading ? 'Loading…' : 'Load more';
    }
  }

  // ─── Playback / enqueue ──────────────────────────────────────────────────────
  async function playTrack(track, rowEl) {
    if (!track) return;
    trackMap.set(track.video_id, track);
    document.querySelectorAll('.browse-row.loading').forEach((r) => r.classList.remove('loading'));
    rowEl?.classList.add('loading');
    App.showToast(`Fetching “${track.title}”…`);
    const ok = await Bridge.invoke('online_play', { track });
    rowEl?.classList.remove('loading');
    if (ok !== null) {
      // Any successful online play means onboarding did its job — the
      // first-run chip row must not nag on the next launch (#58).
      try { localStorage.setItem('musico.onboarded', '1'); } catch { /* ignore */ }
      App.navigateTo('now-playing');
    }
  }

  // Enqueue pool: at most 3 background resolves at once — Queue-all on a
  // 50-track album must not spawn 50 concurrent yt-dlp processes.
  const enqueueQueue = [];
  let enqueueActive = 0;
  const ENQUEUE_CAP = 3;

  function pumpEnqueue() {
    while (enqueueActive < ENQUEUE_CAP && enqueueQueue.length) {
      const job = enqueueQueue.shift();
      enqueueActive += 1;
      job().finally(() => { enqueueActive -= 1; pumpEnqueue(); });
    }
  }

  async function doEnqueue(track, btnEl) {
    if (btnEl) btnEl.classList.add('busy');
    await Bridge.invoke('online_enqueue', { track });
    if (btnEl) btnEl.classList.remove('busy');
  }

  function enqueueTrack(track, btnEl, { silent = false } = {}) {
    if (!track) return Promise.resolve();
    if (!silent) App.showToast(`Queueing “${track.title}”…`);
    return new Promise((resolve) => {
      enqueueQueue.push(() => doEnqueue(track, btnEl).then(resolve));
      pumpEnqueue();
    });
  }

  // ─── Explicit downloads (C3.3) ───────────────────────────────────────────────
  // Downloads share the enqueue pool — N context-menu downloads must not
  // spawn N concurrent yt-dlp processes (same rule as Queue-all).
  async function downloadTrack(track) {
    if (!track) return Promise.resolve();
    markRow(track.video_id, 'dl-busy');
    return new Promise((resolve) => {
      enqueueQueue.push(async () => {
        const ok = await Bridge.invoke('online_download', { track });
        if (ok !== null) trackMap.set(track.video_id, track);
        resolve(ok);
      });
      pumpEnqueue();
    });
  }

  // Row badges for in-flight downloads (browse rows carry data-video-id).
  function markRow(videoId, cls, on = true) {
    if (!/^[A-Za-z0-9_-]{1,20}$/.test(videoId || '')) return;
    document.querySelectorAll(`[data-video-id="${videoId}"]`).forEach((row) => {
      row.classList.toggle(cls, on);
      if (cls === 'dl-busy' && !on) row.classList.add('dl-done');
    });
  }

  Bridge.listen('download-state', (e) => {
    const p = e.payload ?? e;
    if (p.stage === 'done') {
      markRow(p.videoId, 'dl-busy', false);
      App.showToast(`Downloaded \u201C${p.title}\u201D`);
    } else if (p.stage === 'failed') {
      markRow(p.videoId, 'dl-busy', false);
      App.showToast(`Download failed: ${p.message || p.title || 'unknown error'}`);
    }
  });

  // ─── online-status events (resolving / playing / error) ─────────────────────
  Bridge.listen('online-status', (e) => {
    const p = e.payload ?? e;
    if (p.stage === 'error') {
      App.showToast(`Online playback failed: ${p.message || 'unknown error'}`);
    } else if (p.stage === 'radio') {
      App.showToast('Smart radio: fetching related tracks…');
    }
  });

  // ─── Now-playing decoration for online tracks ────────────────────────────────
  // The engine reports `yt:<videoId>` songs without thumbnails; we know them.
  Bridge.listen('playback-event', (e) => {
    const p = e.payload ?? e;
    if (p.type !== 'Playing' || !p.song) return;
    if (!String(p.song.id).startsWith('yt:')) return;
    const track = trackMap.get(String(p.song.id).slice(3));
    if (!track?.thumbnail) return;
    const art = document.getElementById('np-art');
    if (!art) return;
    art.style.background = `url("${track.thumbnail}") center/cover no-repeat, var(--surface-raised)`;
    const icon = art.querySelector('.np-art-icon');
    if (icon) icon.style.display = 'none';
  });

  // ─── Fullscreen player (vinyl lives here and only here) ──────────────────────
  function setFullscreen(on) {
    document.body.classList.toggle('player-fullscreen', on);
    const btn = document.getElementById('btn-fullscreen');
    if (btn) btn.setAttribute('aria-pressed', String(on));
  }

  function toggleFullscreen() {
    setFullscreen(!document.body.classList.contains('player-fullscreen'));
  }

  function setupFullscreen() {
    document.getElementById('btn-fullscreen')?.addEventListener('click', toggleFullscreen);
    document.getElementById('btn-fs-close')?.addEventListener('click', () => setFullscreen(false));
    document.addEventListener('keydown', (e) => {
      if (e.key === 'Escape' && document.body.classList.contains('player-fullscreen')) {
        setFullscreen(false);
      }
    });
  }

  // ─── Wiring ──────────────────────────────────────────────────────────────────
  function svgMusic() {
    return '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><circle cx="12" cy="12" r="10"/><circle cx="12" cy="12" r="3"/></svg>';
  }

  function escapeHtml(s) {
    return String(s ?? '').replace(/[&<>"']/g, (c) => ({
      '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;',
    }[c]));
  }

  function escapeAttr(s) { return escapeHtml(s); }

  function init() {
    const form = document.getElementById('browse-form');
    const input = document.getElementById('browse-input');

    form?.addEventListener('submit', (e) => {
      e.preventDefault();
      clearTimeout(debounceTimer);
      clearTimeout(suggestTimer);
      // A highlighted suggestion wins over the raw input text (#59).
      const q = (activeSuggestionQuery() ?? input.value).trim();
      closeSuggestions();
      if (q) search(q, { videos: currentVideos() });
    });

    // Live search: debounce so fast typing fires one request, min 2 chars.
    // Suggestions ride the same input on a faster 200 ms debounce (#59).
    input?.addEventListener('input', () => {
      clearTimeout(debounceTimer);
      clearTimeout(suggestTimer);
      const q = input.value.trim();
      if (q.length < 2) {
        closeSuggestions();
        return;
      }
      suggestTimer = setTimeout(() => fetchSuggestions(q), SUGGEST_DEBOUNCE_MS);
      debounceTimer = setTimeout(() => search(q, { videos: currentVideos() }), DEBOUNCE_MS);
    });

    // Combobox wiring (#68 review nit): the input owns the options list.
    input?.setAttribute('role', 'combobox');
    input?.setAttribute('aria-autocomplete', 'list');
    input?.setAttribute('aria-controls', 'browse-suggest');
    input?.setAttribute('aria-expanded', 'false');

    // Typeahead keyboard nav (#59): ↑/↓ highlight, Enter commits via the
    // submit handler, Escape closes and keeps focus in the input.  Escape
    // also preventDefaults (#68 review nit): input[type=search]'s native
    // Escape-clears-value would empty the query the user meant to keep.
    input?.addEventListener('keydown', (e) => {
      if (e.key === 'ArrowDown') {
        if (moveSuggestion(1)) e.preventDefault();
      } else if (e.key === 'ArrowUp') {
        if (moveSuggestion(-1)) e.preventDefault();
      } else if (e.key === 'Escape') {
        // Only intercept while the popover is open — otherwise the native
        // Escape-clears-search-box affordance keeps working.
        if (document.querySelector('#browse-suggest .suggest-item')) e.preventDefault();
        closeSuggestions();
      }
    });

    // Pointerdown (not click): commits before the input's blur can close the
    // popover, and preventDefault keeps keyboard focus in the input.
    document.getElementById('browse-suggest')?.addEventListener('pointerdown', (e) => {
      const row = e.target.closest('.suggest-item');
      if (!row) return;
      e.preventDefault();
      const q = row.dataset.q;
      // Clear BOTH pending timers: the 350 ms live-search captured the raw
      // prefix and would otherwise overwrite the committed search with the
      // stale query (#68 review).
      clearTimeout(debounceTimer);
      clearTimeout(suggestTimer);
      closeSuggestions();
      if (q) {
        input.value = q;
        search(q, { videos: currentVideos() });
      }
    });

    // Click anywhere outside the search box dismisses the popover.
    document.addEventListener('pointerdown', (e) => {
      if (!e.target.closest('#browse-form')) closeSuggestions();
    });

    // '/' focuses search from anywhere (except while typing in another field).
    document.addEventListener('keydown', (e) => {
      if (e.key !== '/' || e.ctrlKey || e.metaKey || e.altKey) return;
      const tag = document.activeElement?.tagName?.toLowerCase();
      if (tag === 'input' || tag === 'textarea' || tag === 'search') return;
      e.preventDefault();
      App.navigateTo('browse');
      input?.focus();
      input?.select();
    });

    // Songs / Videos filter pill.
    const toggle = document.getElementById('browse-videos-toggle');
    toggle?.addEventListener('click', () => {
      toggle.classList.toggle('active');
      const videos = currentVideos();
      toggle.setAttribute('aria-pressed', String(videos));
      toggle.textContent = videos ? 'Videos' : 'Songs';
      const q = input?.value.trim();
      if (q && q.length >= 2) search(q, { videos });
    });

    // Load more (InnerTube continuation).
    document.getElementById('browse-load-more')?.addEventListener('click', () => {
      if (loadingMore || !nextContinuation || !lastQuery) return;
      search(lastQuery, { videos: lastVideos, continuation: nextContinuation, append: true });
    });

    renderHistoryChips();
    setupFullscreen();

    // Surface yt-dlp availability once (helps first-run setup).
    Bridge.invoke('online_available').then((ok) => {
      if (!ok) {
        setStatus('yt-dlp is not installed (pacman -S yt-dlp) — playback still works via the direct-source fast path while instances are healthy.');
      }
    });
  }

  // ─── Context-menu actions (C3.1 play-from-anywhere) ─────────────────────────
  async function playNext(track) {
    if (!track) return;
    const ok = await Bridge.invoke('online_play_next', { track });
    if (ok !== null) {
      trackMap.set(track.video_id, track);
      App.showToast(`Playing \u201C${track.title}\u201D next\u2026`);
    }
  }

  async function startRadio(track) {
    if (!track) return;
    const ok = await Bridge.invoke('online_start_radio', { track });
    if (ok !== null) {
      trackMap.set(track.video_id, track);
      App.showToast(`Starting radio from \u201C${track.title}\u201D\u2026`);
      App.navigateTo('now-playing');
    }
  }

  document.addEventListener('DOMContentLoaded', init);

  return { search, playTrack, enqueueTrack, playNext, startRadio, downloadTrack, toggleFullscreen, trackMap };
})();
window.Online = Online;
