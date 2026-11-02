import test from 'node:test';
import assert from 'node:assert/strict';
import {
  EndpointScoreboard,
  getQualitySpec,
  isCrossSourceMatch,
  isProgressiveStreamPlayable,
  raceHedgedSources,
  rewriteSaavnUrlForQuality,
} from './streamer.ts';

test('getQualitySpec and rewriteSaavnUrlForQuality map streaming & download qualities accurately', () => {
  const lowSpec = getQualitySpec('low', 44100);
  assert.equal(lowSpec.targetKbps, 96);
  assert.equal(lowSpec.saavnSuffix, '_96.mp4');
  assert.equal(lowSpec.sampleRate, 44100);

  const highUrl = rewriteSaavnUrlForQuality(
    'https://aac.saavncdn.com/123/track_name_96.mp4',
    'high'
  );
  assert.equal(highUrl, 'https://aac.saavncdn.com/123/track_name_320.mp4');

  const normalUrl = rewriteSaavnUrlForQuality(
    'https://aac.saavncdn.com/123/track_name_320.mp4',
    'normal'
  );
  assert.equal(normalUrl, 'https://aac.saavncdn.com/123/track_name_160.mp4');
});

test('isCrossSourceMatch matches normalized title, artist token, and duration tolerance', () => {
  const ytRequested = {
    title: 'Midnight City (Official Video HD)',
    artist: 'M83 Official',
    durationSecs: 243,
  };
  const saavnCandidate = {
    title: 'Midnight City',
    artist: 'M83',
    durationSecs: 244,
  };
  assert.equal(isCrossSourceMatch(ytRequested, saavnCandidate), true);

  const wrongDuration = {
    title: 'Midnight City',
    artist: 'M83',
    durationSecs: 410,
  };
  assert.equal(isCrossSourceMatch(ytRequested, wrongDuration), false);
});

test('EndpointScoreboard ranks fast healthy endpoints first and demotes failing endpoints', () => {
  const board = new EndpointScoreboard([
    { url: 'https://piped-slow.example', provider: 'piped', initialLatencyMs: 900 },
    { url: 'https://piped-fast.example', provider: 'piped', initialLatencyMs: 180 },
  ]);

  const initialRank = board.getRankedEndpoints('piped', 1000);
  assert.equal(initialRank[0].url, 'https://piped-fast.example');

  // Record 2 consecutive failures on piped-fast -> should be demoted for 15 minutes
  board.recordOutcome('https://piped-fast.example', false, 2000, 1000);
  board.recordOutcome('https://piped-fast.example', false, 2000, 1100);

  const afterFailureRank = board.getRankedEndpoints('piped', 1200);
  assert.equal(afterFailureRank[0].url, 'https://piped-slow.example');
});

test('raceHedgedSources returns the fastest successful source without waiting for slow stragglers', async () => {
  const board = new EndpointScoreboard();
  const winner = await raceHedgedSources(
    [
      {
        endpointUrl: 'https://slow-dead.example',
        delayMs: 0,
        execute: () =>
          new Promise<string>((_, reject) => setTimeout(() => reject(new Error('timeout')), 120)),
      },
      {
        endpointUrl: 'https://fast-hedged.example',
        delayMs: 10,
        execute: () =>
          new Promise<string>((resolve) => setTimeout(() => resolve('https://cdn.audio/stream.mp4'), 15)),
      },
    ],
    board
  );

  assert.equal(winner.result, 'https://cdn.audio/stream.mp4');
  assert.equal(winner.winnerUrl, 'https://fast-hedged.example');
});

test('isProgressiveStreamPlayable unlocks playback once initial 64KB chunk is buffered', () => {
  assert.equal(isProgressiveStreamPlayable(32 * 1024, 64, 5_000_000), false);
  assert.equal(isProgressiveStreamPlayable(64 * 1024, 64, 5_000_000), true);
  assert.equal(isProgressiveStreamPlayable(20 * 1024, 64, 20 * 1024), true);
});
