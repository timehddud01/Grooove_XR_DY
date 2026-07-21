# TASK-02 — 휴대폰 오디오 캡처와 WAV

## 목적

Android 휴대폰 마이크에서 PCM 16-bit mono 오디오를 안전하게 캡처하고 TASK-01의 validator에 연결한다. 유효한 PCM을 standard PCM WAV로 변환한다.

## 선행 조건

- TASK-01의 모델, 계약, validator, ViewModel과 테스트가 완료되어 있다.
- `DisplayAccess` 모듈을 Android SDK로 빌드할 수 있다.

## 구현 범위

- `AudioRecord` 기반 휴대폰 오디오 캡처
- 무음 1회 재캡처, 취소와 recorder 해제
- TASK-01 validator 연결
- 44-byte RIFF/WAVE encoder와 테스트
- manifest의 `RECORD_AUDIO` 선언

## 후속 범위

- runtime 마이크 권한 요청과 Compose UI
- HTTP 요청, 공급자 adapter와 서버
- 네트워크 오류 및 공급자 응답 처리
- DAT Display 및 실제 안경 출력
- 실제 휴대폰 권한·녹음·취소·무음 재시도 검증

## 생성 파일

- `DisplayAccess/app/src/main/java/com/meta/wearable/dat/externalsampleapps/displayaccess/music/audio/PhoneAudioInput.kt`
- `DisplayAccess/app/src/main/java/com/meta/wearable/dat/externalsampleapps/displayaccess/music/network/WavEncoder.kt`
- `DisplayAccess/app/src/test/java/com/meta/wearable/dat/externalsampleapps/displayaccess/music/WavEncoderTest.kt`

## 수정 파일

- `DisplayAccess/app/src/main/AndroidManifest.xml`
  - `android.permission.RECORD_AUDIO`만 TASK-02 변경으로 추가한다.
  - 기존 `INTERNET`, Bluetooth, DAT metadata와 application 설정은 TASK-02 산출물이 아니다.

## 실행 순서

1. manifest 권한을 선언한다.
2. `PhoneAudioInput`을 구현한다.
3. 기존 validator에 캡처 결과를 연결한다.
4. `WavEncoder`와 단위 테스트를 구현한다.
5. unit test와 debug build를 검증한다.

### 1. Manifest 권한

`AndroidManifest.xml`의 manifest 직계 자식으로 `<uses-permission android:name="android.permission.RECORD_AUDIO" />`를 추가한다. 이 선언만으로 runtime 권한이 허용되지는 않는다. 권한 요청 UI는 후속 단계에서 연결하며 권한 없이 capture가 호출되면 `PermissionDenied`를 반환한다.

### 2. 캡처 context와 format

`PhoneAudioInput`은 TASK-01의 `AudioInput`을 구현한다.

- `capture(durationMillis)`를 `withContext(Dispatchers.IO)`에서 실행한다.
- source는 `MediaRecorder.AudioSource.MIC`이다.
- channel은 `CHANNEL_IN_MONO`, encoding은 `ENCODING_PCM_16BIT`다.
- 결과는 PCM16 little-endian, channelCount 1의 `AudioSample`이다.
- ViewModel은 12,000ms를 요청하고 `RETRY_DURATION_MILLIS`도 12,000이다.

### 3. Sample rate와 buffer

`44_100 → 48_000 → 16_000 → 8_000` 순서로 `AudioRecord.getMinBufferSize`가 0보다 큰 첫 rate를 선택한다. 지원 rate가 없으면 typed `AudioCapture` failure를 반환한다.

- `bufferSize = maxOf(minBuffer, 4_096)`
- `targetBytes = sampleRate * 2 * durationMillis / 1_000`
- `AudioRecord.READ_BLOCKING`으로 목표 byte 수 또는 취소 시점까지 읽는다.
- read가 양수면 해당 byte를 append하고 0이면 계속한다. 음수면 read code가 포함된 `AudioCapture` failure를 반환한다.

### 4. 무음 재캡처와 validator 연결

첫 `captureOnce(durationMillis)`가 `Captured`이고 PCM RMS가 128 미만일 때만 `captureOnce(RETRY_DURATION_MILLIS)`를 한 번 실행한다.

- 두 번째 captured sample도 RMS 128 미만이면 `InvalidAudio(SILENT)` failure를 반환한다.
- 두 번째 sample이 유효하면 그대로 반환한다.
- retry가 `Failure` 또는 `Cancelled`이면 그대로 반환한다.
- 첫 결과가 silent captured가 아니면 재캡처하지 않는다.

이 RMS 판정은 재캡처 여부만 결정한다. 최종 empty, odd bytes, 3초 미만, silent, clipped 판정은 TASK-01의 `AudioSampleValidator`가 capture 뒤 recognizer 호출 전에 수행한다. validator를 다시 구현하지 않는다.

### 5. 취소와 자원 해제

- `AtomicBoolean cancelled`로 취소 여부를 관리한다.
- `recordLock`과 `activeRecord`로 활성 recorder 참조를 보호한다.
- capture 시작 시 cancel flag를 false로 초기화하고 recorder 시작 전에 lock에서 active 참조를 설정한다.
- `cancel()`은 flag를 true로 바꾸고 활성 recorder가 recording 중이면 `stop()`을 시도한다.
- loop가 cancel을 확인하면 `Cancelled`를 반환한다.

모든 terminal path의 `finally`에서 같은 recorder의 active 참조를 null로 해제하고, recording 중이면 `stop()`을 시도한 뒤 `release()`를 호출한다.

### 6. Typed failure와 로그

- `SecurityException` → `PermissionDenied`
- recorder 초기화 실패와 음수 read → `AudioCapture`
- 기타 예외 → cancel flag가 true면 `Cancelled`, 아니면 `AudioCapture(message)`

성공 로그에는 duration, sample rate, RMS, peak와 byte 수만 기록한다. PCM/WAV 원본과 사용자 데이터는 로그에 기록하지 않는다.

### 7. WavEncoder

`WavEncoder.encode(sample)`는 PCM을 변경하지 않고 앞에 정확히 44-byte little-endian RIFF/WAVE header를 붙인다.

| header 항목 | 값 |
|---|---|
| RIFF | ASCII `RIFF` |
| chunk size | `36 + pcm.size` |
| format | ASCII `WAVEfmt ` |
| fmt chunk size / audio format | 16 / 1(PCM) |
| channels / sample rate | sample의 channelCount / sampleRateHz |
| byte rate | `sampleRateHz * channelCount * 2` |
| block align / bits | `channelCount * 2` / 16 |
| data marker / size | ASCII `data` / `pcm.size` |
| payload | 원본 PCM bytes |

결과 크기는 항상 `44 + pcm.size`다.

### 8. WavEncoderTest

8,000Hz mono sample과 4-byte PCM으로 다음 항목을 직접 assert한다.

- byte 0..3이 `RIFF`
- byte 8..11이 `WAVE`
- header sample rate가 8,000
- data size가 4
- offset 44 이후 byte가 입력 PCM과 동일

encoder는 위 표의 모든 field를 구현하지만 현재 테스트의 직접 assertion은 이 다섯 항목이다.

## 완료 조건

- 지정 format, sample-rate fallback, buffer와 target byte 계산이 구현되어 있다.
- 무음 첫 캡처에만 12초 재캡처를 한 번 수행한다.
- 취소와 모든 terminal path에 recorder stop/release 경로가 있다.
- capture 결과가 기존 validator를 거쳐 recognizer 전에 차단된다.
- WAV가 44-byte header와 변경되지 않은 PCM으로 생성된다.
- `WavEncoderTest` 1개와 debug unit test 전체 11개가 통과한다.
- `assembleDebug`가 성공한다.
- 실제 AudioRecord, runtime permission, stop/release와 무음 재시도는 JVM 테스트 완료로 간주하지 않고 TASK-05에서 검증한다.

## 예상 결과

ViewModel의 12초 요청에 `PhoneAudioInput`이 지원 sample rate의 PCM16 mono `AudioSample`을 반환한다. 첫 sample이 무음이면 한 번만 다시 녹음하며 기존 validator를 통과한 sample만 recognizer로 전달할 수 있다. `WavEncoder`는 sample을 공급자와 무관한 PCM WAV bytes로 변환한다.

## 사용자 검증 절차

1. PowerShell에서 모듈로 이동한다.

```powershell
Set-Location DisplayAccess
```

2. encoder 테스트를 실행해 `1 test`, `0 failures`를 확인한다.

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.meta.wearable.dat.externalsampleapps.displayaccess.music.WavEncoderTest"
```

3. debug unit test 전체를 실행해 TASK-01 10개와 encoder 1개, 총 `11 tests`, `0 failures`를 확인한다.

```powershell
.\gradlew.bat testDebugUnitTest
```

4. Android source와 manifest compile을 확인한다.

```powershell
.\gradlew.bat assembleDebug
```

5. 세 명령이 `BUILD SUCCESSFUL`인지 확인한다.
6. 실기기 마이크 권한, 실제 녹음, 녹음 중 취소, 무음 1회 재시도와 recorder 해제는 TASK-05에서 실행하고 기록한다.

## 체크리스트

[`TASK-02 체크리스트`](../checklist/TASK-02-휴대폰-음악-인식-PoC.md)
