package com.meta.wearable.dat.externalsampleapps.displayaccess.music

import com.meta.wearable.dat.externalsampleapps.displayaccess.music.domain.AudioCaptureOutcome
import com.meta.wearable.dat.externalsampleapps.displayaccess.music.domain.AudioInput
import com.meta.wearable.dat.externalsampleapps.displayaccess.music.domain.AudioSample
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

data class WavFixture(val name: String, val bytes: ByteArray) {
  val sha256: String =
      MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}

object TestAudioFixtures {
  private const val SAMPLE_RATE_HZ = 8_000

  fun normal(): WavFixture = sine("normal.wav", durationMillis = 4_000)

  fun silence(): WavFixture =
      wav("silence.wav", ShortArray(SAMPLE_RATE_HZ * 4), SAMPLE_RATE_HZ)

  fun noise(): WavFixture {
    val random = Random(42)
    return wav(
        "noise.wav",
        ShortArray(SAMPLE_RATE_HZ * 4) { random.nextInt(-8_000, 8_001).toShort() },
        SAMPLE_RATE_HZ,
    )
  }

  fun tooShort(): WavFixture = sine("too-short.wav", durationMillis = 1_000)

  fun clipped(): WavFixture =
      wav("clipped.wav", ShortArray(SAMPLE_RATE_HZ * 4) { Short.MAX_VALUE }, SAMPLE_RATE_HZ)

  private fun sine(name: String, durationMillis: Int): WavFixture {
    val sampleCount = SAMPLE_RATE_HZ * durationMillis / 1_000
    val samples =
        ShortArray(sampleCount) { index ->
          (sin(2.0 * PI * 440.0 * index / SAMPLE_RATE_HZ) * 8_000).toInt().toShort()
        }
    return wav(name, samples, SAMPLE_RATE_HZ)
  }

  private fun wav(name: String, samples: ShortArray, sampleRateHz: Int): WavFixture {
    val pcmSize = samples.size * 2
    val output = ByteArrayOutputStream(44 + pcmSize)
    output.writeAscii("RIFF")
    output.writeIntLe(36 + pcmSize)
    output.writeAscii("WAVE")
    output.writeAscii("fmt ")
    output.writeIntLe(16)
    output.writeShortLe(1)
    output.writeShortLe(1)
    output.writeIntLe(sampleRateHz)
    output.writeIntLe(sampleRateHz * 2)
    output.writeShortLe(2)
    output.writeShortLe(16)
    output.writeAscii("data")
    output.writeIntLe(pcmSize)
    samples.forEach(output::writeShortLe)
    return WavFixture(name, output.toByteArray())
  }
}

class FixtureAudioInput(private val fixture: WavFixture) : AudioInput {
  var captureCount: Int = 0
    private set

  val requestedDurationsMillis = mutableListOf<Long>()

  var cancelCount: Int = 0
    private set

  override suspend fun capture(durationMillis: Long): AudioCaptureOutcome {
    captureCount += 1
    requestedDurationsMillis += durationMillis
    return AudioCaptureOutcome.Captured(Pcm16MonoWav.decode(fixture.bytes))
  }

  override fun cancel() {
    cancelCount += 1
  }
}

private object Pcm16MonoWav {
  fun decode(bytes: ByteArray): AudioSample {
    require(bytes.size >= 44 && bytes.copyOfRange(0, 4).decodeToString() == "RIFF")
    require(bytes.copyOfRange(8, 12).decodeToString() == "WAVE")
    require(readShortLe(bytes, 20) == 1) { "Only PCM fixtures are supported" }
    val channelCount = readShortLe(bytes, 22)
    val sampleRateHz = readIntLe(bytes, 24)
    require(readShortLe(bytes, 34) == 16) { "Only PCM 16-bit fixtures are supported" }
    val pcmSize = readIntLe(bytes, 40)
    require(44 + pcmSize <= bytes.size)
    return AudioSample(
        pcm16Le = bytes.copyOfRange(44, 44 + pcmSize),
        sampleRateHz = sampleRateHz,
        channelCount = channelCount,
    )
  }

  private fun readShortLe(bytes: ByteArray, offset: Int): Int =
      (bytes[offset].toInt() and 0xff) or ((bytes[offset + 1].toInt() and 0xff) shl 8)

  private fun readIntLe(bytes: ByteArray, offset: Int): Int =
      (bytes[offset].toInt() and 0xff) or
          ((bytes[offset + 1].toInt() and 0xff) shl 8) or
          ((bytes[offset + 2].toInt() and 0xff) shl 16) or
          ((bytes[offset + 3].toInt() and 0xff) shl 24)
}

private fun ByteArrayOutputStream.writeAscii(value: String) = write(value.encodeToByteArray())

private fun ByteArrayOutputStream.writeShortLe(value: Int) {
  write(value and 0xff)
  write((value ushr 8) and 0xff)
}

private fun ByteArrayOutputStream.writeShortLe(value: Short) = writeShortLe(value.toInt())

private fun ByteArrayOutputStream.writeIntLe(value: Int) {
  write(value and 0xff)
  write((value ushr 8) and 0xff)
  write((value ushr 16) and 0xff)
  write((value ushr 24) and 0xff)
}
