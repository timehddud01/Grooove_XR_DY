package com.meta.wearable.dat.externalsampleapps.displayaccess.music.domain

import kotlin.math.max

/** Raw, interleaved little-endian PCM 16-bit audio captured for recognition. */
data class AudioSample(
    val pcm16Le: ByteArray,
    val sampleRateHz: Int,
    val channelCount: Int = 1,
) {
  init {
    require(sampleRateHz > 0) { "sampleRateHz must be positive" }
    require(channelCount > 0) { "channelCount must be positive" }
  }

  val durationMillis: Long
    get() {
      val bytesPerFrame = 2L * channelCount
      return if (pcm16Le.isEmpty()) {
        0
      } else {
        max(1L, pcm16Le.size * 1_000L / bytesPerFrame / sampleRateHz)
      }
    }

  override fun equals(other: Any?): Boolean =
      this === other ||
          (other is AudioSample &&
              pcm16Le.contentEquals(other.pcm16Le) &&
              sampleRateHz == other.sampleRateHz &&
              channelCount == other.channelCount)

  override fun hashCode(): Int {
    var result = pcm16Le.contentHashCode()
    result = 31 * result + sampleRateHz
    result = 31 * result + channelCount
    return result
  }
}

enum class RecognitionProvider {
  SHAZAM_KIT,
  ACRCLOUD,
  CUSTOM,
}

data class RecognitionResult(
    val title: String,
    val artist: String,
    val album: String? = null,
    val artworkUrl: String? = null,
    val provider: RecognitionProvider,
) {
  init {
    require(title.isNotBlank()) { "title must not be blank" }
    require(artist.isNotBlank()) { "artist must not be blank" }
  }
}

enum class InvalidAudioReason {
  EMPTY,
  TOO_SHORT,
  SILENT,
  DISTORTED,
  UNSUPPORTED_FORMAT,
}

sealed interface MusicRecognitionError {
  data object PermissionDenied : MusicRecognitionError

  data object Timeout : MusicRecognitionError

  data object Authentication : MusicRecognitionError

  data class RateLimited(val retryAfterSeconds: Long? = null) : MusicRecognitionError

  data class Network(val message: String? = null) : MusicRecognitionError

  data class InvalidAudio(val reason: InvalidAudioReason) : MusicRecognitionError

  data class AudioCapture(val message: String? = null) : MusicRecognitionError

  data class Provider(val code: String? = null, val message: String? = null) :
      MusicRecognitionError

  data class Unexpected(val message: String? = null) : MusicRecognitionError
}

sealed interface AudioCaptureOutcome {
  data class Captured(val sample: AudioSample) : AudioCaptureOutcome

  data class Failure(val error: MusicRecognitionError) : AudioCaptureOutcome

  data object Cancelled : AudioCaptureOutcome
}

sealed interface RecognitionOutcome {
  data class Match(val result: RecognitionResult) : RecognitionOutcome

  data object NoMatch : RecognitionOutcome

  data class Failure(val error: MusicRecognitionError) : RecognitionOutcome
}

enum class MusicRecognitionStatus {
  IDLE,
  LISTENING,
  RECOGNIZING,
  MATCHED,
  NO_MATCH,
  ERROR,
}

sealed interface MusicRecognitionUiState {
  val status: MusicRecognitionStatus

  data object Idle : MusicRecognitionUiState {
    override val status = MusicRecognitionStatus.IDLE
  }

  data object Listening : MusicRecognitionUiState {
    override val status = MusicRecognitionStatus.LISTENING
  }

  data object Recognizing : MusicRecognitionUiState {
    override val status = MusicRecognitionStatus.RECOGNIZING
  }

  data class Matched(val result: RecognitionResult) : MusicRecognitionUiState {
    override val status = MusicRecognitionStatus.MATCHED
  }

  data object NoMatch : MusicRecognitionUiState {
    override val status = MusicRecognitionStatus.NO_MATCH
  }

  data class Error(val error: MusicRecognitionError) : MusicRecognitionUiState {
    override val status = MusicRecognitionStatus.ERROR
  }
}
