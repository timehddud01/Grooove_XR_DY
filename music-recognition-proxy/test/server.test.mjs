import assert from 'node:assert/strict';
import { createHmac } from 'node:crypto';
import { afterEach, test } from 'node:test';
import { createApp } from '../src/server.mjs';

const servers = [];
afterEach(() => Promise.all(servers.splice(0).map((server) => new Promise((resolve) => server.close(resolve)))));

test('signs ACRCloud requests and selects the canonical candidate', async () => {
  let upstreamUrl;
  let upstreamBody;
  const fetchImpl = async (url, options) => {
    upstreamUrl = url;
    upstreamBody = options.body;
    return new Response(JSON.stringify(acrSuccess([
      { title: 'Booty Baby', artists: [{ name: 'Đang Cập Nhật' }], score: 100 },
      { title: "Mashup: Baby, Give It Away", artists: [{ name: 'V.A' }], score: 100 },
      {
        title: 'Baby (Feat. Ludacris)', artists: [{ name: 'Justin Bieber' }],
        album: { name: 'My Worlds' }, score: 100, label: 'Universal',
        external_ids: { isrc: 'TEST123' },
        external_metadata: { spotify: { track: { id: 'spotify-id' } } },
      },
    ])), { status: 200 });
  };
  const now = () => 1_700_000_000_000;
  const response = await postAudio(await start({ fetchImpl, now }), wav(4));
  assert.equal(response.status, 200);
  const payload = await response.json();
  assert.deepEqual(payload.result, {
    title: 'Baby (Feat. Ludacris)', artist: 'Justin Bieber', album: 'My Worlds',
    artworkUrl: null, provider: 'ACRCLOUD',
  });
  assert.equal(upstreamUrl, 'https://identify.example.com/v1/identify');
  assert.equal(upstreamBody.get('access_key'), 'server-access-key');
  assert.equal(upstreamBody.get('sample_bytes'), String(wav(4).length));
  assert.equal(upstreamBody.get('data_type'), 'audio');
  assert.equal(upstreamBody.get('signature_version'), '1');
  assert.equal(upstreamBody.get('timestamp'), '1700000000');
  const stringToSign = 'POST\n/v1/identify\nserver-access-key\naudio\n1\n1700000000';
  assert.equal(
    upstreamBody.get('signature'),
    createHmac('sha1', 'server-access-secret').update(stringToSign).digest('base64'),
  );
  assert.equal(JSON.stringify(payload).includes('server-access-secret'), false);
});

test('maps ACRCloud no-result status to NO_MATCH', async () => {
  const fetchImpl = async () => new Response(JSON.stringify({ status: { code: 1001, msg: 'No result' } }), { status: 200 });
  const response = await postAudio(await start({ fetchImpl }), wav(4));
  assert.deepEqual(await response.json(), { status: 'NO_MATCH', result: null });
});

test('rejects invalid MIME and overlong WAV before calling ACRCloud', async () => {
  let calls = 0;
  const baseUrl = await start({ fetchImpl: async () => { calls += 1; return new Response(); } });
  const invalidMime = new FormData();
  invalidMime.set('audio', new Blob([wav(1)], { type: 'audio/mpeg' }), 'capture.mp3');
  const mimeResponse = await fetch(`${baseUrl}/v1/music-recognitions`, { method: 'POST', headers: { 'X-User-Id': 'test-user-one' }, body: invalidMime });
  assert.equal(mimeResponse.status, 415);
  const durationResponse = await postAudio(baseUrl, wav(13), 'test-user-two');
  assert.equal(durationResponse.status, 400);
  assert.equal(calls, 0);
});

test('applies a per-user rate limit', async () => {
  const fetchImpl = async () => new Response(JSON.stringify({ status: { code: 1001 } }), { status: 200 });
  const baseUrl = await start({ fetchImpl, rateLimit: 1 });
  assert.equal((await postAudio(baseUrl, wav(1))).status, 200);
  const limited = await postAudio(baseUrl, wav(1));
  assert.equal(limited.status, 429);
  assert.ok(Number(limited.headers.get('Retry-After')) > 0);
});

test('maps provider authentication, quota and timeout to typed errors', async () => {
  for (const [fetchImpl, expectedStatus, expectedType] of [
    [async () => new Response(JSON.stringify({ status: { code: 3001 } }), { status: 200 }), 502, 'AUTHENTICATION'],
    [async () => new Response(JSON.stringify({ status: { code: 3015 } }), { status: 200 }), 429, 'RATE_LIMITED'],
    [async () => { throw Object.assign(new Error(), { name: 'TimeoutError' }); }, 504, 'TIMEOUT'],
  ]) {
    const response = await postAudio(
      await start({ fetchImpl }),
      wav(1),
      `test-user-${expectedType.toLowerCase().replaceAll('_', '-')}`,
    );
    assert.equal(response.status, expectedStatus);
    assert.equal((await response.json()).error.type, expectedType);
  }
});

async function start(options) {
  const server = createApp({
    acrHost: 'identify.example.com',
    acrAccessKey: 'server-access-key',
    acrAccessSecret: 'server-access-secret',
    ...options,
  });
  servers.push(server);
  await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve));
  return `http://127.0.0.1:${server.address().port}`;
}

function acrSuccess(music) {
  return { status: { code: 0, msg: 'Success' }, metadata: { music } };
}

async function postAudio(baseUrl, bytes, userId = 'test-user-default') {
  const form = new FormData();
  form.set('audio', new Blob([bytes], { type: 'audio/wav' }), 'capture.wav');
  return fetch(`${baseUrl}/v1/music-recognitions`, { method: 'POST', headers: { 'X-User-Id': userId }, body: form });
}

function wav(durationSeconds, sampleRate = 8_000) {
  const pcmBytes = sampleRate * durationSeconds * 2;
  const bytes = Buffer.alloc(44 + pcmBytes);
  bytes.write('RIFF', 0); bytes.writeUInt32LE(36 + pcmBytes, 4); bytes.write('WAVEfmt ', 8);
  bytes.writeUInt32LE(16, 16); bytes.writeUInt16LE(1, 20); bytes.writeUInt16LE(1, 22);
  bytes.writeUInt32LE(sampleRate, 24); bytes.writeUInt32LE(sampleRate * 2, 28);
  bytes.writeUInt16LE(2, 32); bytes.writeUInt16LE(16, 34); bytes.write('data', 36); bytes.writeUInt32LE(pcmBytes, 40);
  return bytes;
}
