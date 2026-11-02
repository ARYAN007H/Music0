import test from 'node:test';
import assert from 'node:assert/strict';
import {
  buildUnifiedTrackVector,
  computeHarmonicBpmTransitionScore,
  defaultSignalWeights,
  HybridRecommenderEngine,
  standardizeVectorsZScore,
  TrackItem,
  updateBanditWeights,
} from './recommender.ts';

function makeSampleTracks(): TrackItem[] {
  const raw: Array<Omit<TrackItem, 'vector'>> = [
    {
      id: 'saavn:101',
      source: 'saavn',
      title: 'Neon Horizon',
      artist: 'Kavinsky & The Midnight',
      album: 'Outrun Nights',
      genre: 'Synthwave',
      durationSecs: 238,
      bpm: 118,
      camelotKey: 8,
      camelotMode: 'A',
      energy: 0.84,
      valence: 0.48,
      danceability: 0.74,
      acousticness: 0.12,
      temporalVariance: 0.42,
      playCount: 6,
      skipCount: 0,
      liked: true,
      cachedOffline: true,
    },
    {
      id: 'yt:202',
      source: 'yt',
      title: 'Starlight Drive',
      artist: 'Timecop1983',
      album: 'Night Drive',
      genre: 'Synthwave',
      durationSecs: 245,
      bpm: 120,
      camelotKey: 9,
      camelotMode: 'A',
      energy: 0.81,
      valence: 0.52,
      danceability: 0.72,
      acousticness: 0.15,
      temporalVariance: 0.40,
      playCount: 2,
      skipCount: 0,
      liked: false,
      cachedOffline: false,
    },
    {
      id: 'local:303',
      source: 'local',
      title: 'Forest Rain Canopy',
      artist: 'Helios',
      album: 'Quiet Dawn',
      genre: 'Ambient',
      durationSecs: 310,
      bpm: 74,
      camelotKey: 2,
      camelotMode: 'B',
      energy: 0.18,
      valence: 0.40,
      danceability: 0.20,
      acousticness: 0.92,
      temporalVariance: 0.15,
      playCount: 4,
      skipCount: 0,
      liked: true,
      cachedOffline: true,
    },
    {
      id: 'saavn:404',
      source: 'saavn',
      title: 'Cyberpunk Pulse',
      artist: 'FM-84',
      album: 'Atlas',
      genre: 'Synthwave',
      durationSecs: 229,
      bpm: 119,
      camelotKey: 8,
      camelotMode: 'B',
      energy: 0.83,
      valence: 0.55,
      danceability: 0.76,
      acousticness: 0.10,
      temporalVariance: 0.44,
      playCount: 0,
      skipCount: 0,
      liked: false,
      cachedOffline: false,
    },
  ];

  return raw.map((t) => ({
    ...t,
    vector: buildUnifiedTrackVector(t),
  }));
}

test('computeHarmonicBpmTransitionScore rewards harmonic Camelot neighbors and smooth BPM transitions', () => {
  const tracks = makeSampleTracks();
  const seed = tracks[0]; // 118 BPM, 8A
  const harmonicNeighbor = tracks[1]; // 120 BPM, 9A
  const jarringAmbient = tracks[2]; // 74 BPM, 2B

  const smoothScore = computeHarmonicBpmTransitionScore(seed, harmonicNeighbor);
  const jarringScore = computeHarmonicBpmTransitionScore(seed, jarringAmbient);

  assert.ok(
    smoothScore > jarringScore + 0.3,
    `Expected smooth transition (${smoothScore}) to beat jarring transition (${jarringScore})`
  );
});

test('standardizeVectorsZScore produces zero-centered normalized vectors without NaN', () => {
  const tracks = makeSampleTracks();
  const { standardized, mean, std } = standardizeVectorsZScore(tracks.map((t) => t.vector));
  assert.equal(standardized.length, tracks.length);
  assert.equal(mean.length, 30);
  assert.equal(std.length, 30);
  for (const vec of standardized) {
    const norm = Math.hypot(...vec);
    assert.ok(Math.abs(norm - 1.0) < 1e-5);
  }
});

test('Multi-modal taste personas update selectively and recommend online + local tracks', () => {
  const engine = new HybridRecommenderEngine(makeSampleTracks());

  // Record positive completions on Synthwave tracks
  engine.recordInteraction({ trackId: 'saavn:101', completedRatio: 0.95, liked: true });
  engine.recordInteraction({ trackId: 'yt:202', completedRatio: 0.90 });

  // Co-occurrence between saavn:101 and yt:202 should now be positive
  assert.ok(engine.getCooccurrenceScore('saavn:101', 'yt:202') > 0);

  const recs = engine.recommend({ seedTrackId: 'saavn:101', limit: 3 });
  assert.equal(recs.length, 3);
  // Top recommendation after Synthwave 8A 118BPM should be Synthwave 8B/9A (yt:202 or saavn:404)
  assert.ok(recs[0].trackId === 'yt:202' || recs[0].trackId === 'saavn:404');
});

test('updateBanditWeights updates all signals proportionally and maintains unit sum', () => {
  const initial = defaultSignalWeights();
  const updated = updateBanditWeights(
    initial,
    {
      similarity: 0.95,
      affinity: 0.8,
      profile: 0.9,
      session: 0.5,
      artist: 0.3,
      cluster: 0.4,
      context: 0.5,
      discovery: 0.2,
      harmonic: 0.92,
      cooccurrence: 0.7,
    },
    1.0,
    0.05
  );

  const sum = Object.values(updated).reduce((a, b) => a + b, 0);
  assert.ok(Math.abs(sum - 1.0) < 0.01);
  assert.ok(updated.similarity > initial.similarity);
  assert.ok(updated.harmonic > initial.harmonic);
});

test('planPredictivePrefetch respects prefetch_enabled, prefetch_count, queue order, and streaming_quality', () => {
  const engine = new HybridRecommenderEngine(makeSampleTracks());

  const disabledPlan = engine.planPredictivePrefetch({
    currentTrackId: 'saavn:101',
    upcomingQueueIds: ['yt:202'],
    prefetchEnabled: false,
    prefetchCount: 3,
    streamingQuality: 'high',
  });
  assert.equal(disabledPlan.length, 0);

  const activePlan = engine.planPredictivePrefetch({
    currentTrackId: 'saavn:101',
    upcomingQueueIds: ['yt:202'],
    prefetchEnabled: true,
    prefetchCount: 3,
    streamingQuality: 'lossless',
    instantPlayBufferKb: 64,
  });
  assert.equal(activePlan.length, 3);
  assert.equal(activePlan[0].trackId, 'yt:202');
  assert.equal(activePlan[0].reason, 'queue-next');
  assert.equal(activePlan[0].targetBitrateKbps, 1411);
});
