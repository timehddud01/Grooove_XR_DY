# TASK-04 체크리스트 — UI와 앱 통합

## 범위

- [x] production factory와 독립 Music 탭이 구현되어 있다.
- [x] 음악용 gateway는 `NoOpDisplayGateway`로 유지된다.
- [x] 기존 DAT Display 샘플을 음악 출력 완료로 간주하지 않는다.

## 생성·수정 파일과 소유 경계

- [x] `music/MusicRecognitionViewModelFactory.kt`가 있다.
- [x] `music/ui/MusicRecognitionScreen.kt`가 있다.
- [x] `ui/AppScaffold.kt`에 Music route와 tab이 연결되어 있다.
- [x] manifest에 기존 `INTERNET`과 개발용 `usesCleartextTraffic=true`가 있다.
- [x] `RECORD_AUDIO`는 TASK-02, BuildConfig URL과 adapter는 TASK-03 산출물로 구분했다.
- [x] `DisplayAccess/README.md`에 Music 기능, URL과 실행·권한 절차가 있다.

## Production factory

- [x] factory가 `applicationContext`를 보관한다. 근거: `MusicRecognitionViewModelFactory`.
- [x] SharedPreferences 이름이 `music_recognition`, key가 `anonymous_user_id`다.
- [x] 기존 UUID를 재사용하고 없으면 생성 후 `apply()`로 저장한다.
- [x] `PhoneAudioInput()`을 주입한다.
- [x] BuildConfig URL과 user ID로 `AcrCloudProxyMusicRecognizer`를 만들어 주입한다.
- [x] `DisplayGateway`를 전달하지 않아 기본 `NoOpDisplayGateway`를 사용한다.
- [x] model class assignable 여부를 require하고 제한된 unchecked cast를 사용한다.

## Runtime 권한

- [x] screen이 state를 `collectAsStateWithLifecycle`로 수집한다.
- [x] `RequestPermission` launcher가 `RECORD_AUDIO`를 요청한다.
- [x] granted면 recognition을 시작하고 denied면 `onPermissionDenied`를 호출한다.
- [x] 버튼이 현재 permission을 먼저 검사해 허용 상태면 즉시 시작한다.

## 상태별 화면

- [x] busy가 Listening과 Recognizing에만 적용된다.
- [x] busy 중 progress와 취소 버튼을 표시하고 recognition 버튼을 비활성화한다.
- [x] Idle, Listening, Recognizing, Matched, NoMatch와 Error별 content가 있다.
- [x] match가 title과 artist만 최대 2줄·ellipsis로 표시한다.
- [x] album과 artwork를 현재 화면에 렌더링하지 않는다.
- [x] retry가 ViewModel의 새 recognition을 호출해 fresh audio를 사용한다.
- [x] permission, timeout, authentication, rate, network 오류 문구가 구분된다.
- [x] silent, distorted와 기타 invalid audio 문구가 구분된다.
- [x] capture, provider와 unexpected 오류 문구가 구분된다.
- [x] 사용자 문자열을 정상 UTF-8 한국어로 유지한다.
- [x] Compose UI unit/instrumentation test가 없음을 완료 범위에 명시했다.

## AppScaffold와 route lifecycle

- [x] route 값이 `music_recognition`이다.
- [x] root에서 production factory로 Music ViewModel을 생성한다.
- [x] Music navigation이 CONNECT까지 pop하고 singleTop을 사용한다.
- [x] bottom Music tab이 항상 enabled이고 MusicNote icon을 사용한다.
- [x] 현재 Music route에서 selected 상태를 표시한다.
- [x] composable이 Music screen에 ViewModel을 연결한다.
- [x] route dispose 때 `cancelRecognition()`을 호출한다.
- [x] Music 탭이 Display session readiness와 독립이다.
- [x] 기존 Samples와 Settings route를 유지한다.

## Manifest와 README 요구

- [x] manifest의 `INTERNET` 선언을 확인했다.
- [x] 개발 HTTP용 global cleartext가 설정되어 있다.
- [x] production에서 HTTPS 전환 후 cleartext 제거/debug 제한이 필요함을 명시했다.
- [x] README에 `music_recognition_proxy_url=http://10.0.2.2:8787` 예시가 있다.
- [x] README가 emulator 전용 alias와 physical phone의 LAN/HTTPS URL 차이를 설명한다.
- [x] README가 proxy 시작 → Music 탭 → permission → 12초 녹음 흐름을 설명한다.

## 자동 검증

- [x] `Set-Location DisplayAccess; .\gradlew.bat test`가 성공한다.
- [x] Android unit test는 build variant별 11개다: ViewModel 10개 + WAV 1개.
- [x] `.\gradlew.bat lint`가 성공한다.
- [x] `.\gradlew.bat assembleDebug`가 성공한다.
- [x] `Set-Location ..\music-recognition-proxy; npm test`에서 proxy test 5개가 성공한다.
- [x] 자동 테스트가 Screen, factory, navigation과 실제 permission 상호작용을 직접 검증하지 않는다고 명시했다.

## 수동 검증

- [ ] emulator/phone에서 Music 탭이 안경 연결 없이 열리는지 확인했다.
- [ ] 마이크 권한 거부와 허용을 확인했다.
- [ ] Listening/Recognizing progress, 중복 방지와 취소를 확인했다.
- [ ] match title/artist와 no-match를 확인했다.
- [ ] permission, timeout, auth, rate, network, invalid audio와 provider 오류를 확인했다.
- [ ] 인식 중 route 이탈 후 늦은 결과가 표시되지 않는지 확인했다.
- [ ] 실제 ACRCloud account로 12초 녹음 E2E를 확인했다. TASK-05에서 실행한다.

## 예상 결과와 사용자 확인

- [x] Music 탭은 기존 DAT Display session과 독립적으로 동작하도록 연결되어 있다.
- [x] 결과는 휴대폰 화면에만 표시되고 안경 gateway는 NoOp다.
- [x] 사용자는 Android test → lint → assembleDebug → proxy test 순서로 자동 검증한다.
- [x] 수동 UI 항목은 실행 증거가 생길 때만 체크한다.
