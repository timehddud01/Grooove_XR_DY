# TASK-04 — UI와 앱 통합

## 목적

TASK-01 ViewModel, TASK-02 휴대폰 오디오와 TASK-03 proxy adapter를 production factory로 조립하고, runtime 마이크 권한과 route 수명주기를 갖는 독립 Music 탭을 연결한다.

## 선행 조건

- TASK-01부터 TASK-03까지 완료되어 있다.
- Android 앱과 Node proxy를 각각 빌드·테스트할 수 있다.

## 구현 범위

- production ViewModel factory
- Music Compose 화면과 runtime 권한
- Music route, bottom tab과 route 이탈 취소
- 개발 proxy URL 사용법과 README

## 후속 범위

- 실제 ACRCloud 실기기 E2E와 production HTTPS
- 음악 결과의 실제 안경 Display 출력

## 생성 파일

- `DisplayAccess/app/src/main/java/com/meta/wearable/dat/externalsampleapps/displayaccess/music/MusicRecognitionViewModelFactory.kt`
- `DisplayAccess/app/src/main/java/com/meta/wearable/dat/externalsampleapps/displayaccess/music/ui/MusicRecognitionScreen.kt`

## 수정 파일

- `DisplayAccess/app/src/main/java/com/meta/wearable/dat/externalsampleapps/displayaccess/ui/AppScaffold.kt`
- `DisplayAccess/app/src/main/AndroidManifest.xml`
  - 기존 `INTERNET` 선언을 확인한다.
  - 개발 HTTP proxy용 `android:usesCleartextTraffic="true"`를 설정한다.
- `DisplayAccess/README.md`
  - Music 기능, proxy URL과 실행·권한 절차를 추가한다.

`BuildConfig.MUSIC_RECOGNITION_PROXY_URL`과 adapter는 TASK-03 소유다. `PhoneAudioInput`, WAV, manifest `RECORD_AUDIO`는 TASK-02 소유다. TASK-04에서는 이 산출물을 조립하고 UI에 연결하며 기존 항목을 신규 성과로 과장하지 않는다.

## 1. Production factory

`MusicRecognitionViewModelFactory(context)`는 즉시 `context.applicationContext`를 보관한다. `create`는 요청 model class가 `MusicRecognitionViewModel`에 assignable인지 `require`로 확인하고 반환 시 unchecked cast를 제한적으로 사용한다.

`applicationContext.getSharedPreferences("music_recognition", MODE_PRIVATE)`에서 `anonymous_user_id`를 읽는다. 기존 값이 있으면 재사용하고 없으면 `UUID.randomUUID().toString()`을 생성해 같은 key에 `apply()`로 저장한다.

다음 production 구현을 주입한다.

```kotlin
MusicRecognitionViewModel(
    audioInput = PhoneAudioInput(),
    musicRecognizer = AcrCloudProxyMusicRecognizer(
        BuildConfig.MUSIC_RECOGNITION_PROXY_URL,
        userId,
    ),
)
```

`DisplayGateway`를 전달하지 않아 ViewModel 기본값인 `NoOpDisplayGateway`를 사용한다. 따라서 Music 탭 결과는 휴대폰 UI에만 표시되고 안경에는 전송되지 않는다.

## 2. MusicRecognitionScreen 권한 흐름

`MusicRecognitionScreen`은 `viewModel.uiState`를 `collectAsStateWithLifecycle()`로 수집한다. `rememberLauncherForActivityResult(RequestPermission())`로 `RECORD_AUDIO`를 요청한다.

- launcher 결과가 granted면 `startRecognition()`을 호출한다.
- denied면 `onPermissionDenied()`를 호출한다.
- 인식 버튼은 `ContextCompat.checkSelfPermission`을 먼저 확인한다.
- 이미 허용되었으면 즉시 시작하고, 아니면 launcher를 실행한다.

## 3. 상태별 화면

busy는 `Listening` 또는 `Recognizing`일 때만 true다. busy 중에는 progress와 취소 버튼을 표시하고 인식 버튼을 비활성화한다.

- `Idle`: 주변 음악을 들려 달라는 안내
- `Listening`: 녹음 중 progress
- `Recognizing`: 검색 중 progress
- `Matched`: title과 artist
- `NoMatch`: 다시 시도 안내
- `Error`: typed error 안내

match 화면은 title과 artist만 렌더링하며 각각 최대 2줄과 ellipsis를 적용한다. album과 artwork는 domain 값에 있어도 이 화면에서 렌더링하지 않는다. matched 상태의 버튼과 다른 terminal 상태의 인식 버튼은 모두 ViewModel의 새 recognition을 시작하므로 이전 audio/result cache를 사용하지 않는다.

사용자 문자열은 정상 UTF-8 한국어로 유지한다. `errorMessage`는 다음 분기를 제공한다.

- `PermissionDenied`: 마이크 권한 허용 안내
- `Timeout`: 요청 시간 초과와 재시도
- `Authentication`: 음악 인식 서비스 인증 실패
- `RateLimited`: 잠시 후 재시도
- `Network`: 연결 확인과 재시도
- `InvalidAudio(SILENT)`: 소리가 들리지 않음
- `InvalidAudio(DISTORTED)`: 너무 크거나 찌그러진 소리
- 그 외 invalid audio: 사용할 수 없는 녹음
- `AudioCapture`: 마이크 녹음 실패
- `Provider`: 음악 인식 서비스 오류
- `Unexpected`: 예상하지 못한 오류

현재 Compose UI unit test나 instrumentation test는 없다. Android unit test 성공을 화면 상호작용 검증으로 해석하지 않는다.

## 4. AppScaffold route와 tab

- `Routes.MUSIC_RECOGNITION = "music_recognition"`을 추가한다.
- root `AppScaffold`에서 application context를 사용하는 factory로 `MusicRecognitionViewModel`을 생성한다.
- `openMusicRecognition`은 Music route로 이동하며 `popUpTo(Routes.CONNECT)`와 `launchSingleTop=true`를 적용한다.
- bottom bar의 Music tab은 항상 enabled이며 MusicNote icon을 사용한다.
- 현재 route가 Music route일 때 selected 상태를 표시한다.
- Music composable은 `MusicRecognitionScreen(viewModel)`을 연결한다.
- route의 `DisposableEffect(Unit)`가 dispose될 때 `musicViewModel.cancelRecognition()`을 호출한다.

Music route는 Display session active/readiness와 독립적으로 접근할 수 있다. 기존 Samples와 Settings route, DisplayViewModel과 DAT sample 동작은 유지한다. 기존 안경 연결 성공을 음악 결과의 안경 출력 완료로 간주하지 않는다.

## 5. Manifest와 개발 HTTP

manifest에 `INTERNET`이 선언되어 있는지 확인한다. TASK-02의 `RECORD_AUDIO` 선언은 유지한다. emulator에서 host PC의 HTTP proxy를 사용할 수 있도록 application에 `usesCleartextTraffic=true`를 둔다.

global cleartext 허용은 개발 편의 설정이다. production에서는 TASK-05에서 HTTPS URL로 전환하고 이 설정을 제거하거나 debug source set으로 제한한다.

## 6. README 사용법

`DisplayAccess/README.md`에 다음을 추가한다.

- Music 탭이 휴대폰 마이크로 12초 녹음하고 proxy를 호출한다는 기능 설명
- `local.properties`의 정확한 emulator 설정:

```properties
music_recognition_proxy_url=http://10.0.2.2:8787
```

- `10.0.2.2`는 Android emulator 전용 host alias라는 설명
- 실제 휴대폰은 개발 PC의 접근 가능한 LAN URL 또는 배포 HTTPS URL을 사용한다는 설명
- proxy를 먼저 시작하고 앱에서 Music 탭을 연 뒤 마이크 권한을 허용하는 실행 순서
- 실제 secret은 Android local property나 APK에 넣지 않는다는 주의

## 완료 조건

- factory가 설치별 UUID를 재사용하고 production input/adapter를 ViewModel에 주입한다.
- Music screen이 runtime permission과 모든 UI state, 취소와 fresh retry를 연결한다.
- Music route가 항상 접근 가능하고 route dispose 때 active recognition을 취소한다.
- 기존 Samples/Settings 및 DAT Display 흐름을 유지한다.
- README가 emulator와 physical phone URL 차이, proxy 시작, 12초 녹음과 권한 절차를 설명한다.
- Android test/lint/build와 proxy test가 성공한다.
- 수동 UI와 실제 공급자 검증은 실행 증거가 생길 때만 완료로 표시한다.

## 예상 결과

사용자가 안경 연결 여부와 무관하게 Music 탭을 열고 마이크 권한을 허용하면 앱이 매 요청 새 12초 녹음을 시작한다. Listening과 Recognizing을 거쳐 match, no-match 또는 typed error가 휴대폰 화면에 표시된다. 취소하거나 route를 떠나면 recognition을 중단하고 늦은 결과가 화면을 덮지 않는다. 안경에는 음악 결과를 출력하지 않는다.

## 사용자 검증 절차

1. Android 자동 검증을 실행한다.

```powershell
Set-Location DisplayAccess
.\gradlew.bat test
.\gradlew.bat lint
.\gradlew.bat assembleDebug
```

Android unit test는 build variant별 11개(TASK-01 ViewModel 10개 + TASK-02 WAV 1개), failure 0을 기대한다. lint와 build도 `BUILD SUCCESSFUL`이어야 한다.

2. proxy 자동 검증을 실행한다.

```powershell
Set-Location ..\music-recognition-proxy
npm test
```

proxy test 5개, failure 0을 기대한다.

3. proxy 환경변수를 process에 설정하고 proxy를 먼저 시작한다. 문서나 명령 history에 실제 secret 값을 남기지 않는다.
4. emulator면 `10.0.2.2:8787`, 실제 휴대폰이면 접근 가능한 LAN 또는 HTTPS URL을 local property에 설정하고 앱을 다시 build한다.
5. Music 탭이 안경 연결 없이 열리는지 확인한다.
6. 권한 거부와 허용, Listening/Recognizing 중복 방지, 취소를 확인한다.
7. match, no-match와 각 오류 안내를 확인한다.
8. 인식 중 route를 떠난 뒤 늦은 결과가 표시되지 않는지 확인한다.

자동 테스트는 factory, Screen, navigation과 실제 permission 상호작용을 직접 검증하지 않는다. 위 수동 항목과 실제 ACRCloud 결과는 실행 전까지 미완료이며 실제 공급자 E2E는 TASK-05에서 기록한다.

## 체크리스트

[`TASK-04 체크리스트`](../checklist/TASK-04-UI와-앱-통합.md)
