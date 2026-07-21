# TASK-03 — ACRCloud 프록시와 앱 adapter

## 목적

Android가 TASK-02의 WAV를 자체 Node 프록시로 전송하고, 프록시가 ACRCloud secret과 HMAC 서명을 소유해 `/v1/identify`를 호출하도록 구현한다.

## 선행 조건

- TASK-01 도메인 계약과 TASK-02 WAV encoder가 완료되어 있다.
- Node.js 20 이상과 Android 빌드 환경이 준비되어 있다.

## 구현 범위

- zero-dependency Node 프록시
- 입력 검증, rate limit, ACRCloud 서명·응답 정규화
- Android `AcrCloudProxyMusicRecognizer`
- proxy unit test와 Android compile 검증

## 후속 범위

- ViewModel factory 주입, Music UI/navigation, runtime 권한
- 실제 ACRCloud 네트워크·실기기 E2E와 운영 배포

## 생성 파일

- `music-recognition-proxy/package.json`
- `music-recognition-proxy/.gitignore`
- `music-recognition-proxy/.env.example`
- `music-recognition-proxy/README.md`
- `music-recognition-proxy/src/server.mjs`
- `music-recognition-proxy/test/server.test.mjs`
- `DisplayAccess/app/src/main/java/com/meta/wearable/dat/externalsampleapps/displayaccess/music/network/AcrCloudProxyMusicRecognizer.kt`

## 수정 파일

- `DisplayAccess/app/build.gradle.kts`
  - local property 기반 `MUSIC_RECOGNITION_PROXY_URL` BuildConfig field
  - `buildFeatures { buildConfig = true }`

기존 manifest의 `INTERNET`와 cleartext, UI 연결은 TASK-03의 신규 성과로 간주하지 않는다.

## 1. Node runtime과 설정

`package.json`은 private ESM package로 작성한다. Node.js 20 이상을 요구하고 runtime dependency는 추가하지 않는다.

```json
{
  "scripts": { "start": "node src/server.mjs", "test": "node --test" },
  "engines": { "node": ">=20" }
}
```

필수 환경변수는 `ACRCLOUD_HOST`, `ACRCLOUD_ACCESS_KEY`, `ACRCLOUD_ACCESS_SECRET`이며 `PORT` 기본값은 8787이다. 서버는 dotenv를 로드하지 않으므로 PowerShell process 환경변수로 직접 주입한다. 문서나 source에는 실제 값을 작성하지 않는다.

현재 `.env.example`에는 legacy `AUDD_API_TOKEN`이 남아 있어 runtime 계약과 불일치한다. 이 설정 결함은 코드 자동 테스트와 별개이며, ACRCloud 세 변수와 `PORT=8787` 예시로 수정하기 전까지 미완료다. `.gitignore`의 `.env` 제외 규칙은 유지한다. README에는 ACRCloud 환경변수와 실행법, production의 HTTPS 및 shared limiter 필요성을 설명한다.

## 2. 서버 생성과 입력 경계

`createApp`은 `acrHost`, `acrAccessKey`, `acrAccessSecret`과 테스트용 `fetchImpl`, `now`, `requestTimeoutMs`, `rateLimit`, `rateWindowMs`를 주입받는다. 세 credential이 없으면 server 생성 시 즉시 실패한다.

정확히 `POST /v1/music-recognitions`만 처리하고 그 외 method/path는 404다.

- `X-User-Id`는 영문 대소문자, 숫자, 하이픈으로 된 8~128자다.
- `multipart/form-data` boundary와 이름이 `audio`인 part가 필요하다.
- MIME은 `audio/wav`, `audio/x-wav`, `audio/vnd.wave`만 허용한다.
- audio file은 최대 5MiB, 전체 request는 `5MiB + 16KiB`다.
- Content-Length와 streaming 누적 크기를 모두 제한한다.
- WAV는 최소 44 bytes, `RIFF`/`WAVE`, PCM format 1, 16-bit, byte rate와 fixed header data size를 검사한다.
- duration은 0초 초과 12.5초 이하다.

request body와 audio는 요청 처리 중 memory에서만 사용하며 영속화하지 않는다.

## 3. Process-local rate limit

`X-User-Id`별 count와 reset time을 process 내 `Map`에 저장한다. 기본값은 60초에 5회다. 초과 시 HTTP 429, `RATE_LIMITED` error와 최소 1초인 `Retry-After`를 반환한다.

이 제한은 server restart와 여러 instance 사이에 공유되지 않는다. production에서는 외부 shared limiter로 교체해야 한다.

## 4. ACRCloud HMAC 요청

host에서 scheme과 trailing slash를 제거하고 `https://{host}/v1/identify`만 호출한다. Unix timestamp 초 단위를 사용하며 canonical string은 실제 newline으로 다음 여섯 줄을 연결한다.

```text
POST
/v1/identify
{access_key}
audio
1
{timestamp}
```

access secret을 key로 canonical UTF-8 string을 HMAC-SHA1 처리하고 Base64로 인코딩한다. upstream multipart에는 `sample`, `sample_bytes`, `access_key`, `data_type=audio`, `signature_version=1`, `signature`, `timestamp`를 넣는다. `AbortSignal.timeout` 기본값은 20초다.

## 5. Provider 상태와 HTTP mapping

- provider status 1001 → HTTP 200 `NO_MATCH`
- status 0 → 후보 선택
- status 3001/3014 → HTTP 502 `AUTHENTICATION`
- status 3003/3015 → HTTP 429 `RATE_LIMITED`
- upstream HTTP 401/403 → HTTP 502 `AUTHENTICATION`
- upstream HTTP 429 → HTTP 429 `RATE_LIMITED`
- 기타 upstream HTTP → HTTP 502 `PROVIDER_ERROR`
- abort/timeout → HTTP 504 `TIMEOUT`
- 기타 처리 오류 → HTTP 502 `UPSTREAM_UNAVAILABLE`

오류 body는 `{"error":{"type":"...","message":"..."}}` 형식으로 통일한다.

## 6. 후보 선택과 성공 응답

title과 첫 artist name이 모두 있는 후보만 남기고 provider score 내림차순으로 정렬한다. score가 같으면 다음 metadata quality 합계를 비교한다.

| metadata | 가점 |
|---|---:|
| ISRC | 40 |
| Spotify track ID | 15 |
| Deezer track ID | 10 |
| MusicBrainz track ID | 8 |
| YouTube video ID | 5 |
| label | 3 |

placeholder artist는 100점 감점한다. 선택 결과의 title/artist가 없으면 HTTP 502 `INVALID_UPSTREAM_RESPONSE`다. 유효 후보가 없으면 `NO_MATCH`다.

```json
{"status":"MATCH","result":{"title":"...","artist":"...","album":null,"artworkUrl":null,"provider":"ACRCLOUD"}}
```

album은 선택 값이고 artwork는 현재 `null`이다.

## 7. Privacy와 운영 경계

- original audio, filename, request body, secret과 `X-User-Id`를 저장하거나 로그에 남기지 않는다.
- rate-limit Map 외의 사용자별 저장소를 만들지 않는다.
- README에 production HTTPS, shared limiter와 process-local 제한을 명시한다.

## 8. Android proxy adapter

`AcrCloudProxyMusicRecognizer(baseUrl, userId, timeoutMillis=20_000)`가 `MusicRecognizer`를 구현한다. endpoint는 `baseUrl.trimEnd('/') + "/v1/music-recognitions"`다.

- `withContext(Dispatchers.IO)`에서 TASK-02 `WavEncoder`로 WAV를 만든다.
- `Groove-{UUID}` multipart boundary를 매 요청 생성한다.
- `HttpURLConnection`에 POST, connect/read timeout 20초, `doOutput=true`를 설정한다.
- header는 multipart `Content-Type`과 `X-User-Id`다.
- part는 name `audio`, filename `capture.wav`, MIME `audio/wav`, payload WAV다.
- 모든 경로의 `finally`에서 connection을 disconnect한다.

성공 body의 `status=NO_MATCH`는 `RecognitionOutcome.NoMatch`다. `result`가 있으면 title, artist, 선택 album/artwork를 `RecognitionResult`로 만들며 provider는 응답 값을 신뢰하지 않고 `ACRCLOUD`로 설정한다.

result가 없는 성공 JSON은 typed `Provider(INVALID_RESPONSE)` failure다. JSON parsing이나 필수 string 접근에서 발생한 예외는 바깥 `Throwable` catch에 의해 `Unexpected`로 변환된다.

- HTTP 401/403 또는 type `AUTHENTICATION` → `Authentication`
- HTTP 408/504 또는 type `TIMEOUT` → `Timeout`
- HTTP 429 또는 type `RATE_LIMITED` → `RateLimited`; `Retry-After` 초 보존
- 그 외 non-2xx → `Provider(type 또는 status, message)`
- `SocketTimeoutException` → `Timeout`
- `IOException` → `Network(message)`
- 기타 `Throwable` → `Unexpected(message)`

로그는 HTTP status, normalized outcome과 WAV byte 수만 포함한다. audio, user ID, response body와 secret은 기록하지 않는다.

## 9. BuildConfig URL

`DisplayAccess/app/build.gradle.kts`에서 `local.properties`의 `music_recognition_proxy_url`을 읽어 `BuildConfig.MUSIC_RECOGNITION_PROXY_URL` String field로 만든다. 기본값은 emulator가 host PC에 접근하는 `http://10.0.2.2:8787`이다. `buildFeatures { buildConfig = true }`를 활성화한다.

이 TASK에서는 factory에 adapter를 주입하지 않는다. 개발용 cleartext와 production HTTPS 전환, 실제 URL 연결은 후속 단계에서 전체 앱과 함께 검증한다.

## 10. Proxy unit test 5개

`test/server.test.mjs`에 다음 이름과 의도의 Node test를 작성한다.

1. `signs ACRCloud requests and selects the canonical candidate`: timestamp/signature/upstream fields를 확인하고 score 동률에서 metadata가 풍부한 정식 후보를 선택한다.
2. `maps ACRCloud no-result status to NO_MATCH`: status 1001을 normalized no-match로 변환한다.
3. `rejects invalid MIME and overlong WAV before calling ACRCloud`: 잘못된 MIME과 13초 WAV를 upstream 호출 전에 거부한다.
4. `applies a per-user rate limit`: 같은 user limit 초과 시 429와 `Retry-After`를 반환한다.
5. `maps provider authentication, quota and timeout to typed errors`: provider 인증, quota와 timeout을 지정 HTTP/type으로 변환한다.

Android adapter의 직접 unit test는 없다. `assembleDebug`는 adapter와 BuildConfig의 compile만 확인하며 실제 HTTP body, 응답 mapping 또는 ACRCloud 정확성을 증명하지 않는다.

## 완료 조건

- Node 20 이상, runtime dependency 0과 start/test scripts가 정의되어 있다.
- 입력 검증, request 제한, process-local rate limit, HMAC 요청과 status/candidate mapping이 구현되어 있다.
- secret과 원본 요청을 source/log/storage에 남기지 않는다.
- proxy unit test 5개가 0 failure로 통과한다.
- Android adapter와 BuildConfig URL이 `assembleDebug`에서 컴파일된다.
- stale `.env.example` 갱신과 실제 provider E2E는 완료로 표시하지 않는다.

## 예상 결과

Android adapter가 WAV와 설치별 user ID를 자체 proxy로 보내면 proxy가 검증·제한·서명을 수행하고 provider 응답을 `MATCH`, `NO_MATCH` 또는 typed error JSON으로 정규화한다. Android는 이를 공통 domain outcome으로 변환하며 어느 앱 source에도 실제 provider secret이 포함되지 않는다.

## 사용자 검증 절차

1. Node proxy test를 실행해 `5 tests`, `0 failures`를 확인한다.

```powershell
Set-Location music-recognition-proxy
npm test
```

2. Android adapter compile을 확인한다.

```powershell
Set-Location ..\DisplayAccess
.\gradlew.bat assembleDebug
```

3. 실제 secret 노출 여부는 값 자체를 대상으로 확인한다. 환경변수 이름, placeholder와 README 예시는 허용되므로 단순히 `ACRCLOUD_` 문자열 존재를 실패로 판단하지 않는다.

```powershell
$secretValue = $env:ACRCLOUD_ACCESS_SECRET
if ($secretValue) {
  git grep -n -F -- "$secretValue" -- ':!music-recognition-proxy/.env'
}
```

출력이 없어야 한다. 실제 secret을 명령행 literal이나 문서에 붙여 넣지 않는다.

4. `.env.example`의 legacy 항목은 별도 설정 결함으로 남기고 TASK-05 전에 ACRCloud 변수 예시로 갱신한다.
5. 실제 네트워크와 provider account E2E는 TASK-05에서 실행한다.

## 체크리스트

[`TASK-03 체크리스트`](../checklist/TASK-03-ACRCloud-프록시와-앱-adapter.md)
