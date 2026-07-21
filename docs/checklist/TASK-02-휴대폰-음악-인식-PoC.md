# TASK-02 체크리스트 — 휴대폰 오디오 캡처와 WAV

## 선행 조건과 범위

- [x] TASK-01 모델, 계약, validator, ViewModel과 테스트가 준비되어 있다.
- [x] TASK-02가 캡처, validator 연결, WAV와 manifest 권한으로 한정되어 있다.
- [x] 공급자, HTTP, UI와 안경 출력은 후속 범위다.

## 생성·수정 파일

- [x] `music/audio/PhoneAudioInput.kt`가 있다.
- [x] `music/network/WavEncoder.kt`가 있다.
- [x] test package에 `WavEncoderTest.kt`가 있다.
- [x] `AndroidManifest.xml`에 `RECORD_AUDIO`가 선언되어 있다.
- [x] 기존 `INTERNET`, Bluetooth와 DAT 설정을 TASK-02 성과로 포함하지 않았다. 근거: TASK-02 수정 파일 범위.

## 캡처 format과 실행 context

- [x] `capture`가 `withContext(Dispatchers.IO)`에서 실행된다. 근거: `PhoneAudioInput.capture`.
- [x] source가 `MediaRecorder.AudioSource.MIC`이다. 근거: `PhoneAudioInput.captureOnce`.
- [x] format이 PCM 16-bit mono다. 근거: `CHANNEL_IN_MONO`, `ENCODING_PCM_16BIT`.
- [x] 결과가 little-endian PCM, channelCount 1의 `AudioSample`이다. 근거: `captureOnce`의 sample 생성.
- [x] ViewModel 요청과 retry 길이가 각각 12,000ms다. 근거: `DEFAULT_CAPTURE_DURATION_MILLIS`, `RETRY_DURATION_MILLIS`.

## Sample rate와 read

- [x] `44_100`, `48_000`, `16_000`, `8_000` 순서로 지원 rate를 찾는다. 근거: `PhoneAudioInput.SAMPLE_RATES`.
- [x] `getMinBufferSize > 0`인 첫 rate를 선택한다. 근거: `findSupportedSampleRate`.
- [x] 지원 rate가 없으면 typed `AudioCapture` failure다. 근거: `captureOnce`.
- [x] buffer가 `maxOf(minBuffer, 4_096)`로 계산된다. 근거: `captureOnce`.
- [x] target byte가 `sampleRate * 2 * durationMillis / 1_000`으로 계산된다. 근거: `captureOnce`.
- [x] `READ_BLOCKING`으로 읽고 양수, 0, 음수 결과를 구분한다. 근거: `captureOnce` read loop.

## 무음 재캡처와 validator

- [x] 첫 captured sample의 RMS가 128 미만일 때만 12초 재캡처를 한 번 수행한다. 근거: `PhoneAudioInput.capture`.
- [x] 두 번째 captured sample도 무음이면 `InvalidAudio(SILENT)`를 반환한다. 근거: `PhoneAudioInput.capture`.
- [x] retry failure와 cancellation은 변환하지 않고 반환한다. 근거: retry 결과 분기.
- [x] TASK-01 validator가 capture 뒤 recognizer 전에 실행된다. 근거: `MusicRecognitionViewModel.startRecognition`.
- [x] 최소 3초, odd bytes, silent, clipped 정책을 다시 구현하지 않고 기존 validator를 사용한다. 근거: `AudioSampleValidator` 생성자 주입과 validate 호출.

## 취소, failure와 자원 해제

- [x] `AtomicBoolean`과 lock으로 cancel flag와 active recorder를 관리한다. 근거: `PhoneAudioInput` fields.
- [x] `cancel()`이 flag 설정 후 active recording의 `stop()`을 시도한다. 근거: `PhoneAudioInput.cancel`.
- [x] loop가 취소를 확인하면 `Cancelled`를 반환한다. 근거: `captureOnce`.
- [x] 모든 terminal path의 `finally`가 active 참조 해제, stop 시도와 release를 수행한다. 근거: `captureOnce` finally.
- [x] `SecurityException`이 `PermissionDenied`로 변환된다. 근거: `captureOnce` catch.
- [x] 초기화/read/기타 capture 문제가 typed `AudioCapture`로 변환된다. 근거: `captureOnce` failure 분기.
- [x] 로그가 duration, sample rate, RMS, peak와 byte 수로 제한된다. 근거: `PhoneAudioInput` 성공 로그.
- [x] PCM/WAV 원본을 로그에 기록하지 않는다. 근거: `PhoneAudioInput` logging code.

## WAV 구현과 직접 assertion

- [x] encoder가 정확히 44-byte RIFF/WAVE PCM header를 쓴다. 근거: `WavEncoder.encode`.
- [x] chunk size가 `36 + pcm.size`다. 근거: `WavEncoder.encode`.
- [x] format 1, channel count, sample rate, byte rate, block align과 16-bit field를 쓴다. 근거: `WavEncoder.encode`.
- [x] data size 뒤에 입력 PCM을 변경 없이 쓴다. 근거: `WavEncoder.encode`.
- [x] 결과 크기가 `44 + pcm.size`다. 근거: encoder 구현과 `WavEncoderTest` fixture.
- [x] 테스트가 `RIFF`와 `WAVE` marker를 직접 assert한다. 근거: `WavEncoderTest`.
- [x] 테스트가 8,000Hz sample rate와 data size 4를 직접 assert한다. 근거: `WavEncoderTest`.
- [x] 테스트가 offset 44 이후의 PCM byte 보존을 직접 assert한다. 근거: `WavEncoderTest`.
- [x] 구현 계약 전체와 현재 테스트의 직접 assertion 범위를 구분했다. 근거: TASK-02 `WavEncoderTest` 절.

## 자동 검증

- [x] `WavEncoderTest` 1개가 통과한다. 명령: `.\gradlew.bat testDebugUnitTest --tests "com.meta.wearable.dat.externalsampleapps.displayaccess.music.WavEncoderTest"`.
- [x] debug unit test 전체 11개가 통과한다. 명령: `.\gradlew.bat testDebugUnitTest`.
- [x] `assembleDebug`가 통과해 `PhoneAudioInput`과 manifest가 컴파일된다. 명령: `.\gradlew.bat assembleDebug`.
- [x] TASK-01 10개와 TASK-02 encoder 1개의 수치를 구분했다.
- [x] 세 자동 명령의 기대 결과가 `BUILD SUCCESSFUL`이다.

## 실기기 후속 검증

- [ ] runtime 마이크 권한 허용·거부를 확인했다.
- [ ] 실제 녹음과 녹음 중 취소를 확인했다.
- [ ] 무음 1회 재시도와 recorder 해제를 확인했다.

JVM unit test는 실제 `AudioRecord`, runtime permission, device별 recorder 상태와 release를 검증하지 않는다. 위 세 항목은 TASK-05에서 실기기 실행 증거가 생길 때만 체크한다.

## 예상 결과와 사용자 확인

- [x] 12초 PCM16 mono capture가 기존 validator를 통과한 뒤 후속 recognizer에 전달되는 구조다.
- [x] 첫 무음 capture에만 한 번 재시도하는 경계가 있다.
- [x] 유효 PCM을 공급자와 무관한 WAV bytes로 변환할 수 있다.
- [x] 사용자는 `Set-Location DisplayAccess` 후 encoder test, 전체 debug unit test, `assembleDebug` 순서로 확인한다.
