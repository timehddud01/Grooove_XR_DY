# TASK-01 체크리스트 — 기반 설계와 테스트 골격

## 선행 조건과 범위

- [x] 작업 대상이 `DisplayAccess` 모듈로 한정되어 있다. 근거: TASK-01 선행 조건.
- [x] 실제 마이크와 공급자 계정 없이 JVM 단위 테스트로 실행할 수 있다. 근거: `MusicRecognitionViewModelTest.kt`의 fake 주입.
- [x] Android 녹음, production WAV encoder, HTTP/ACRCloud, Compose UI와 실안경 출력은 후속 범위로 분리되어 있다. 근거: TASK-01 후속 범위.
- [x] provider enum 값과 실제 provider adapter 구현 여부를 구분했다. 근거: `MusicModels.kt`의 `RecognitionProvider`, TASK-01 범위 설명.

## 생성 파일

- [x] `music/domain/MusicContracts.kt`가 있다. 근거: `AudioInput`, `MusicRecognizer`, `DisplayGateway`, `NoOpDisplayGateway`.
- [x] `music/domain/MusicModels.kt`가 있다. 근거: domain model, outcome, error, UI state.
- [x] `music/validation/AudioSampleValidator.kt`가 있다. 근거: `AudioSampleValidator`.
- [x] `music/MusicRecognitionViewModel.kt`가 있다. 근거: `MusicRecognitionViewModel`.
- [x] test package에 `TestAudioFixtures.kt`가 있다. 근거: 합성 WAV fixture 생성·decode.
- [x] test package에 `TestDoubles.kt`가 있다. 근거: `FakeMusicRecognizer`, `RecordingDisplayGateway`.
- [x] test package에 `MusicRecognitionViewModelTest.kt`가 있다. 근거: TASK-01 단위 테스트 10개.

## 테스트 의존성

- [x] `DisplayAccess/gradle/libs.versions.toml`에 JUnit 4.13.2가 선언되어 있다. 근거: `junit` version/library alias.
- [x] `DisplayAccess/gradle/libs.versions.toml`에 kotlinx-coroutines-test 1.10.2가 선언되어 있다. 근거: `kotlinx-coroutines` version과 test library alias.
- [x] `DisplayAccess/app/build.gradle.kts`에 `testImplementation(libs.junit)`가 있다.
- [x] `DisplayAccess/app/build.gradle.kts`에 `testImplementation(libs.kotlinx.coroutines.test)`가 있다.

## 모델 계약

- [x] `AudioSample`은 PCM16 little-endian bytes, 양수 sample rate와 channel count를 가진다. 근거: `MusicModels.kt`.
- [x] `AudioSample.durationMillis`가 PCM frame 크기와 sample rate로 계산된다. 근거: `MusicModels.kt`.
- [x] `AudioSample.equals/hashCode`가 `ByteArray.contentEquals/contentHashCode`를 사용한다. 근거: `MusicModels.kt`.
- [x] provider enum에 `SHAZAM_KIT`, `ACRCLOUD`, `CUSTOM`이 정의되어 있다. 근거: `MusicModels.kt`.
- [x] `RecognitionResult`가 title, artist, album, artworkUrl, provider를 공통 모델로 제공하고 빈 title/artist를 거부한다. 근거: `MusicModels.kt`.
- [x] `InvalidAudioReason`에 empty, short, silent, distorted, unsupported format이 모두 있다. 근거: `MusicModels.kt`.
- [x] permission, timeout, authentication, rate limit, network, invalid audio, capture, provider, unexpected 오류가 typed error로 정의되어 있다. 근거: `MusicModels.kt`.
- [x] `AudioCaptureOutcome`과 `RecognitionOutcome`이 성공, 실패, 취소/미인식을 구분한다. 근거: `MusicModels.kt`.
- [x] status와 UI state가 IDLE, LISTENING, RECOGNIZING, MATCHED, NO_MATCH, ERROR를 일대일로 표현한다. 근거: `MusicModels.kt`.

## Port 계약

- [x] `AudioInput.capture(durationMillis)`가 typed capture outcome을 반환하고 `cancel()`을 제공한다. 근거: `MusicContracts.kt`.
- [x] `MusicRecognizer.recognize(sample)`가 공통 recognition outcome을 반환한다. 근거: `MusicContracts.kt`.
- [x] `DisplayGateway.present(state)`가 UI state를 받는다. 근거: `MusicContracts.kt`.
- [x] `NoOpDisplayGateway`가 아무 외부 출력 없이 기본 gateway로 동작한다. 근거: `MusicContracts.kt`와 `MusicRecognitionViewModel` 기본 인자.

## 오디오 검증 정책

- [x] 기본 최소 길이가 3,000ms다. 근거: `AudioSampleValidator.minimumDurationMillis`.
- [x] 기본 silence RMS threshold가 128이다. 근거: `AudioSampleValidator.silenceRmsThreshold`.
- [x] clipping amplitude가 32,700이고 허용 ratio가 0.05다. 근거: `AudioSampleValidator` 기본값.
- [x] empty → odd bytes → too short → silent → distorted 순서로 검사한다. 근거: `AudioSampleValidator.validate`.
- [x] clipped ratio가 0.05를 초과할 때만 distorted로 판정한다. 근거: `clippedSampleRatio > maximumClippedSampleRatio`.
- [x] validation error가 있으면 recognizer를 호출하지 않는다. 근거: `silentAudioIsRejectedBeforeRecognizer`, `shortAudioIsRejectedBeforeRecognizer`, `clippedAudioIsRejectedBeforeRecognizer`.

## ViewModel 상태와 취소

- [x] 기본 capture 길이가 12,000ms다. 근거: `DEFAULT_CAPTURE_DURATION_MILLIS`.
- [x] read-only `StateFlow<MusicRecognitionUiState>`를 노출한다. 근거: `MusicRecognitionViewModel.uiState`.
- [x] active job이 있으면 중복 시작하지 않는다. 근거: `startRecognition`의 active 검사.
- [x] 정상 흐름이 Listening → capture → validate → Recognizing → outcome 순서다. 근거: `successfulRecognitionTransitionsThroughListeningAndRecognizing`.
- [x] capture failure와 cancellation, match, no-match, provider failure를 공통 UI state로 변환한다. 근거: `MusicRecognitionViewModel.startRecognition`.
- [x] permission denial은 capture를 시작하지 않고 `PermissionDenied` Error 상태가 된다. 근거: `permissionDenialIsRecoverableAndDoesNotStartCapture`.
- [x] cancel이 generation 증가, job 취소, `audioInput.cancel()`, Idle 설정을 수행한다. 근거: `cancelRecognition`.
- [x] non-cancellable 늦은 결과가 Idle을 덮지 못한다. 근거: `cancelledLateResultCannotOverwriteIdle`.
- [x] `onCleared()`가 취소와 자원 중단 경로를 호출한다. 근거: `MusicRecognitionViewModel.onCleared`.
- [x] `DisplayGateway.present` 예외가 UI state 변경을 막지 않는다. 근거: `updateState`와 cancel 경로의 `runCatching`.

## Fixture와 test double

- [x] 8kHz 4초 sine WAV를 코드에서 결정적으로 생성하고 decode한다. 근거: `TestAudioFixtures.kt`.
- [x] 8kHz 4초 silence, noise, clipped WAV를 코드에서 생성하고 decode한다. 근거: `TestAudioFixtures.kt`.
- [x] 8kHz 1초 too-short WAV를 코드에서 생성하고 decode한다. 근거: `TestAudioFixtures.kt`.
- [x] fixture가 실제 음원이나 저작권 파일을 포함하지 않는다. 근거: 코드 생성 방식과 `audio-fixtures/README.md`.
- [x] `FixtureAudioInput`이 고정 fixture를 decode해 반환하고 capture 호출 횟수, 요청 duration과 cancel 횟수를 기록한다. 근거: `TestAudioFixtures.kt`.
- [x] `FakeMusicRecognizer`가 단일 outcome, delay, ignoreCancellation, callCount와 lastSample을 제공한다. 근거: `TestDoubles.kt`.
- [x] `RecordingDisplayGateway`가 전달된 states를 기록한다. 근거: `TestDoubles.kt`.
- [x] `ignoreCancellation`을 사용한 늦은 결과가 Idle을 덮지 못한다. 근거: `cancelledLateResultCannotOverwriteIdle`.

## 단위 테스트 10개

- [x] `successfulRecognitionTransitionsThroughListeningAndRecognizing`
- [x] `noMatchIsExposedWithoutProviderDto`
- [x] `typedProviderFailuresReachErrorState`
- [x] `silentAudioIsRejectedBeforeRecognizer`
- [x] `shortAudioIsRejectedBeforeRecognizer`
- [x] `clippedAudioIsRejectedBeforeRecognizer`
- [x] `cancelledLateResultCannotOverwriteIdle`
- [x] `generatedWavFixturesAreDeterministicAndDistinct`
- [x] `repeatedRecognitionAlwaysCapturesFreshAudio`
- [x] `permissionDenialIsRecoverableAndDoesNotStartCapture`

근거 파일: `DisplayAccess/app/src/test/java/com/meta/wearable/dat/externalsampleapps/displayaccess/music/MusicRecognitionViewModelTest.kt`.

## 최종 검증

- [x] 다음 명령으로 TASK-01 테스트 클래스만 실행할 수 있다.

```powershell
Set-Location DisplayAccess
.\gradlew.bat testDebugUnitTest --tests "com.meta.wearable.dat.externalsampleapps.displayaccess.music.MusicRecognitionViewModelTest"
```

- [x] TASK-01 완료 기준은 위 클래스의 `10 tests`, `0 failures`, `BUILD SUCCESSFUL`이다.
- [x] 전체 Gradle unit test도 성공한 상태다. 단, 전체 test의 variant별 11개는 후속 `WavEncoderTest`를 포함하므로 TASK-01 독립 완료 수치와 구분한다.
- [x] TASK-02는 validator를 다시 만들지 않고 TASK-01 정책을 실제 capture sample에 연결하도록 경계가 정리되어 있다. 근거: 실행 순서 문서 TASK-01/02.

## 예상 결과와 사용자 확인

- [x] fake만으로 정상 인식, 미인식, typed error, invalid audio, 취소와 재시도를 확인할 수 있다.
- [x] 공급자 DTO가 ViewModel과 UI state에 노출되지 않는다.
- [x] 반복 인식이 이전 sample을 재사용하지 않고 매번 새 capture를 요청한다.
- [x] 사용자는 PowerShell 명령 실행 후 테스트 보고서에서 위 10개 이름과 실패 0개를 확인한다.
