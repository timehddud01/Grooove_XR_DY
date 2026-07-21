# TASK-05 — 운영 설정과 실기기 E2E

## 목적

TASK-01부터 TASK-04까지 자동 검증한 앱과 proxy를 실제 환경에 설정하고, ACRCloud credentials와 휴대폰 URL을 주입해 실기기 E2E를 수행한다. 운영 보안과 후속 범위를 증거에 따라 결정한다.

## 선행 조건

- Android unit test, lint, debug build와 proxy test가 통과한다.
- Node.js 20 이상, ACRCloud project와 Android 휴대폰이 준비되어 있다.

## 핵심 원칙

자동 테스트 통과는 실제 마이크, 네트워크와 provider 성공을 의미하지 않는다. 실기기 결과와 운영 보안은 별도 증거가 있어야 완료로 표시한다.

## 1. 자동 검증 기준선 확인

PowerShell에서 다음을 실행한다.

```powershell
Set-Location DisplayAccess
.\gradlew.bat testDebugUnitTest
.\gradlew.bat lint
.\gradlew.bat assembleDebug
Set-Location ..\music-recognition-proxy
npm test
```

기준선은 Android unit test 11개(ViewModel 10개 + WAV 1개), proxy test 5개, lint와 assembleDebug 성공이다. 이 결과는 실제 휴대폰 E2E와 구분해 기록한다.

## 2. Proxy credentials 설정

proxy는 dotenv를 로드하지 않는다. Node.js 20 이상 PowerShell process에 `ACRCLOUD_HOST`, `ACRCLOUD_ACCESS_KEY`, `ACRCLOUD_ACCESS_SECRET`을 직접 설정하고 필요할 때만 `PORT`를 설정한다. 기본 port는 8787이다.

```powershell
# 실제 값은 승인된 secret manager 또는 안전한 process 주입 절차에서 가져온다.
$env:ACRCLOUD_HOST = '<host-from-secure-source>'
$env:ACRCLOUD_ACCESS_KEY = '<key-from-secure-source>'
$env:ACRCLOUD_ACCESS_SECRET = '<secret-from-secure-source>'
$env:PORT = '8787'
```

위 placeholder를 실제 값으로 바꾼 명령을 문서, shell history, issue나 채팅에 저장하지 않는다. secret은 APK, Android local property와 앱/proxy 로그에도 넣지 않는다.

현재 `music-recognition-proxy/.env.example`에는 legacy `AUDD_API_TOKEN`이 남아 있다. 이 설정 결함은 자동 테스트 통과와 별개이며 실제 값 없이 ACRCloud 세 변수와 `PORT=8787` 예시로 갱신해야 한다. 완료 증거가 없으므로 미완료로 유지한다.

## 3. Proxy 시작

```powershell
Set-Location music-recognition-proxy
npm start
```

health endpoint는 없다. process가 종료되지 않고 `Music recognition proxy listening on port 8787` startup log를 출력하는지 확인한다. 실제 audio, user ID, request body와 secret이 로그에 나타나면 즉시 테스트를 중단하고 로그를 폐기·정정한다.

## 4. 앱 URL과 device 설정

emulator는 `DisplayAccess/local.properties`에 다음을 사용한다.

```properties
music_recognition_proxy_url=http://10.0.2.2:8787
```

`10.0.2.2`는 emulator 전용 host alias다. 실제 휴대폰은 같은 trusted development network에서 접근 가능한 PC의 LAN HTTP URL 또는 배포 HTTPS URL을 사용한다. 방화벽과 port 접근을 확인하고 URL 변경 뒤 앱을 다시 build/install한다.

manifest의 global `usesCleartextTraffic=true`는 local development 편의 설정이다. production에서는 HTTPS로 전환하고 cleartext를 제거하거나 debug build에만 제한한다.

Music 탭은 DAT registration과 안경 연결이 필요하지 않다. 휴대폰 runtime `RECORD_AUDIO` 권한은 필요하며 인식 버튼을 누를 때 요청한다.

## 5. E2E test matrix

각 행은 실제 device에서 실행하고 expected와 actual을 따로 기록한다.

| 시나리오 | 기대 결과 | 상태 |
|---|---|---|
| 마이크 권한 거부 | capture 없이 PermissionDenied 안내 | 미실행 |
| 마이크 권한 허용 | Listening에서 12초 새 녹음 시작 | 미실행 |
| 정상 곡 여러 개 | 매 tap fresh 12초, title/artist match | 미실행 |
| 미등록/불명확 곡 | NoMatch와 재시도 | 미실행 |
| 무음 | 한 번 재녹음 후 silent error | 미실행 |
| clipping/invalid audio | provider 전 invalid-audio error | 미실행 |
| network disconnect | Network 안내, 앱 복구 가능 | 미실행 |
| provider timeout | Timeout 안내 | 미실행 |
| 잘못된 provider credential | Authentication 안내 | 미실행 |
| provider quota/429 | RateLimited 안내 | 미실행 |
| local 동일 user 5회/분 초과 | 429와 retry 안내 | 미실행 |
| Listening 중 취소 | Idle 복귀, recorder 해제 | 미실행 |
| Recognizing 중 취소 | Idle 유지, 늦은 결과 무시 | 미실행 |
| 인식 중 route 이탈 | dispose 취소, stale 결과 없음 | 미실행 |

정상 곡은 여러 곡으로 반복하고 각 요청이 새 녹음을 사용하는지 확인한다. match 화면은 title과 artist를 확인하며 album/artwork 표시는 현재 요구가 아니다.

## 6. Evidence 기록

각 실행에 다음 template을 복사해 민감정보 없이 작성한다.

```text
실행 날짜/시간:
휴대폰 model / Android version:
앱 build/version:
proxy commit/version / Node version:
연결 유형: emulator | trusted LAN | HTTPS
provider 설정 상태: configured | intentionally invalid (값 기록 금지)
시나리오:
기대 결과:
실제 UI state / response type:
총 latency:
통과 여부:
비고:
```

원본 audio 내용, 실제 secret, access key, 전체 user ID와 raw response body를 증거에 기록하지 않는다.

## 7. Privacy와 운영 보안 점검

- proxy source, log와 storage에 실제 secret이나 원본 audio가 없는지 확인한다.
- Android source, generated APK와 app log에 실제 ACRCloud secret이 없는지 확인한다.
- 환경변수 이름과 README placeholder 존재는 노출이 아니므로 실제 secret 값 자체를 검사한다.
- production traffic이 HTTPS인지 확인한다.
- process-local rate-limit Map을 Redis 같은 shared limiter로 교체한다.
- structured monitoring에서 audio, secret, user ID와 response body를 redact한다.
- secret manager, 최소 권한과 rotation 절차를 마련한다.

실제 secret 값 검색 예시는 다음과 같다. 값이 없는 환경에서는 검사를 통과했다고 기록하지 않는다.

```powershell
$secretValue = $env:ACRCLOUD_ACCESS_SECRET
if ($secretValue) {
  git grep -n -F -- "$secretValue" -- ':!music-recognition-proxy/.env'
}
```

## 8. 후속 결정

- AudD 재도입, ShazamKit 구현과 200곡 공급자 비교는 구현·실험 증거가 없으므로 별도 후속 결정이다.
- 음악 결과의 실제 안경 Display 출력은 구현되지 않았고 `NoOpDisplayGateway`를 사용한다.
- production shared limiter, HTTPS, monitoring/redaction과 secret rotation이 준비되지 않으면 운영 출시를 승인하지 않는다.

## 완료 조건

- 실제 credentials가 안전한 process 환경에 주입되고 proxy startup을 확인했다.
- 대상 device에서 접근 가능한 proxy URL로 앱을 build했다.
- permission, 정상 다곡, no-match, invalid audio, network/provider failure, rate limit, 취소와 route 이탈 matrix를 실행했다.
- 각 결과를 secret/audio 없이 evidence template으로 기록했다.
- source/log/storage/APK privacy와 production HTTPS를 확인했다.
- shared limiter, monitoring redaction과 secret rotation을 운영 수준으로 준비했다.
- 미실행 항목이 하나라도 있으면 release ready로 판단하지 않는다.

## 예상 결과

실제 휴대폰에서 Music 탭을 누를 때마다 새 12초 audio가 proxy로 전달되고 match, no-match 또는 typed error가 화면에 표시된다. 취소와 route 이탈 뒤 stale 결과가 나타나지 않으며 원본 audio와 실제 secret은 앱·proxy의 source, log와 storage에 남지 않는다.

## 사용자 실행 순서

1. Android 11 tests/lint/build와 proxy 5 tests 기준선을 다시 확인한다.
2. 승인된 방식으로 PowerShell process에 ACRCloud 환경변수를 주입한다.
3. `npm start`로 proxy를 실행하고 startup log만 확인한다.
4. emulator 또는 실제 휴대폰에 맞는 `music_recognition_proxy_url`을 설정한다.
5. 앱을 build/install하고 Music 탭에서 마이크 권한 거부·허용을 확인한다.
6. E2E matrix를 한 행씩 실행하고 evidence template을 작성한다.
7. actual secret/audio가 source, APK, log와 storage에 없는지 검사한다.
8. production HTTPS, shared limiter, monitoring redaction과 secret rotation을 검토한다.
9. 모든 필수 항목에 증거가 있을 때만 release readiness를 승인한다.

## 체크리스트

[`TASK-05 체크리스트`](../checklist/TASK-05-운영-설정과-실기기-E2E.md)
