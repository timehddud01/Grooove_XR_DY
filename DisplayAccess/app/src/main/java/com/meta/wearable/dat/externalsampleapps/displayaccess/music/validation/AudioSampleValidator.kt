package com.meta.wearable.dat.externalsampleapps.displayaccess.music.validation

import com.meta.wearable.dat.externalsampleapps.displayaccess.music.domain.AudioSample
import com.meta.wearable.dat.externalsampleapps.displayaccess.music.domain.InvalidAudioReason
import com.meta.wearable.dat.externalsampleapps.displayaccess.music.domain.MusicRecognitionError
import kotlin.math.sqrt

class AudioSampleValidator(
    private val minimumDurationMillis: Long = 3_000,
    private val silenceRmsThreshold: Double = 128.0,
    private val clippingAmplitude: Int = 32_700,
    private val maximumClippedSampleRatio: Double = 0.05,
) {
  fun validate(sample: AudioSample): MusicRecognitionError.InvalidAudio? {
    if (sample.pcm16Le.isEmpty()) {
      return MusicRecognitionError.InvalidAudio(InvalidAudioReason.EMPTY)
    }
    if (sample.pcm16Le.size % 2 != 0) {
      return MusicRecognitionError.InvalidAudio(InvalidAudioReason.UNSUPPORTED_FORMAT)
    }
    if (sample.durationMillis < minimumDurationMillis) {
      return MusicRecognitionError.InvalidAudio(InvalidAudioReason.TOO_SHORT)
    }
    val statistics = calculateStatistics(sample.pcm16Le)
    if (statistics.rms < silenceRmsThreshold) {
      return MusicRecognitionError.InvalidAudio(InvalidAudioReason.SILENT)
    }
    if (statistics.clippedSampleRatio > maximumClippedSampleRatio) {
      return MusicRecognitionError.InvalidAudio(InvalidAudioReason.DISTORTED)
    }
    return null
  }

  private fun calculateStatistics(pcm16Le: ByteArray): AudioStatistics {
    var sumOfSquares = 0.0
    var sampleCount = 0
    var clippedSampleCount = 0
    var index = 0
    while (index + 1 < pcm16Le.size) {
      val low = pcm16Le[index].toInt() and 0xff
      val high = pcm16Le[index + 1].toInt()
      val value = (high shl 8) or low
      sumOfSquares += value.toDouble() * value
      if (kotlin.math.abs(value) >= clippingAmplitude) clippedSampleCount += 1
      sampleCount += 1
      index += 2
    }
    if (sampleCount == 0) return AudioStatistics(rms = 0.0, clippedSampleRatio = 0.0)
    return AudioStatistics(
        rms = sqrt(sumOfSquares / sampleCount),
        clippedSampleRatio = clippedSampleCount.toDouble() / sampleCount,
    )
  }

  private data class AudioStatistics(
      val rms: Double,
      val clippedSampleRatio: Double,
  )
}
