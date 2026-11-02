export const FEATURE_DIM = 30;
export const NUM_TASTE_PERSONAS = 6;

export interface TrackItem {
  id: string;
  source: 'local' | 'saavn' | 'yt';
  title: string;
  artist: string;
  album: string;
  genre: string;
  durationSecs: number;
  bpm: number;
  camelotKey: number; // 1..12 on the Camelot harmonic wheel
  camelotMode: 'A' | 'B'; // A = minor, B = major
  energy: number; // 0..1
  valence: number; // 0..1
  danceability: number; // 0..1
  acousticness: number; // 0..1
  temporalVariance: number; // dynamic contrast over song segments
  vector: number[]; // 30-D normalized audio/semantic vector
  playCount: number;
  skipCount: number;
  liked: boolean;
  cachedOffline: boolean;
  thumbnailUrl?: string;
}

export interface TastePersona {
  id: number;
  label: string;
  centroid: number[];
  weight: number;
  sampleCount: number;
  dominantGenre: string;
}

export interface AdaptiveSignalWeights {
  similarity: number;
  affinity: number;
  profile: number;
  session: number;
  artist: number;
  cluster: number;
  context: number;
  discovery: number;
  harmonic: number;
  cooccurrence: number;
}

export interface ScoredRecommendation {
  trackId: string;
  track: TrackItem;
  totalScore: number;
  matchedPersona: string;
  explanation: string;
  signals: Record<keyof AdaptiveSignalWeights, number>;
}

export interface PrefetchPlanItem {
  trackId: string;
  title: string;
  artist: string;
  source: 'local' | 'saavn' | 'yt';
  reason: 'queue-next' | 'smart-radio' | 'taste-persona';
  confidence: number;
  targetBitrateKbps: number;
  estimatedBufferKb: number;
  status: 'ready' | 'warming';
}

export function defaultSignalWeights(): AdaptiveSignalWeights {
  return {
    similarity: 0.22,
    affinity: 0.14,
    profile: 0.16,
    session: 0.10,
    artist: 0.08,
    cluster: 0.06,
    context: 0.06,
    discovery: 0.04,
    harmonic: 0.08,
    cooccurrence: 0.06,
  };
}

export function l2Normalize(vec: number[]): number[] {
  let sumSq = 0;
  for (let i = 0; i < vec.length; i++) {
    sumSq += vec[i] * vec[i];
  }
  const norm = Math.sqrt(sumSq);
  if (norm < 1e-9) return vec.map(() => 0);
  return vec.map((x) => x / norm);
}

export function cosineSimilarity(a: number[], b: number[]): number {
  const len = Math.min(a.length, b.length);
  if (len === 0) return 0;
  let dot = 0;
  let normA = 0;
  let normB = 0;
  for (let i = 0; i < len; i++) {
    dot += a[i] * b[i];
    normA += a[i] * a[i];
    normB += b[i] * b[i];
  }
  const denom = Math.sqrt(normA) * Math.sqrt(normB);
  if (denom < 1e-9) return 0;
  return Math.max(-1, Math.min(1, dot / denom));
}

/**
 * Generates a deterministic 30-D feature vector from track attributes & temporal dynamics
 * so both local files and online (JioSaavn / YouTube) tracks share the exact same embedding space.
 */
export function buildUnifiedTrackVector(params: {
  bpm: number;
  camelotKey: number;
  camelotMode: 'A' | 'B';
  energy: number;
  valence: number;
  danceability: number;
  acousticness: number;
  temporalVariance: number;
  genre: string;
}): number[] {
  const vec = new Array<number>(FEATURE_DIM).fill(0);
  const normBpm = Math.max(0, Math.min(1, (params.bpm - 60) / 140));
  vec[0] = normBpm;
  vec[1] = params.energy;
  vec[2] = params.valence;
  vec[3] = params.danceability;
  vec[4] = params.acousticness;
  vec[5] = params.temporalVariance;
  vec[6] = params.camelotMode === 'B' ? 0.85 : 0.25;

  // 12-bin harmonic chroma encoding around the Camelot key wheel
  const keyIdx = ((params.camelotKey - 1) % 12 + 12) % 12;
  for (let i = 0; i < 12; i++) {
    const dist = Math.min(Math.abs(i - keyIdx), 12 - Math.abs(i - keyIdx));
    vec[7 + i] = dist === 0 ? 1.0 : dist === 1 ? 0.6 : dist === 2 ? 0.25 : 0.05;
  }

  // Genre & spectral envelope signature across remaining 11 dimensions
  const genreLower = params.genre.toLowerCase();
  const genreSeeds: Record<string, number[]> = {
    electronic: [0.9, 0.8, 0.2, 0.85, 0.7, 0.3, 0.9, 0.6, 0.8, 0.4, 0.75],
    synthwave: [0.85, 0.75, 0.3, 0.8, 0.65, 0.4, 0.88, 0.7, 0.75, 0.5, 0.8],
    indie: [0.45, 0.55, 0.7, 0.5, 0.6, 0.65, 0.4, 0.75, 0.5, 0.65, 0.55],
    rock: [0.75, 0.85, 0.4, 0.6, 0.8, 0.5, 0.7, 0.65, 0.85, 0.45, 0.6],
    hiphop: [0.8, 0.7, 0.35, 0.9, 0.75, 0.3, 0.85, 0.5, 0.7, 0.8, 0.65],
    pop: [0.7, 0.65, 0.55, 0.85, 0.6, 0.5, 0.75, 0.8, 0.6, 0.7, 0.7],
    ambient: [0.15, 0.2, 0.9, 0.2, 0.3, 0.85, 0.25, 0.9, 0.2, 0.8, 0.3],
    jazz: [0.35, 0.45, 0.85, 0.55, 0.5, 0.9, 0.3, 0.85, 0.45, 0.75, 0.5],
  };

  const matchedKey =
    Object.keys(genreSeeds).find((k) => genreLower.includes(k)) ?? 'pop';
  const profile = genreSeeds[matchedKey];
  for (let i = 0; i < 11; i++) {
    vec[19 + i] = profile[i] * 0.7 + params.energy * 0.15 + params.valence * 0.15;
  }

  return l2Normalize(vec);
}

/**
 * Z-Score Feature Standardizer across the library so high-variance dimensions
 * do not drown out subtle timbral/chroma dimensions.
 */
export function standardizeVectorsZScore(vectors: number[][]): {
  standardized: number[][];
  mean: number[];
  std: number[];
} {
  if (vectors.length === 0) {
    return { standardized: [], mean: [], std: [] };
  }
  const dim = vectors[0].length;
  const mean = new Array<number>(dim).fill(0);
  const std = new Array<number>(dim).fill(0);

  for (const v of vectors) {
    for (let d = 0; d < dim; d++) {
      mean[d] += v[d];
    }
  }
  for (let d = 0; d < dim; d++) {
    mean[d] /= vectors.length;
  }

  for (const v of vectors) {
    for (let d = 0; d < dim; d++) {
      const diff = v[d] - mean[d];
      std[d] += diff * diff;
    }
  }
  for (let d = 0; d < dim; d++) {
    std[d] = Math.sqrt(std[d] / Math.max(1, vectors.length));
    if (std[d] < 1e-6) std[d] = 1.0;
  }

  const standardized = vectors.map((v) =>
    l2Normalize(v.map((val, d) => (val - mean[d]) / std[d]))
  );

  return { standardized, mean, std };
}

/**
 * Sequence-Aware Harmonic (Camelot Wheel) & BPM Transition Compatibility Score in [0, 1].
 * Adjacent Camelot keys (±1 hour on the wheel, or relative major/minor A<->B) and smooth BPM transitions
 * score highest, eliminating jarring transitions in Autoplay / Smart Radio.
 */
export function computeHarmonicBpmTransitionScore(
  current: Pick<TrackItem, 'bpm' | 'camelotKey' | 'camelotMode' | 'energy'>,
  candidate: Pick<TrackItem, 'bpm' | 'camelotKey' | 'camelotMode' | 'energy'>
): number {
  const wheelDiff = Math.abs(current.camelotKey - candidate.camelotKey);
  const circularDistance = Math.min(wheelDiff, 12 - wheelDiff);

  let harmonicScore = 0.25;
  if (circularDistance === 0 && current.camelotMode === candidate.camelotMode) {
    harmonicScore = 1.0; // Exact same key
  } else if (circularDistance === 0 && current.camelotMode !== candidate.camelotMode) {
    harmonicScore = 0.92; // Relative major/minor (e.g., 8A <-> 8B)
  } else if (circularDistance === 1 && current.camelotMode === candidate.camelotMode) {
    harmonicScore = 0.88; // Perfect fifth / fourth step on Camelot wheel
  } else if (circularDistance === 1) {
    harmonicScore = 0.72;
  } else if (circularDistance === 2) {
    harmonicScore = 0.55;
  }

  // BPM continuity including half-time / double-time awareness
  const directBpmDiff = Math.abs(current.bpm - candidate.bpm);
  const halfTimeDiff = Math.abs(current.bpm - candidate.bpm * 2);
  const doubleTimeDiff = Math.abs(current.bpm * 2 - candidate.bpm);
  const bestBpmDiff = Math.min(directBpmDiff, halfTimeDiff, doubleTimeDiff);
  const bpmScore = Math.exp(-bestBpmDiff / 22.0);

  // Energy arc continuity
  const energyDiff = Math.abs(current.energy - candidate.energy);
  const energyScore = Math.max(0, 1 - energyDiff * 1.2);

  return Math.max(0, Math.min(1, 0.45 * harmonicScore + 0.35 * bpmScore + 0.20 * energyScore));
}

/**
 * Multi-Signal Contextual Bandit Weight Update (replaces winner-take-all dominant_signal).
 * Updates all active signals proportionally to their centered contribution and re-normalizes.
 */
export function updateBanditWeights(
  weights: AdaptiveSignalWeights,
  signalContributions: Record<keyof AdaptiveSignalWeights, number>,
  reward: number, // e.g. +1.0 for full play/like, -0.8 for early skip
  learningRate = 0.04
): AdaptiveSignalWeights {
  const keys = Object.keys(weights) as Array<keyof AdaptiveSignalWeights>;
  const meanSignal =
    keys.reduce((acc, k) => acc + (signalContributions[k] ?? 0), 0) / keys.length;

  const updated: AdaptiveSignalWeights = { ...weights };
  let sum = 0;
  for (const k of keys) {
    const centered = (signalContributions[k] ?? 0) - meanSignal;
    const nextVal = Math.max(0.02, Math.min(0.45, weights[k] + learningRate * reward * centered));
    updated[k] = nextVal;
    sum += nextVal;
  }

  for (const k of keys) {
    updated[k] = Number((updated[k] / sum).toFixed(4));
  }
  return updated;
}

/**
 * Unified Hybrid Spotify-Grade Recommender Engine.
 * Combines:
 * 1. Multi-Modal Taste Personas (K=6 sub-profiles so distinct genres/moods never collapse into a muddy average)
 * 2. Co-Occurrence Session Graph (tracks played together in sessions or playlists)
 * 3. Sequence-Aware Harmonic (Camelot) & BPM Transition Continuity
 * 4. Contextual Bandit 10-Signal Scorer + MMR Artist/Cluster Diversity
 * 5. Predictive Prefetching Planner
 */
export class HybridRecommenderEngine {
  private tracks = new Map<string, TrackItem>();
  private personas: TastePersona[] = [];
  private cooccurrence = new Map<string, Map<string, number>>();
  private sessionHistory: string[] = [];
  private weights: AdaptiveSignalWeights = defaultSignalWeights();

  constructor(initialTracks: TrackItem[] = []) {
    for (const t of initialTracks) {
      this.indexTrack(t);
    }
    this.initializePersonas();
  }

  indexTrack(track: TrackItem): void {
    const normalizedVector =
      track.vector && track.vector.length === FEATURE_DIM
        ? l2Normalize(track.vector)
        : buildUnifiedTrackVector(track);
    this.tracks.set(track.id, {
      ...track,
      vector: normalizedVector,
    });
  }

  getTrack(id: string): TrackItem | undefined {
    return this.tracks.get(id);
  }

  getAllTracks(): TrackItem[] {
    return Array.from(this.tracks.values());
  }

  getPersonas(): TastePersona[] {
    return this.personas.map((p) => ({ ...p, centroid: [...p.centroid] }));
  }

  getWeights(): AdaptiveSignalWeights {
    return { ...this.weights };
  }

  private initializePersonas(): void {
    const defaultSeeds: Array<{ label: string; genre: string; energy: number; valence: number; bpm: number }> = [
      { label: 'Late-Night Synth & Electronic', genre: 'synthwave', energy: 0.82, valence: 0.45, bpm: 118 },
      { label: 'Indie & Alternative Focus', genre: 'indie', energy: 0.55, valence: 0.65, bpm: 108 },
      { label: 'High-Energy Workout & Club', genre: 'electronic', energy: 0.92, valence: 0.78, bpm: 128 },
      { label: 'Acoustic & Ambient Chill', genre: 'ambient', energy: 0.24, valence: 0.52, bpm: 82 },
      { label: 'Modern Pop & Groove', genre: 'pop', energy: 0.72, valence: 0.82, bpm: 116 },
      { label: 'Deep Rhythm & Hip-Hop', genre: 'hiphop', energy: 0.76, valence: 0.58, bpm: 96 },
    ];

    this.personas = defaultSeeds.slice(0, NUM_TASTE_PERSONAS).map((seed, idx) => ({
      id: idx,
      label: seed.label,
      dominantGenre: seed.genre,
      weight: 1 / NUM_TASTE_PERSONAS,
      sampleCount: 1,
      centroid: buildUnifiedTrackVector({
        bpm: seed.bpm,
        camelotKey: ((idx * 2) % 12) + 1,
        camelotMode: idx % 2 === 0 ? 'A' : 'B',
        energy: seed.energy,
        valence: seed.valence,
        danceability: 0.68,
        acousticness: 1 - seed.energy * 0.7,
        temporalVariance: 0.45,
        genre: seed.genre,
      }),
    }));
  }

  /**
   * Updates the closest taste sub-profile (persona) rather than collapsing all plays into a single mean vector,
   * records co-occurrence edges with recent session tracks, and adapts bandit signal weights.
   */
  recordInteraction(params: {
    trackId: string;
    completedRatio: number; // 0..1
    liked?: boolean;
    skippedEarly?: boolean;
  }): { updatedPersona: TastePersona | null; updatedWeights: AdaptiveSignalWeights } {
    const track = this.tracks.get(params.trackId);
    if (!track) {
      return { updatedPersona: null, updatedWeights: this.getWeights() };
    }

    if (params.liked !== undefined) {
      track.liked = params.liked;
    }
    if (params.skippedEarly) {
      track.skipCount += 1;
    } else if (params.completedRatio >= 0.5) {
      track.playCount += 1;
    }

    // Update Co-Occurrence Graph with last 3 played tracks in session
    if (!params.skippedEarly && params.completedRatio >= 0.4) {
      const recent = this.sessionHistory.slice(-3);
      for (const prevId of recent) {
        if (prevId !== track.id) {
          this.incrementCooccurrence(prevId, track.id, 1.0);
          this.incrementCooccurrence(track.id, prevId, 0.7);
        }
      }
      this.sessionHistory.push(track.id);
      if (this.sessionHistory.length > 30) {
        this.sessionHistory.shift();
      }
    }

    // Find closest Taste Persona via cosine similarity
    let bestIdx = 0;
    let bestSim = -2;
    for (let i = 0; i < this.personas.length; i++) {
      const sim = cosineSimilarity(this.personas[i].centroid, track.vector);
      if (sim > bestSim) {
        bestSim = sim;
        bestIdx = i;
      }
    }

    const persona = this.personas[bestIdx];
    const positiveSignal = !params.skippedEarly && (params.completedRatio >= 0.6 || params.liked);

    if (positiveSignal) {
      const alpha = params.liked ? 0.28 : 0.18;
      persona.centroid = l2Normalize(
        persona.centroid.map((val, d) => (1 - alpha) * val + alpha * track.vector[d])
      );
      persona.sampleCount += 1;
      persona.weight = Math.min(0.55, persona.weight * 1.08);
    } else if (params.skippedEarly) {
      persona.weight = Math.max(0.05, persona.weight * 0.94);
    }

    // Normalize persona weights
    const weightSum = this.personas.reduce((acc, p) => acc + p.weight, 0);
    for (const p of this.personas) {
      p.weight = Number((p.weight / weightSum).toFixed(4));
    }

    const reward = params.liked ? 1.0 : params.skippedEarly ? -0.75 : (params.completedRatio - 0.35);
    const prevSeed = this.sessionHistory.length >= 2
      ? this.tracks.get(this.sessionHistory[this.sessionHistory.length - 2])
      : undefined;
    const signals = this.computeSignalsForTrack(track, prevSeed);
    this.weights = updateBanditWeights(this.weights, signals, reward);

    return {
      updatedPersona: { ...persona, centroid: [...persona.centroid] },
      updatedWeights: this.getWeights(),
    };
  }

  private incrementCooccurrence(fromId: string, toId: string, delta: number): void {
    let row = this.cooccurrence.get(fromId);
    if (!row) {
      row = new Map<string, number>();
      this.cooccurrence.set(fromId, row);
    }
    row.set(toId, (row.get(toId) ?? 0) + delta);
  }

  public getCooccurrenceScore(seedId: string | undefined, candidateId: string): number {
    if (!seedId) return 0;
    const row = this.cooccurrence.get(seedId);
    if (!row) return 0;
    const raw = row.get(candidateId) ?? 0;
    return 1 - Math.exp(-raw * 0.6);
  }

  private computeSignalsForTrack(
    candidate: TrackItem,
    seedTrack?: TrackItem,
    hourOfDay = 20
  ): Record<keyof AdaptiveSignalWeights, number> {
    const similarity = seedTrack
      ? Math.max(0, (cosineSimilarity(seedTrack.vector, candidate.vector) + 1) / 2)
      : 0.65;

    const netPlays = Math.max(0, candidate.playCount - candidate.skipCount * 0.8);
    const affinity = Math.min(1, (candidate.liked ? 0.45 : 0) + (1 - Math.exp(-netPlays * 0.35)) * 0.55);

    // Multi-modal persona score: max over K personas of cosine(persona_k, candidate)
    let maxPersonaSim = 0;
    for (const p of this.personas) {
      const sim = Math.max(0, cosineSimilarity(p.centroid, candidate.vector));
      const weightedSim = sim * (0.75 + 0.25 * p.weight * NUM_TASTE_PERSONAS);
      if (weightedSim > maxPersonaSim) {
        maxPersonaSim = Math.min(1, weightedSim);
      }
    }

    // Session momentum
    let sessionScore = 0.5;
    if (this.sessionHistory.length > 0) {
      const recentTracks = this.sessionHistory
        .slice(-4)
        .map((id) => this.tracks.get(id))
        .filter((t): t is TrackItem => Boolean(t));
      if (recentTracks.length > 0) {
        const avgSim =
          recentTracks.reduce((acc, t) => acc + Math.max(0, cosineSimilarity(t.vector, candidate.vector)), 0) /
          recentTracks.length;
        sessionScore = avgSim;
      }
    }

    const artistScore =
      seedTrack && seedTrack.artist.toLowerCase() === candidate.artist.toLowerCase() ? 0.85 : 0.45;

    const clusterScore =
      seedTrack && seedTrack.genre.toLowerCase() === candidate.genre.toLowerCase() ? 0.9 : 0.45;

    // Diurnal context alignment (morning = uplifting, afternoon = focus/groove, night = synth/chill/deep)
    const targetEnergy = hourOfDay >= 6 && hourOfDay <= 18 ? 0.72 : 0.58;
    const contextScore = Math.max(0, 1 - Math.abs(candidate.energy - targetEnergy));

    // Discovery bonus for low-play high-fit tracks
    const discoveryScore = candidate.playCount === 0 ? 0.9 : Math.exp(-candidate.playCount * 0.4);

    const harmonicScore = seedTrack
      ? computeHarmonicBpmTransitionScore(seedTrack, candidate)
      : 0.7;

    const cooccurrenceScore = this.getCooccurrenceScore(seedTrack?.id, candidate.id);

    return {
      similarity,
      affinity,
      profile: maxPersonaSim,
      session: sessionScore,
      artist: artistScore,
      cluster: clusterScore,
      context: contextScore,
      discovery: discoveryScore,
      harmonic: harmonicScore,
      cooccurrence: cooccurrenceScore,
    };
  }

  /**
   * Generates ranked, MMR-diversified recommendations with human-readable explanations.
   */
  recommend(options: {
    seedTrackId?: string;
    limit?: number;
    excludeTrackIds?: string[];
    hourOfDay?: number;
  } = {}): ScoredRecommendation[] {
    const limit = options.limit ?? 10;
    const exclude = new Set<string>(options.excludeTrackIds ?? []);
    if (options.seedTrackId) exclude.add(options.seedTrackId);

    const seedTrack = options.seedTrackId
      ? this.tracks.get(options.seedTrackId)
      : this.sessionHistory.length > 0
      ? this.tracks.get(this.sessionHistory[this.sessionHistory.length - 1])
      : undefined;

    const rawCandidates: ScoredRecommendation[] = [];
    for (const candidate of this.tracks.values()) {
      if (exclude.has(candidate.id)) continue;

      const signals = this.computeSignalsForTrack(candidate, seedTrack, options.hourOfDay ?? 20);
      let totalScore = 0;
      for (const k of Object.keys(this.weights) as Array<keyof AdaptiveSignalWeights>) {
        totalScore += this.weights[k] * signals[k];
      }

      // Identify best matching persona for explanation UI
      let bestPersona = this.personas[0];
      let bestPersonaSim = -1;
      for (const p of this.personas) {
        const s = cosineSimilarity(p.centroid, candidate.vector);
        if (s > bestPersonaSim) {
          bestPersonaSim = s;
          bestPersona = p;
        }
      }

      const explanation = this.buildExplanation(candidate, seedTrack, signals, bestPersona);
      rawCandidates.push({
        trackId: candidate.id,
        track: candidate,
        totalScore: Number(totalScore.toFixed(4)),
        matchedPersona: bestPersona.label,
        explanation,
        signals,
      });
    }

    // Maximal Marginal Relevance (MMR) re-ranking for artist & sonic diversity
    const selected: ScoredRecommendation[] = [];
    const artistCounts = new Map<string, number>();
    const pool = [...rawCandidates].sort((a, b) => b.totalScore - a.totalScore);

    while (selected.length < limit && pool.length > 0) {
      let bestIdx = 0;
      let bestMmr = -Infinity;

      for (let i = 0; i < pool.length; i++) {
        const cand = pool[i];
        const artistKey = cand.track.artist.toLowerCase();
        const artistPenalty = (artistCounts.get(artistKey) ?? 0) * 0.12;

        let maxSimToSelected = 0;
        for (const sel of selected) {
          const sim = cosineSimilarity(cand.track.vector, sel.track.vector);
          if (sim > maxSimToSelected) maxSimToSelected = sim;
        }

        const mmrScore = 0.78 * cand.totalScore - 0.22 * maxSimToSelected - artistPenalty;
        if (mmrScore > bestMmr) {
          bestMmr = mmrScore;
          bestIdx = i;
        }
      }

      const [chosen] = pool.splice(bestIdx, 1);
      const artistKey = chosen.track.artist.toLowerCase();
      artistCounts.set(artistKey, (artistCounts.get(artistKey) ?? 0) + 1);
      selected.push(chosen);
    }

    return selected;
  }

  private buildExplanation(
    candidate: TrackItem,
    seedTrack: TrackItem | undefined,
    signals: Record<keyof AdaptiveSignalWeights, number>,
    persona: TastePersona
  ): string {
    if (seedTrack && signals.harmonic >= 0.85) {
      return `Harmonic ${candidate.camelotKey}${candidate.camelotMode} · ${candidate.bpm} BPM match with "${seedTrack.title}"`;
    }
    if (signals.cooccurrence >= 0.45 && seedTrack) {
      return `Frequently paired in sessions after "${seedTrack.title}"`;
    }
    if (signals.profile >= 0.75) {
      return `Matches your "${persona.label}" taste profile`;
    }
    if (signals.discovery >= 0.8) {
      return `Fresh discovery in ${candidate.genre} (${candidate.bpm} BPM)`;
    }
    return `Sonic & timbral match · ${persona.label}`;
  }

  /**
   * Predictive Prefetching Planner:
   * Pre-warms upcoming queue tracks + top Smart Radio / Persona recommendations
   * according to user settings (`predictive_prefetch`, `prefetch_count`, `streaming_quality`, `instant_play_buffer_kb`).
   */
  planPredictivePrefetch(params: {
    currentTrackId?: string;
    upcomingQueueIds?: string[];
    prefetchEnabled: boolean;
    prefetchCount: number;
    streamingQuality: 'low' | 'normal' | 'high' | 'lossless';
    instantPlayBufferKb?: number;
  }): PrefetchPlanItem[] {
    if (!params.prefetchEnabled || params.prefetchCount <= 0) {
      return [];
    }

    const maxItems = Math.max(1, Math.min(5, params.prefetchCount));
    const bitrateMap: Record<'low' | 'normal' | 'high' | 'lossless', number> = {
      low: 96,
      normal: 160,
      high: 320,
      lossless: 1411,
    };
    const targetBitrateKbps = bitrateMap[params.streamingQuality] ?? 320;
    const estimatedBufferKb = params.instantPlayBufferKb ?? 64;

    const planned: PrefetchPlanItem[] = [];
    const seen = new Set<string>();
    if (params.currentTrackId) seen.add(params.currentTrackId);

    // 1. Prioritize explicit upcoming queue tracks first
    for (const qId of params.upcomingQueueIds ?? []) {
      if (planned.length >= maxItems) break;
      if (seen.has(qId)) continue;
      const track = this.tracks.get(qId);
      if (!track) continue;
      seen.add(qId);
      planned.push({
        trackId: track.id,
        title: track.title,
        artist: track.artist,
        source: track.source,
        reason: 'queue-next',
        confidence: 0.98,
        targetBitrateKbps,
        estimatedBufferKb,
        status: 'ready',
      });
    }

    // 2. Fill remaining prefetch slots with highest-probability hybrid recommendations
    if (planned.length < maxItems) {
      const recs = this.recommend({
        seedTrackId: params.currentTrackId,
        limit: maxItems * 2,
        excludeTrackIds: Array.from(seen),
      });

      for (const rec of recs) {
        if (planned.length >= maxItems) break;
        if (seen.has(rec.trackId)) continue;
        seen.add(rec.trackId);
        planned.push({
          trackId: rec.track.id,
          title: rec.track.title,
          artist: rec.track.artist,
          source: rec.track.source,
          reason: rec.signals.harmonic >= 0.8 ? 'smart-radio' : 'taste-persona',
          confidence: Number(Math.min(0.96, Math.max(0.62, rec.totalScore)).toFixed(2)),
          targetBitrateKbps,
          estimatedBufferKb,
          status: 'ready',
        });
      }
    }

    return planned;
  }
}
