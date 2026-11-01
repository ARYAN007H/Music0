import { chromium } from 'playwright';
const browser = await chromium.launch({ channel: 'chrome', headless: true });
const page = await browser.newPage();
const errors = [];
page.on('pageerror', (e) => errors.push(e.message));
await page.goto('http://127.0.0.1:8143/index.html');
await page.waitForTimeout(1000);
const results = [];
const check = (n, c) => results.push(`${c ? 'PASS' : 'FAIL'} ${n}`);

// Stub the suggestions command at the bridge: the static server has no
// backend, so mockCommand would return null (popover stays closed).  We
// answer with canned data to exercise the real UI flow (#59).
await page.evaluate(() => {
  window.__ipc = [];
  const orig = window.Bridge.invoke.bind(window.Bridge);
  window.Bridge.invoke = (cmd, ...rest) => {
    window.__ipc.push([cmd, rest[0]]);
    if (cmd === 'online_search_suggestions') {
      const q = (rest[0]?.query || '').toLowerCase();
      const all = ['believer', 'believer song', 'believer imagine dragons'];
      return Promise.resolve(q.length >= 2 ? all.filter((s) => s.startsWith(q)) : []);
    }
    return orig(cmd, ...rest);
  };
});
const ipcCalls = (cmd) => page.evaluate(
  (c) => window.__ipc.filter(([k]) => k === c).map(([, a]) => a), cmd);

await page.evaluate(() => window.MusicoRouter.navigate('search'));
await page.waitForTimeout(300);
const input = page.locator('#browse-input');

// 1. Typing ≥2 chars opens the popover with the stubbed suggestions.
// (fill() dispatches a real input event — no extra keypress needed.)
await input.click();
await input.fill('belie');
await page.waitForTimeout(500); // 200 ms debounce + IPC
check('popover opens with suggestions', await page.evaluate(() => {
  const box = document.getElementById('browse-suggest');
  return !box.hidden && box.querySelectorAll('.suggest-item').length === 3;
}));
check('suggestions command fired once', (await ipcCalls('online_search_suggestions')).length === 1);

// 2. Short input closes it again, silently.
await input.fill('');
await page.waitForTimeout(400);
check('empty input closes the popover', await page.evaluate(() => {
  const box = document.getElementById('browse-suggest');
  return box.hidden && box.querySelectorAll('.suggest-item').length === 0;
}));

// 3. Keyboard: ↓ highlights the first row, Enter commits it as the search.
await input.fill('belie');
await page.waitForTimeout(500);
await input.press('ArrowDown');
check('ArrowDown highlights the first row', await page.evaluate(() =>
  document.querySelector('#browse-suggest .suggest-item')
    ?.getAttribute('aria-selected') === 'true'));
await input.press('Enter');
await page.waitForTimeout(300);
// The pre-existing 350 ms live-search also fires while typing — the commit
// is the LAST search call, with the highlighted suggestion as its query.
const searches = (await ipcCalls('online_search'));
check('Enter commits the highlighted suggestion', searches.length >= 1 &&
  searches[searches.length - 1]?.query === 'believer');
check('popover closes after commit', await page.evaluate(() =>
  document.getElementById('browse-suggest').hidden));

// 4. Escape dismisses without committing.  Baseline AFTER the fill's own
// live-search debounce settles — Escape itself must add zero searches.
await input.fill('belie');
await page.waitForTimeout(500);
const searchesBeforeEscape = (await ipcCalls('online_search')).length;
await input.press('Escape');
await page.waitForTimeout(150);
check('Escape closes the popover', await page.evaluate(() =>
  document.getElementById('browse-suggest').hidden));
check('Escape ran no new search', (await ipcCalls('online_search')).length === searchesBeforeEscape);

// 5. API failure (null from the bridge) degrades silently: no popover, no error.
await page.evaluate(() => {
  const orig = window.Bridge.invoke.bind(window.Bridge);
  window.Bridge.invoke = (cmd, ...rest) => {
    if (cmd === 'online_search_suggestions') return Promise.resolve(null);
    return orig(cmd, ...rest);
  };
});
await input.fill('belie');
await page.waitForTimeout(500);
check('API failure keeps the popover closed', await page.evaluate(() =>
  document.getElementById('browse-suggest').hidden));
check('no error UI on API failure', await page.evaluate(() =>
  !document.getElementById('browse-status').textContent.includes('failed')));

console.log(results.join('\n'));
console.log('JS errors:', errors.length ? errors : 'none');
const failed = results.filter((r) => r.startsWith('FAIL'));
await browser.close();
process.exit(failed.length || errors.length ? 1 : 0);
