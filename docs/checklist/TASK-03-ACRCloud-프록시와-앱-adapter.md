# TASK-03 체크리스트 — ACRCloud 프록시와 앱 adapter

## 범위

- [x] Node 프록시가 공급자 secret과 HMAC 책임을 가진다.
- [x] Android 앱은 자체 프록시만 호출한다.
- [x] UI, factory 주입과 실기기 E2E는 후속 범위다.

## 파일과 runtime 설정

- [x] `music-recognition-proxy/package.json`이 Node 20 이상, ESM, start/test script와 runtime dependency 0을 정의한다.
- [x] `music-recognition-proxy/.gitignore`가 `.env`를 제외한다.
- [ ] `.env.example`을 `ACRCLOUD_HOST`, `ACRCLOUD_ACCESS_KEY`, `ACRCLOUD_ACCESS_SECRET`, `PORT=8787` 예시로 갱신했다. 현재 legacy `AUDD_API_TOKEN`이 남은 설정 결함이며 자동 테스트 통과와 별개다.
- [x] `README.md`가 ACRCloud 환경변수, PowerShell 주입, 실행법과 production 주의사항을 설명한다.
- [x] `src/server.mjs`와 `test/server.test.mjs`가 있다.
- [x] Android에 `AcrCloudProxyMusicRecognizer.kt`가 있다.
- [x] `app/build.gradle.kts`에 local property 기반 proxy URL과 `buildConfig=true`가 있다.
- [x] 기존 manifest의 INTERNET/cleartext를 TASK-03 신규 성과로 포함하지 않았다.

## createApp과 입력 검증

- [x] `createApp`이 fetch, clock, timeout과 rate 설정을 주입받는다. 근거: `server.mjs`.
- [x] 세 credential이 없으면 server 생성이 즉시 실패한다. 근거: `createApp` fail-fast.
- [x] `POST /v1/music-recognitions` 이외 요청은 404다.
- [x] `X-User-Id`가 영문·숫자·하이픈 8~128자로 제한된다.
- [x] multipart boundary와 `audio` field를 검사한다.
- [x] WAV MIME 세 종류만 허용한다.
- [x] file 5MiB와 request `5MiB + 16KiB`를 제한한다.
- [x] RIFF/WAVE, PCM format 1, 16-bit, byte rate와 data size를 검사한다.
- [x] WAV duration이 0초 초과 12.5초 이하로 제한된다.

## Rate limit과 upstream 서명

- [x] user별 기본 5회/60초 제한을 process-local Map으로 관리한다.
- [x] 초과 응답이 429와 `Retry-After`를 포함한다.
- [x] process restart/다중 instance에 공유되지 않는 한계를 문서화했다.
- [x] host를 정규화하고 HTTPS `/v1/identify`를 호출한다.
- [x] canonical string이 POST, path, key, audio, version 1, timestamp 여섯 줄이다.
- [x] access secret key로 HMAC-SHA1 후 Base64 signature를 만든다.
- [x] upstream multipart가 sample, sample_bytes, access_key, data_type, signature_version, signature, timestamp를 포함한다.
- [x] upstream timeout이 20초 `AbortSignal`이다.

## 상태와 후보 mapping

- [x] status 1001을 no-match로 변환한다.
- [x] 3001/3014와 upstream 401/403을 authentication type으로 변환한다.
- [x] 3003/3015와 upstream 429를 rate-limited type으로 변환한다.
- [x] timeout은 504, 다른 provider/processing 오류는 502로 변환한다.
- [x] title과 첫 artist가 있는 후보만 score 내림차순으로 비교한다.
- [x] 동률 quality가 ISRC 40, Spotify 15, Deezer 10, MusicBrainz 8, YouTube 5, label 3을 사용한다.
- [x] placeholder artist를 100점 감점한다.
- [x] 불완전한 선택 결과는 invalid upstream response다.
- [x] match 응답이 title, artist, 선택 album, artwork null과 provider ACRCLOUD로 정규화된다.

## Privacy

- [x] audio, filename, request body, secret과 user ID를 저장하거나 로그에 남기지 않는다.
- [x] user별 state는 memory rate-limit Map에만 있다.
- [x] README가 production HTTPS와 shared limiter 필요성을 명시한다.

## Android adapter

- [x] constructor가 baseUrl, userId, 기본 20초 timeout을 받는다.
- [x] endpoint가 trim된 base URL과 `/v1/music-recognitions`를 결합한다.
- [x] IO dispatcher에서 `WavEncoder`와 UUID boundary를 사용한다.
- [x] `HttpURLConnection` POST에 connect/read timeout과 `doOutput`을 설정한다.
- [x] multipart header와 `X-User-Id`를 설정한다.
- [x] audio part가 `capture.wav`, `audio/wav`, WAV payload를 사용한다.
- [x] success no-match와 result를 공통 outcome으로 변환하고 provider를 ACRCLOUD로 고정한다.
- [x] missing result는 typed invalid-response provider failure다.
- [x] JSON/필수 field parsing 예외는 실제 catch 경로에서 `Unexpected`가 된다.
- [x] authentication, timeout, rate+Retry-After와 provider HTTP/type mapping이 있다.
- [x] socket timeout, IO와 기타 예외가 각각 Timeout, Network, Unexpected가 된다.
- [x] `finally`에서 connection을 disconnect한다.
- [x] 로그가 HTTP, outcome과 WAV byte 수로 제한되고 audio/user/body를 포함하지 않는다.
- [x] BuildConfig URL 기본값이 `http://10.0.2.2:8787`이며 local property로 덮어쓸 수 있다.
- [x] factory 주입과 UI 연결을 TASK-04로 남겼다.

## Proxy test 5개

- [x] `signs ACRCloud requests and selects the canonical candidate`
- [x] `maps ACRCloud no-result status to NO_MATCH`
- [x] `rejects invalid MIME and overlong WAV before calling ACRCloud`
- [x] `applies a per-user rate limit`
- [x] `maps provider authentication, quota and timeout to typed errors`

근거: `music-recognition-proxy/test/server.test.mjs`와 `npm test`.

## 자동 검증

- [x] `Set-Location music-recognition-proxy; npm test`에서 proxy test 5개, failure 0을 확인했다.
- [x] `Set-Location DisplayAccess; .\gradlew.bat assembleDebug`에서 Android adapter와 BuildConfig가 컴파일된다.
- [x] Android adapter 직접 unit test가 없으며 assemble 결과가 실제 HTTP/provider 정확성을 증명하지 않는다고 명시했다.
- [x] secret scan은 실제 secret 값 자체를 `git grep -F`로 찾고 key 이름, placeholder와 README 예시는 실패로 오판하지 않는다.
- [x] source와 APK 설정에 실제 ACRCloud secret을 직접 넣지 않는다.

## 후속 검증

- [ ] 실제 ACRCloud 계정으로 네트워크 E2E를 완료했다.
- [ ] production HTTPS와 shared rate limiter를 적용했다.

## 예상 결과와 사용자 확인

- [x] 앱 WAV가 자체 proxy를 거쳐 normalized match/no-match/error로 반환되는 계약이 정의되어 있다.
- [x] 공급자 secret과 HMAC 처리가 proxy 경계에만 있다.
- [x] 사용자는 proxy directory에서 `npm test`, DisplayAccess에서 `assembleDebug` 순서로 확인한다.
- [x] 실제 secret 값이 process 환경에 있을 때만 그 값을 source에서 검색하며 문서에 값을 붙여 넣지 않는다.
