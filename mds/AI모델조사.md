# Meta Ray-Ban Display용 음악 인식 기술 조사

> 조사 기준일: 2026-07-20  
> 목표: 주변에서 재생되는 원곡 녹음을 짧게 듣고 곡 제목과 가수를 찾아 Meta Ray-Ban Display에 표시

## 1. 결론

### 권장안

1. **첫 PoC는 AudD로 구현한다.**
   - Android/Kotlin에서 짧은 녹음 파일을 REST API로 보내면 되므로 가장 빨리 검증할 수 있다.
   - 공개 가격이 명확하고, 카드 없이 300회 무료 시험이 가능하다.
   - 응답에 제목, 가수, 앨범, 발매일, 스트리밍 링크가 바로 포함된다.
2. **동일한 실제 녹음 데이터로 ShazamKit과 ACRCloud를 A/B 테스트한다.**
   - 상용 서비스끼리 카탈로그 크기와 실제 환경 정확도를 동일 조건으로 비교한 공개 벤치마크가 없으므로, 특정 서비스를 객관적인 “최고 성능”이라고 단정할 수 없다.
   - ShazamKit은 Shazam의 대규모 카탈로그와 Android 스트리밍 SDK를 제공해 가장 유력한 성능 후보이다.
   - ACRCloud는 마이크 녹음용 설정, 원곡 지문 인식, 커버/허밍 인식을 구분해 제공하므로 기능 확장 후보로 좋다.
3. **출시 서비스는 PoC 결과와 계약 조건을 보고 선택한다.**
   - 추천 우선순위: `AudD로 빠른 구현 → ShazamKit/ACRCloud 실측 → 정확도·지연·비용으로 최종 결정`.
4. **완전 무료 오픈소스는 “보유 음원만 찾는 자체 카탈로그”일 때 사용한다.**
   - Neural Audio Fingerprint(NAFP), Panako, Chromaprint는 인식 알고리즘이지 전 세계 상용 음원 카탈로그가 아니다.
   - 최신곡까지 찾으려면 합법적으로 확보한 기준 음원, 메타데이터, 검색 인덱스, 지속적인 업데이트가 별도로 필요하다.

### 필수 개발 조건

- **먼저 휴대폰만으로 음악 인식 프로토타입을 완성해 핵심 사용자 가치를 검증한다.**
- **스마트글래스와 연동되는 모든 DAT 기능은 MockDeviceKit 기반 테스트를 먼저 통과한 뒤 실제 스마트글래스 테스트로 확장한다.**
- MockDeviceKit이 직접 모사하지 않는 오디오 입력이나 외부 음악 인식 API는 테스트용 WAV fixture와 `FakeMusicRecognizer`로 대체해 상태 전이, 성공, 미인식, 오류, timeout을 먼저 검증한다.
- Mock 단계와 실기기 단계가 같은 앱 로직을 사용하도록 `AudioInput`, `MusicRecognizer`, `DisplayGateway`를 인터페이스로 분리한다. 환경별로 구현체만 교체한다.
- Mock 단계의 완료 조건을 충족하기 전에는 실제 스마트글래스 의존 기능을 붙이지 않는다. 단, MockDeviceKit의 지원 범위를 확인하기 위한 최소한의 기술 검증은 허용한다.
- 실제 스마트글래스 단계에서는 Bluetooth 마이크 라우팅, DAT Display 출력, 거리·소음·연결 끊김처럼 Mock으로 재현하기 어려운 항목만 추가 검증한다.

### 한 줄 선택표

| 목적 | 1순위 | 이유 |
|---|---|---|
| 가장 빨리 PoC | **AudD** | 단순한 REST, Kotlin SDK, 300회 무료 시험, 공개 종량 가격 |
| 성능 잠재력 우선 | **ShazamKit** | Shazam 카탈로그, 잡음 환경용 음원 지문, Android 실시간 스트림 지원 |
| 커버/허밍까지 확장 | **ACRCloud** | 원곡 지문과 커버·허밍 엔진을 선택하거나 함께 사용 가능 |
| 무료 비상업 실험 | **Chromaprint + AcoustID** | 오픈소스 지문 생성, 비상업 API 무료 |
| 자체 음원 DB·연구 | **NAFP** | MIT 라이선스의 신경망 지문 모델과 Faiss 검색 예제 |
| 속도/피치 변형 자체 DB | **Panako** | 속도·타임 스트레치·피치 변화에 강하도록 설계 |

## 2. “음악 인식 AI”가 실제로 하는 일

이 기능은 장르 분류나 음성 인식이 아니라 **음원 지문(audio fingerprint) 검색** 문제이다.

```text
주변 음악 6~10초 녹음
        ↓
주파수·시간 패턴을 작은 지문/임베딩으로 변환
        ↓
수천만~수억 곡의 기준 지문 인덱스에서 근접한 구간 검색
        ↓
곡 ID → 제목·가수·앨범 메타데이터 반환
```

성능은 다음 세 요소의 곱으로 결정된다.

- **카탈로그 커버리지**: 찾는 곡과 정확한 버전이 기준 DB에 있는가
- **지문/모델의 견고성**: 대화, 반향, 블루투스 압축, 거리, 속도 변화에도 같은 곡으로 찾는가
- **검색 인프라**: 짧은 시간 안에 대규모 인덱스를 검색하고 오탐을 억제하는가

따라서 오픈소스 모델의 추론 정확도가 좋아도 기준 음원 카탈로그가 없으면 일반적인 Shazam형 서비스가 되지 않는다. 또한 원곡 인식과 사용자가 부른 허밍/커버곡 인식은 별도 문제이다. 이 프로젝트의 첫 범위는 **스피커에서 재생되는 원곡 녹음 인식**으로 잡는 것이 안전하다.

## 3. 후보 비교

### 3.1 상용 카탈로그 서비스

| 후보 | 카탈로그/방식 | 비용·무료 범위 | 도입 난이도 | 장점 | 주의점 |
|---|---|---|---|---|---|
| **ShazamKit for Android 2.1.1** | Shazam 온라인 카탈로그 또는 자체 카탈로그, 클라이언트에서 단방향 음원 지문 생성 | 공식 문서에 호출당 가격은 게시되어 있지 않음. ShazamKit 서비스와 Media ID/키 사용을 위해 Apple Developer Program이 필요하며 현재 연 **US$99** | 중간 | 방대한 Shazam 카탈로그, 실시간 `StreamingSession`, Android 지원, 원본 대신 되돌릴 수 없는 지문을 전송 | AAR 수동 배치, Apple Media ID와 서명 JWT 필요. Android SDK의 상용 사용 조건·쿼터는 출시 전에 Apple에 서면 확인 권장 |
| **AudD** | 업체 주장 1.6억+ 곡 DB, 파일/URL REST 인식 | 카드 없이 **300회 무료**, 이후 기본 **US$5/1,000회**; 대량은 공식 가격표 참고 | **낮음** | API 토큰과 multipart 업로드만으로 시작, Kotlin SDK, 제목·가수·ISRC·음악 서비스 링크 반환, 공식 문서상 보통 0.1~1.5초 응답 | 오디오 또는 파일 URL이 서버로 전송됨. 토큰을 APK에 넣으면 탈취 가능하므로 백엔드 프록시 권장 |
| **ACRCloud** | ACRCloud Music DB, 원곡 지문, 커버/라이브/허밍 엔진 | 가입 후 **14일 무료 시험**. 실제 유료 가격은 로그인 또는 문의 필요 | 중간 | 마이크 잡음용 `Recorded Audio` 설정, Android/Mobile SDK와 Identification API, 자체 DB와 오프라인 DB 가능 | 공개 가격 비교가 어려움. HMAC 서명용 `access_secret`을 APK에 넣지 말고 백엔드에서 요청하는 편이 안전 |

#### ShazamKit

Apple은 ShazamKit이 잡음이 있는 식당 같은 환경의 짧은 녹음도 음원 지문으로 대조하며, 제목과 가수 등의 메타데이터를 돌려준다고 설명한다. Android SDK는 `Session`과 연속 입력용 `StreamingSession`을 모두 제공한다. Shazam 온라인 카탈로그의 최소 쿼리 지문 길이는 3초이며, 입력은 PCM 16-bit mono, 16/32/44.1/48 kHz 중 하나를 사용할 수 있다.

- 적합도: **높음 — 최종 성능 후보**
- 권장 사용: 안경 마이크 PCM을 Android에서 받아 `StreamingSession.matchStream()`에 순차 입력
- 인증: 서버에 Apple Media Services `.p8` 개인 키를 보관하고 짧은 수명의 개발자 JWT를 발급
- 프라이버시 장점: Apple 설명상 원본 오디오가 아니라 역변환할 수 없는 서명이 전송됨
- 확인 필요: Apple 공개 문서만으로는 호출 쿼터, SLA, Android 상용 배포 범위를 확정할 수 없으므로 제품 출시 전 계약 조건 확인

공식 자료: [ShazamKit 개요](https://developer.apple.com/shazamkit/), [Android SDK 및 예제](https://developer.apple.com/shazamkit/android/index.html), [Shazam Catalog](https://developer.apple.com/shazamkit/android/shazamkit/com.shazam.shazamkit/-shazam-catalog/index.html), [Media ID와 개인 키](https://developer.apple.com/kr/help/account/capabilities/create-a-media-identifier-and-private-key/), [Apple Developer Program 가격](https://developer.apple.com/programs/whats-included/)

#### AudD

표준 엔드포인트 `POST https://api.audd.io/`에 짧은 오디오를 multipart 파일로 올리면 된다. 응답은 `artist`, `title`, `album`, `release_date`, `label`, `timecode`, `song_link`를 포함하며 선택적으로 Apple Music, Spotify, MusicBrainz 등의 메타데이터도 요청할 수 있다.

- 적합도: **매우 높음 — PoC 1순위**
- 권장 사용: 6~10초 mono 녹음을 WAV 또는 압축 오디오로 만들어 앱 백엔드에 전송하고, 백엔드가 AudD 호출
- 공개 가격: 300회 무료 시험, 기본 US$5/1,000회. 가격은 바뀔 수 있으므로 출시 시 재확인
- 표준 API 파일 한도: 10 MB
- 업체가 표시한 일반 응답 시간: 약 0.1~1.5초. 네트워크 업로드 시간은 별도

공식 자료: [AudD API 문서](https://docs.audd.io/), [AudD 가격](https://www.audd.io/), [Kotlin SDK](https://docs.audd.io/sdks/kotlin)

#### ACRCloud

ACRCloud는 마이크/잡음 녹음에 맞춘 `Recorded Audio` 입력 모드와 원본 파일용 `Line-in Audio` 모드를 구분한다. 기본 음원 지문은 동일 녹음 버전을 찾고, 커버·라이브·허밍은 별도 엔진으로 처리한다. SDK는 일반적으로 요청 한 번에 10초 클립을 사용한다.

- 적합도: **높음 — ShazamKit과 함께 성능 비교 후보**
- 권장 사용: Android SDK가 만든 지문을 전송하거나, 백엔드에서 15초 미만 오디오를 Identification API에 전송
- 무료 범위: 14일 시험
- 확장성: 자체 음원 bucket, 온라인/오프라인 자체 DB, Spotify/Deezer/ISRC 등 외부 ID 연결
- 보안: Identification API 서명에 `access_secret`이 필요하므로 서버에서 서명

공식 자료: [음악 인식 튜토리얼](https://docs.acrcloud.com/tutorials/recognize-music), [Identification API](https://docs.acrcloud.com/reference/identification-api/identification-api), [오프라인 자체 콘텐츠 인식](https://docs.acrcloud.com/tutorials/recognize-custom-content-offline)

### 3.2 무료·오픈소스 후보

| 후보 | 라이선스 | 필요한 것 | 적합한 용도 | 이 프로젝트의 한계 |
|---|---|---|---|---|
| **Chromaprint + AcoustID** | Chromaprint MIT. AcoustID 공개 서비스는 비상업 무료 | Android/서버 지문 생성기, AcoustID 앱 키 | 보유 파일 식별, 메타데이터 정리, 비상업 실험 | AcoustID는 3 req/s 제한과 비상업 조건. 짧고 시끄러운 현장 녹음을 주목적으로 한 Shazam 대체재로 보기는 어려움 |
| **Neural Audio Fingerprint (NAFP)** | MIT | TensorFlow 모델, GPU, Faiss, 합법적으로 확보한 자체 기준 음원 | 신경망 기반 대규모 자체 검색 연구 | 제공 저장소는 연구 재현 중심이며 100K 더미 DB 평가에 대용량 GPU/SSD 환경을 요구. 상용 곡 카탈로그가 없음 |
| **Panako** | AGPL-3.0, 일부 구성요소/특허 주의 | Java 서버, FFmpeg, LMDB, 자체 음원 | 속도·피치가 변형된 음악, DJ/방송 자료의 자체 DB | AGPL 의무와 명시된 특허 위험 검토 필요. 모바일 제품에 그대로 내장하기보다 연구 서버에 적합 |
| **Dejavu** | MIT | Python, MySQL/PostgreSQL, 자체 음원 | 소규모 교육용 Shazam형 PoC | 오래된 Python 중심 구현이며 대규모 운영·Android 직접 탑재용 제품은 아님 |

#### Chromaprint + AcoustID

Chromaprint는 AcoustID용 지문을 생성하는 C 라이브러리다. AcoustID는 지문을 MusicBrainz 메타데이터와 연결해 주지만 공개 서비스는 비상업 사용만 무료이며 초당 3요청 이하를 요구한다. 상업 사용은 별도 계약 대상이다. 즉 **알고리즘 코드는 무료지만 서비스 사용 조건은 별개**이다.

공식 자료: [Chromaprint 저장소](https://github.com/acoustid/chromaprint), [AcoustID Web Service와 제한](https://acoustid.org/webservice), [상업용 AcoustID](https://acoustid.biz/)

#### Neural Audio Fingerprint (NAFP)

대조학습으로 1초 단위의 신경망 지문 임베딩을 만들고 Faiss에서 검색하는 연구 모델이다. 공식 저장소는 MIT 라이선스이며 잡음, 음성, 마이크/공간 반향 증강 데이터 구성을 제공한다. 다만 논문 결과 재현에는 11 GB 이상 GPU 메모리와 최대 500 GB 이상의 SSD가 안내되어 있고, 실제 음원 카탈로그와 운영 API는 직접 만들어야 한다.

공식 자료: [NAFP 공식 구현](https://github.com/mimbres/neural-audio-fp), [논문](https://arxiv.org/abs/2010.11910)

#### Panako와 Dejavu

Panako는 속도, 타임 스트레치, 피치가 달라진 짧은 조각을 자체 DB에서 찾는 데 강점이 있다. 저장소가 AGPL과 관련 특허 주의를 명시하므로 폐쇄형 상용 서비스에서는 법무 검토가 필요하다. Dejavu는 소규모 DB에서 음원 지문의 원리를 빠르게 확인하기 좋은 MIT Python 프로젝트지만, 현재 제품의 주 엔진으로 추천하지 않는다.

공식 자료: [Panako](https://github.com/JorenSix/Panako), [Dejavu](https://github.com/worldveil/dejavu)

## 4. Meta Ray-Ban Display 프로젝트 도입 구조

### 4.1 중요한 SDK 경계

DAT의 `Session`, `Stream`, `Display`는 각각 연결, 카메라, 화면 기능이다. 음악 인식 오디오는 카메라의 `videoStream`에서 얻는 것이 아니라 **Android 표준 오디오 입력**으로 받아야 한다. Meta의 공개 Android 저장소도 DAT의 대표 기능을 비디오 스트리밍과 사진 캡처로 설명하며, 오디오는 Bluetooth 입력 장치로 라우팅하는 Android API를 사용한다.

Android 공식 가이드는 연결된 입력 장치를 `AudioManager.getDevices(GET_DEVICES_INPUTS)`로 찾고, `AudioRecord.setPreferredDevice()`로 Bluetooth/BLE headset 입력을 지정하는 방식을 제공한다. 실제 Ray-Ban Display 펌웨어와 휴대폰 조합에서 안경 마이크가 선택되는지는 반드시 실기기에서 확인한다. 라우팅 실패 시 휴대폰 마이크로 명시적으로 폴백하고 UI에 현재 입력 장치를 표시한다.

공식 자료: [Meta DAT Android](https://github.com/facebook/meta-wearables-dat-android), [Android Bluetooth 오디오 녹음](https://developer.android.com/develop/connectivity/bluetooth/ble-audio/audio-recording), [Android BLE Audio 개요](https://developer.android.com/develop/connectivity/bluetooth/ble-audio/overview)

### 4.2 권장 아키텍처

```text
[Meta Ray-Ban Display 마이크]
       │ Bluetooth audio route
       ▼
[Android AudioRecord]
  - PCM 16-bit mono
  - 6~10초 수집
  - 녹음 중 표시/취소
       │
       ├─ ShazamKit: 앱에서 지문 생성 → Shazam catalog
       │
       └─ AudD/ACRCloud: HTTPS → 우리 백엔드 → 인식 업체 API
                                      │
                                      ▼
                              정규화된 RecognitionResult
                                      │
                                      ▼
                         [DAT Display: 제목 · 가수 · 상태]
```

`RecognitionResult`는 업체 교체가 쉽도록 공통 타입으로 감싼다.

```kotlin
data class RecognitionResult(
    val title: String,
    val artist: String,
    val album: String? = null,
    val artworkUrl: String? = null,
    val provider: RecognitionProvider,
    val confidence: Double? = null,
)

enum class RecognitionProvider { AUDD, SHAZAMKIT, ACRCLOUD }

interface MusicRecognizer {
    suspend fun recognize(pcm: ByteArray, sampleRateHz: Int): Result<RecognitionResult?>
}
```

### 4.3 Android 권한

```xml
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
<uses-permission android:name="android.permission.MODIFY_AUDIO_SETTINGS" />
<uses-permission android:name="android.permission.INTERNET" />
```

- `RECORD_AUDIO`는 Android 런타임 권한으로도 요청한다.
- 녹음은 사용자가 인식 버튼을 누른 동안만 수행하고, 성공·실패·취소 즉시 `AudioRecord.stop()`과 `release()`를 호출한다.
- 백그라운드에서 계속 녹음하지 않는다. 백그라운드 녹음이 제품 요구사항이 되면 foreground service와 최신 Play 정책을 별도로 검토한다.

### 4.4 오디오 캡처 권장값

- 캡처 길이: **초기 8초**, 실패하면 최대 12초까지 한 번 재시도
- 형식: PCM 16-bit mono
- 샘플레이트: 실제 Bluetooth 입력이 지원하는 값으로 녹음하고 엔진 요구값으로 리샘플링
- ShazamKit: 지원 값 16/32/44.1/48 kHz 중 하나, 최소 3초
- 네트워크 API: 6~10초를 WAV 또는 API가 지원하는 압축 포맷으로 전송
- 무음/VAD: 시작 1초의 RMS가 임계치 미만이면 네트워크 요청을 보내지 않고 “음악이 잘 들리지 않아요” 표시
- 중복 억제: 동일 결과를 30초 동안 캐시해 반복 과금과 화면 깜빡임 방지

## 5. 서비스별 도입 방법

### 5.1 1차 PoC: AudD

#### 백엔드

1. AudD Dashboard에서 시험 API 토큰을 발급한다.
2. 토큰은 Android 앱이나 Git 저장소에 넣지 않고 백엔드 secret manager에 저장한다.
3. 앱이 `POST /v1/music-recognitions`에 녹음 파일을 보내면 백엔드가 AudD를 호출한다.
4. 업체 응답을 공통 `RecognitionResult` JSON으로 축약해 앱에 반환한다.
5. 사용자/IP rate limit, 10초 timeout, 요청 ID, 비용 지표를 추가한다. 원본 녹음은 응답 후 즉시 폐기한다.

AudD 호출 형태:

```bash
curl https://api.audd.io/ \
  -F api_token="$AUDD_API_TOKEN" \
  -F file=@sample.wav \
  -F return=apple_music,spotify
```

예상 앱 상태:

```text
IDLE → ROUTING_AUDIO → LISTENING → RECOGNIZING → MATCHED
                                            └→ NO_MATCH / ERROR
```

Display에는 긴 오류 전문 대신 다음 정도만 보여준다.

- 듣는 중: `음악을 듣고 있어요…`
- 성공: 1행 제목, 2행 가수
- 미인식: `곡을 찾지 못했어요. 소리에 조금 더 가까이 가보세요.`
- 네트워크 오류: `인터넷 연결 후 다시 시도해 주세요.`

### 5.2 성능 후보: ShazamKit Android

1. Apple Developer Program에 가입한다.
2. 앱별 Media ID를 만들고 ShazamKit이 활성화된 Media Services 개인 키를 생성한다.
3. Android용 ShazamKit AAR을 내려받아 프로젝트의 `libs`에 추가한다.
4. 서버가 `.p8`로 짧은 수명의 개발자 JWT를 발급하고 앱은 `DeveloperTokenProvider`를 통해 가져온다. **개인 키를 APK에 포함하지 않는다.**
5. `createShazamCatalog()`와 `createStreamingSession()`을 만든다.
6. `AudioRecord`에서 읽은 PCM 16-bit mono chunk를 시간 순서대로 `matchStream()`에 넣는다.
7. `recognitionResults()` Flow에서 `Match`, `NoMatch`, `Error`를 typed branch로 처리한다.
8. 성공 시 `MatchedMediaItem.title`, `artist`, artwork/URL을 공통 결과로 변환한다.

핵심 형태:

```kotlin
val catalog = ShazamKit.createShazamCatalog(developerTokenProvider, Locale.KOREA)
val sessionResult = ShazamKit.createStreamingSession(
    catalog = catalog,
    audioSampleRateInHz = AudioSampleRateInHz.SAMPLE_RATE_32000,
    audioRecordReadBufferSize = bufferSize,
)

// ShazamKitResult.Success인지 확인한 뒤에만 사용한다.
// AudioRecord.read() 결과의 실제 길이만 matchStream()에 전달한다.
```

공식 샘플은 성공 경로를 단순화하므로 제품 코드에서는 모든 `ShazamKitResult.Failure`, 인증 만료, 네트워크 오류, 취소를 처리해야 한다.

### 5.3 ACRCloud

1. Console에서 `Audio & Video Recognition` 프로젝트를 만든다.
2. `ACRCloud Music` bucket을 연결하고 Audio Source를 **Recorded Audio**로 설정한다.
3. 첫 범위에서는 `Audio Fingerprinting`만 활성화한다. 허밍이 필요해질 때 별도 엔진을 비교한다.
4. `host`, `access_key`, `access_secret`을 백엔드 secret으로 저장한다.
5. 백엔드에서 HMAC 서명을 만들고 15초 미만 녹음 또는 SDK가 만든 fingerprint를 Identification API에 보낸다.
6. `metadata.music[0].title`, `artists`, `score`, 외부 ID를 공통 결과로 변환한다.

대역폭과 프라이버시를 줄여야 하면 ACRCloud Mobile SDK에서 지문을 만든 뒤 지문만 서버로 보내는 방식을 우선 검토한다.

### 5.4 자체 모델: NAFP 또는 Panako

이 경로는 “우리에게 권리가 있는 제한된 음원 목록”만 인식해도 될 때 선택한다.

1. 기준 음원과 제목·가수 메타데이터 사용 권리를 확보한다.
2. 모든 기준 음원에서 지문/임베딩을 미리 생성한다.
3. NAFP는 Faiss, Panako는 제공 저장소를 이용해 검색 인덱스를 구축한다.
4. Android는 6~10초 샘플 또는 지문을 자체 API로 전송한다.
5. 서버는 top-k 검색 뒤 시간 정렬 일관성과 점수 임계치를 검사한다.
6. 모르는 곡을 억지로 반환하지 않도록 `NO_MATCH` threshold를 실제 잡음 데이터로 보정한다.
7. 새 음원 등록, 삭제, 인덱스 버전, 롤백 파이프라인을 운영한다.

완전 무료라는 표현에는 서버/GPU/저장소 비용과 기준 음원 라이선스 비용이 빠져 있다. 수천 곡 이하의 폐쇄형 카탈로그가 아니라면 초기 제품에는 상용 API가 대체로 더 경제적이다.

## 6. 성능 검증 계획

업체의 자체 정확도 문구 대신 프로젝트 환경에서 다음 방식으로 비교한다.

### 데이터셋

- 테스트 곡 200곡 이상: K-pop 최신곡/구곡, 해외 팝, 인디, 클래식, 라이브 버전
- 곡별 3개 구간: 인트로, 보컬 구간, 간주
- 조건별 녹음:
  - 조용한 방 1 m
  - 카페 소음
  - 사용자 대화가 겹침
  - 스피커에서 3 m
  - 작은 음량
  - Meta Ray-Ban Display 마이크와 휴대폰 마이크 각각
- 동일한 원본 녹음 파일을 모든 서비스에 입력해 공정하게 비교

### 지표

| 지표 | 목표 예시 |
|---|---|
| Top-1 정확도 | 전체 90% 이상, 핵심 K-pop 세트 95% 이상 |
| 오탐률(false positive) | 무음/대화/미등록곡에서 1% 미만 |
| p50 전체 지연 | 3초 미만(녹음 시간 제외) |
| p95 전체 지연 | 5초 미만(녹음 시간 제외) |
| 1회 성공까지 평균 녹음 길이 | 10초 이하 |
| 비용 | 월 활성 사용자·인식 횟수 시나리오별 계산 |

### 판정 규칙

- 제목 문자열만 비교하지 말고 ISRC 또는 서비스 곡 ID를 우선 사용한다.
- 리마스터, 라이브, sped-up 버전은 제품 정책에 따라 정답 허용 범위를 미리 정한다.
- 결과가 없으면 실패이지만, 틀린 곡을 자신 있게 보여주는 오탐에는 더 큰 페널티를 준다.
- 서비스가 `confidence`를 주지 않으면 자체적으로 값을 만들어 표시하지 않는다.
- 최종 선택은 정확도 50%, 지연 20%, 비용 15%, 도입/운영 10%, 개인정보 5%처럼 가중 점수화한다.

## 7. 예상 비용 계산 예시

AudD 기본 공개 단가 US$5/1,000회를 그대로 적용한 단순 예시다. 세금, 환율, 재시도, 할인은 제외한다.

| 월 인식 요청 | API 비용 예시 |
|---:|---:|
| 10,000회 | US$50 |
| 100,000회 | 공개 월 플랜 US$450 수준 |
| 200,000회 | 공개 월 플랜 US$800 수준 |
| 500,000회 | 공개 월 플랜 US$1,800 수준 |

실제 비용 모델에는 실패 재시도, 개발/QA 트래픽, 악용 요청을 포함한다. ShazamKit과 ACRCloud는 공개 정보만으로 동일한 호출당 비교가 어려우므로 예상 트래픽을 전달해 서면 견적과 쿼터를 받아야 한다.

## 8. 개인정보·보안 체크리스트

- 인식 버튼을 누른 뒤에만 마이크를 켜고 화면에 녹음 상태를 명확히 표시
- 원본 오디오는 인식 완료 또는 timeout 직후 삭제; 기본적으로 서버 저장·로그 저장 금지
- AudD/ACRCloud에 오디오가 전달된다는 사실과 처리 목적을 개인정보 처리방침에 고지
- 모든 업체 토큰, ACRCloud secret, Apple `.p8`은 APK와 Git에 넣지 않음
- 백엔드에서 사용자별 rate limit, 파일 크기/길이 제한, MIME 검증 적용
- 로그에는 원본 오디오와 전체 업체 응답 대신 요청 ID, 지연, 결과 유무, 오류 코드만 기록
- 이용 국가의 주변 대화 녹음·동의 관련 법률과 앱스토어 마이크 정책 검토
- 공급업체 데이터 보존 기간, 학습 이용 여부, 처리 지역, 삭제 정책을 출시 전 계약서로 확인

## 9. 실행 로드맵

### 1단계: 휴대폰 음악 인식 프로토타입

- 휴대폰의 `AudioRecord`로 8초간 주변 음악을 녹음한다.
- AudD 백엔드 프록시와 공통 `MusicRecognizer` 인터페이스를 구현한다.
- 휴대폰 화면에 곡 제목과 가수, 듣는 중, 검색 중, 미인식, 오류 상태를 표시한다.
- 테스트 WAV와 fake recognizer로 상태 전이를 자동 테스트한다.
- 실제 휴대폰에서 `버튼 → 녹음 → AudD → 제목/가수 표시` 흐름을 완성한다.

### 2단계: MockDeviceKit 기반 DAT 검증

- `DisplayAccess`에 MockDeviceKit 의존성과 debug 전용 진입점을 추가한다.
- MockDeviceKit으로 등록, 페어링, 전원, 착용, 연결, 권한과 session 상태를 재현한다.
- 휴대폰 PoC의 인식 코어를 그대로 사용하고 출력만 `DisplayGateway`로 분리한다.
- `FakeDisplayGateway`로 Display 콘텐츠를 검증하고, SDK가 mock display를 지원할 때만 해당 기능까지 연결한다.
- Mock 테스트와 `assembleDebug`, `test`, `lint`를 통과해야 실기기 단계로 이동한다.

### 3단계: 실제 스마트글래스 연결

- 실제 Ray-Ban Display에서 Android `AudioRecord` 입력 장치로 안경 마이크가 선택되는지 확인한다.
- 휴대폰 PoC의 `PhoneAudioInput`을 안경 입력을 우선하는 `BluetoothAudioInput`으로 교체하고 휴대폰 마이크 폴백을 유지한다.
- 실제 DAT `Session`과 `Display`를 연결해 제목과 가수를 표시한다.
- 연결 해제, 안경 접기/벗기, 권한 거부, 네트워크 단절 시 자원 정리와 복구를 확인한다.

### 4단계: 실환경 후보 비교와 제품 결정

- ShazamKit Android adapter와 ACRCloud trial adapter를 추가한다.
- 최소 200곡을 조용한 방, 카페 소음, 대화 중첩, 거리, 작은 음량 조건에서 실기기로 녹음한다.
- 동일한 녹음 파일로 정확도, 오탐, p50/p95 지연, 비용을 비교한다.
- 가중 점수와 월 트래픽별 견적, 약관, 개인정보, 상용 사용 범위를 검토해 공급자를 정한다.
- 주 공급자와 장애 UX를 결정한다. 이중 공급자 자동 재시도는 호출 비용이 거의 두 배가 될 수 있으므로 기본값으로 두지 않는다.

## 최종 제안

현재 단계에서는 **휴대폰과 AudD로 기능과 사용자 경험을 가장 먼저 완성**하는 것이 합리적이다. 그다음 MockDeviceKit으로 DAT 장치·session·Display 상태를 검증한 후 실제 스마트글래스로 확장한다. 동시에 같은 테스트 녹음을 보관해 **ShazamKit을 성능 기준 후보로, ACRCloud를 커버/허밍 확장 후보로 비교**한다. 무료 오픈소스는 글로벌 최신곡 인식의 비용 절감책이 아니라, 제한된 자체 카탈로그나 연구용 대안으로 취급한다.
