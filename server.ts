import express from 'express';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import {
  buildUnifiedTrackVector,
  HybridRecommenderEngine,
  TrackItem,
} from './src/engine/recommender.ts';
import {
  AudioSampleRate,
  DownloadQuality,
  EndpointScoreboard,
  getQualitySpec,
  isCrossSourceMatch,
  StreamingQuality,
} from './src/engine/streamer.ts';
import {
  AUTOEQ_PROFILES,
  applyTubeWarmthSample,
  buildDaypartAndTimeCapsule,
  buildEnhancedLyrics,
  computeBeatAlignedCrossfade,
  computeLoudnessNormalization,
  DEFAULT_TASTE_DNA,
  findSonicBridgePath,
  getAutoEqProfile,
  getStemModeSpec,
  JamSessionEngine,
  parseAndExecuteSonicCommand,
  scoreTracksWithPersonaMix,
  StemMode,
  TASTE_PERSONAS,
  TasteDnaConfig,
  TrackFeatures,
} from './src/engine/features.ts';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const app = express();
app.use(express.json());

// ─── Seeded Multi-Source Catalog (Local + JioSaavn Direct + YouTube Music) ───
const CATALOG_SEED: Array<Omit<TrackItem, 'vector'>> = [
  {
    id: '1',
    source: 'local',
    title: 'Midnight City',
    artist: 'M83',
    album: "Hurry Up, We're Dreaming",
    genre: 'Synthwave',
    durationSecs: 244,
    bpm: 105,
    camelotKey: 10,
    camelotMode: 'A',
    energy: 0.86,
    valence: 0.68,
    danceability: 0.72,
    acousticness: 0.08,
    temporalVariance: 0.45,
    playCount: 8,
    skipCount: 0,
    liked: true,
    cachedOffline: true,
    filePath: '/library/Midnight_City.flac',
  } as Omit<TrackItem, 'vector'> & { filePath?: string },
  {
    id: '2',
    source: 'local',
    title: 'Teardrop',
    artist: 'Massive Attack',
    album: 'Mezzanine',
    genre: 'Trip-Hop',
    durationSecs: 329,
    bpm: 77,
    camelotKey: 8,
    camelotMode: 'A',
    energy: 0.48,
    valence: 0.38,
    danceability: 0.62,
    acousticness: 0.42,
    temporalVariance: 0.35,
    playCount: 5,
    skipCount: 0,
    liked: true,
    cachedOffline: true,
  },
  {
    id: '3',
    source: 'local',
    title: 'Breathe (In the Air)',
    artist: 'Pink Floyd',
    album: 'Dark Side of the Moon',
    genre: 'Art Rock',
    durationSecs: 165,
    bpm: 126,
    camelotKey: 9,
    camelotMode: 'A',
    energy: 0.42,
    valence: 0.44,
    danceability: 0.38,
    acousticness: 0.65,
    temporalVariance: 0.38,
    playCount: 3,
    skipCount: 0,
    liked: false,
    cachedOffline: true,
  },
  {
    id: '4',
    source: 'local',
    title: 'Intro',
    artist: 'The xx',
    album: 'xx',
    genre: 'Indie',
    durationSecs: 127,
    bpm: 100,
    camelotKey: 8,
    camelotMode: 'A',
    energy: 0.64,
    valence: 0.45,
    danceability: 0.68,
    acousticness: 0.35,
    temporalVariance: 0.30,
    playCount: 6,
    skipCount: 0,
    liked: true,
    cachedOffline: true,
  },
  {
    id: '5',
    source: 'local',
    title: 'Motion Picture Soundtrack',
    artist: 'Radiohead',
    album: 'Kid A',
    genre: 'Ambient',
    durationSecs: 421,
    bpm: 102,
    camelotKey: 6,
    camelotMode: 'B',
    energy: 0.22,
    valence: 0.20,
    danceability: 0.25,
    acousticness: 0.88,
    temporalVariance: 0.28,
    playCount: 2,
    skipCount: 0,
    liked: false,
    cachedOffline: true,
  },
  {
    id: '6',
    source: 'saavn',
    title: 'Blue (Da Ba Dee)',
    artist: 'Eiffel 65',
    album: 'Europop',
    genre: 'Electronic',
    durationSecs: 220,
    bpm: 128,
    camelotKey: 6,
    camelotMode: 'A',
    energy: 0.89,
    valence: 0.78,
    danceability: 0.84,
    acousticness: 0.05,
    temporalVariance: 0.40,
    playCount: 4,
    skipCount: 1,
    liked: false,
    cachedOffline: true,
  },
  {
    id: '7',
    source: 'local',
    title: 'Crystalised',
    artist: 'The xx',
    album: 'xx',
    genre: 'Indie',
    durationSecs: 227,
    bpm: 103,
    camelotKey: 9,
    camelotMode: 'A',
    energy: 0.56,
    valence: 0.52,
    danceability: 0.70,
    acousticness: 0.48,
    temporalVariance: 0.34,
    playCount: 4,
    skipCount: 0,
    liked: false,
    cachedOffline: true,
  },
  {
    id: '8',
    source: 'local',
    title: 'Holocene',
    artist: 'Bon Iver',
    album: 'Bon Iver',
    genre: 'Indie Folk',
    durationSecs: 337,
    bpm: 74,
    camelotKey: 5,
    camelotMode: 'B',
    energy: 0.34,
    valence: 0.42,
    danceability: 0.36,
    acousticness: 0.86,
    temporalVariance: 0.42,
    playCount: 3,
    skipCount: 0,
    liked: true,
    cachedOffline: true,
  },
  {
    id: '9',
    source: 'local',
    title: 'Svefn-g-englar',
    artist: 'Sigur Rós',
    album: 'Ágætis byrjun',
    genre: 'Ambient',
    durationSecs: 610,
    bpm: 78,
    camelotKey: 7,
    camelotMode: 'B',
    energy: 0.28,
    valence: 0.35,
    danceability: 0.22,
    acousticness: 0.79,
    temporalVariance: 0.32,
    playCount: 2,
    skipCount: 0,
    liked: false,
    cachedOffline: true,
  },
  {
    id: '10',
    source: 'saavn',
    title: 'Porcelain',
    artist: 'Moby',
    album: 'Play',
    genre: 'Trip-Hop',
    durationSecs: 238,
    bpm: 95,
    camelotKey: 10,
    camelotMode: 'A',
    energy: 0.62,
    valence: 0.56,
    danceability: 0.66,
    acousticness: 0.32,
    temporalVariance: 0.36,
    playCount: 5,
    skipCount: 0,
    liked: true,
    cachedOffline: true,
  },
  {
    id: '11',
    source: 'local',
    title: 'Teardrop',
    artist: 'José González',
    album: 'Veneer',
    genre: 'Indie Folk',
    durationSecs: 191,
    bpm: 82,
    camelotKey: 8,
    camelotMode: 'B',
    energy: 0.31,
    valence: 0.48,
    danceability: 0.50,
    acousticness: 0.91,
    temporalVariance: 0.24,
    playCount: 2,
    skipCount: 0,
    liked: false,
    cachedOffline: true,
  },
  {
    id: '12',
    source: 'local',
    title: 'Everything In Its Right Place',
    artist: 'Radiohead',
    album: 'Kid A',
    genre: 'Electronic',
    durationSecs: 252,
    bpm: 124,
    camelotKey: 7,
    camelotMode: 'A',
    energy: 0.66,
    valence: 0.34,
    danceability: 0.58,
    acousticness: 0.44,
    temporalVariance: 0.41,
    playCount: 7,
    skipCount: 0,
    liked: true,
    cachedOffline: true,
  },
  {
    id: 'saavn:nightcall',
    source: 'saavn',
    title: 'Nightcall',
    artist: 'Kavinsky',
    album: 'OutRun',
    genre: 'Synthwave',
    durationSecs: 258,
    bpm: 91,
    camelotKey: 10,
    camelotMode: 'A',
    energy: 0.78,
    valence: 0.48,
    danceability: 0.76,
    acousticness: 0.12,
    temporalVariance: 0.39,
    playCount: 4,
    skipCount: 0,
    liked: true,
    cachedOffline: true,
  },
  {
    id: 'yt:sunset_lover',
    source: 'yt',
    title: 'Sunset Lover',
    artist: 'Petit Biscuit',
    album: 'Presence',
    genre: 'Electronic',
    durationSecs: 237,
    bpm: 91,
    camelotKey: 11,
    camelotMode: 'B',
    energy: 0.68,
    valence: 0.74,
    danceability: 0.78,
    acousticness: 0.28,
    temporalVariance: 0.37,
    playCount: 3,
    skipCount: 0,
    liked: false,
    cachedOffline: false,
  },
  {
    id: 'saavn:veridis_quo',
    source: 'saavn',
    title: 'Veridis Quo',
    artist: 'Daft Punk',
    album: 'Discovery',
    genre: 'Synthwave',
    durationSecs: 345,
    bpm: 110,
    camelotKey: 9,
    camelotMode: 'A',
    energy: 0.72,
    valence: 0.62,
    danceability: 0.74,
    acousticness: 0.14,
    temporalVariance: 0.43,
    playCount: 5,
    skipCount: 0,
    liked: true,
    cachedOffline: true,
  },
  {
    id: 'yt:resonance_home',
    source: 'yt',
    title: 'Resonance',
    artist: 'HOME',
    album: 'Odyssey',
    genre: 'Synthwave',
    durationSecs: 212,
    bpm: 85,
    camelotKey: 10,
    camelotMode: 'B',
    energy: 0.76,
    valence: 0.69,
    danceability: 0.71,
    acousticness: 0.18,
    temporalVariance: 0.38,
    playCount: 6,
    skipCount: 0,
    liked: true,
    cachedOffline: true,
  },
];

const recommender = new HybridRecommenderEngine(
  CATALOG_SEED.map((t) => ({
    ...t,
    vector: buildUnifiedTrackVector(t),
  }))
);

function getFeatureCatalog(): TrackFeatures[] {
  return recommender.getAllTracks().map((t) => ({
    id: t.id,
    title: t.title,
    artist: t.artist,
    album: t.album,
    genre: t.genre,
    durationSec: t.durationSecs,
    bpm: t.bpm,
    key: t.camelotKey % 12,
    isMajor: t.camelotMode === 'B',
    energy: t.energy,
    danceability: t.danceability,
    valence: t.valence,
    acousticness: t.acousticness,
    instrumentalness: 1 - t.acousticness * 0.5,
    spectralCentroid: t.energy,
    warmth: t.temporalVariance,
    source: t.source,
  }));
}

const jamEngine = new JamSessionEngine();
let activeJamRoomCode: string | null = null;
let tasteDnaState: TasteDnaConfig = { ...DEFAULT_TASTE_DNA, personaWeights: { ...DEFAULT_TASTE_DNA.personaWeights } };
let recentPlayedIds: string[] = ['1', '2'];

const scoreboard = new EndpointScoreboard([
  { url: 'https://aac.saavncdn.com', provider: 'saavn', initialLatencyMs: 68 },
  { url: 'https://pipedapi.ducks.party', provider: 'piped', initialLatencyMs: 145 },
  { url: 'https://pipedapi.kavin.rocks', provider: 'piped', initialLatencyMs: 210 },
  { url: 'https://inv.nadeko.net', provider: 'invidious', initialLatencyMs: 260 },
]);

interface PersistedWebSettings {
  music_folder: string | null;
  volume: number;
  palette: string;
  art_tint: boolean;
  smart_recs: boolean;
  eq_enabled: boolean;
  eq_preset: string;
  normalization: string;
  crossfade_enabled: boolean;
  crossfade_duration: number;
  library_view_mode: string;
  shuffle_mode: number;
  repeat_mode: number;
  theme: string;
  autoplay: boolean;
  online_cache_mb: number;
  offline_mode: boolean;
  streaming_quality: StreamingQuality;
  download_quality: DownloadQuality;
  audio_sample_rate: AudioSampleRate;
  gapless_playback: boolean;
  predictive_prefetch: boolean;
  prefetch_count: number;
  instant_play_buffer_kb: number;
  autodj_beat_sync: boolean;
  stem_mode: StemMode;
  loudness_norm_enabled: boolean;
  target_lufs: number;
  autoeq_profile: string;
  tube_warmth: boolean;
  lyrics_romanize: boolean;
  lyrics_translate: boolean;
}

let serverSettings: PersistedWebSettings = {
  music_folder: '~/Music/Lossless',
  volume: 0.68,
  palette: 'nebula',
  art_tint: true,
  smart_recs: true,
  eq_enabled: true,
  eq_preset: 'Flat',
  normalization: 'Normal',
  crossfade_enabled: true,
  crossfade_duration: 4.0,
  library_view_mode: 'list',
  shuffle_mode: 0,
  repeat_mode: 0,
  theme: 'light',
  autoplay: true,
  online_cache_mb: 1024,
  offline_mode: false,
  streaming_quality: 'high',
  download_quality: 'high',
  audio_sample_rate: 48000,
  gapless_playback: true,
  predictive_prefetch: true,
  prefetch_count: 3,
  instant_play_buffer_kb: 64,
  autodj_beat_sync: true,
  stem_mode: 'full',
  loudness_norm_enabled: true,
  target_lufs: -14.0,
  autoeq_profile: 'flat_studio',
  tube_warmth: false,
  lyrics_romanize: true,
  lyrics_translate: true,
};

let currentTrackId = '1';
let queueTrackIds: string[] = [];

function toSongMeta(t: TrackItem) {
  return {
    id: t.id,
    title: t.title,
    artist: t.artist,
    album: t.album,
    duration_secs: t.durationSecs,
    file_path: `/api/audio/${encodeURIComponent(t.id)}?stem=${serverSettings.stem_mode}&warmth=${serverSettings.tube_warmth ? 1 : 0}`,
    source: t.source,
    bpm: t.bpm,
    camelot: `${t.camelotKey}${t.camelotMode}`,
    genre: t.genre,
  };
}

// ─── Synthesized Harmonic Audio Stream Endpoint (Zero-Wait Instant Play) ────
// Generates a clean, pleasant harmonic chord progression WAV buffer tailored
// to each song's Camelot key, BPM, Stem Mode (Full / Karaoke / Vocals), and Tube Warmth.
app.get('/api/audio/:trackId', (req, res) => {
  const trackId = req.params.trackId;
  const stemParam = String(req.query.stem || serverSettings.stem_mode) as StemMode;
  const warmthParam = req.query.warmth === '1' || serverSettings.tube_warmth;
  const stemSpec = getStemModeSpec(stemParam);

  const track = recommender.getTrack(trackId) ?? recommender.getAllTracks()[0];
  const sampleRate = 22050;
  const durationSecs = 12;
  const numSamples = sampleRate * durationSecs;
  const dataBytes = numSamples * 2;
  const buffer = Buffer.alloc(44 + dataBytes);

  // WAV RIFF Header
  buffer.write('RIFF', 0);
  buffer.writeUInt32LE(36 + dataBytes, 4);
  buffer.write('WAVE', 8);
  buffer.write('fmt ', 12);
  buffer.writeUInt32LE(16, 16);
  buffer.writeUInt16LE(1, 20);
  buffer.writeUInt16LE(1, 22);
  buffer.writeUInt32LE(sampleRate, 24);
  buffer.writeUInt32LE(sampleRate * 2, 28);
  buffer.writeUInt16LE(2, 32);
  buffer.writeUInt16LE(16, 34);
  buffer.write('data', 36);
  buffer.writeUInt32LE(dataBytes, 40);

  const keySemitone = ((track?.camelotKey ?? 8) - 1) % 12;
  const rootFreq = 220 * Math.pow(2, keySemitone / 12);
  const thirdFreq = rootFreq * (track?.camelotMode === 'B' ? 1.2599 : 1.1892);
  const fifthFreq = rootFreq * 1.4983;
  const vocalFormantFreq = rootFreq * 2.0; // Lead melody harmonic
  const beatHz = (track?.bpm ?? 110) / 60;

  // Apply EBU R128 loudness normalization gain
  const trackLufs = -16.5 + (track?.energy ?? 0.6) * 4.5;
  const norm = serverSettings.loudness_norm_enabled
    ? computeLoudnessNormalization(trackLufs, -1.2, serverSettings.target_lufs, true)
    : { linearMultiplier: 1.0 };

  for (let i = 0; i < numSamples; i++) {
    const t = i / sampleRate;
    const pulse = 0.65 + 0.35 * Math.sin(2 * Math.PI * beatHz * t);
    const accompaniment =
      (0.40 * Math.sin(2 * Math.PI * rootFreq * t) +
        0.25 * Math.sin(2 * Math.PI * thirdFreq * t) +
        0.20 * Math.sin(2 * Math.PI * fifthFreq * t)) *
      stemSpec.instrumentGain;
    const vocalLead =
      0.22 *
      Math.sin(2 * Math.PI * vocalFormantFreq * t + 0.3 * Math.sin(2 * Math.PI * 4.5 * t)) *
      stemSpec.vocalGain;

    let sample = (accompaniment + vocalLead) * pulse * 0.25 * norm.linearMultiplier;
    if (warmthParam) {
      sample = applyTubeWarmthSample(sample, 0.35);
    }
    sample = Math.max(-1, Math.min(1, sample));
    buffer.writeInt16LE(Math.round(sample * 32767), 44 + i * 2);
  }

  res.setHeader('Content-Type', 'audio/wav');
  res.setHeader('Content-Length', buffer.length);
  res.setHeader('Accept-Ranges', 'bytes');
  res.setHeader('Cache-Control', 'no-cache');
  res.send(buffer);
});

// ─── Unified Backend RPC Endpoint (`/api/invoke/:cmd`) ──────────────────────
app.post('/api/invoke/:cmd', (req, res) => {
  const cmd = req.params.cmd;
  const args = req.body ?? {};

  switch (cmd) {
    case 'get_library': {
      const tracks = recommender.getAllTracks().slice(0, 12).map(toSongMeta);
      return res.json(tracks);
    }

    case 'get_settings': {
      return res.json(serverSettings);
    }

    case 'save_settings': {
      if (args.settings && typeof args.settings === 'object') {
        serverSettings = {
          ...serverSettings,
          ...args.settings,
        };
      }
      return res.json(serverSettings);
    }

    case 'get_recommendations': {
      const seedId = args.seedTrackId || currentTrackId;
      const catalog = getFeatureCatalog();
      const seedTrack = catalog.find((t) => t.id === seedId) || catalog[0];
      const dnaRanked = scoreTracksWithPersonaMix(catalog, seedTrack, tasteDnaState, recentPlayedIds);
      return res.json(
        dnaRanked.slice(0, 6).map((r) => {
          const item = recommender.getTrack(r.track.id)!;
          return {
            ...toSongMeta(item),
            explanation: r.reason,
            matched_persona: r.dominantPersona,
            score: r.score,
          };
        })
      );
    }

    case 'home_recommendation': {
      const liked = recommender.getAllTracks().filter((t) => t.liked);
      const anchor = liked[0] ?? recommender.getAllTracks()[0];
      const recs = recommender.recommend({
        seedTrackId: anchor?.id,
        limit: 6,
      });
      return res.json({
        anchor_title: anchor ? `${anchor.title} (${anchor.camelotKey}${anchor.camelotMode})` : 'Midnight City',
        songs: recs.map((r) => ({
          ...toSongMeta(r.track),
          explanation: r.explanation,
          matched_persona: r.matchedPersona,
        })),
      });
    }

    case 'play_song':
    case 'online_play': {
      const songId =
        args.song?.id ||
        (args.track?.video_id ? `yt:${args.track.video_id}` : undefined) ||
        '1';
      currentTrackId = String(songId);
      if (!recentPlayedIds.includes(currentTrackId)) {
        recentPlayedIds = [currentTrackId, ...recentPlayedIds.slice(0, 7)];
      }
      if (args.track && !recommender.getTrack(currentTrackId)) {
        recommender.indexTrack({
          id: currentTrackId,
          source: args.track.source === 'saavn' ? 'saavn' : 'yt',
          title: args.track.title || 'Online Track',
          artist: args.track.artist || 'Unknown Artist',
          album: args.track.album || 'Single',
          genre: 'Electronic',
          durationSecs: args.track.duration_secs || 210,
          bpm: 118,
          camelotKey: 8,
          camelotMode: 'A',
          energy: 0.78,
          valence: 0.64,
          danceability: 0.74,
          acousticness: 0.18,
          temporalVariance: 0.4,
          vector: [],
          playCount: 1,
          skipCount: 0,
          liked: false,
          cachedOffline: true,
        });
      }
      recommender.recordInteraction({
        trackId: currentTrackId,
        completedRatio: 0.85,
      });

      const spec = getQualitySpec(
        serverSettings.streaming_quality,
        serverSettings.audio_sample_rate
      );
      const prefetchPlan = recommender.planPredictivePrefetch({
        currentTrackId,
        upcomingQueueIds: queueTrackIds,
        prefetchEnabled: serverSettings.predictive_prefetch,
        prefetchCount: serverSettings.prefetch_count,
        streamingQuality: serverSettings.streaming_quality,
        instantPlayBufferKb: serverSettings.instant_play_buffer_kb,
      });
      scoreboard.recordOutcome('https://aac.saavncdn.com', true, 42);

      // Compute Auto-DJ beat-aligned crossfade plan to the next track
      const currentTrack = recommender.getTrack(currentTrackId) ?? recommender.getAllTracks()[0];
      const nextId = prefetchPlan[0]?.trackId || '2';
      const nextTrack = recommender.getTrack(nextId) ?? recommender.getAllTracks()[1];
      const autodjPlan = computeBeatAlignedCrossfade(
        currentTrack.bpm,
        nextTrack.bpm,
        currentTrack.durationSecs,
        serverSettings.crossfade_duration,
        serverSettings.autodj_beat_sync
      );

      // Compute EBU R128 loudness normalization telemetry
      const trackLufs = Number((-16.5 + currentTrack.energy * 4.5).toFixed(1));
      const loudnessNorm = computeLoudnessNormalization(
        trackLufs,
        -1.2,
        serverSettings.target_lufs,
        true
      );

      return res.json({
        ok: true,
        streamUrl: `/api/audio/${encodeURIComponent(currentTrackId)}?stem=${serverSettings.stem_mode}&warmth=${serverSettings.tube_warmth ? 1 : 0}`,
        qualitySpec: spec,
        resolveLatencyMs: 38,
        resolvedVia: 'saavn-direct',
        prefetchPlan,
        autodjPlan,
        loudnessNorm,
      });
    }

    case 'set_song_like': {
      const { songId, liked } = args;
      if (songId) {
        recommender.recordInteraction({
          trackId: String(songId),
          completedRatio: liked ? 1.0 : 0.5,
          liked: Boolean(liked),
        });
      }
      return res.json(true);
    }

    case 'get_prefetch_plan': {
      const plan = recommender.planPredictivePrefetch({
        currentTrackId: args.currentTrackId || currentTrackId,
        upcomingQueueIds: args.upcomingQueueIds || queueTrackIds,
        prefetchEnabled: serverSettings.predictive_prefetch,
        prefetchCount: serverSettings.prefetch_count,
        streamingQuality: serverSettings.streaming_quality,
        instantPlayBufferKb: serverSettings.instant_play_buffer_kb,
      });
      const currentTrack = recommender.getTrack(currentTrackId) ?? recommender.getAllTracks()[0];
      const nextTrack = recommender.getTrack(plan[0]?.trackId || '2') ?? recommender.getAllTracks()[1];
      const autodjPlan = computeBeatAlignedCrossfade(
        currentTrack.bpm,
        nextTrack.bpm,
        currentTrack.durationSecs,
        serverSettings.crossfade_duration,
        serverSettings.autodj_beat_sync
      );
      const trackLufs = Number((-16.5 + currentTrack.energy * 4.5).toFixed(1));
      const loudnessNorm = computeLoudnessNormalization(
        trackLufs,
        -1.2,
        serverSettings.target_lufs,
        true
      );

      return res.json({
        enabled: serverSettings.predictive_prefetch,
        count: serverSettings.prefetch_count,
        streamingQuality: serverSettings.streaming_quality,
        downloadQuality: serverSettings.download_quality,
        sampleRate: serverSettings.audio_sample_rate,
        instantBufferKb: serverSettings.instant_play_buffer_kb,
        gaplessPlayback: serverSettings.gapless_playback,
        autodjPlan,
        loudnessNorm,
        stemMode: getStemModeSpec(serverSettings.stem_mode),
        autoeqProfile: getAutoEqProfile(serverSettings.autoeq_profile),
        tubeWarmth: serverSettings.tube_warmth,
        items: plan,
        personas: recommender.getPersonas(),
        weights: recommender.getWeights(),
        endpoints: scoreboard.getRankedEndpoints(),
      });
    }

    // ─── Standout Capability 1: Beat-Aligned Auto-DJ Plan ────────────────────
    case 'get_autodj_plan': {
      const curr = recommender.getTrack(args.currentTrackId || currentTrackId) ?? recommender.getAllTracks()[0];
      const next = recommender.getTrack(args.nextTrackId || '2') ?? recommender.getAllTracks()[1];
      return res.json(
        computeBeatAlignedCrossfade(
          curr.bpm,
          next.bpm,
          curr.durationSecs,
          args.crossfadeDuration ?? serverSettings.crossfade_duration,
          args.enabled ?? serverSettings.autodj_beat_sync
        )
      );
    }

    // ─── Standout Capability 2: Interactive Taste DNA & Persona Mixer ────────
    case 'get_taste_dna': {
      const catalog = getFeatureCatalog();
      const curr = catalog.find((t) => t.id === currentTrackId) || catalog[0];
      const scored = scoreTracksWithPersonaMix(catalog, curr, tasteDnaState, recentPlayedIds);
      return res.json({
        config: tasteDnaState,
        personas: TASTE_PERSONAS,
        recommendations: scored.slice(0, 6).map((s) => ({
          ...toSongMeta(recommender.getTrack(s.track.id)!),
          score: s.score,
          matched_persona: s.dominantPersona,
          explanation: s.reason,
        })),
      });
    }

    case 'apply_taste_dna': {
      if (args.config && typeof args.config === 'object') {
        tasteDnaState = {
          ...tasteDnaState,
          ...args.config,
          personaWeights: {
            ...tasteDnaState.personaWeights,
            ...(args.config.personaWeights || {}),
          },
        };
      }
      const catalog = getFeatureCatalog();
      const curr = catalog.find((t) => t.id === currentTrackId) || catalog[0];
      const scored = scoreTracksWithPersonaMix(catalog, curr, tasteDnaState, recentPlayedIds);
      return res.json({
        config: tasteDnaState,
        recommendations: scored.slice(0, 6).map((s) => ({
          ...toSongMeta(recommender.getTrack(s.track.id)!),
          score: s.score,
          matched_persona: s.dominantPersona,
          explanation: s.reason,
        })),
      });
    }

    // ─── Standout Capability 3: Real-Time Stem Separation & Karaoke Mode ─────
    case 'set_stem_mode': {
      const mode = (args.mode || 'full') as StemMode;
      serverSettings.stem_mode = mode;
      return res.json({
        spec: getStemModeSpec(mode),
        streamUrl: `/api/audio/${encodeURIComponent(currentTrackId)}?stem=${mode}&warmth=${serverSettings.tube_warmth ? 1 : 0}`,
      });
    }

    // ─── Standout Capability 4: EBU R128 Loudness Normalization ──────────────
    case 'set_loudness_norm': {
      if (typeof args.enabled === 'boolean') serverSettings.loudness_norm_enabled = args.enabled;
      if (typeof args.targetLufs === 'number') serverSettings.target_lufs = args.targetLufs;
      const curr = recommender.getTrack(currentTrackId) ?? recommender.getAllTracks()[0];
      const trackLufs = Number((-16.5 + curr.energy * 4.5).toFixed(1));
      return res.json(
        computeLoudnessNormalization(
          trackLufs,
          -1.2,
          serverSettings.target_lufs,
          serverSettings.loudness_norm_enabled
        )
      );
    }

    // ─── Standout Capability 5: Sonic Bridge Pathfinding ─────────────────────
    case 'build_sonic_bridge': {
      const catalog = getFeatureCatalog();
      const startId = String(args.startTrackId || '1');
      const targetId = String(args.targetTrackId || '8');
      const steps = findSonicBridgePath(startId, targetId, catalog, Number(args.steps || 3));
      return res.json({
        startId,
        targetId,
        steps: steps.map((s) => ({
          stepIndex: s.stepIndex,
          transitionScoreFromPrev: s.transitionScoreFromPrev,
          bpmDelta: s.bpmDelta,
          reason: s.reason,
          song: toSongMeta(recommender.getTrack(s.track.id)!),
        })),
      });
    }

    // ─── Standout Capability 6: Headphone AutoEQ & Tube Warmth ───────────────
    case 'get_autoeq_profiles': {
      return res.json({
        profiles: AUTOEQ_PROFILES,
        activeProfile: getAutoEqProfile(serverSettings.autoeq_profile),
        tubeWarmth: serverSettings.tube_warmth,
      });
    }

    case 'apply_autoeq_profile': {
      if (args.profileId) {
        serverSettings.autoeq_profile = String(args.profileId);
        serverSettings.eq_enabled = serverSettings.autoeq_profile !== 'flat_studio';
      }
      if (typeof args.tubeWarmth === 'boolean') {
        serverSettings.tube_warmth = args.tubeWarmth;
      }
      const profile = getAutoEqProfile(serverSettings.autoeq_profile);
      return res.json({
        activeProfile: profile,
        tubeWarmth: serverSettings.tube_warmth,
        eqEnabled: serverSettings.eq_enabled,
      });
    }

    // ─── Standout Capability 7: Live Collaborative Jam Sessions ──────────────
    case 'create_jam_session': {
      const catalog = getFeatureCatalog();
      const session = jamEngine.createSession(
        args.hostName || 'You',
        currentTrackId,
        catalog.slice(0, 6)
      );
      activeJamRoomCode = session.roomCode;
      return res.json(session);
    }

    case 'join_jam_session': {
      const catalog = getFeatureCatalog();
      const session = jamEngine.joinSession(
        args.roomCode || activeJamRoomCode || 'JAM-777',
        args.userName || 'You',
        catalog.slice(0, 6)
      );
      activeJamRoomCode = session.roomCode;
      return res.json(session);
    }

    case 'vote_jam_track': {
      const code = args.roomCode || activeJamRoomCode;
      if (!code) return res.json(null);
      const updated = jamEngine.voteTrack(code, String(args.trackId), Number(args.delta ?? 1));
      return res.json(updated);
    }

    case 'get_jam_session': {
      const code = args.roomCode || activeJamRoomCode;
      if (!code) return res.json(null);
      return res.json(jamEngine.getSession(code));
    }

    // ─── Standout Capability 8: Word-Synced Romanized & Translated Lyrics ────
    case 'get_enhanced_lyrics': {
      const trackId = String(args.trackId || currentTrackId);
      const track = recommender.getTrack(trackId) ?? recommender.getAllTracks()[0];
      const lines = buildEnhancedLyrics({
        id: track.id,
        title: track.title,
        artist: track.artist,
        durationSec: track.durationSecs,
      });
      return res.json({
        trackId: track.id,
        title: track.title,
        artist: track.artist,
        romanizeEnabled: serverSettings.lyrics_romanize,
        translateEnabled: serverSettings.lyrics_translate,
        lines,
      });
    }

    // ─── Standout Capability 9: Time Capsule & Contextual Daypart Memory ─────
    case 'get_daypart_capsule': {
      const catalog = getFeatureCatalog();
      const hour = typeof args.hour === 'number' ? args.hour : new Date().getHours();
      return res.json(buildDaypartAndTimeCapsule(catalog, recentPlayedIds, hour));
    }

    // ─── Standout Capability 10: Command Palette with Sonic Filters ──────────
    case 'execute_sonic_command': {
      const catalog = getFeatureCatalog();
      const result = parseAndExecuteSonicCommand(String(args.query || ''), catalog);
      if (result.actionType === 'apply_setting' && result.settingPatch) {
        serverSettings = {
          ...serverSettings,
          ...(result.settingPatch as Partial<PersistedWebSettings>),
        };
      }
      return res.json({
        ...result,
        matchedTracks: result.matchedTracks.map((mt) => ({
          ...mt,
          song: toSongMeta(recommender.getTrack(mt.id) || recommender.getAllTracks()[0]),
        })),
        updatedSettings: result.actionType === 'apply_setting' ? serverSettings : undefined,
      });
    }

    case 'cross_source_check': {
      const requested = args.requested;
      const all = recommender.getAllTracks();
      const match = all.find((cand) =>
        isCrossSourceMatch(
          {
            title: requested?.title || '',
            artist: requested?.artist || '',
            durationSecs: requested?.durationSecs || 0,
          },
          cand
        )
      );
      return res.json({ matched: Boolean(match), candidate: match ? toSongMeta(match) : null });
    }

    default:
      return res.json(null);
  }
});

// Serve static frontend assets
const frontendDir = path.join(__dirname, 'frontend');
app.use(express.static(frontendDir));

app.use((_req, res) => {
  res.sendFile(path.join(frontendDir, 'index.html'));
});

const PORT = Number(process.env.PORT) || 3000;
app.listen(PORT, '0.0.0.0', () => {
  console.log(`Musico Online web server listening on http://0.0.0.0:${PORT}`);
});
