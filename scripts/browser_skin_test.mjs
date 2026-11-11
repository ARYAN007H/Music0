/* ══════════════════════════════════════════════════════════════════════════════
   MUSICO ONLINE — scripts/browser_skin_test.mjs
   The design-language (skin) system: data-skin flip, persistence, live
   re-skin, and the M3 Expressive token takeover. Runs against the static
   preview (bridge mock mode), port 8143 by default.
   ══════════════════════════════════════════════════════════════════════════════ */
// playwright when available; playwright-core + the system chrome
// (channel: 'chrome') is enough and downloads nothing.
let chromium;
try {
  ({ chromium } = await import('playwright'));
} catch {
  ({ chromium } = await import('playwright-core'));
}
const PORT = process.argv[2] || '8143';
const browser = await chromium.launch({ channel: 'chrome', headless: true });
const page = await browser.newPage();
const errors = [];
page.on('pageerror', (e) => errors.push(e.message));
// Start on the M3E skin via the persisted localStorage (the same path the
// pre-paint script in index.html uses to avoid a flash of the wrong skin).
await page.addInitScript(() => {
  try { localStorage.setItem('musico-skin', 'm3e'); } catch (e) {}
});
await page.goto(`http://127.0.0.1:${PORT}/index.html`);
// Real readiness, not a blind sleep: the shell must have rendered nav.
// M3E hides the sidebar — readiness is the pill island (the shell under test).
await page.waitForSelector('#m3e-pill .nav-item', { timeout: 10000 });
await page.waitForTimeout(300);
const results = [];
const check = (n, c) => results.push(`${c ? 'PASS' : 'FAIL'} ${n}`);

// 1. The pre-paint attribute: applied before stylesheets resolved.
check('data-skin=m3e from localStorage', await page.evaluate(() =>
  document.documentElement.getAttribute('data-skin') === 'm3e'));

// 1b. Webfonts load only under the skin (pre-paint injected them).
check('M3E webfont stylesheet injected', await page.evaluate(() =>
  !!document.getElementById('m3e-fonts')));

// 2. Settings (mock bridge has no skin) must not flip it back.
check('applySettings respects the skin', await page.evaluate(() =>
  document.documentElement.getAttribute('data-skin') === 'm3e'));

// 3. M3E tokens actually take over: nav item active background resolves to a
//    concrete color and differs from the iOS accent selection.
const navBg = await page.evaluate(() => {
  const el = document.querySelector('#sidebar-root .nav-item.active') ||
    document.querySelector('.nav-item.active');
  if (!el) return null;
  return getComputedStyle(el).backgroundColor;
});
check('M3E active nav has a computed background', !!navBg && navBg !== 'rgba(0, 0, 0, 0)');

// 4. The radius scale is the M3 shape scale (extra-large = 28px).
check('M3E radius-xl is 28px', await page.evaluate(() =>
  getComputedStyle(document.documentElement).getPropertyValue('--radius-xl').trim() === '28px'));

// 5. The emphasized easing token is in place.
check('M3E emphasized easing present', await page.evaluate(() =>
  getComputedStyle(document.documentElement).getPropertyValue('--ease-emphasized').includes('cubic-bezier')));

// 6. Live flip: clicking the iOS pill re-skins instantly (no reload). The
//    Bridge is wrapped first so the persisted payload can be asserted.
await page.evaluate(() => {
  window.__skinSaves = [];
  const orig = window.Bridge.invoke.bind(window.Bridge);
  window.Bridge.invoke = (cmd, args) => {
    if (cmd === 'save_settings' && args?.settings?.skin !== undefined) {
      window.__skinSaves.push(args.settings.skin);
    }
    return orig(cmd, args);
  };
});
const flipped = await page.evaluate(() => {
  const pill = document.querySelector('[data-skin-pill="ios"]');
  if (!pill) return 'no-pill';
  pill.click();
  return document.documentElement.getAttribute('data-skin');
});
check('pill click flips to iOS live', flipped === 'ios');
check('pill click persists localStorage', await page.evaluate(() =>
  localStorage.getItem('musico-skin') === 'ios'));
check('skin pill active state follows', await page.evaluate(() => {
  const active = document.querySelector('[data-skin-pill].active');
  return !!active && active.dataset.skinPill === 'ios';
}));

// 7. Back to M3E through the other pill.
check('pill click flips back to M3E', await page.evaluate(() => {
  document.querySelector('[data-skin-pill="m3e"]')?.click();
  return document.documentElement.getAttribute('data-skin') === 'm3e';
}));

// 8. The persisted settings payload carried the skin to save_settings
//    (ios then m3e — one per click).
check('save_settings received both skins', await page.evaluate(() =>
  window.__skinSaves.join(',') === 'ios,m3e'));

// 9. Dark theme under the skin still resolves readable tokens (sanity — the
//    full WCAG matrix lives in contrast_gate.mjs).
check('M3E dark token resolves', await page.evaluate(() => {
  document.documentElement.setAttribute('data-theme', 'dark');
  const probe = document.createElement('div');
  probe.style.display = 'none';
  document.body.appendChild(probe);
  probe.style.color = 'var(--text-primary)';
  const c = getComputedStyle(probe).color;
  document.documentElement.setAttribute('data-theme', 'light');
  return (/^(rgb|color)\(/.test(c)) && c !== 'rgba(0, 0, 0, 0)';
}));

// ── 4. The shell restructure: sidebar + top bar OUT, pill island IN ──
check('sidebar hidden under M3E', await page.evaluate(() =>
  getComputedStyle(document.getElementById('sidebar')).display === 'none'));
check('top bar hidden under M3E', await page.evaluate(() =>
  getComputedStyle(document.getElementById('topbar-root')).display === 'none'));
check('pill island rendered with 5 destinations', await page.evaluate(() => {
  const pill = document.getElementById('m3e-pill');
  return pill && !pill.hidden && pill.querySelectorAll('.nav-item').length === 5;
}));
check('pill Home is active on load', await page.evaluate(() =>
  document.querySelector('#m3e-pill .nav-item[data-view="browse"]')?.classList.contains('active')));
await page.click('#m3e-pill .nav-item[data-view="genres"]');
await page.waitForTimeout(250);
check('pill click routes to Browse', await page.evaluate(() =>
  document.getElementById('view-genres')?.classList.contains('active')));
check('pill active state follows the route', await page.evaluate(() =>
  document.querySelector('#m3e-pill .nav-item[data-view="genres"]')?.classList.contains('active')));
await page.click('#m3e-pill .nav-item[data-view="browse"]');
await page.waitForTimeout(250);
await page.evaluate(() => document.querySelector('[data-skin-pill="ios"]')?.click());
await page.waitForTimeout(350);
check('iOS skin restores the sidebar', await page.evaluate(() =>
  getComputedStyle(document.getElementById('sidebar')).display !== 'none'));
check('iOS skin hides the pill island', await page.evaluate(() =>
  document.getElementById('m3e-pill').hidden));
await page.evaluate(() => document.querySelector('[data-skin-pill="m3e"]')?.click());
await page.waitForTimeout(350);
check('M3E skin re-hides the sidebar', await page.evaluate(() =>
  getComputedStyle(document.getElementById('sidebar')).display === 'none'));

console.log(results.join('\n'));
console.log(`JS errors: ${errors.length ? '\n' + errors.join('\n') : 'none'}`);
await browser.close();
const fails = results.filter((r) => r.startsWith('FAIL'));
console.log(`Skin test: ${results.length - fails.length} passed, ${fails.length} failed`);
process.exit(fails.length || errors.length ? 1 : 0);
