# 음악 인식 기능 실행 순서

> 현재 코드를 새 저장소에서 동일한 구조로 재현하기 위한 상위 실행 명세다. 과거 실행 기록이 아니며 TASK 1~5를 아래 순서로 실행한다.

## 1. 최종 범위와 경계

구현 범위:

- 기존 Android `DisplayAccess` 앱에 독립적인 `Music` 탭을 추가한다.
- 버튼을 누르면 휴대폰 마이크로 주변 음악을 녹음한다.
- 앱은 PCM을 검증하고 WAV로 인코딩해 자체 Node 프록시에 전송한다.
- 프록시는 서버에만 보관한 ACRCloud 자격 증명으로 Recorded Audio API를 호출한다.
- 제목, 가수, 선택적 앨범과 오류 상태를 휴대폰 Compose 화면에 표시한다.

범위 밖:

- 음악 상태나 결과를 실제 Meta 안경 Display로 보내지 않는다.
- 음악 기능은 기존 `DisplayAccess` 안경 연결·Display 샘플과 같은 앱에 있지만 DAT 세션이나 Display capability에 의존하지 않는다.
- `DisplayGateway` 계약은 있으나 실제 앱은 `NoOpDisplayGateway`를 사용하고 테스트에서만 기록 fake를 쓴다.
- 안경 마이크, Bluetooth 라우팅, MockDeviceKit 음악 통합, 실제 안경 음악 출력은 미구현이다.
- ShazamKit adapter와 복수 공급자 전환 UI도 미구현이다. `RecognitionProvider.SHAZAM_KIT` 값은 adapter 구현을 뜻하지 않는다.
- 대규모 곡 비교 벤치마크나 출시 공급자 선정은 완료 조건이 아니다.

## 2. 최종 구조와 원칙

```text
사용자 -> DisplayAccess Music 탭 -> RECORD_AUDIO 권한
      -> MusicRecognitionViewModel
      -> PhoneAudioInput (12초 PCM 16-bit mono)
      -> AudioSampleValidator
      -> AcrCloudProxyMusicRecognizer -> WavEncoder
      -> POST /v1/music-recognitions -> Node 프록시
      -> ACRCloud POST /v1/identify
      -> RecognitionOutcome -> MusicRecognitionUiState -> 휴대폰 화면
```

- UI와 ViewModel은 `AudioRecord`, `HttpURLConnection`, 공급자 DTO를 직접 알지 않는다.
- Android 앱에는 ACRCloud access key와 secret을 넣지 않는다.
- 공급자 응답은 프록시에서 앱의 공통 JSON 계약으로 정규화한다.
- 버튼을 누를 때마다 새 오디오를 녹음하며 이전 결과를 시간 기반으로 재사용하지 않는다.
- 취소 뒤 늦은 결과가 `Idle`을 덮지 못하게 generation 번호를 사용한다.
- 음악 Display 출력은 확장 계약만 두고 현재는 no-op이다.

패키지 기준 경로:

```text
DisplayAccess/app/src/main/java/com/meta/wearable/dat/externalsampleapps/displayaccess/music/
```

## 3. 고정 도메인 계약

`domain/MusicContracts.kt`:

- `AudioInput`: `capture(durationMillis): AudioCaptureOutcome`, `cancel()`
- `MusicRecognizer`: `recognize(sample): RecognitionOutcome`
- `DisplayGateway`: `present(state: MusicRecognitionUiState)`
- `NoOpDisplayGateway`: 아무 동작 없이 종료하는 기본 구현

`domain/MusicModels.kt`:

- `AudioSample`: little-endian raw PCM 16-bit byte, sample rate, 기본 1채널, 계산 duration, byte 내용 기반 동등성
- `RecognitionResult`: 필수 title·artist·provider, 선택적 album·artwork URL
- `RecognitionProvider`: 현재 실제 결과는 `ACRCLOUD`; `SHAZAM_KIT`, `CUSTOM`은 향후 확장값
- `InvalidAudioReason`: `EMPTY`, `TOO_SHORT`, `SILENT`, `DISTORTED`, `UNSUPPORTED_FORMAT`
- `MusicRecognitionError`: `PermissionDenied`, `Timeout`, `Authentication`, `RateLimited`, `Network`, `InvalidAudio`, `AudioCapture`, `Provider`, `Unexpected`
- `AudioCaptureOutcome`: `Captured`, `Failure`, `Cancelled`
- `RecognitionOutcome`: `Match`, `NoMatch`, `Failure`
- `MusicRecognitionStatus`: `IDLE`, `LISTENING`, `RECOGNIZING`, `MATCHED`, `NO_MATCH`, `ERROR`
- `MusicRecognitionUiState`: `Idle`, `Listening`, `Recognizing`, `Matched`, `NoMatch`, `Error`

공급자 전용 JSON이나 상태 코드는 도메인에 넣지 않는다.

## 4. TASK 1~5 실행 순서

## TASK 1 — 도메인 계약, 상태 머신, 테스트 골격

생성 순서:

1. `music/domain/MusicModels.kt`
2. `music/domain/MusicContracts.kt`
3. `music/validation/AudioSampleValidator.kt`
4. `music/MusicRecognitionViewModel.kt`
5. `DisplayAccess/app/src/test/java/com/meta/wearable/dat/externalsampleapps/displayaccess/music/TestAudioFixtures.kt`
6. 같은 테스트 경로의 `TestDoubles.kt`
7. 같은 테스트 경로의 `MusicRecognitionViewModelTest.kt`

구현 규칙:

- `DEFAULT_CAPTURE_DURATION_MILLIS = 12_000L`로 둔다.
- 활성 Job이 있으면 중복 시작하지 않는다.
- 정상 상태는 `Idle -> Listening -> Recognizing -> Matched`로 전이한다.
- 미인식은 `NoMatch`, typed failure는 `Error`다.
- 녹음·검증 실패 시 recognizer를 호출하지 않는다.
- 취소는 generation 증가, Job과 `AudioInput` 취소, 즉시 `Idle` 전환을 수행한다.
- 각 상태를 `DisplayGateway.present`에 전달하되 gateway 실패는 `runCatching`으로 격리한다.
- `onCleared()`도 같은 정리 경로를 쓴다.
- fake recognizer는 지연, 취소 무시, 성공, 미인식, typed failure를 재현한다.
- fixture는 정상 sine, 무음, 고정 seed 잡음, 너무 짧은 입력, clipping 입력을 결정론적으로 생성하고 SHA-256을 계산한다.

`AudioSampleValidator` 기본 기준:

| 검사 순서 | 기준 | 실패 이유 |
|---|---:|---|
| 1 | PCM byte 수 `0` | `EMPTY` |
| 2 | byte 수가 홀수 | `UNSUPPORTED_FORMAT` |
| 3 | duration `< 3,000ms` | `TOO_SHORT` |
| 4 | RMS `< 128.0` | `SILENT` |
| 5 | 절대 진폭 `>= 32,700`인 sample 비율 `> 0.05` | `DISTORTED` |

PCM은 little-endian signed 16-bit로 해석한다.

완료 조건:

- adapter 없이 ViewModel 테스트가 실행된다.
- 성공, 미인식, typed error, 무음, 짧은 입력, 왜곡, 취소 후 늦은 결과 방어, fixture 결정성, 매 요청 새 녹음, 권한 거부 복구가 검증된다.
- 공급자 DTO가 ViewModel과 UI 상태에 노출되지 않는다.

## TASK 2 — 휴대폰 녹음, 검증, WAV

생성·수정 순서:

1. `music/audio/PhoneAudioInput.kt`
2. `music/network/WavEncoder.kt`
3. 테스트 경로의 `WavEncoderTest.kt`
4. `DisplayAccess/app/src/main/AndroidManifest.xml`

녹음 규칙:

- `AudioRecord`, `MediaRecorder.AudioSource.MIC`, mono, PCM 16-bit를 쓴다.
- `44_100`, `48_000`, `16_000`, `8_000` 순으로 `getMinBufferSize(...) > 0`인 첫 sample rate를 고른다.
- buffer는 `maxOf(minBuffer, 4_096)`, 목표 byte 수는 `sampleRate * 2 * durationMillis / 1_000`이다.
- blocking read 중 매 반복 취소 플래그를 확인한다.
- 첫 12초 샘플의 RMS가 `128.0` 미만이면 12초 녹음을 정확히 한 번 더 한다.
- 두 번째도 무음이면 `InvalidAudio(SILENT)`이며 다른 오류는 임의 재시도하지 않는다.
- `SecurityException`은 `PermissionDenied`, 초기화·read 실패는 `AudioCapture`, 사용자 중단은 `Cancelled`다.
- active recorder를 lock으로 보호하고 `cancel()`에서 `stop()`해 blocking read를 해제한다.
- 성공·실패·취소·예외 모든 경로의 `finally`에서 `stop()`과 `release()`를 수행한다.
- 로그에는 길이, sample rate, RMS, peak, byte 수만 남기며 PCM은 남기지 않는다.

WAV 규칙:

- PCM 앞에 표준 44바이트 RIFF/WAVE 헤더를 붙인다.
- format `1`, bit depth `16`, channel과 sample rate는 `AudioSample` 값을 쓴다.
- byte rate는 `sampleRate * channelCount * 2`, block align은 `channelCount * 2`다.
- RIFF size는 `36 + pcmSize`, data size는 `pcmSize`로 little-endian 기록한다.
- 테스트에서 헤더 필드와 PCM payload 보존을 확인한다.

Manifest에는 `RECORD_AUDIO`, `INTERNET`을 선언하고 기존 DisplayAccess용 Bluetooth 권한은 유지한다. 런타임 마이크 권한 UI는 TASK 4에서 연결한다.

완료 조건:

- 지원 sample rate 탐색과 12초 PCM 캡처가 구현된다.
- 무음일 때만 한 번 재녹음하고 모든 종료 경로에서 recorder를 해제한다.
- WAV에 정확한 44바이트 PCM 헤더와 원본 PCM이 들어간다.
