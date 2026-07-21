# TASK-05 체크리스트 — 운영 설정과 실기기 E2E

## 자동 검증 기준선

- [x] `Set-Location music-recognition-proxy; npm test`에서 proxy unit test 5개가 통과한다.
- [x] `Set-Location DisplayAccess; .\gradlew.bat testDebugUnitTest`에서 Android unit test 11개가 통과한다.
- [x] Android 11개가 ViewModel 10개와 WavEncoder 1개임을 구분했다.
- [x] `.\gradlew.bat lint`가 성공한다.
- [x] `.\gradlew.bat assembleDebug`가 성공한다.
- [x] 자동 검증이 실제 microphone/network/provider 성공을 증명하지 않는다고 명시했다.

## Proxy runtime 설정

- [ ] Node.js 20 이상 운영 대상에서 실행했다.
- [ ] `ACRCLOUD_HOST`, `ACRCLOUD_ACCESS_KEY`, `ACRCLOUD_ACCESS_SECRET`을 안전한 PowerShell process 환경에 주입했다.
- [ ] 선택 `PORT` 또는 기본 8787을 확인했다.
- [x] server가 dotenv를 로드하지 않아 process 환경변수가 필요함을 문서화했다.
- [ ] `.env.example`의 legacy `AUDD_API_TOKEN`을 실제 값 없는 ACRCloud 세 변수와 PORT 예시로 갱신했다.
- [ ] `npm start` 뒤 startup log와 지속 실행을 확인했다. health endpoint가 없음을 고려했다.
- [ ] 실제 secret이 문서, history와 proxy log에 남지 않았음을 확인했다.

## 앱과 device 설정

- [x] emulator URL이 `http://10.0.2.2:8787`임을 문서화했다.
- [x] 실제 휴대폰에는 접근 가능한 trusted LAN HTTP 또는 HTTPS URL이 필요함을 문서화했다.
- [ ] 대상 device URL로 앱을 다시 build/install했다.
- [ ] 방화벽과 port 접근을 확인했다.
- [x] Music 탭이 DAT registration/안경 연결 없이 동작하고 runtime microphone 권한은 필요함을 구분했다.
- [ ] production에서 HTTPS를 사용하고 global cleartext를 제거하거나 debug로 제한했다.

## 실제 환경

- [ ] 마이크 권한 거부가 capture 없이 안내되는지 확인했다.
- [ ] 마이크 권한 허용 뒤 Listening과 fresh 12초 capture를 확인했다.
- [ ] 정상 곡 여러 개에서 매 tap 새 녹음과 title/artist match를 확인했다.
- [ ] 미등록·불명확 곡의 NoMatch와 재시도를 확인했다.
- [ ] 첫 무음 뒤 1회 재녹음과 silent error를 확인했다.
- [ ] clipping/invalid audio가 provider 호출 전에 차단되는지 확인했다.
- [ ] network disconnect가 typed Network 안내가 되는지 확인했다.
- [ ] provider timeout이 typed Timeout 안내가 되는지 확인했다.
- [ ] 의도적으로 잘못된 credential로 Authentication 안내를 확인했다. 실제 값은 기록하지 않았다.
- [ ] provider 429/quota가 RateLimited 안내가 되는지 확인했다.
- [ ] 같은 user의 local 5회/분 초과와 retry 안내를 확인했다.
- [ ] Listening 중 취소와 recorder 해제를 확인했다.
- [ ] Recognizing 중 취소 뒤 늦은 결과가 Idle을 덮지 않는지 확인했다.
- [ ] 인식 중 route 이탈 뒤 stale 결과가 나타나지 않는지 확인했다.

## Evidence

- [ ] 각 실행에 날짜, device/Android, app build, proxy/Node version을 기록했다.
- [ ] 연결 유형과 secret 없는 provider 설정 상태를 기록했다.
- [ ] expected, actual UI/response type, latency와 통과 여부를 기록했다.
- [ ] 원본 audio, actual secret/key, 전체 user ID와 raw response를 기록하지 않았다.

## 출시 판단

- [ ] proxy source/log/storage에 actual secret과 original audio가 없음을 확인했다.
- [ ] Android source/APK/log에 actual provider secret이 없음을 확인했다.
- [ ] 환경변수 이름·placeholder가 아니라 실제 secret 값을 대상으로 scan했다.
- [ ] production HTTPS를 확인했다.
- [ ] process-local Map 대신 shared rate limiter를 적용했다.
- [ ] monitoring/log에서 audio, secret, user ID와 response body를 redact했다.
- [ ] secret manager, 최소 권한과 rotation 절차를 준비했다.
- [ ] 모든 E2E·보안 증거를 검토하고 release readiness를 승인했다.

## 후속 범위

- [x] AudD 재도입, ShazamKit과 200곡 비교가 구현·검증되지 않은 후속 결정임을 명시했다.
- [x] 음악용 `DisplayGateway`가 NoOp이며 실제 안경 출력이 없음을 명시했다.
- [x] 후속 공급자 비교나 안경 출력을 현재 release 근거로 사용하지 않는다.

## 사용자 실행 순서

- [x] 자동 기준선 → credentials 주입 → proxy 시작 → device URL 설정 순서가 문서화되어 있다.
- [x] permission과 E2E matrix를 순서대로 실행하도록 안내한다.
- [x] evidence 작성 뒤 privacy/HTTPS/shared limiter/rotation을 검토하도록 안내한다.
- [x] 미실행 항목이 있으면 release ready로 판단하지 않는다.
