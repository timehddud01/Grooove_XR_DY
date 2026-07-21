import { createHmac } from 'node:crypto';
import http from 'node:http';
import { pathToFileURL } from 'node:url';

const ACR_ENDPOINT = '/v1/identify';
const ALLOWED_MIME = new Set(['audio/wav', 'audio/x-wav', 'audio/vnd.wave']);
const MAX_FILE_BYTES = 5 * 1024 * 1024;
const MAX_REQUEST_BYTES = MAX_FILE_BYTES + 16 * 1024;
const MAX_DURATION_SECONDS = 12.5;
const DEFAULT_TIMEOUT_MS = 20_000;
const DEFAULT_RATE_LIMIT = 5;
const DEFAULT_RATE_WINDOW_MS = 60_000;

export function createApp({
  acrHost,
  acrAccessKey,
  acrAccessSecret,
  fetchImpl = fetch,
  now = Date.now,
  requestTimeoutMs = DEFAULT_TIMEOUT_MS,
  rateLimit = DEFAULT_RATE_LIMIT,
  rateWindowMs = DEFAULT_RATE_WINDOW_MS,
} = {}) {
  if (!acrHost) throw new Error('ACRCLOUD_HOST is required');
  if (!acrAccessKey) throw new Error('ACRCLOUD_ACCESS_KEY is required');
  if (!acrAccessSecret) throw new Error('ACRCLOUD_ACCESS_SECRET is required');
  const normalizedAcrHost = acrHost.replace(/^https?:\/\//i, '').replace(/\/+$/, '');
  const clients = new Map();

  return http.createServer(async (request, response) => {
    try {
      if (request.method !== 'POST' || request.url !== '/v1/music-recognitions') {
        return sendError(response, 404, 'NOT_FOUND', 'Endpoint not found');
      }
      const userId = request.headers['x-user-id'];
      if (typeof userId !== 'string' || !/^[A-Za-z0-9-]{8,128}$/.test(userId)) {
        return sendError(response, 400, 'INVALID_USER', 'A valid X-User-Id header is required');
      }
      const retryAfter = consumeRateLimit(clients, userId, now(), rateLimit, rateWindowMs);
      if (retryAfter !== null) {
        response.setHeader('Retry-After', String(retryAfter));
        return sendError(response, 429, 'RATE_LIMITED', 'Too many recognition requests');
      }
      const contentType = request.headers['content-type'] ?? '';
      const boundary = /boundary=(?:"([^"]+)"|([^;]+))/i.exec(contentType)?.slice(1).find(Boolean);
      if (!contentType.toLowerCase().startsWith('multipart/form-data') || !boundary) {
        return sendError(response, 415, 'INVALID_MIME', 'multipart/form-data is required');
      }
      const body = await readLimitedBody(request, MAX_REQUEST_BYTES);
      const audio = parseAudioPart(body, boundary);
      validateAudio(audio);

      const timestamp = Math.floor(now() / 1_000).toString();
      const dataType = 'audio';
      const signatureVersion = '1';
      const stringToSign = ['POST', ACR_ENDPOINT, acrAccessKey, dataType, signatureVersion, timestamp].join('\n');
      const signature = createHmac('sha1', acrAccessSecret).update(stringToSign, 'utf8').digest('base64');
      const upstreamForm = new FormData();
      upstreamForm.set('sample', new Blob([audio.bytes], { type: 'application/octet-stream' }), 'capture.wav');
      upstreamForm.set('sample_bytes', String(audio.bytes.length));
      upstreamForm.set('access_key', acrAccessKey);
      upstreamForm.set('data_type', dataType);
      upstreamForm.set('signature_version', signatureVersion);
      upstreamForm.set('signature', signature);
      upstreamForm.set('timestamp', timestamp);
      const upstream = await fetchImpl(`https://${normalizedAcrHost}${ACR_ENDPOINT}`, {
        method: 'POST',
        body: upstreamForm,
        signal: AbortSignal.timeout(requestTimeoutMs),
      });
      const payload = await readJson(upstream);
      if (!upstream.ok) return mapUpstreamHttpError(response, upstream.status, payload);
      const providerStatus = Number(payload?.status?.code ?? -1);
      if (providerStatus === 1001) return sendJson(response, 200, { status: 'NO_MATCH', result: null });
      if (providerStatus !== 0) return mapAcrError(response, providerStatus);
      const match = selectBestMusicMatch(payload?.metadata?.music);
      if (!match) return sendJson(response, 200, { status: 'NO_MATCH', result: null });

      const title = nullableString(match.title);
      const artist = nullableString(match.artists?.[0]?.name);
      if (!title || !artist) {
        return sendError(response, 502, 'INVALID_UPSTREAM_RESPONSE', 'ACRCloud returned an incomplete match');
      }
      return sendJson(response, 200, {
        status: 'MATCH',
        result: {
          title,
          artist,
          album: nullableString(match.album?.name),
          artworkUrl: null,
          provider: 'ACRCLOUD',
        },
      });
    } catch (error) {
      if (error?.code === 'PAYLOAD_TOO_LARGE') {
        return sendError(response, 413, 'FILE_TOO_LARGE', 'Audio exceeds the size limit');
      }
      if (error?.code === 'VALIDATION') {
        return sendError(response, error.status ?? 400, error.type, error.message);
      }
      if (error?.name === 'TimeoutError' || error?.name === 'AbortError') {
        return sendError(response, 504, 'TIMEOUT', 'Music provider timed out');
      }
      return sendError(response, 502, 'UPSTREAM_UNAVAILABLE', 'Music provider is unavailable');
    }
  });
}

function consumeRateLimit(clients, userId, timestamp, limit, windowMs) {
  const current = clients.get(userId);
  if (!current || timestamp >= current.resetAt) {
    clients.set(userId, { count: 1, resetAt: timestamp + windowMs });
    return null;
  }
  if (current.count >= limit) return Math.max(1, Math.ceil((current.resetAt - timestamp) / 1000));
  current.count += 1;
  return null;
}

async function readLimitedBody(request, maximum) {
  const declared = Number(request.headers['content-length'] ?? 0);
  if (declared > maximum) throw Object.assign(new Error(), { code: 'PAYLOAD_TOO_LARGE' });
  const chunks = [];
  let total = 0;
  for await (const chunk of request) {
    total += chunk.length;
    if (total > maximum) throw Object.assign(new Error(), { code: 'PAYLOAD_TOO_LARGE' });
    chunks.push(chunk);
  }
  return Buffer.concat(chunks);
}

function parseAudioPart(body, boundary) {
  const marker = Buffer.from(`--${boundary}`);
  let offset = body.indexOf(marker);
  while (offset >= 0) {
    const headerStart = offset + marker.length + 2;
    const headerEnd = body.indexOf(Buffer.from('\r\n\r\n'), headerStart);
    if (headerEnd < 0) break;
    const headers = body.subarray(headerStart, headerEnd).toString('utf8');
    const nextBoundary = body.indexOf(Buffer.from(`\r\n--${boundary}`), headerEnd + 4);
    if (nextBoundary < 0) break;
    if (/content-disposition:[^\r\n]*name="audio"/i.test(headers)) {
      const mime = /content-type:\s*([^\r\n]+)/i.exec(headers)?.[1]?.trim().toLowerCase();
      return { mime, bytes: body.subarray(headerEnd + 4, nextBoundary) };
    }
    offset = body.indexOf(marker, nextBoundary + 2);
  }
  throw validation(400, 'MISSING_AUDIO', 'The audio multipart field is required');
}

function validateAudio(audio) {
  if (!ALLOWED_MIME.has(audio.mime)) throw validation(415, 'INVALID_MIME', 'Only WAV audio is accepted');
  if (audio.bytes.length === 0) throw validation(400, 'EMPTY_AUDIO', 'Audio must not be empty');
  if (audio.bytes.length > MAX_FILE_BYTES) throw validation(413, 'FILE_TOO_LARGE', 'Audio exceeds the size limit');
  const duration = wavDurationSeconds(audio.bytes);
  if (duration === null) throw validation(400, 'INVALID_WAV', 'PCM 16-bit WAV is required');
  if (duration <= 0 || duration > MAX_DURATION_SECONDS) {
    throw validation(400, 'INVALID_DURATION', `Audio must be at most ${MAX_DURATION_SECONDS} seconds`);
  }
}

function wavDurationSeconds(bytes) {
  if (bytes.length < 44 || bytes.toString('ascii', 0, 4) !== 'RIFF' || bytes.toString('ascii', 8, 12) !== 'WAVE') return null;
  if (bytes.readUInt16LE(20) !== 1 || bytes.readUInt16LE(34) !== 16) return null;
  const byteRate = bytes.readUInt32LE(28);
  const dataSize = bytes.readUInt32LE(40);
  if (!byteRate || 44 + dataSize > bytes.length) return null;
  return dataSize / byteRate;
}

function validation(status, type, message) {
  return Object.assign(new Error(message), { code: 'VALIDATION', status, type });
}

async function readJson(response) {
  try { return await response.json(); } catch { return null; }
}

function mapUpstreamHttpError(response, status, payload) {
  if (status === 401 || status === 403) return sendError(response, 502, 'AUTHENTICATION', 'Provider authentication failed');
  if (status === 429) return sendError(response, 429, 'RATE_LIMITED', 'Provider rate limit reached');
  return sendError(response, 502, 'PROVIDER_ERROR', `Provider returned HTTP ${status}`);
}

function mapAcrError(response, code) {
  if ([3001, 3014].includes(code)) return sendError(response, 502, 'AUTHENTICATION', 'Provider authentication failed');
  if ([3003, 3015].includes(code)) return sendError(response, 429, 'RATE_LIMITED', 'Provider rate limit reached');
  return sendError(response, 502, 'PROVIDER_ERROR', `Provider returned status ${code}`);
}

export function selectBestMusicMatch(music) {
  if (!Array.isArray(music)) return null;
  return music
    .filter((candidate) => nullableString(candidate?.title) && nullableString(candidate?.artists?.[0]?.name))
    .sort((left, right) => {
      const scoreDifference = Number(right.score ?? 0) - Number(left.score ?? 0);
      return scoreDifference || metadataQuality(right) - metadataQuality(left);
    })[0] ?? null;
}

function metadataQuality(candidate) {
  const artist = nullableString(candidate?.artists?.[0]?.name)?.toLowerCase() ?? '';
  const placeholderArtist = ['unknown', 'various artists', 'v.a', 'đang cập nhật'].includes(artist);
  return (candidate?.external_ids?.isrc ? 40 : 0)
    + (candidate?.external_metadata?.spotify?.track?.id ? 15 : 0)
    + (candidate?.external_metadata?.deezer?.track?.id ? 10 : 0)
    + (candidate?.external_metadata?.musicbrainz?.track?.id ? 8 : 0)
    + (candidate?.external_metadata?.youtube?.vid ? 5 : 0)
    + (candidate?.label ? 3 : 0)
    - (placeholderArtist ? 100 : 0);
}

function nullableString(value) {
  const normalized = String(value ?? '').trim();
  return normalized || null;
}

function sendError(response, status, type, message) {
  return sendJson(response, status, { error: { type, message } });
}

function sendJson(response, status, value) {
  if (response.headersSent || response.writableEnded) return;
  const body = JSON.stringify(value);
  response.writeHead(status, { 'Content-Type': 'application/json; charset=utf-8', 'Content-Length': Buffer.byteLength(body) });
  response.end(body);
}

if (import.meta.url === pathToFileURL(process.argv[1]).href) {
  const server = createApp({
    acrHost: process.env.ACRCLOUD_HOST,
    acrAccessKey: process.env.ACRCLOUD_ACCESS_KEY,
    acrAccessSecret: process.env.ACRCLOUD_ACCESS_SECRET,
  });
  const port = Number(process.env.PORT ?? 8787);
  server.listen(port, '0.0.0.0', () => console.log(`Music recognition proxy listening on port ${port}`));
}
