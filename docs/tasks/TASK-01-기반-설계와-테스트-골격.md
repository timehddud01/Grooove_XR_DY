# TASK-01 — 기반 설계와 테스트 골격

## 목적

Android 녹음 장치, 네트워크 공급자, Compose와 DAT Display에 의존하지 않는 음악 인식 도메인 계약을 정의하고, ViewModel의 상태 흐름과 오디오 검증 정책을 JVM 단위 테스트로 고정한다.

## 선행 조건

- 작업 디렉터리는 저장소의 `DisplayAccess` 모듈이다.
- JDK 17과 Android SDK가 준비되어 있어야 한다.
- Gradle이 기존 Meta Wearables 의존성을 해석할 수 있어야 한다.
- 실제 마이크, ACRCloud 계정, Meta 안경은 필요하지 않다.

## 구현 범위

- 음악 인식 도메인 model, outcome, typed error, UI state
- 입력·인식·표시 port와 NoOp 표시 구현
- 공급자 호출 전 오디오 품질 validator
- ViewModel 상태 전이, 단일 실행, 취소와 stale result 차단
- 코드에서 결정적으로 생성하는 합성 fixture, fake와 단위 테스트 10개

## 후속 범위

- Android `AudioRecord` 휴대폰 녹음
- production WAV encoder
- HTTP 프록시와 ACRCloud 호출
- Compose UI와 runtime 마이크 권한
- MockDeviceKit, DAT Display 및 실제 안경 출력

`RecognitionProvider`의 enum 값은 확장 가능한 도메인 표현이다. TASK-01에서 ShazamKit, ACRCloud 또는 custom 공급자 adapter를 구현하지 않는다.

## 생성 파일

### production

- `DisplayAccess/app/src/main/java/com/meta/wearable/dat/externalsampleapps/displayaccess/music/domain/MusicContracts.kt`
- `DisplayAccess/app/src/main/java/com/meta/wearable/dat/externalsampleapps/displayaccess/music/domain/MusicModels.kt`
- `DisplayAccess/app/src/main/java/com/meta/wearable/dat/externalsampleapps/displayaccess/music/validation/AudioSampleValidator.kt`
- `DisplayAccess/app/src/main/java/com/meta/wearable/dat/externalsampleapps/displayaccess/music/MusicRecognitionViewModel.kt`

### test

- `DisplayAccess/app/src/test/java/com/meta/wearable/dat/externalsampleapps/displayaccess/music/TestAudioFixtures.kt`
- `DisplayAccess/app/src/test/java/com/meta/wearable/dat/externalsampleapps/displayaccess/music/TestDoubles.kt`
- `DisplayAccess/app/src/test/java/com/meta/wearable/dat/externalsampleapps/displayaccess/music/MusicRecognitionViewModelTest.kt`

## 수정 파일

- `DisplayAccess/gradle/libs.versions.toml`
- `DisplayAccess/app/build.gradle.kts`

## 구현 순서

### 0. 테스트 의존성

`DisplayAccess/gradle/libs.versions.toml`에 JUnit `4.13.2`와 kotlinx-coroutines-test `1.10.2` version 및 library alias를 선언한다. `DisplayAccess/app/build.gradle.kts`의 dependencies에 다음 두 항목을 연결한다.

```kotlin
testImplementation(libs.junit)
testImplementation(libs.kotlinx.coroutines.test)
```

### 1. 도메인 모델

`MusicModels.kt`에 다음 타입을 정의한다.

- `AudioSample(pcm16Le: ByteArray, sampleRateHz: Int, channelCount: Int = 1)`
  - sample rate와 channel count는 양수만 허용한다.
  - `durationMillis`는 PCM16 frame 크기, channel 수와 sample rate로 계산한다.
  - `ByteArray` 기본 참조 비교를 쓰지 않도록 `contentEquals`와 `contentHashCode` 기반 `equals`/`hashCode`를 직접 구현한다.
- `RecognitionProvider`: `SHAZAM_KIT`, `ACRCLOUD`, `CUSTOM`. enum 존재와 adapter 구현 여부를 구분한다.
- `RecognitionResult`: 필수 `title`, `artist`; 선택 `album`, `artworkUrl`; 필수 `provider`. 빈 title/artist를 거부한다.
- `InvalidAudioReason`: `EMPTY`, `TOO_SHORT`, `SILENT`, `DISTORTED`, `UNSUPPORTED_FORMAT`.
- `MusicRecognitionError`: `PermissionDenied`, `Timeout`, `Authentication`, `RateLimited(retryAfterSeconds)`, `Network(message)`, `InvalidAudio(reason)`, `AudioCapture(message)`, `Provider(code, message)`, `Unexpected(message)`.
- `AudioCaptureOutcome`: `Captured(sample)`, `Failure(error)`, `Cancelled`.
- `RecognitionOutcome`: `Match(result)`, `NoMatch`, `Failure(error)`.
- `MusicRecognitionStatus`: `IDLE`, `LISTENING`, `RECOGNIZING`, `MATCHED`, `NO_MATCH`, `ERROR`.
- `MusicRecognitionUiState`: `Idle`, `Listening`, `Recognizing`, `Matched(result)`, `NoMatch`, `Error(error)`. 각 state는 대응 status를 제공한다.

### 2. Port 계약

`MusicContracts.kt`에 다음 시그니처를 정의한다.

```kotlin
interface AudioInput {
  suspend fun capture(durationMillis: Long): AudioCaptureOutcome
  fun cancel()
}

interface MusicRecognizer {
  suspend fun recognize(sample: AudioSample): RecognitionOutcome
}

interface DisplayGateway {
  suspend fun present(state: MusicRecognitionUiState)
}
```

`NoOpDisplayGateway`는 `DisplayGateway`를 구현하고 `present`에서 아무 작업도 하지 않는다.

### 3. 오디오 검증 정책

`AudioSampleValidator`의 기본값은 최소 길이 3,000ms, silence RMS 128, clipping amplitude 32,700, 최대 clipped ratio 0.05다. 아래 순서를 바꾸지 않는다.

1. PCM이 비었으면 `EMPTY`.
2. PCM byte 수가 홀수면 `UNSUPPORTED_FORMAT`.
3. 길이가 3,000ms 미만이면 `TOO_SHORT`.
4. PCM16 little-endian sample RMS가 128 미만이면 `SILENT`.
5. 절대 진폭 32,700 이상 sample 비율이 0.05를 초과하면 `DISTORTED`.
6. 그 외에는 `null`을 반환한다.

ratio가 정확히 0.05인 입력은 distorted가 아니다. validator는 Android API나 공급자 DTO에 의존하지 않는다.

### 4. ViewModel orchestration

`MusicRecognitionViewModel`은 `AudioInput`, `MusicRecognizer`, 선택적 `DisplayGateway=NoOpDisplayGateway`, 선택적 `AudioSampleValidator=AudioSampleValidator()`를 생성자 주입받는다.

- `DEFAULT_CAPTURE_DURATION_MILLIS`는 12,000이다.
- `MutableStateFlow` 내부 상태와 read-only `StateFlow<MusicRecognitionUiState>`를 제공한다.
- 한 번에 하나의 active recognition job만 허용한다.
- 시작할 때 request generation을 증가시키고 `Listening`으로 전환한다.
- `audioInput.capture(12_000)` 결과를 처리한다.
  - `Cancelled` → `Idle`
  - `Failure` → `Error`
  - `Captured` → validator 실행
- validation error면 recognizer를 호출하지 않고 `Error`로 전환한다.
- 유효하면 `Recognizing`으로 전환한 뒤 `Match`, `NoMatch`, `Failure`를 각각 `Matched`, `NoMatch`, `Error`로 변환한다.
- `onPermissionDenied()`는 active job이 없을 때 `PermissionDenied` error를 표시하며 capture를 시작하지 않는다.
- `cancelRecognition()`은 generation을 증가시키고 job과 `AudioInput`을 취소한 뒤 `Idle`을 설정한다.
- 취소를 무시하는 non-cancellable fake가 늦게 결과를 반환해도 generation이 다르면 상태를 변경하지 않는다.
- `onCleared()`에서 `cancelRecognition()`을 호출한다.
- `DisplayGateway.present` 실패는 `runCatching`으로 격리해 UI state 변경을 막지 않는다.

정상 상태 전이는 `Idle → Listening → Recognizing → Matched|NoMatch|Error`다. capture/validation 실패는 `Listening → Error`, 사용자 취소는 busy state에서 `Idle`이다.

### 5. Fixture와 test double

`TestAudioFixtures`는 외부 음원 파일 없이 코드로 8kHz mono PCM16 WAV를 생성하고 다시 decode해 `AudioSample`을 제공한다.

- 4초 sine: 정상 인식 입력
- 4초 silence: 무음 거부 입력
- 4초 deterministic noise: 서로 다른 정상 fixture 확인
- 4초 clipped: clipping ratio 초과 입력
- 1초 sine WAV: too-short 입력

생성 결과는 동일한 입력에서 byte 단위로 같고 fixture 종류끼리는 달라야 한다. 이 fixture는 실제 음악이나 저작권 음원을 포함하지 않는다.

`TestAudioFixtures.kt`의 `FixtureAudioInput`은 고정 fixture를 decode해 반환하고 capture 호출 횟수, 요청 duration과 cancel 횟수를 기록한다.

`TestDoubles.kt`에는 단일 outcome을 반환하며 `delay`, `ignoreCancellation`, `callCount`, `lastSample`을 제공하는 `FakeMusicRecognizer`와 전달된 `states`를 기록하는 `RecordingDisplayGateway`를 둔다. `ignoreCancellation` fake로 취소 뒤 늦은 결과에 대한 generation 차단을 검증한다.

## 단위 테스트

`MusicRecognitionViewModelTest`에 다음 10개 테스트를 정확히 둔다.

1. `successfulRecognitionTransitionsThroughListeningAndRecognizing`: Listening과 Recognizing을 거쳐 match를 표시하고 gateway에도 전달한다.
2. `noMatchIsExposedWithoutProviderDto`: 공급자 DTO 없이 공통 `NoMatch`가 노출된다.
3. `typedProviderFailuresReachErrorState`: typed provider failure가 그대로 Error에 도달한다.
4. `silentAudioIsRejectedBeforeRecognizer`: 무음은 recognizer 호출 전에 차단된다.
5. `shortAudioIsRejectedBeforeRecognizer`: 3초 미만 입력은 recognizer 호출 전에 차단된다.
6. `clippedAudioIsRejectedBeforeRecognizer`: clipping ratio 초과 입력은 recognizer 호출 전에 차단된다.
7. `cancelledLateResultCannotOverwriteIdle`: 취소 후 늦은 non-cancellable 결과가 Idle을 덮지 못한다.
8. `generatedWavFixturesAreDeterministicAndDistinct`: 합성 WAV가 결정적이며 fixture끼리 다르다.
9. `repeatedRecognitionAlwaysCapturesFreshAudio`: 반복 실행마다 capture를 다시 호출하고 새 sample을 사용한다.
10. `permissionDenialIsRecoverableAndDoesNotStartCapture`: 권한 거부가 capture 없이 `PermissionDenied` Error가 되는지 확인한다. 이후 UI에서 다시 시도할 수 있도록 막지 않는 설계지만, 이 테스트가 후속 재시작까지 직접 assert하지는 않는다.

## 완료 조건

- 지정한 production 4개와 test 3개 파일이 컴파일된다.
- JUnit 4.13.2와 kotlinx-coroutines-test 1.10.2가 version catalog와 app test configuration에 연결되어 있다.
- 모든 model, port, validator와 ViewModel 계약이 구현된다.
- 구체 Android 녹음·네트워크·Compose·DAT 타입이 도메인 흐름에 들어오지 않는다.
- `MusicRecognitionViewModelTest` 10개가 모두 통과한다.
- 테스트가 실제 음원, 마이크, 네트워크와 시간 경과에 의존하지 않는다.
- TASK-02가 `AudioSampleValidator`를 새로 만들지 않고 이 정책을 실제 capture sample에 연결할 수 있다.

## 예상 결과

버튼이나 실제 공급자 없이도 fake 입력을 주입해 `Idle`, `Listening`, `Recognizing`, `Matched`, `NoMatch`, `Error`와 취소 복구를 결정적으로 확인할 수 있다. 잘못된 오디오는 recognizer 전에 차단되고, 표시 gateway 오류와 취소된 늦은 결과는 UI 상태를 오염시키지 않는다.

## 사용자 검증 절차

1. PowerShell에서 모듈로 이동한다.

```powershell
Set-Location DisplayAccess
```

2. TASK-01 테스트 클래스만 실행한다.

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.meta.wearable.dat.externalsampleapps.displayaccess.music.MusicRecognitionViewModelTest"
```

3. 결과가 `10 tests`, `0 failures`, `BUILD SUCCESSFUL`인지 확인한다.
4. 테스트 보고서에서 위 10개 이름이 모두 실행됐는지 확인한다.
5. 전체 `test`에서 보이는 variant별 11개 결과는 후속 `WavEncoderTest`를 포함한 수치다. TASK-01 독립 완료 기준인 이 클래스의 10개와 혼동하지 않는다.

## 체크리스트

[`TASK-01 체크리스트`](../checklist/TASK-01-기반-설계와-테스트-골격.md)
