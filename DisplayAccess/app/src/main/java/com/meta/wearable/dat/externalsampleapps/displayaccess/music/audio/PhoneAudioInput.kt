package com.meta.wearable.dat.externalsampleapps.displayaccess.music.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import com.meta.wearable.dat.externalsampleapps.displayaccess.music.domain.AudioCaptureOutcome
import com.meta.wearable.dat.externalsampleapps.displayaccess.music.domain.AudioInput
import com.meta.wearable.dat.externalsampleapps.displayaccess.music.domain.AudioSample
import com.meta.wearable.dat.externalsampleapps.displayaccess.music.domain.InvalidAudioReason
import com.meta.wearable.dat.externalsampleapps.displayaccess.music.domain.MusicRecognitionError
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.sqrt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Captures PCM 16-bit mono from the phone microphone and always releases AudioRecord. */
class PhoneAudioInput(
    private val silenceRmsThreshold: Double = 128.0,
) : AudioInput {
  companion object {
    private const val TAG = "MusicAudioInput"
    const val RETRY_DURATION_MILLIS = 12_000L
    private val SAMPLE_RATES = intArrayOf(44_100, 48_000, 16_000, 8_000)
  }

  private val cancelled = AtomicBoolean(false)
  private val recordLock = Any()
  private var activeRecord: AudioRecord? = null

  override suspend fun capture(durationMillis: Long): AudioCaptureOutcome =
      withContext(Dispatchers.IO) {
        cancelled.set(false)
        val first = captureOnce(durationMillis)
        if (first is AudioCaptureOutcome.Captured && isSilent(first.sample)) {
          captureOnce(RETRY_DURATION_MILLIS).let { retry ->
            if (retry is AudioCaptureOutcome.Captured && isSilent(retry.sample)) {
              AudioCaptureOutcome.Failure(
                  MusicRecognitionError.InvalidAudio(InvalidAudioReason.SILENT))
            } else {
              retry
            }
          }
        } else {
          first
        }
      }

  @SuppressLint("MissingPermission")
  private fun captureOnce(durationMillis: Long): AudioCaptureOutcome {
    val sampleRate = findSupportedSampleRate()
        ?: return AudioCaptureOutcome.Failure(
            MusicRecognitionError.AudioCapture("No supported mono PCM sample rate"))
    val minBuffer = AudioRecord.getMinBufferSize(
        sampleRate,
        AudioFormat.CHANNEL_IN_MONO,
        AudioFormat.ENCODING_PCM_16BIT,
    )
    val bufferSize = maxOf(minBuffer, 4_096)
    var recorder: AudioRecord? = null
    return try {
      recorder = AudioRecord(
          MediaRecorder.AudioSource.MIC,
          sampleRate,
          AudioFormat.CHANNEL_IN_MONO,
          AudioFormat.ENCODING_PCM_16BIT,
          bufferSize,
      )
      if (recorder.state != AudioRecord.STATE_INITIALIZED) {
        return AudioCaptureOutcome.Failure(
            MusicRecognitionError.AudioCapture("AudioRecord initialization failed"))
      }
      synchronized(recordLock) { activeRecord = recorder }
      recorder.startRecording()

      val targetBytes = (sampleRate * 2L * durationMillis / 1_000L).toInt()
      val output = ByteArrayOutputStream(targetBytes)
      val buffer = ByteArray(bufferSize)
      while (output.size() < targetBytes && !cancelled.get()) {
        val read = recorder.read(
            buffer,
            0,
            minOf(buffer.size, targetBytes - output.size()),
            AudioRecord.READ_BLOCKING,
        )
        when {
          read > 0 -> output.write(buffer, 0, read)
          read == 0 -> Unit
          else -> return AudioCaptureOutcome.Failure(
              MusicRecognitionError.AudioCapture("AudioRecord read failed: $read"))
        }
      }
      if (cancelled.get()) {
        AudioCaptureOutcome.Cancelled
      } else {
        val sample = AudioSample(output.toByteArray(), sampleRate, 1)
        val metrics = calculateSignalMetrics(sample)
        Log.i(
            TAG,
            "Captured durationMs=${sample.durationMillis} sampleRateHz=$sampleRate " +
                "rms=${metrics.rms.toInt()} peak=${metrics.peak} bytes=${sample.pcm16Le.size}",
        )
        AudioCaptureOutcome.Captured(sample)
      }
    } catch (security: SecurityException) {
      AudioCaptureOutcome.Failure(MusicRecognitionError.PermissionDenied)
    } catch (error: Throwable) {
      if (cancelled.get()) AudioCaptureOutcome.Cancelled
      else AudioCaptureOutcome.Failure(MusicRecognitionError.AudioCapture(error.message))
    } finally {
      synchronized(recordLock) {
        if (activeRecord === recorder) activeRecord = null
      }
      runCatching {
        if (recorder?.recordingState == AudioRecord.RECORDSTATE_RECORDING) recorder.stop()
      }
      recorder?.release()
    }
  }

  override fun cancel() {
    cancelled.set(true)
    val recorder = synchronized(recordLock) { activeRecord }
    runCatching {
      if (recorder?.recordingState == AudioRecord.RECORDSTATE_RECORDING) recorder.stop()
    }
  }

  private fun findSupportedSampleRate(): Int? = SAMPLE_RATES.firstOrNull { rate ->
    AudioRecord.getMinBufferSize(
        rate,
        AudioFormat.CHANNEL_IN_MONO,
        AudioFormat.ENCODING_PCM_16BIT,
    ) > 0
  }

  private fun isSilent(sample: AudioSample): Boolean {
    return calculateSignalMetrics(sample).rms < silenceRmsThreshold
  }

  private fun calculateSignalMetrics(sample: AudioSample): SignalMetrics {
    if (sample.pcm16Le.isEmpty()) return SignalMetrics(rms = 0.0, peak = 0)
    var sum = 0.0
    var count = 0
    var peak = 0
    var index = 0
    while (index + 1 < sample.pcm16Le.size) {
      val value = (sample.pcm16Le[index].toInt() and 0xff) or
          (sample.pcm16Le[index + 1].toInt() shl 8)
      sum += value.toDouble() * value
      peak = maxOf(peak, kotlin.math.abs(value))
      count += 1
      index += 2
    }
    return if (count == 0) {
      SignalMetrics(rms = 0.0, peak = 0)
    } else {
      SignalMetrics(rms = sqrt(sum / count), peak = peak)
    }
  }

  private data class SignalMetrics(val rms: Double, val peak: Int)
}
