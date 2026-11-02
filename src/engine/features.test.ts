import { describe, it } from 'node:test';
import assert from 'node:assert/strict';
import {
  computeBeatAlignedCrossfade,
  scoreTracksWithPersonaMix,
  DEFAULT_TASTE_DNA,
  getStemModeSpec,
  computeLoudnessNormalization,
  findSonicBridgePath,
  getAutoEqProfile,
  applyTubeWarmthSample,
  JamSessionEngine,
  buildEnhancedLyrics,
  buildDaypartAndTimeCapsule,
  parseAndExecuteSonicCommand,
  TrackFeatures,
} from './features.js';

const MOCK_CATALOG: TrackFeatures[] = [
  { id: 'song_1', title: 'Midnight City', artist: 'M83', album: "Hurry Up, We're Dreaming", genre: 'Synthwave', durationSec: 244, bpm: 105, key: 9, isMajor: false, energy: 0.85, danceability: 0.78, valence: 0.72, acousticness: 0.08, instrumentalness: 0.45, spectralCentroid: 0.74, warmth: 0.65, source: 'local' },
  { id: 'song_2', title: 'Veridis Quo', artist: 'Daft Punk', album: 'Discovery', genre: 'Electronic', durationSec: 345, bpm: 108, key: 9, isMajor: false, energy: 0.64, danceability: 0.70, valence: 0.60, acousticness: 0.15, instrumentalness: 0.90, spectralCentroid: 0.58, warmth: 0.82, source: 'local' },
  { id: 'song_3', title: 'Resonance', artist: 'HOME', album: 'Odyssey', genre: 'Synthwave', durationSec: 213, bpm: 100, key: 4, isMajor: false, energy: 0.70, danceability: 0.72, valence: 0.68, acousticness: 0.12, instrumentalness: 0.88, spectralCentroid: 0.66, warmth: 0.85, source: 'saavn' },
  { id: 'song_4', title: 'Holocene', artist: 'Bon Iver', album: 'Bon Iver', genre: 'Indie Folk', durationSec: 337, bpm: 74, key: 7, isMajor: true, energy: 0.38, danceability: 0.42, valence: 0.45, acousticness: 0.88, instrumentalness: 0.25, spectralCentroid: 0.40, warmth: 0.90, source: 'local' },
  { id: 'song_5', title: 'Teardrop', artist: 'Massive Attack', album: 'Mezzanine', genre: 'Trip-Hop', durationSec: 331, bpm: 77, key: 9, isMajor: true, energy: 0.48, danceability: 0.62, valence: 0.38, acousticness: 0.42, instrumentalness: 0.35, spectralCentroid: 0.48, warmth: 0.86, source: 'local' },
];

describe('Musico 10 Standout Capabilities Engine', () => {
  it('1. Beat-Aligned Auto-DJ Crossfader clamps stretch ratio to pitch-safe ±3% and aligns bars', () => {
    const plan = computeBeatAlignedCrossfade(105, 107, 244, 4.0, true);
    assert.equal(plan.enabled, true);
    assert.ok(plan.stretchRatio >= 0.97 && plan.stretchRatio <= 1.03);
    assert.ok(plan.phraseAlignedBars >= 1);
    assert.ok(plan.outroCueSec < 244);
    assert.ok(plan.label.includes('Beat-Locked'));
  });

  it('2. Interactive Taste DNA & Persona Mixer re-ranks catalog according to persona sliders', () => {
    const synthHeavy = {
      ...DEFAULT_TASTE_DNA,
      personaWeights: {
        ...DEFAULT_TASTE_DNA.personaWeights,
        synthwave_night: 100,
        indie_warmth: 5,
      },
    };
    const ranked = scoreTracksWithPersonaMix(MOCK_CATALOG, MOCK_CATALOG[0], synthHeavy, []);
    assert.ok(ranked.length >= 4);
    assert.ok(ranked[0].score >= ranked[ranked.length - 1].score);
    assert.ok(ranked[0].dominantPersona.length > 0);
  });

  it('3. Real-Time Stem Separation returns distinct vocal & instrumental gains for karaoke and vocal modes', () => {
    const karaoke = getStemModeSpec('instrumental');
    const vocals = getStemModeSpec('vocals');
    assert.ok(karaoke.vocalGain < 0.2);
    assert.ok(vocals.vocalGain > 1.0 && vocals.instrumentGain < 0.3);
  });

  it('4. EBU R128 Loudness Normalization applies Dynamic Range Guard to prevent true-peak clipping', () => {
    // Track at -20 LUFS with true peak at -2.0 dBTP: raw gain is +6 dB, but guard limits to +1.0 dB
    const guarded = computeLoudnessNormalization(-20.0, -2.0, -14.0, true);
    assert.equal(guarded.peakLimited, true);
    assert.equal(guarded.gainDb, 1.0);
    assert.ok(guarded.linearMultiplier > 1.0);
  });

  it('5. Sonic Bridge Pathfinding computes a multi-step harmonic transition path between two tracks', () => {
    const bridge = findSonicBridgePath('song_1', 'song_4', MOCK_CATALOG, 2);
    assert.equal(bridge.length, 4); // start + 2 intermediate + target
    assert.equal(bridge[0].track.id, 'song_1');
    assert.equal(bridge[bridge.length - 1].track.id, 'song_4');
    assert.ok(bridge[1].transitionScoreFromPrev > 0);
  });

  it('6. Headphone AutoEQ & Tube Warmth return calibrated 5-band curves and soft-clipping harmonics', () => {
    const hd650 = getAutoEqProfile('sennheiser_hd650');
    assert.equal(hd650.bands5.length, 5);
    assert.ok(hd650.bands5[0] > 0); // Sub-bass lift for HD650
    const warmed = applyTubeWarmthSample(0.6, 0.5);
    assert.ok(Math.abs(warmed) <= 1.0);
  });

  it('7. Live Collaborative Jam Sessions create rooms, join participants, and reorder queue by upvotes', () => {
    const jam = new JamSessionEngine();
    const room = jam.createSession('Aryan', 'song_1', MOCK_CATALOG);
    assert.ok(room.roomCode.startsWith('JAM-'));
    const targetTrack = room.sharedQueue[room.sharedQueue.length - 1];
    const updated = jam.voteTrack(room.roomCode, targetTrack.id, 10);
    assert.ok(updated);
    assert.equal(updated!.sharedQueue[0].id, targetTrack.id);
  });

  it('8. Word-By-Word Synced Lyrics builds word timestamps, romanization, and translation', () => {
    const lines = buildEnhancedLyrics({ id: 'song_1', title: 'Midnight City', artist: 'M83', durationSec: 244 });
    assert.ok(lines.length >= 5);
    assert.ok(lines[1].words.length >= 4);
    assert.ok(lines[1].romanized.length > 0);
    assert.ok(lines[1].translation.length > 0);
  });

  it('9. Time Capsule & Contextual Daypart Memory identifies active daypart and forgotten gems', () => {
    const report = buildDaypartAndTimeCapsule(MOCK_CATALOG, ['song_1'], 23);
    assert.ok(report.activeDaypart.includes('Late Night'));
    assert.ok(report.forgottenGems.length > 0);
    assert.ok(!report.forgottenGems.some((g) => g.id === 'song_1'));
  });

  it('10. Command Palette parses natural-language BPM filters, Sonic Bridge queries, and AutoEQ actions', () => {
    const bpmRes = parseAndExecuteSonicCommand('bpm:95..110', MOCK_CATALOG);
    assert.equal(bpmRes.actionType, 'filter_tracks');
    assert.ok(bpmRes.matchedTracks.every((t) => t.bpm >= 95 && t.bpm <= 110));

    const bridgeRes = parseAndExecuteSonicCommand('bridge Midnight City to Holocene', MOCK_CATALOG);
    assert.equal(bridgeRes.actionType, 'sonic_bridge');
    assert.ok(bridgeRes.matchedTracks.length >= 3);

    const eqRes = parseAndExecuteSonicCommand('eq sennheiser hd650', MOCK_CATALOG);
    assert.equal(eqRes.actionType, 'apply_setting');
    assert.equal(eqRes.settingPatch?.autoeq_profile, 'sennheiser_hd650');
  });
});
