export type StreamingQuality = 'low' | 'normal' | 'high' | 'lossless';
export type DownloadQuality = 'normal' | 'high' | 'lossless';
export type AudioSampleRate = 44100 | 48000 | 96000;

export interface StreamQualitySpec {
  quality: StreamingQuality;
  targetKbps: number;
  saavnSuffix: string;
  codec: string;
  sampleRate: AudioSampleRate;
}

export interface EndpointMetrics {
  url: string;
  provider: 'saavn' | 'piped' | 'invidious' | 'ytdlp';
  ewmaLatencyMs: number;
  successRate: number;
  consecutiveFailures: number;
  demotedUntilMs: number;
  totalCalls: number;
}

export interface TrackDescriptor {
  id: string;
  source?: 'local' | 'saavn' | 'yt';
  title: string;
  artist: string;
  album?: string;
  durationSecs: number;
  thumbnailUrl?: string;
  filePath?: string;
}

export interface ResolvedStreamResult {
  trackId: string;
  resolvedVia: 'cache' | 'local' | 'saavn-direct' | 'saavn-cross-match' | 'hedged-piped' | 'hedged-invidious' | 'ytdlp-stream';
  streamUrl: string;
  bitrateKbps: number;
  codec: string;
  sampleRate: AudioSampleRate;
  initialBufferReadyKb: number;
  resolveLatencyMs: number;
  coverArtStatus: 'cached' | 'async-pending';
}

export function getQualitySpec(
  quality: StreamingQuality | DownloadQuality = 'high',
  sampleRate: AudioSampleRate = 48000
): StreamQualitySpec {
  switch (quality) {
    case 'low':
      return {
        quality: 'low',
        targetKbps: 96,
        saavnSuffix: '_96.mp4',
        codec: 'AAC-LC 96kbps',
        sampleRate,
      };
    case 'normal':
      return {
        quality: 'normal',
        targetKbps: 160,
        saavnSuffix: '_160.mp4',
        codec: 'AAC 160kbps',
        sampleRate,
      };
    case 'lossless':
      return {
        quality: 'lossless',
        targetKbps: 1411,
        saavnSuffix: '_320.mp4',
        codec: 'FLAC / ALAC Lossless',
        sampleRate: sampleRate === 44100 ? 48000 : sampleRate,
      };
    case 'high':
    default:
      return {
        quality: 'high',
        targetKbps: 320,
        saavnSuffix: '_320.mp4',
        codec: 'AAC 320kbps',
        sampleRate,
      };
  }
}

export function rewriteSaavnUrlForQuality(
  baseUrl: string,
  quality: StreamingQuality | DownloadQuality
): string {
  const spec = getQualitySpec(quality);
  return baseUrl.replace(/_(96|160|320)\.mp4$/i, spec.saavnSuffix);
}

export function normalizeTrackText(text: string): string {
  return text
    .toLowerCase()
    .replace(/\((official|lyric|audio|video|remastered|hd|hq|4k|visualizer)[^)]*\)/gi, '')
    .replace(/\[(official|lyric|audio|video|remastered|hd|hq|4k|visualizer)[^\]]*\]/gi, '')
    .replace(/[^a-z0-9\s]/g, ' ')
    .replace(/\s+/g, ' ')
    .trim();
}

/**
 * Cross-source fast matcher: determines if a JioSaavn candidate matches a YouTube/Requested track
 * using normalized title/artist token overlap and duration tolerance (±4s).
 */
export function isCrossSourceMatch(
  requested: Pick<TrackDescriptor, 'title' | 'artist' | 'durationSecs'>,
  candidate: Pick<TrackDescriptor, 'title' | 'artist' | 'durationSecs'>,
  durationToleranceSecs = 4
): boolean {
  const reqTitle = normalizeTrackText(requested.title);
  const candTitle = normalizeTrackText(candidate.title);
  if (!reqTitle || !candTitle) return false;

  const titleMatch =
    reqTitle === candTitle ||
    reqTitle.includes(candTitle) ||
    candTitle.includes(reqTitle);
  if (!titleMatch) return false;

  const reqArtistTokens = normalizeTrackText(requested.artist).split(' ').filter(Boolean);
  const candArtistNorm = normalizeTrackText(candidate.artist);
  const artistMatch =
    reqArtistTokens.length === 0 ||
    reqArtistTokens.some((tok) => tok.length >= 2 && candArtistNorm.includes(tok));
  if (!artistMatch) return false;

  if (requested.durationSecs > 0 && candidate.durationSecs > 0) {
    const diff = Math.abs(requested.durationSecs - candidate.durationSecs);
    if (diff > durationToleranceSecs) return false;
  }

  return true;
}

/**
 * Phase 1 EWMA Health Scoreboard for Piped / Invidious / Saavn endpoints.
 * Ranks healthy, low-latency instances first and demotes dead instances for 15 minutes.
 */
export class EndpointScoreboard {
  private endpoints = new Map<string, EndpointMetrics>();
  private readonly alpha = 0.3;
  private readonly demotionDurationMs = 15 * 60 * 1000;

  constructor(initialEndpoints: Array<{ url: string; provider: EndpointMetrics['provider']; initialLatencyMs?: number }> = []) {
    for (const ep of initialEndpoints) {
      this.endpoints.set(ep.url, {
        url: ep.url,
        provider: ep.provider,
        ewmaLatencyMs: ep.initialLatencyMs ?? 350,
        successRate: 1.0,
        consecutiveFailures: 0,
        demotedUntilMs: 0,
        totalCalls: 0,
      });
    }
  }

  recordOutcome(url: string, success: boolean, latencyMs: number, nowMs = Date.now()): EndpointMetrics {
    const existing = this.endpoints.get(url) ?? {
      url,
      provider: 'piped' as const,
      ewmaLatencyMs: latencyMs,
      successRate: 1.0,
      consecutiveFailures: 0,
      demotedUntilMs: 0,
      totalCalls: 0,
    };

    existing.totalCalls += 1;
    if (success) {
      existing.ewmaLatencyMs =
        existing.totalCalls === 1
          ? latencyMs
          : (1 - this.alpha) * existing.ewmaLatencyMs + this.alpha * latencyMs;
      existing.successRate = (1 - this.alpha) * existing.successRate + this.alpha * 1.0;
      existing.consecutiveFailures = 0;
      existing.demotedUntilMs = 0;
    } else {
      const penalizedLatency = Math.max(latencyMs, 2500);
      existing.ewmaLatencyMs = (1 - this.alpha) * existing.ewmaLatencyMs + this.alpha * penalizedLatency;
      existing.successRate = (1 - this.alpha) * existing.successRate;
      existing.consecutiveFailures += 1;
      if (existing.consecutiveFailures >= 2) {
        existing.demotedUntilMs = nowMs + this.demotionDurationMs;
      }
    }

    this.endpoints.set(url, existing);
    return { ...existing };
  }

  getRankedEndpoints(provider?: EndpointMetrics['provider'], nowMs = Date.now()): EndpointMetrics[] {
    const all = Array.from(this.endpoints.values()).filter(
      (ep) => !provider || ep.provider === provider
    );

    return all.sort((a, b) => {
      const aDemoted = a.demotedUntilMs > nowMs ? 1 : 0;
      const bDemoted = b.demotedUntilMs > nowMs ? 1 : 0;
      if (aDemoted !== bDemoted) return aDemoted - bDemoted;

      // Composite health score: lower effective latency (ewma / max(successRate, 0.1)) wins
      const aScore = a.ewmaLatencyMs / Math.max(a.successRate, 0.1);
      const bScore = b.ewmaLatencyMs / Math.max(b.successRate, 0.1);
      return aScore - bScore;
    });
  }
}

/**
 * Hedged concurrent request runner: fires primary candidates and staggers backup candidates,
 * returning the first successful response and cancelling/ignoring slower stragglers.
 */
export async function raceHedgedSources<T>(
  tasks: Array<{
    endpointUrl: string;
    delayMs: number;
    execute: () => Promise<T>;
  }>,
  scoreboard?: EndpointScoreboard,
  nowMs = Date.now()
): Promise<{ result: T; winnerUrl: string; elapsedMs: number }> {
  if (tasks.length === 0) {
    throw new Error('No source tasks provided to raceHedgedSources');
  }

  let settled = false;
  return new Promise((resolve, reject) => {
    let failureCount = 0;
    let lastError: unknown = new Error('All hedged sources failed');

    tasks.forEach((task) => {
      const run = () => {
        if (settled) return;
        const start = Date.now();
        task
          .execute()
          .then((result) => {
            const elapsed = Math.max(1, Date.now() - start);
            scoreboard?.recordOutcome(task.endpointUrl, true, elapsed, nowMs);
            if (!settled) {
              settled = true;
              resolve({ result, winnerUrl: task.endpointUrl, elapsedMs: elapsed });
            }
          })
          .catch((err) => {
            const elapsed = Math.max(1, Date.now() - start);
            scoreboard?.recordOutcome(task.endpointUrl, false, elapsed, nowMs);
            lastError = err;
            failureCount += 1;
            if (failureCount === tasks.length && !settled) {
              settled = true;
              reject(lastError);
            }
          });
      };

      if (task.delayMs <= 0) {
        run();
      } else {
        setTimeout(run, task.delayMs);
      }
    });
  });
}

/**
 * Calculates progressive stream readiness threshold so playback starts as soon as
 * the initial chunk (e.g. 64 KB) is buffered instead of waiting for full-file download.
 */
export function isProgressiveStreamPlayable(
  bufferedBytes: number,
  instantPlayBufferKb = 64,
  totalBytes?: number
): boolean {
  const thresholdBytes = Math.max(16, instantPlayBufferKb) * 1024;
  if (totalBytes !== undefined && totalBytes > 0 && bufferedBytes >= totalBytes) {
    return true;
  }
  return bufferedBytes >= thresholdBytes;
}
