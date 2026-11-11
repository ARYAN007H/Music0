// playwright when available; playwright-core + the system chrome
// (channel: 'chrome') is enough and downloads nothing.
let chromium;
try {
  ({ chromium } = await import('playwright'));
} catch {
  ({ chromium } = await import('playwright-core'));
}
const browser = await chromium.launch({ channel: 'chrome', headless: true });
const page = await browser.newPage();
const errors = [];
page.on('pageerror', (e) => errors.push(e.message));
await page.goto('http://127.0.0.1:8143/index.html');
await page.waitForTimeout(1000);
const results = [];
const check = (n, c) => results.push(`${c ? 'PASS' : 'FAIL'} ${n}`);

// Full original suite (updated selectors)
check('panel hidden initially', await page.evaluate(() => !document.querySelector('.queue-panel')));
await page.evaluate(async () => {
  await Bridge.invoke('online_play', { track: { video_id: 's1', title: 'Now Song', artist: 'A1', album: '', duration_secs: 200 } });
  await Bridge.invoke('add_to_queue', { song: { id: 'q2', title: 'Next One', artist: 'A2', album: '', duration_secs: 180, file_path: '/x' } });
});
await page.waitForTimeout(300);
await page.click('#btn-queue');
await page.waitForTimeout(250);
check('panel opens', await page.evaluate(() => !!document.querySelector('.queue-panel')));
check('rows present', await page.evaluate(() => {
  const t = [...document.querySelectorAll('.qp-row .qp-title')].map(e => e.textContent);
  return t.includes('Now Song') && t.includes('Next One');
}));

// Blocking finding 1: App.state.settings syncs on toggle
await page.evaluate(() => document.querySelector('.qp-autoplay').click());
await page.waitForTimeout(150);
check('settings snapshot synced (no silent revert)', await page.evaluate(async () => {
  const snapOk = App.state.settings.autoplay === false;
  const backend = (await Bridge.invoke('get_settings')).autoplay === false;
  return snapOk && backend;
}));
await page.evaluate(() => document.querySelector('.qp-autoplay').click());
await page.waitForTimeout(150);
check('toggle back on', await page.evaluate(() => App.state.settings.autoplay === true));

// Finding 5: duplicate current-song in queue keeps replay copy visible
await page.evaluate(() => Bridge.invoke('add_to_queue', { song: { id: 'yt:s1', title: 'Now Song replay', artist: 'A1', album: '', duration_secs: 200, file_path: '/r' } }));
await page.waitForTimeout(200);
check('replay copy visible in Playing Next', await page.evaluate(() => {
  const t = [...document.querySelectorAll('.qp-row .qp-title')].map(e => e.textContent);
  return t.includes('Now Song replay');
}));

// Finding 7: Space activates rows
check('space key activates row', await page.evaluate(() => {
  const row = [...document.querySelectorAll('.qp-row')].find(r => r.querySelector('.qp-title')?.textContent === 'Next One');
  row.focus();
  row.dispatchEvent(new KeyboardEvent('keydown', { key: ' ', bubbles: true }));
  return App.state.currentSong?.title === 'Next One';
}));

// Finding 8: Now Playing row restarts
check('now-playing row seeks to 0', await page.evaluate(() => {
  const spy = App.transport.seek;
  let called = 0;
  App.transport.seek = (s) => { called = s; };
  const row = document.querySelector('.qp-row.qp-current');
  row.click();
  App.transport.seek = spy;
  return called === 0;
}));

// ── #62: player quick wins ────────────────────────────────────────────────────
check('expanded player has the volume row', await page.evaluate(
  () => !!document.getElementById('fs-volume')));

// Two extra upcoming rows so there are draggable neighbors to work with.
await page.evaluate(async () => {
  await Bridge.invoke('add_to_queue', { song: { id: 'd1', title: 'Drag A', artist: 'A', album: '', duration_secs: 100, file_path: '/a' } });
  await Bridge.invoke('add_to_queue', { song: { id: 'd2', title: 'Drag B', artist: 'B', album: '', duration_secs: 110, file_path: '/b' } });
});
await page.waitForTimeout(250);

// Drag-to-reorder: the stub applies the move to the store so the panel
// re-renders like it does on the real queue-changed event.
await page.evaluate(() => {
  window.__ipc = [];
  const orig = window.Bridge.invoke.bind(window.Bridge);
  window.Bridge.invoke = (cmd, ...rest) => {
    window.__ipc.push([cmd, rest[0]]);
    if (cmd === 'queue_move') {
      const { from, to } = rest[0] || {};
      const q = window.MusicoStore.get('queue');
      if (Number.isInteger(from) && Number.isInteger(to) && from !== to && q[from] && q[to]) {
        const [s] = q.splice(from, 1);
        q.splice(to, 0, s);
        window.MusicoStore.set('queue', [...q]);
      }
      return Promise.resolve(true);
    }
    return orig(cmd, ...rest);
  };
});
await page.waitForTimeout(200);
await page.evaluate(() => {
  const rows = [...document.querySelectorAll('.qp-row[draggable="true"]')];
  const dt = new DataTransfer();
  rows[0].dispatchEvent(new DragEvent('dragstart', { bubbles: true, cancelable: true, dataTransfer: dt }));
  rows[1].dispatchEvent(new DragEvent('dragover', { bubbles: true, cancelable: true, dataTransfer: dt }));
  rows[1].dispatchEvent(new DragEvent('drop', { bubbles: true, cancelable: true, dataTransfer: dt }));
});
await page.waitForTimeout(250);
check('drag fires an adjacent queue_move', await page.evaluate(() => {
  const calls = (window.__ipc || []).filter(([c]) => c === 'queue_move');
  const a = calls[calls.length - 1]?.[1];
  return !!a && a.to === a.from + 1;
}));
check('reorder re-renders the panel in store order', await page.evaluate(() => {
  const q = window.MusicoStore.get('queue');
  const offset = q[0]?.id === App.state.currentSong?.id ? 1 : 0;
  const titles = [...document.querySelectorAll('.qp-row[draggable="true"] .qp-title')]
    .map((e) => e.textContent);
  return titles.length > 0 && titles.every((t, i) => t === q[i + offset]?.title);
}));

// Keyboard reorder: Alt+↓ on the first draggable row.
await page.evaluate(() => {
  document.querySelector('.qp-row[draggable="true"]')
    .dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowDown', altKey: true, bubbles: true }));
});
await page.waitForTimeout(250);
check('Alt+ArrowDown fires queue_move', await page.evaluate(() => {
  const calls = (window.__ipc || []).filter(([c]) => c === 'queue_move');
  const a = calls[calls.length - 1]?.[1];
  return !!a && a.to === a.from + 1;
}));

// Click-to-jump: an upcoming row is consumed and played via queue_play_at
// (mock parity emits Playing, so currentSong follows the jumped row).
const jumpTarget = await page.evaluate(
  () => document.querySelector('.qp-row[draggable="true"] .qp-title')?.textContent);
await page.evaluate(() => document.querySelector('.qp-row[draggable="true"]').click());
await page.waitForTimeout(300);
check('row click jumps via queue_play_at', await page.evaluate(() => {
  const calls = (window.__ipc || []).filter(([c]) => c === 'queue_play_at');
  return calls.length >= 1 && Number.isInteger(calls[0][1]?.index);
}));
check('jumped row is now current', await page.evaluate((t) =>
  App.state.currentSong?.title === t, jumpTarget));

console.log(results.join('\n'));
console.log('JS errors:', errors.length ? errors : 'none');
await browser.close();
const failed = results.filter(r => r.startsWith('FAIL'));
process.exit(failed.length || errors.length ? 1 : 0);
