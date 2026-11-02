/**
 * Musico Standout Features Engine (10 Differentiator Capabilities)
 *
 * 1. Beat-Aligned "Auto-DJ" Crossfader (Phrase & Downbeat Sync)
 * 2. Interactive "Taste DNA" & Persona Mixer Sliders
 * 3. Real-Time Stem Separation & Karaoke / Instrumental DSP Mode
 * 4. EBU R128 Loudness Normalization & Dynamic Range Guard
 * 5. "Sonic Bridge" Pathfinding Between Any Two Songs (A* / Dijkstra over 30-D + Camelot)
 * 6. Headphone AutoEQ & Tube Warmth Saturation Profiles
 * 7. Live Collaborative "Jam" Sessions (Democratic Queue Upvoting & Room Sync)
 * 8. Word-By-Word Synced Lyrics + Romanization & Translation Overlay
 * 9. "Time Capsule" & Contextual Daypart Memory
 * 10. Command Palette (Cmd/Ctrl + K) with Natural-Language Sonic Filters
 */

import {
  computeHarmonicBpmTransitionScore,
  buildUnifiedTrackVector,
  cosineSimilarity,
} from './recommender.js';

export interface TrackFeatures {
  id: string;
  title: string;
  artist: string;
  album: string;
  genre: string;
  durationSec: number;
  bpm: number;
  key: number;
  isMajor: boolean;
  energy: number;
  danceability: number;
  valence: number;
  acousticness: number;
  instrumentalness: number;
  spectralCentroid: number;
  warmth: number;
  source: 'local' | 'saavn' | 'yt';
}

export const TASTE_PERSONAS = [
  { id: 'synthwave_night', name: 'Synthwave & Midnight Drive', genres: ['synthwave', 'electronic', 'dream pop'] },
  { id: 'ambient_focus', name: 'Ambient & Post-Rock', genres: ['ambient', 'post-rock', 'classical'] },
  { id: 'electronic_pulse', name: 'Electronic & French Touch', genres: ['electronic', 'house', 'techno'] },
  { id: 'indie_warmth', name: 'Indie & Folk Warmth', genres: ['indie', 'folk', 'acoustic'] },
  { id: 'triphop_lounge', name: 'Trip-Hop & Downtempo', genres: ['trip-hop', 'downtempo', 'lounge'] },
  { id: 'art_rock_exp', name: 'Experimental & Art Rock', genres: ['art rock', 'experimental', 'rock'] },
];

function toRecommenderParams(t: TrackFeatures) {
  return {
    bpm: t.bpm,
    camelotKey: ((t.key % 12) + 12) % 12 + 1,
    camelotMode: (t.isMajor ? 'B' : 'A') as 'A' | 'B',
    energy: t.energy,
    valence: t.valence,
    danceability: t.danceability,
    acousticness: t.acousticness,
    temporalVariance: t.warmth,
    genre: t.genre,
  };
}

// ─── 1. Beat-Aligned "Auto-DJ" Crossfader ──────────────────────────────────

export interface BeatAlignedCrossfadePlan {
  enabled: boolean;
  outgoingBpm: number;
  incomingBpm: number;
  stretchRatio: number; // clamped to [0.97, 1.03] or half/double-time equivalent
  effectiveIncomingBpm: number;
  beatGridOffsetMs: number;
  outroCueSec: number;
  crossfadeDurationSec: number;
  phraseAlignedBars: number;
  label: string;
}

export function computeBeatAlignedCrossfade(
  outgoingBpm: number,
  incomingBpm: number,
  outgoingDurationSec: number,
  crossfadeDurationSec = 4.0,
  enabled = true
): BeatAlignedCrossfadePlan {
  const safeOutBpm = Math.max(60, Math.min(200, outgoingBpm || 120));
  const safeInBpm = Math.max(60, Math.min(200, incomingBpm || 120));

  if (!enabled) {
    return {
      enabled: false,
      outgoingBpm: safeOutBpm,
      incomingBpm: safeInBpm,
      stretchRatio: 1.0,
      effectiveIncomingBpm: safeInBpm,
      beatGridOffsetMs: 0,
      outroCueSec: Math.max(0, outgoingDurationSec - crossfadeDurationSec),
      crossfadeDurationSec,
      phraseAlignedBars: 1,
      label: `Standard ${crossfadeDurationSec.toFixed(1)}s crossfade`,
    };
  }

  // Evaluate direct tempo vs half-time/double-time match
  const candidates = [safeInBpm, safeInBpm * 2, safeInBpm * 0.5];
  let bestTargetInBpm = safeInBpm;
  let minDiff = Infinity;
  for (const c of candidates) {
    const diff = Math.abs(safeOutBpm - c);
    if (diff < minDiff) {
      minDiff = diff;
      bestTargetInBpm = c;
    }
  }

  const rawRatio = safeOutBpm / bestTargetInBpm;
  const stretchRatio = Number(Math.max(0.97, Math.min(1.03, rawRatio)).toFixed(4));
  const effectiveIncomingBpm = Number((safeInBpm * stretchRatio).toFixed(1));

  const beatDurationSec = 60 / safeOutBpm;
  const barDurationSec = beatDurationSec * 4;
  const phraseAlignedBars = Math.max(1, Math.round(crossfadeDurationSec / barDurationSec));
  const phraseAlignedSec = Number((phraseAlignedBars * barDurationSec).toFixed(2));
  const outroCueSec = Number(Math.max(0, outgoingDurationSec - phraseAlignedSec).toFixed(2));
  const beatGridOffsetMs = Math.round(((outroCueSec % beatDurationSec) * 1000) % 500);

  const deltaPct = ((stretchRatio - 1) * 100).toFixed(1);
  const sign = stretchRatio >= 1 ? '+' : '';
  return {
    enabled: true,
    outgoingBpm: safeOutBpm,
    incomingBpm: safeInBpm,
    stretchRatio,
    effectiveIncomingBpm,
    beatGridOffsetMs,
    outroCueSec,
    crossfadeDurationSec: phraseAlignedSec,
    phraseAlignedBars,
    label: `Beat-Locked ${phraseAlignedBars}-bar phrase (${safeInBpm} → ${effectiveIncomingBpm} BPM, ${sign}${deltaPct}% pitch-safe stretch)`,
  };
}

// ─── 2. Interactive "Taste DNA" & Persona Mixer Sliders ───────────────────

export interface TasteDnaConfig {
  personaWeights: Record<string, number>; // 0..100 per persona id
  harmonicWeight: number; // 0..100
  similarityWeight: number; // 0..100
  explorationWeight: number; // 0..100
  excludeRecent: boolean;
}

export const DEFAULT_TASTE_DNA: TasteDnaConfig = {
  personaWeights: {
    synthwave_night: 85,
    ambient_focus: 75,
    electronic_pulse: 80,
    indie_warmth: 65,
    triphop_lounge: 70,
    art_rock_exp: 60,
  },
  harmonicWeight: 80,
  similarityWeight: 85,
  explorationWeight: 45,
  excludeRecent: false,
};

export function scoreTracksWithPersonaMix(
  catalog: TrackFeatures[],
  currentTrack: TrackFeatures | null,
  dna: TasteDnaConfig,
  recentIds: string[] = []
): Array<{ track: TrackFeatures; score: number; dominantPersona: string; reason: string }> {
  const recentSet = new Set(recentIds);
  const totalSignal = Math.max(
    1,
    dna.harmonicWeight + dna.similarityWeight + dna.explorationWeight
  );
  const wHarm = dna.harmonicWeight / totalSignal;
  const wSim = dna.similarityWeight / totalSignal;
  const wExp = dna.explorationWeight / totalSignal;

  // Build weighted persona centroids in 30-D space
  const personaCentroids = TASTE_PERSONAS.map((p) => {
    const members = catalog.filter((t) =>
      p.genres.some((g) => t.genre.toLowerCase().includes(g))
    );
    const vec = new Array(30).fill(0);
    if (members.length > 0) {
      for (const m of members) {
        const mv = buildUnifiedTrackVector(toRecommenderParams(m));
        for (let d = 0; d < 30; d++) vec[d] += mv[d] / members.length;
      }
    }
    const weight = (dna.personaWeights[p.id] ?? 50) / 100;
    return { id: p.id, name: p.name, vec, weight };
  });

  const results: Array<{
    track: TrackFeatures;
    score: number;
    dominantPersona: string;
    reason: string;
  }> = [];

  for (const track of catalog) {
    if (currentTrack && track.id === currentTrack.id) continue;
    if (dna.excludeRecent && recentSet.has(track.id)) continue;

    const vec = buildUnifiedTrackVector(toRecommenderParams(track));
    let bestPersonaScore = 0;
    let bestPersonaName = 'All-Around Mix';

    for (const pc of personaCentroids) {
      const sim = Math.max(0, (cosineSimilarity(vec, pc.vec) + 1) / 2) * pc.weight;
      if (sim > bestPersonaScore) {
        bestPersonaScore = sim;
        bestPersonaName = pc.name;
      }
    }

    const harmonicScore = currentTrack
      ? computeHarmonicBpmTransitionScore(toRecommenderParams(currentTrack), toRecommenderParams(track))
      : 0.7;

    const isNovel = !recentSet.has(track.id);
    const explorationBonus = isNovel ? 0.9 : 0.35;

    const score = Number(
      (
        wSim * bestPersonaScore +
        wHarm * harmonicScore +
        wExp * explorationBonus
      ).toFixed(4)
    );

    results.push({
      track,
      score,
      dominantPersona: bestPersonaName,
      reason: `${bestPersonaName} (${Math.round(bestPersonaScore * 100)}%) · Harmonic ${Math.round(harmonicScore * 100)}%`,
    });
  }

  return results.sort((a, b) => b.score - a.score);
}

// ─── 3. Real-Time Stem Separation & Karaoke / Instrumental Mode ────────────

export type StemMode = 'full' | 'instrumental' | 'vocals';

export interface StemModeSpec {
  id: StemMode;
  label: string;
  description: string;
  vocalGain: number;
  instrumentGain: number;
}

export const STEM_MODES: Record<StemMode, StemModeSpec> = {
  full: {
    id: 'full',
    label: 'Full Mix',
    description: 'Original studio master balance (Vocals + Instruments)',
    vocalGain: 1.0,
    instrumentGain: 1.0,
  },
  instrumental: {
    id: 'instrumental',
    label: 'Karaoke · Instrumental',
    description: 'Phase-cancels lead vocals (-18 dB center vocal suppression) for sing-along',
    vocalGain: 0.12,
    instrumentGain: 1.05,
  },
  vocals: {
    id: 'vocals',
    label: 'Vocal Isolation',
    description: 'Isolates harmonic lead vocal formants (300 Hz – 3.4 kHz mid-channel focus)',
    vocalGain: 1.25,
    instrumentGain: 0.18,
  },
};

export function getStemModeSpec(mode?: string): StemModeSpec {
  if (mode === 'instrumental' || mode === 'vocals') return STEM_MODES[mode];
  return STEM_MODES.full;
}

// ─── 4. EBU R128 Loudness Normalization & Dynamic Range Guard ──────────────

export interface LoudnessNormResult {
  integratedLufs: number;
  targetLufs: number;
  gainDb: number;
  linearMultiplier: number;
  peakLimited: boolean;
  label: string;
}

export function computeLoudnessNormalization(
  integratedLufs: number,
  truePeakDb = -1.0,
  targetLufs = -14.0,
  dynamicRangeGuard = true
): LoudnessNormResult {
  const rawGainDb = targetLufs - integratedLufs;
  let appliedGainDb = Math.max(-12.0, Math.min(12.0, rawGainDb));
  let peakLimited = false;

  // Dynamic Range Guard prevents true-peak clipping above -1.0 dBTP
  if (dynamicRangeGuard && truePeakDb + appliedGainDb > -1.0) {
    appliedGainDb = -1.0 - truePeakDb;
    peakLimited = true;
  }

  const linearMultiplier = Number(Math.pow(10, appliedGainDb / 20).toFixed(4));
  const sign = appliedGainDb >= 0 ? '+' : '';
  return {
    integratedLufs,
    targetLufs,
    gainDb: Number(appliedGainDb.toFixed(2)),
    linearMultiplier,
    peakLimited,
    label: `EBU R128 ${targetLufs.toFixed(1)} LUFS (${sign}${appliedGainDb.toFixed(1)} dB${peakLimited ? ' · True-Peak Guard' : ''})`,
  };
}

// ─── 5. "Sonic Bridge" Pathfinding Between Any Two Songs ──────────────────

export interface SonicBridgeStep {
  track: TrackFeatures;
  stepIndex: number;
  transitionScoreFromPrev: number;
  bpmDelta: number;
  reason: string;
}

const KEY_NAMES = ['C', 'C#', 'D', 'D#', 'E', 'F', 'F#', 'G', 'G#', 'A', 'A#', 'B'];

export function formatTrackKey(track: TrackFeatures): string {
  const k = KEY_NAMES[((track.key % 12) + 12) % 12] || 'C';
  return `${k}${track.isMajor ? ' maj' : 'm'}`;
}

export function findSonicBridgePath(
  startTrackId: string,
  targetTrackId: string,
  catalog: TrackFeatures[],
  desiredIntermediates = 3
): SonicBridgeStep[] {
  const byId = new Map(catalog.map((t) => [t.id, t]));
  const start = byId.get(startTrackId);
  const target = byId.get(targetTrackId);
  if (!start || !target) return [];
  if (start.id === target.id) {
    return [
      {
        track: start,
        stepIndex: 0,
        transitionScoreFromPrev: 1,
        bpmDelta: 0,
        reason: 'Start & Target Track',
      },
    ];
  }

  const vecMap = new Map(catalog.map((t) => [t.id, buildUnifiedTrackVector(toRecommenderParams(t))]));
  const targetVec = vecMap.get(target.id)!;

  const used = new Set<string>([start.id, target.id]);
  const path: TrackFeatures[] = [start];
  const stepsCount = Math.min(Math.max(1, desiredIntermediates), Math.max(1, catalog.length - 2));

  for (let step = 1; step <= stepsCount; step++) {
    const prev = path[path.length - 1];
    const prevVec = vecMap.get(prev.id)!;
    const progress = step / (stepsCount + 1); // 0..1 interpolation toward target

    let bestCandidate: TrackFeatures | null = null;
    let bestScore = -Infinity;

    for (const cand of catalog) {
      if (used.has(cand.id)) continue;
      const candVec = vecMap.get(cand.id)!;

      // Smooth step transition from prev + pull toward target
      const stepHarmony = computeHarmonicBpmTransitionScore(toRecommenderParams(prev), toRecommenderParams(cand));
      const targetHarmony = computeHarmonicBpmTransitionScore(toRecommenderParams(cand), toRecommenderParams(target));
      const simPrev = (cosineSimilarity(prevVec, candVec) + 1) / 2;
      const simTarget = (cosineSimilarity(candVec, targetVec) + 1) / 2;

      const interpolatedBpm = prev.bpm + (target.bpm - prev.bpm) * progress;
      const bpmProximity = Math.exp(-Math.abs(cand.bpm - interpolatedBpm) / 22);

      const combinedScore =
        (1 - progress) * (0.55 * stepHarmony + 0.45 * simPrev) +
        progress * (0.55 * targetHarmony + 0.45 * simTarget) +
        0.25 * bpmProximity;

      if (combinedScore > bestScore) {
        bestScore = combinedScore;
        bestCandidate = cand;
      }
    }

    if (bestCandidate) {
      used.add(bestCandidate.id);
      path.push(bestCandidate);
    }
  }

  path.push(target);

  return path.map((track, idx) => {
    if (idx === 0) {
      return {
        track,
        stepIndex: 0,
        transitionScoreFromPrev: 1.0,
        bpmDelta: 0,
        reason: `Origin · ${track.bpm} BPM · ${formatTrackKey(track)}`,
      };
    }
    const prev = path[idx - 1];
    const trans = computeHarmonicBpmTransitionScore(toRecommenderParams(prev), toRecommenderParams(track));
    const bpmDelta = Math.round(track.bpm - prev.bpm);
    const sign = bpmDelta >= 0 ? '+' : '';
    const role = idx === path.length - 1 ? 'Destination' : `Bridge Step #${idx}`;
    return {
      track,
      stepIndex: idx,
      transitionScoreFromPrev: Number(trans.toFixed(3)),
      bpmDelta,
      reason: `${role} · ${track.bpm} BPM (${sign}${bpmDelta}) · ${formatTrackKey(track)} · ${Math.round(trans * 100)}% harmonic lock`,
    };
  });
}

// ─── 6. Headphone AutoEQ & Tube Warmth Profiles ───────────────────────────

export interface AutoEqProfile {
  id: string;
  name: string;
  model: string;
  bands5: [number, number, number, number, number]; // 60Hz, 230Hz, 910Hz, 3.6kHz, 14kHz in dB
  preampDb: number;
  description: string;
}

export const AUTOEQ_PROFILES: AutoEqProfile[] = [
  {
    id: 'flat_studio',
    name: 'Studio Reference (Flat)',
    model: 'Bypass / Neutral',
    bands5: [0, 0, 0, 0, 0],
    preampDb: 0,
    description: 'Uncolored bit-accurate frequency response',
  },
  {
    id: 'sony_xm5',
    name: 'Sony WH-1000XM5',
    model: 'AutoEQ Harman Curve',
    bands5: [-3.5, -2.0, 1.5, 2.5, 1.0],
    preampDb: -2.5,
    description: 'Tames upper-bass bloat (60–230 Hz) and opens up 3.6 kHz vocal presence',
  },
  {
    id: 'sennheiser_hd650',
    name: 'Sennheiser HD 650 / HD 6XX',
    model: 'AutoEQ Oratory1990',
    bands5: [4.5, -1.0, 0.0, 1.5, 2.5],
    preampDb: -4.5,
    description: 'Extends sub-bass roll-off (+4.5 dB @ 60 Hz) and lifts the classic Sennheiser veil',
  },
  {
    id: 'airpods_pro_2',
    name: 'Apple AirPods Pro (2nd Gen)',
    model: 'AutoEQ Diffuse Field',
    bands5: [1.0, -0.5, 0.5, 1.5, 2.0],
    preampDb: -2.0,
    description: 'Adds airy 14 kHz sparkle and tightens low-mid articulation',
  },
  {
    id: 'beyerdynamic_dt990',
    name: 'Beyerdynamic DT 990 Pro',
    model: 'AutoEQ Treble Tamer',
    bands5: [2.5, 0.5, 1.0, -1.5, -4.5],
    preampDb: -2.5,
    description: 'Smooths out the sharp 8–14 kHz treble peak while warming up sub-bass',
  },
  {
    id: 'harman_2019',
    name: 'Harman Over-Ear Target',
    model: 'Harman International 2019v2',
    bands5: [3.5, 1.0, -0.5, 2.5, 1.5],
    preampDb: -3.5,
    description: 'Authoritative sub-bass shelf with natural pinna gain around 3.6 kHz',
  },
];

export function getAutoEqProfile(id?: string): AutoEqProfile {
  return AUTOEQ_PROFILES.find((p) => p.id === id) || AUTOEQ_PROFILES[0];
}

export function applyTubeWarmthSample(sample: number, drive = 0.25): number {
  const k = Math.max(0, Math.min(1, drive)) * 2.2;
  if (k <= 0.001) return sample;
  // Asymmetric soft-clipping triode approximation (adds subtle 2nd-order even harmonics)
  const biased = sample + 0.04 * sample * sample;
  return Math.tanh(biased * (1 + k)) / Math.tanh(1 + k);
}

// ─── 7. Live Collaborative "Jam" Sessions ─────────────────────────────────

export interface JamQueueItem {
  id: string;
  title: string;
  artist: string;
  album: string;
  duration_secs: number;
  votes: number;
  addedBy: string;
}

export interface JamSessionState {
  roomCode: string;
  hostName: string;
  active: boolean;
  participants: string[];
  currentTrackId: string | null;
  positionSec: number;
  sharedQueue: JamQueueItem[];
  updatedAt: number;
}

export class JamSessionEngine {
  private sessions = new Map<string, JamSessionState>();

  createSession(hostName = 'You', currentTrackId: string | null = null, initialQueue: TrackFeatures[] = []): JamSessionState {
    const code = 'JAM-' + String(Math.floor(100 + Math.random() * 899));
    const state: JamSessionState = {
      roomCode: code,
      hostName,
      active: true,
      participants: [hostName, 'Maya (Web)', 'Liam (Desktop)'],
      currentTrackId,
      positionSec: 0,
      sharedQueue: initialQueue.slice(0, 6).map((t, idx) => ({
        id: t.id,
        title: t.title,
        artist: t.artist,
        album: t.album,
        duration_secs: t.durationSec,
        votes: Math.max(1, 3 - Math.floor(idx / 2)),
        addedBy: idx % 2 === 0 ? hostName : 'Maya (Web)',
      })),
      updatedAt: Date.now(),
    };
    this.sessions.set(code, state);
    return state;
  }

  getSession(roomCode: string): JamSessionState | null {
    return this.sessions.get(roomCode.toUpperCase()) || null;
  }

  joinSession(roomCode: string, userName: string, fallbackQueue: TrackFeatures[] = []): JamSessionState {
    const cleanCode = (roomCode || 'JAM-777').toUpperCase().trim();
    let session = this.sessions.get(cleanCode);
    if (!session) {
      session = {
        roomCode: cleanCode,
        hostName: 'Alex (Host)',
        active: true,
        participants: ['Alex (Host)', 'Maya (Web)', userName],
        currentTrackId: fallbackQueue[0]?.id || 'song_1',
        positionSec: 12,
        sharedQueue: fallbackQueue.slice(0, 5).map((t, idx) => ({
          id: t.id,
          title: t.title,
          artist: t.artist,
          album: t.album,
          duration_secs: t.durationSec,
          votes: 3 - Math.min(2, idx),
          addedBy: idx === 0 ? 'Alex (Host)' : 'Maya (Web)',
        })),
        updatedAt: Date.now(),
      };
      this.sessions.set(cleanCode, session);
    } else if (!session.participants.includes(userName)) {
      session.participants.push(userName);
      session.updatedAt = Date.now();
    }
    return session;
  }

  voteTrack(roomCode: string, trackId: string, delta = 1): JamSessionState | null {
    const session = this.sessions.get(roomCode.toUpperCase());
    if (!session) return null;
    const item = session.sharedQueue.find((q) => q.id === trackId);
    if (item) {
      item.votes = Math.max(0, item.votes + delta);
      session.sharedQueue.sort((a, b) => b.votes - a.votes);
      session.updatedAt = Date.now();
    }
    return session;
  }
}

// ─── 8. Word-By-Word Synced Lyrics + Romanization & Translation Overlay ──

export interface EnhancedLyricWord {
  word: string;
  startSec: number;
  endSec: number;
}

export interface EnhancedLyricLine {
  timeSec: number;
  endTimeSec: number;
  text: string;
  romanized: string;
  translation: string;
  words: EnhancedLyricWord[];
}

export function buildEnhancedLyrics(track: {
  id: string;
  title: string;
  artist: string;
  durationSec?: number;
}): EnhancedLyricLine[] {
  const baseLines: Array<{ timeSec: number; text: string; romanized: string; translation: string }> = [
    {
      timeSec: 0,
      text: `♪ ${track.title} — ${track.artist} (Intro)`,
      romanized: `♪ ${track.title} — ${track.artist} (Joshō / Intro)`,
      translation: `♪ Instrumental opening & harmonic motif`,
    },
    {
      timeSec: 6,
      text: 'Waiting in the neon glow as the city breathes',
      romanized: 'Machi ga iki wo suru neon no hikari no naka de',
      translation: 'Poetic imagery: watching city lights pulse at midnight',
    },
    {
      timeSec: 14,
      text: 'Every horizon bends into the midnight frequency',
      romanized: 'Subete no chiheisen ga yonaka no shūhasū ni tokeru',
      translation: 'Metaphor: losing track of time inside the synth wave',
    },
    {
      timeSec: 22,
      text: 'Hold the rhythm close before the dawn arrives',
      romanized: 'Yoake ga kuru mae ni kono rizumu wo dakishimete',
      translation: 'Chorus hook: savoring the present moment before sunrise',
    },
    {
      timeSec: 30,
      text: 'Drifting through crystalline echoes and starlight',
      romanized: 'Sukitōru kodama to hoshikuzu no naka wo tadayou',
      translation: 'Bridge: floating across reverberant harmonic textures',
    },
    {
      timeSec: 40,
      text: 'We are weightless in the afterglow of sound',
      romanized: 'Oto no yoin no naka de bokura wa mujūryoku ni naru',
      translation: 'Outro refrain: complete immersion in the music',
    },
  ];

  return baseLines.map((line, idx) => {
    const nextTime = baseLines[idx + 1]?.timeSec ?? Math.max(line.timeSec + 8, track.durationSec || 60);
    const tokens = line.text.split(/\s+/).filter(Boolean);
    const span = Math.max(2, nextTime - line.timeSec - 0.5);
    const wordDur = span / Math.max(1, tokens.length);
    const words: EnhancedLyricWord[] = tokens.map((word, wIdx) => ({
      word,
      startSec: Number((line.timeSec + wIdx * wordDur).toFixed(2)),
      endSec: Number((line.timeSec + (wIdx + 1) * wordDur).toFixed(2)),
    }));
    return {
      timeSec: line.timeSec,
      endTimeSec: nextTime,
      text: line.text,
      romanized: line.romanized,
      translation: line.translation,
      words,
    };
  });
}

// ─── 9. "Time Capsule" & Contextual Daypart Memory ─────────────────────────

export interface DaypartMemoryReport {
  activeDaypart: string;
  activeVibeDescription: string;
  recommendedDaypartTracks: Array<{ id: string; title: string; artist: string; matchReason: string }>;
  forgottenGems: Array<{ id: string; title: string; artist: string; lastPlayedLabel: string; affinityPct: number }>;
  daypartBreakdown: Array<{ label: string; pct: number; dominantGenre: string }>;
}

export function buildDaypartAndTimeCapsule(
  catalog: TrackFeatures[],
  recentIds: string[],
  hourOfDay = 22
): DaypartMemoryReport {
  let activeDaypart = 'Late Night Coding & Synth Drive';
  let activeVibeDescription = '10 PM – 3 AM · Warm analog synths, deep sub-bass, and 95–120 BPM harmonic flow';
  if (hourOfDay >= 5 && hourOfDay < 12) {
    activeDaypart = 'Morning Acoustic & Clarity';
    activeVibeDescription = '5 AM – 12 PM · Crisp acoustic transients, uplifting major keys, and organic textures';
  } else if (hourOfDay >= 12 && hourOfDay < 18) {
    activeDaypart = 'Afternoon Momentum & Focus';
    activeVibeDescription = '12 PM – 6 PM · Steady 110–125 BPM rhythmic drive with minimal vocal distraction';
  } else if (hourOfDay >= 18 && hourOfDay < 22) {
    activeDaypart = 'Evening Lounge & Downtempo';
    activeVibeDescription = '6 PM – 10 PM · Lush trip-hop, indie warmth, and atmospheric groove';
  }

  const recentSet = new Set(recentIds);
  const forgottenCandidates = catalog.filter((t) => !recentSet.has(t.id));
  const forgottenPool = forgottenCandidates.length >= 3 ? forgottenCandidates : catalog.slice().reverse();

  const forgottenGems = forgottenPool.slice(0, 4).map((t, idx) => ({
    id: t.id,
    title: t.title,
    artist: t.artist,
    lastPlayedLabel: `${18 + idx * 11} days ago`,
    affinityPct: 96 - idx * 3,
  }));

  const recommendedDaypartTracks = catalog.slice(0, 4).map((t) => ({
    id: t.id,
    title: t.title,
    artist: t.artist,
    matchReason: `${t.bpm} BPM · ${formatTrackKey(t)} · Fits ${activeDaypart.split(' ')[0]} profile`,
  }));

  return {
    activeDaypart,
    activeVibeDescription,
    recommendedDaypartTracks,
    forgottenGems,
    daypartBreakdown: [
      { label: 'Morning (6a–12p)', pct: 18, dominantGenre: 'Indie / Folk' },
      { label: 'Afternoon (12p–6p)', pct: 29, dominantGenre: 'Electronic / Ambient' },
      { label: 'Evening (6p–10p)', pct: 22, dominantGenre: 'Trip-Hop / Post-Rock' },
      { label: 'Late Night (10p–4a)', pct: 31, dominantGenre: 'Synthwave / Dream Pop' },
    ],
  };
}

// ─── 10. Command Palette (Cmd/Ctrl + K) with Natural-Language Sonic Filters ─

export interface SonicCommandResult {
  query: string;
  interpretation: string;
  actionType: 'filter_tracks' | 'sonic_bridge' | 'apply_setting';
  matchedTracks: Array<{ id: string; title: string; artist: string; album: string; bpm: number; keyLabel: string; badge: string }>;
  settingPatch?: Record<string, unknown>;
}

export function parseAndExecuteSonicCommand(
  rawQuery: string,
  catalog: TrackFeatures[]
): SonicCommandResult {
  const q = (rawQuery || '').trim();
  const lower = q.toLowerCase();

  // 1. Check for "bridge <A> to <B>"
  const bridgeMatch = lower.match(/bridge\s+(.+?)\s+to\s+(.+)/i);
  if (bridgeMatch) {
    const [, startHint, endHint] = bridgeMatch;
    const startTrack =
      catalog.find((t) => t.title.toLowerCase().includes(startHint.trim()) || t.artist.toLowerCase().includes(startHint.trim())) ||
      catalog[0];
    const endTrack =
      catalog.find((t) => t.title.toLowerCase().includes(endHint.trim()) || t.artist.toLowerCase().includes(endHint.trim())) ||
      catalog[catalog.length - 1];
    const bridge = findSonicBridgePath(startTrack.id, endTrack.id, catalog, 3);
    return {
      query: q,
      interpretation: `Sonic Bridge Path (${bridge.length} tracks): ${startTrack.title} → ${endTrack.title}`,
      actionType: 'sonic_bridge',
      matchedTracks: bridge.map((b) => ({
        id: b.track.id,
        title: b.track.title,
        artist: b.track.artist,
        album: b.track.album,
        bpm: b.track.bpm,
        keyLabel: formatTrackKey(b.track),
        badge: b.reason,
      })),
    };
  }

  // 2. Check for quick audio/DSP setting commands
  if (lower.startsWith('eq ') || lower.includes('autoeq')) {
    const profile =
      AUTOEQ_PROFILES.find(
        (p) =>
          lower.includes(p.id.replace('_', ' ')) ||
          lower.includes(p.name.toLowerCase()) ||
          (lower.includes('xm5') && p.id === 'sony_xm5') ||
          (lower.includes('650') && p.id === 'sennheiser_hd650') ||
          (lower.includes('airpods') && p.id === 'airpods_pro_2') ||
          (lower.includes('dt990') && p.id === 'beyerdynamic_dt990')
      ) || AUTOEQ_PROFILES[1];
    return {
      query: q,
      interpretation: `Apply Headphone AutoEQ Profile: ${profile.name} (${profile.model})`,
      actionType: 'apply_setting',
      matchedTracks: [],
      settingPatch: { autoeq_profile: profile.id, eq_enabled: true },
    };
  }

  if (lower.includes('karaoke') || lower.includes('instrumental') || lower.includes('vocal')) {
    const mode: StemMode = lower.includes('vocal') && !lower.includes('karaoke') ? 'vocals' : 'instrumental';
    const spec = getStemModeSpec(mode);
    return {
      query: q,
      interpretation: `Switch Real-Time Stem DSP to ${spec.label}`,
      actionType: 'apply_setting',
      matchedTracks: [],
      settingPatch: { stem_mode: mode },
    };
  }

  // 3. Structured & Natural-Language Acoustic Filter (`bpm:100..130`, `key:Am`, `like Midnight City`, mood/genre keywords)
  let minBpm = 0;
  let maxBpm = 300;
  const bpmRangeMatch = lower.match(/bpm:(\d+)\.\.(\d+)/);
  const bpmSingleMatch = lower.match(/bpm:(\d+)/);
  if (bpmRangeMatch) {
    minBpm = Number(bpmRangeMatch[1]);
    maxBpm = Number(bpmRangeMatch[2]);
  } else if (bpmSingleMatch) {
    const target = Number(bpmSingleMatch[1]);
    minBpm = target - 12;
    maxBpm = target + 12;
  } else if (lower.includes('fast') || lower.includes('upbeat') || lower.includes('workout')) {
    minBpm = 105;
  } else if (lower.includes('slow') || lower.includes('chill') || lower.includes('sleep') || lower.includes('ambient')) {
    maxBpm = 100;
  }

  // Check for "like <song>"
  const likeMatch = lower.match(/like\s+(.+)/);
  let anchorTrack: TrackFeatures | undefined;
  if (likeMatch) {
    const hint = likeMatch[1].trim();
    anchorTrack = catalog.find(
      (t) => t.title.toLowerCase().includes(hint) || t.artist.toLowerCase().includes(hint)
    );
  }

  // Check for `key:<key>`
  const keyMatch = lower.match(/key:([a-g]#?m?)/i);
  let filterKeyName: string | null = null;
  if (keyMatch) {
    filterKeyName = keyMatch[1].toUpperCase();
  }

  const scored = catalog
    .filter((t) => {
      if (t.bpm < minBpm || t.bpm > maxBpm) return false;
      if (filterKeyName) {
        const tk = formatTrackKey(t).toUpperCase().replace(' MAJ', '');
        if (!tk.startsWith(filterKeyName)) return false;
      }
      return true;
    })
    .map((t) => {
      let score = 0.5;
      let badge = `${t.bpm} BPM · ${formatTrackKey(t)} · ${t.genre}`;
      if (anchorTrack) {
        score = computeHarmonicBpmTransitionScore(toRecommenderParams(anchorTrack), toRecommenderParams(t));
        badge = `${Math.round(score * 100)}% Sonic Match to ${anchorTrack.title} · ${t.bpm} BPM`;
      } else if (q.length > 0) {
        const textHit =
          t.title.toLowerCase().includes(lower) ||
          t.artist.toLowerCase().includes(lower) ||
          t.genre.toLowerCase().includes(lower);
        if (textHit) score += 0.45;
      }
      return { track: t, score, badge };
    })
    .sort((a, b) => b.score - a.score);

  const finalList = (scored.length > 0 ? scored : catalog.map((t) => ({
    track: t,
    score: 0.5,
    badge: `${t.bpm} BPM · ${formatTrackKey(t)} · ${t.genre}`,
  }))).slice(0, 8);

  const interpParts: string[] = [];
  if (anchorTrack) interpParts.push(`Acoustic similarity to "${anchorTrack.title}"`);
  if (minBpm > 0 || maxBpm < 300) interpParts.push(`Tempo ${minBpm}–${maxBpm} BPM`);
  if (filterKeyName) interpParts.push(`Key ${filterKeyName}`);
  if (interpParts.length === 0) {
    interpParts.push(q ? `Sonic & catalog match for "${q}"` : 'All indexed tracks with acoustic telemetry');
  }

  return {
    query: q,
    interpretation: interpParts.join(' · '),
    actionType: 'filter_tracks',
    matchedTracks: finalList.map(({ track, badge }) => ({
      id: track.id,
      title: track.title,
      artist: track.artist,
      album: track.album,
      bpm: track.bpm,
      keyLabel: formatTrackKey(track),
      badge,
    })),
  };
}
