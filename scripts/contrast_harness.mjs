/* ══════════════════════════════════════════════════════════════════════════════
   MUSICO ONLINE — scripts/contrast_harness.mjs
   Zero-dependency WCAG contrast harness: serves frontend/ over plain node
   http, drives the real browser (google-chrome --headless --dump-dom) against
   an in-page harness that measures EVERY palette × theme × skin combination
   through the live computed values, and prints PASS/FAIL lines.

   Companion to contrast_gate.mjs (the playwright variant). This one exists so
   the same gate runs on machines without playwright installed. Exit 1 on any
   failure.

   Usage: node scripts/contrast_harness.mjs [chrome-binary]
   ══════════════════════════════════════════════════════════════════════════════ */
import http from 'node:http';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { spawnSync } from 'node:child_process';

const FRONTEND = path.join(path.dirname(fileURLToPath(import.meta.url)), '..', 'frontend');
const PORT = 8147;

const MIME = {
  '.html': 'text/html', '.css': 'text/css', '.js': 'text/javascript',
  '.mjs': 'text/javascript', '.json': 'application/json', '.svg': 'image/svg+xml',
  '.png': 'image/png', '.ico': 'image/x-icon', '.woff2': 'font/woff2',
};

const server = http.createServer((req, res) => {
  const [url, query] = (req.url || '').split('?');
  if (url.startsWith('/__contrast')) {
    res.writeHead(200, { 'Content-Type': 'text/html' });
    res.end(HARNESS);
    return;
  }
  // The harness measures color tokens, not webfonts — strip @import lines
  // (the Google Fonts fetch stalls virtual time and hangs the dump).
  if (query === 'nofonts' && url.endsWith('.css')) {
    const file = path.normalize(path.join(FRONTEND, url));
    import('node:fs').then((fs) => {
      fs.readFile(file, 'utf8', (err, data) => {
        if (err) { res.writeHead(404); res.end(); return; }
        res.writeHead(200, { 'Content-Type': 'text/css' });
        // Strip whole @import lines — the URLs contain semicolons
        // (wght@400;500), so a semicolon-bounded regex would leave garbage.
        res.end(data.replace(/^@import.*$/gm, ''));
      });
    });
    return;
  }
  const rel = decodeURIComponent(url);
  const file = path.normalize(path.join(FRONTEND, rel === '/' ? 'index.html' : rel));
  if (!file.startsWith(FRONTEND)) { res.writeHead(403); res.end(); return; }
  import('node:fs').then((fs) => {
    fs.readFile(file, (err, data) => {
      if (err) { res.writeHead(404); res.end('not found'); return; }
      res.writeHead(200, { 'Content-Type': MIME[path.extname(file)] || 'application/octet-stream' });
      res.end(data);
    });
  });
});

/* The harness page: links the real stylesheet cascade (no app scripts — the
   tokens live entirely in CSS), then flips every skin × palette × theme and
   measures the six core text/surface pairs via a probe element. */
const HARNESS = `<!DOCTYPE html><html data-palette="nebula"><head>
<link rel="stylesheet" href="/css/tokens.css?nofonts=1"/>
<link rel="stylesheet" href="/css/neumorphic.css"/>
<link rel="stylesheet" href="/css/animations.css"/>
<link rel="stylesheet" href="/css/layout.css"/>
<link rel="stylesheet" href="/css/now-playing.css"/>
<link rel="stylesheet" href="/css/library.css"/>
<link rel="stylesheet" href="/css/queue.css"/>
<link rel="stylesheet" href="/css/settings.css"/>
<link rel="stylesheet" href="/css/online.css"/>
<link rel="stylesheet" href="/css/topbar.css"/>
<link rel="stylesheet" href="/css/queue-panel.css"/>
<link rel="stylesheet" href="/css/am-clean.css"/>
<link rel="stylesheet" href="/css/liquid.css"/>
<link rel="stylesheet" href="/css/apple.css"/>
<link rel="stylesheet" href="/css/native.css"/>
<link rel="stylesheet" href="/css/m3-expressive.css?nofonts=1"/>
</head><body><pre id="out">PENDING</pre>
<script>
const PALETTES = ['nebula', 'sakura', 'aurora', 'ocean', 'ember', 'mono', 'music'];
const THEMES = ['light', 'dark'];
const SKINS = [null, 'm3e'];

function parseColor(c) {
  c = c.trim();
  if (c.startsWith('rgb')) {
    const f = c.match(/[\\d.]+/g).map(Number);
    return { r: f[0], g: f[1], b: f[2], a: c.startsWith('rgba') ? (f[3] ?? 1) : 1 };
  }
  if (c.startsWith('color(')) {
    const [r, g, b, a = 1] = (c.match(/[\\d.]+/g) || []).map(Number);
    return { r: r * 255, g: g * 255, b: b * 255, a };
  }
  const hex = c.replace('#', '');
  const full = hex.length === 3 ? hex.split('').map((ch) => ch + ch).join('') : hex.slice(0, 6);
  return { r: parseInt(full.slice(0, 2), 16), g: parseInt(full.slice(2, 4), 16), b: parseInt(full.slice(4, 6), 16), a: 1 };
}
function lum(raw) {
  const { r, g, b, a } = parseColor(raw);
  const f = (v) => { v /= 255; return v <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4); };
  return 0.2126 * f(r * a + 128 * (1 - a)) + 0.7152 * f(g * a + 128 * (1 - a)) + 0.0722 * f(b * a + 128 * (1 - a));
}
function ratio(fg, bg) {
  const [l1, l2] = [lum(fg), lum(bg)].sort((a, b) => b - a);
  return (l1 + 0.05) / (l2 + 0.05);
}
function blend(fg, bg) {
  const f = parseColor(fg), b = parseColor(bg);
  const mix = (k) => Math.round(f[k] * f.a + b[k] * (1 - f.a));
  return 'rgb(' + mix('r') + ',' + mix('g') + ',' + mix('b') + ')';
}

async function run() {
  try {
  const probe = document.createElement('div');
  probe.style.display = 'none';
  document.body.appendChild(probe);
  const resolve = (token) => {
    probe.style.color = 'var(' + token + ')';
    return getComputedStyle(probe).color;
  };
  const lines = [];
  for (const skin of SKINS) {
    for (const theme of THEMES) {
      for (const palette of PALETTES) {
        document.documentElement.setAttribute('data-theme', theme);
        document.documentElement.setAttribute('data-palette', palette);
        if (skin) document.documentElement.setAttribute('data-skin', skin);
        else document.documentElement.removeAttribute('data-skin');
        await new Promise((res) => requestAnimationFrame(() => res()));
        const surfaces = { base: resolve('--surface-base'), raised: resolve('--surface-raised') };
        const texts = { primary: resolve('--text-primary'), secondary: resolve('--text-secondary'), muted: resolve('--text-muted') };
        const label = theme + '/' + palette + (skin ? '/m3e' : '');
        for (const [tName, tVal] of Object.entries(texts)) {
          for (const [sName, sVal] of Object.entries(surfaces)) {
            const fg = tVal.startsWith('rgba') ? blend(tVal, sVal) : tVal;
            const r = ratio(fg, sVal);
            const ok = r >= 4.5;
            lines.push((ok ? 'PASS' : 'FAIL') + ' ' + label + ' ' + tName + ' on ' + sName + ': ' + r.toFixed(2) + ':1');
          }
        }
      }
    }
  }
  const fails = lines.filter((l) => l.startsWith('FAIL'));
  lines.push('SUMMARY ' + (lines.length - fails.length) + ' passed, ' + fails.length + ' failed');
  lines.push(...fails);
  document.getElementById('out').textContent = lines.join('\\n');
  } catch (err) {
    document.getElementById('out').textContent = 'HARNESS-ERROR ' + (err && err.message ? err.message : String(err));
  }
}
run();
</script></body></html>`;

await new Promise((res) => server.listen(PORT, '127.0.0.1', res));

const chrome = process.argv[2] || 'google-chrome-stable';
// A throwaway profile: the user's real chrome may be running, and a same-
// profile launch delegates to it and exits 0 with EMPTY stdout.
const profile = (await import('node:fs')).mkdtempSync('/tmp/musico-contrast-');
const proc = spawnSync(chrome, [
  '--headless=new', '--disable-gpu', '--no-sandbox', `--user-data-dir=${profile}`,
  // Virtual time, NOT --timeout: headless dumps reliably at budget expiry,
  // while --timeout + dump-dom can exit with empty stdout when the page's
  // load event never settles (the app's preview-mode API fetches don't).
  '--virtual-time-budget=10000', '--dump-dom', `http://127.0.0.1:${PORT}/__contrast`,
], { encoding: 'utf8', timeout: 60000, maxBuffer: 64 * 1024 * 1024 });

server.close();

const dom = proc.stdout || '';
const start = dom.indexOf('<pre id="out">');
const end = dom.indexOf('</pre>');
const text = start >= 0 && end > start ? dom.slice(start + 14, end).trim() : '';
if (!text || text === 'PENDING') {
  const dump = process.env.MUSICO_HARNESS_DEBUG
    ? '/tmp/musico-contrast-dom.html' : null;
  if (dump) { (await import('node:fs')).writeFileSync(dump, dom || '(empty)'); }
  console.error('Harness produced no results.' + (dump ? ` DOM dumped to ${dump}` : ''));
  console.error(`chrome status=${proc.status} signal=${proc.signal} error=${(proc.error || {}).message}`);
  console.error('stderr:', (proc.stderr || '').slice(0, 600));
  process.exit(2);
}
const fails = text.split('\n').filter((l) => l.startsWith('FAIL'));
const summary = text.split('\n').find((l) => l.startsWith('SUMMARY'));
console.log('Contrast harness (chrome CLI):', summary);
if (fails.length) console.log(fails.join('\n'));
process.exit(fails.length ? 1 : 0);
