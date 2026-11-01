import { chromium } from 'playwright';
const browser = await chromium.launch({ channel: 'chrome', headless: true });
const page = await browser.newPage();
const errors = [];
page.on('pageerror', (e) => errors.push(e.message));
await page.goto('http://127.0.0.1:8143/index.html');
await page.waitForTimeout(1000);
const results = [];
const check = (n, c) => results.push(`${c ? 'PASS' : 'FAIL'} ${n}`);

// Original 15 assertions (condensed re-run)
check('sidebar rendered', await page.evaluate(() => document.querySelectorAll('#sidebar-root .nav-item').length >= 6));
check('default route #/browse', await page.evaluate(() => location.hash === '#/browse'));
await page.click('#sidebar-root .nav-item[data-view="library"]');
await page.waitForTimeout(150);
check('library nav', await page.evaluate(() => location.hash === '#/library'));
await page.click('#sidebar-root .nav-item[data-view="made"]');
await page.waitForTimeout(150);
await page.click('#nav-back');
await page.waitForTimeout(150);
check('back works', await page.evaluate(() => location.hash === '#/library'));
await page.click('#nav-forward');
await page.waitForTimeout(150);
check('forward works', await page.evaluate(() => location.hash === '#/made'));

// Review fix 1, superseded by C4.1c: the merged Songs list replaces the
// legacy list/grid toggle — verify it stays hidden and navigation works.
await page.click('#sidebar-root .nav-item[data-view="library"]');
await page.waitForTimeout(150);
await page.evaluate(() => window.MusicoStore.set('libTab', 'songs'));
await page.waitForTimeout(200);
check('library view stays active', await page.evaluate(() =>
  document.getElementById('view-library').classList.contains('active')));
check('legacy list/grid toggle stays hidden', await page.evaluate(() =>
  document.querySelector('#view-library .view-toggle').style.display === 'none'));
check('merged songs rows render', await page.evaluate(() =>
  document.querySelectorAll('.lib-rows .lib-row').length >= 8));

// Review fix 2: LCD tracks PositionUpdate + Seeked
await page.evaluate(async () => {
  await Bridge.invoke('online_play', { track: { video_id: 't1', title: 'T', artist: 'A', album: '', duration_secs: 200 } });
});
await page.waitForTimeout(200);
await page.evaluate(() => App.handlePlaybackEvent({ type: 'PositionUpdate', position_secs: 42 }));
await page.waitForTimeout(150);
check('LCD position updates (0:42)', await page.evaluate(() =>
  [...document.querySelectorAll('.lcd-time')][0].textContent === '0:42'));
check('LCD seek fill reflects position', await page.evaluate(() =>
  Number(document.querySelector('.lcd-seek').value) > 100));
await page.evaluate(() => App.handlePlaybackEvent({ type: 'Seeked', position_secs: 120 }));
await page.waitForTimeout(150);
check('LCD tracks Seeked (2:00)', await page.evaluate(() =>
  [...document.querySelectorAll('.lcd-time')][0].textContent === '2:00'));

// Review fix 3: seek sends camelCase
await page.evaluate(() => {
  const orig = Bridge.invoke;
  window.__seekArgs = null;
  Bridge.invoke = (cmd, args) => { if (cmd === 'seek') window.__seekArgs = args; return orig(cmd, args); };
});
await page.evaluate(() => App.transport.seek(30));
await page.waitForTimeout(100);
check('seek sends positionSecs (camelCase)', await page.evaluate(() =>
  window.__seekArgs && window.__seekArgs.positionSecs === 30 && !('position_secs' in window.__seekArgs)));

// Review fix 4: single module instance
check('store module not double-evaluated', await page.evaluate(() => {
  const entries = performance.getEntriesByType('resource').filter(r => r.name.includes('core/store.js'));
  return entries.length === 1;
}));

// Regression: play/pause + search + fullscreen
const before = await page.evaluate(() => !!document.querySelector('.lcd-play svg polygon'));
await page.click('.lcd-play');
await page.waitForTimeout(200);
const after = await page.evaluate(() => !!document.querySelector('.lcd-play svg polygon'));
check('LCD play/pause toggles', before !== after);
// Search moved to its own view (AM layout) — open it, then search.
await page.click('#sidebar-root .nav-item[data-view="search"]');
await page.waitForTimeout(150);
await page.fill('#browse-input', 'teardrop');
await page.waitForTimeout(600);
check('browse search works', await page.evaluate(() =>
  document.querySelectorAll('#browse-results .browse-row').length > 0));
// The pill's explicit door to the big player (#66): art/title region AND the
// expand button both navigate to the expanded Now Playing view.
await page.evaluate(() => App.navigateTo('browse'));
await page.waitForTimeout(150);
await page.click('.transport-left .lcd-open');
check('pill art/title opens the full player', await page.evaluate(() =>
  document.getElementById('view-now-playing')?.classList.contains('active')));
await page.evaluate(() => App.navigateTo('browse'));
await page.waitForTimeout(150);
await page.click('#btn-open-player');
check('pill expand opens the full player', await page.evaluate(() =>
  document.getElementById('view-now-playing')?.classList.contains('active')));
check('pill hides inside the full player', await page.evaluate(() =>
  !document.querySelector('.transport-bar')));
await page.click('#btn-fullscreen');
check('fullscreen vinyl works', await page.evaluate(() =>
  document.body.classList.contains('player-fullscreen')));
await page.keyboard.press('Escape');
await page.setViewportSize({ width: 700, height: 800 });
await page.waitForTimeout(200);
check('topbar hidden compact', await page.evaluate(() =>
  getComputedStyle(document.getElementById('topbar-root')).display === 'none'));
// Single-player rule: the expanded Now Playing view replaces the pill —
// showing both duplicated every control.
check('pill hidden while expanded player is open', await page.evaluate(() =>
  !document.querySelector('#transport-root .transport-bar')));
await page.evaluate(() => App.navigateTo('browse'));
await page.waitForTimeout(150);
check('transport pill renders on browse pages', await page.evaluate(() =>
  !!document.querySelector('#transport-root .transport-bar')));

// T10 native window chrome. Stubbed pages exercise both platform layers
// pre-paint (addInitScript): MacIntel → native traffic lights (chrome strip
// is a drag-safe spacer), Win32 → drawn min/max/close. The first page (Linux)
// must stay untouched: no platform class, no chrome strip, no sidebar pad.
check('non-mac page has no platform class', await page.evaluate(() =>
  !document.documentElement.classList.contains('is-mac') &&
  !document.documentElement.classList.contains('is-windows')));
check('non-mac window chrome hidden', await page.evaluate(() =>
  getComputedStyle(document.querySelector('.window-chrome')).display === 'none'));

const macErrors = [];
const macPage = await browser.newPage();
macPage.on('pageerror', (e) => macErrors.push(e.message));
await macPage.addInitScript(() => {
  Object.defineProperty(Navigator.prototype, 'platform', { get: () => 'MacIntel' });
});
await macPage.goto('http://127.0.0.1:8143/index.html');
await macPage.waitForTimeout(800);
const mcheck = (n, c) => results.push(`${c ? 'PASS' : 'FAIL'} ${n}`);
mcheck('is-mac class set pre-paint', await macPage.evaluate(() =>
  document.documentElement.classList.contains('is-mac')));
mcheck('SF Pro stack on mac', await macPage.evaluate(() =>
  getComputedStyle(document.documentElement).getPropertyValue('--font-display').includes('-apple-system')));
mcheck('mac chrome strip: fixed drag space, no drawn controls', await macPage.evaluate(() => {
  const s = getComputedStyle(document.querySelector('.window-chrome'));
  return s.display === 'block' && s.position === 'fixed' && s.pointerEvents === 'auto';
}));
mcheck('mac sidebar clears the traffic lights', await macPage.evaluate(() =>
  getComputedStyle(document.getElementById('sidebar')).paddingTop === '58px'));
mcheck('drag regions on topbar + logo + chrome strip', await macPage.evaluate(() =>
  document.querySelector('.topbar').hasAttribute('data-tauri-drag-region') &&
  document.querySelector('.topbar-logo').hasAttribute('data-tauri-drag-region') &&
  document.querySelector('.window-chrome').hasAttribute('data-tauri-drag-region')));
mcheck('mac hides drawn controls', await macPage.evaluate(() =>
  getComputedStyle(document.querySelector('.win-controls')).display === 'none'));
await macPage.setViewportSize({ width: 700, height: 800 });
await macPage.waitForTimeout(200);
mcheck('mac compact: content clears the floating lights', await macPage.evaluate(() =>
  getComputedStyle(document.getElementById('content-area')).paddingTop === '44px'));
await macPage.close();

const winErrors = [];
const winPage = await browser.newPage();
winPage.on('pageerror', (e) => winErrors.push(e.message));
await winPage.addInitScript(() => {
  Object.defineProperty(Navigator.prototype, 'platform', { get: () => 'Win32' });
});
await winPage.goto('http://127.0.0.1:8143/index.html');
await winPage.waitForTimeout(800);
const wcheck = (n, c) => results.push(`${c ? 'PASS' : 'FAIL'} ${n}`);
wcheck('is-windows class set pre-paint', await winPage.evaluate(() =>
  document.documentElement.classList.contains('is-windows')));
wcheck('windows chrome strip interactive', await winPage.evaluate(() => {
  const s = getComputedStyle(document.querySelector('.window-chrome'));
  return s.display === 'block' && s.position === 'fixed' && s.pointerEvents === 'auto';
}));
wcheck('windows drawn controls visible', await winPage.evaluate(() =>
  getComputedStyle(document.querySelector('.win-controls')).display === 'flex'));
wcheck('three window buttons wired (no-op without __TAURI__)', await winPage.evaluate(() => {
  const ids = ['win-minimize', 'win-maximize', 'win-close'];
  if (!ids.every((id) => document.getElementById(id))) return false;
  ids.forEach((id) => document.getElementById(id).click());
  return true;
}));
wcheck('windows sidebar clears the drawn controls', await winPage.evaluate(() =>
  getComputedStyle(document.getElementById('sidebar')).paddingTop === '58px'));
await winPage.close();

console.log('mac page JS errors:', macErrors.length ? macErrors : 'none');
console.log('win page JS errors:', winErrors.length ? winErrors : 'none');

console.log(results.join('\n'));
console.log('JS errors:', errors.length ? errors : 'none');
await browser.close();
const failed = results.filter(r => r.startsWith('FAIL'));
process.exit(failed.length || errors.length || macErrors.length || winErrors.length ? 1 : 0);
