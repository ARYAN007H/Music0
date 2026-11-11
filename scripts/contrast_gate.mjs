/* ══════════════════════════════════════════════════════════════════════════════
   MUSICO ONLINE — scripts/contrast_gate.mjs
   Programmatic WCAG contrast gate: text-on-surface pairs must be ≥ 4.5:1 in
   BOTH themes.  Reads real computed values from the live page across every
   palette × theme combination.  Exit 1 on any failure.
   ══════════════════════════════════════════════════════════════════════════════ */
// playwright (with bundled browser management) when available; playwright-core
// is enough for this gate — it launches the system chrome via channel: 'chrome'
// and downloads nothing.
let chromium;
try {
  ({ chromium } = await import('playwright'));
} catch {
  ({ chromium } = await import('playwright-core'));
}

const PORT = process.argv[2] || '8145';
const BASE = `http://127.0.0.1:${PORT}`;
const PALETTES = ['nebula', 'sakura', 'aurora', 'ocean', 'ember', 'mono'];
const THEMES = ['light', 'dark'];

function parseColor(cRaw) {
  let c = cRaw.trim();
  if (c.startsWith('rgba') || c.startsWith('rgb')) {
    const f = c.match(/[\d.]+/g).map(Number);
    return { r: f[0], g: f[1], b: f[2], a: c.startsWith('rgba') ? (f[3] ?? 1) : 1 };
  }
  if (c.startsWith('color(')) {
    // e.g. color(srgb 0.12 0.34 0.56 / 0.9) — floats in 0..1
    const nums = c.match(/[\d.]+/g) || [];
    const [r, g, b, a = 1] = nums.map(Number);
    return { r: r * 255, g: g * 255, b: b * 255, a };
  }
  const hex = c.replace('#', '');
  const full = hex.length === 3 ? hex.split('').map((ch) => ch + ch).join('') : hex.slice(0, 6);
  return {
    r: parseInt(full.slice(0, 2), 16),
    g: parseInt(full.slice(2, 4), 16),
    b: parseInt(full.slice(4, 6), 16),
    a: 1,
  };
}

function lum(cRaw) {
  const { r, g, b, a } = parseColor(cRaw);
  // blend any alpha over mid-grey so the ratio is measurable
  const R = r * a + 128 * (1 - a);
  const G = g * a + 128 * (1 - a);
  const B = b * a + 128 * (1 - a);
  const f = (v) => {
    v /= 255;
    return v <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
  };
  return 0.2126 * f(R) + 0.7152 * f(G) + 0.0722 * f(B);
}

function ratio(fg, bg) {
  const [l1, l2] = [lum(fg), lum(bg)].sort((a, b) => b - a);
  return (l1 + 0.05) / (l2 + 0.05);
}

function blend(fgRaw, bgRaw) {
  // alpha-composite an fg over a solid bg, returning a concrete rgb() string
  const f = parseColor(fgRaw);
  const b = parseColor(bgRaw);
  const mix = (i) => Math.round(f[i] * f.a + b[i] * (1 - f.a));
  return `rgb(${mix('r')}, ${mix('g')}, ${mix('b')})`;
}

const browser = await chromium.launch({ channel: 'chrome', headless: true });
const page = await browser.newPage();
await page.goto(`${BASE}/index.html`);
await page.waitForTimeout(800);

const results = [];
let failures = 0;

// The M3 Expressive skin re-values every token under html[data-skin="m3e"],
// so its color pairs get the same gate as the default Cupertino look.
const SKINS = [null, 'm3e'];

for (const theme of THEMES) {
  for (const palette of PALETTES) {
    for (const skin of SKINS) {
    await page.evaluate(([t, p, s]) => {
      document.documentElement.setAttribute('data-theme', t);
      document.documentElement.setAttribute('data-palette', p);
      if (s) document.documentElement.setAttribute('data-skin', s);
      else document.documentElement.removeAttribute('data-skin');
    }, [theme, palette, skin]);
    await page.waitForTimeout(60);

    const pairs = await page.evaluate(() => {
      // Tokens may hold unresolved functions (color-mix() in the M3E skin) —
      // getPropertyValue on an unregistered custom property returns the token
      // string verbatim. Resolve each through a probe element's computed
      // color, which is always a concrete rgb()/rgba() string.
      const probe = document.getElementById('contrast-probe') ||
        (() => {
          const el = document.createElement('div');
          el.id = 'contrast-probe';
          el.style.display = 'none';
          document.body.appendChild(el);
          return el;
        })();
      const resolve = (token) => {
        probe.style.color = `var(${token})`;
        return getComputedStyle(probe).color;
      };
      const surfaces = {
        base: resolve('--surface-base'),
        raised: resolve('--surface-raised'),
      };
      const texts = {
        primary: resolve('--text-primary'),
        secondary: resolve('--text-secondary'),
        muted: resolve('--text-muted'),
      };
      return { surfaces, texts };
    });

    const label = `${theme}/${palette}${skin ? '/m3e' : ''}`;
    const check = (name, fgRaw, bgRaw, min = 4.5) => {
      // --text-* values may be rgba(); blend over the surface before ratio.
      const fg = fgRaw.startsWith('rgba') ? blend(fgRaw, bgRaw) : fgRaw;
      const r = ratio(fg, bgRaw);
      const ok = r >= min;
      if (!ok) failures += 1;
      results.push(`${ok ? 'PASS' : 'FAIL'} ${label} ${name}: ${r.toFixed(2)}:1 (min ${min})`);
    };

    for (const [tName, tVal] of Object.entries(pairs.texts)) {
      for (const [sName, sVal] of Object.entries(pairs.surfaces)) {
        check(`${tName} on ${sName}`, tVal, sVal);
      }
    }
    }
  }
}

await browser.close();
const fails = results.filter((r) => r.startsWith('FAIL'));
const passCount = results.length - fails.length;
console.log(`\nContrast gate: ${passCount} passed, ${fails.length} failed (min 4.5:1)`);
if (fails.length) console.log(fails.join('\n'));
process.exit(fails.length ? 1 : 0);
