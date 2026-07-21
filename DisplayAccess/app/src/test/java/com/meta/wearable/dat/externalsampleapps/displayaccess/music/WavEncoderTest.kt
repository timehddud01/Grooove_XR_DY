package com.meta.wearable.dat.externalsampleapps.displayaccess.music

import com.meta.wearable.dat.externalsampleapps.displayaccess.music.domain.AudioSample
import com.meta.wearable.dat.externalsampleapps.displayaccess.music.network.WavEncoder
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class WavEncoderTest {
  @Test
  fun encodesPcm16MonoWithCorrectWavHeader() {
    val pcm = byteArrayOf(1, 2, 3, 4)
    val wav = WavEncoder.encode(AudioSample(pcm, sampleRateHz = 8_000))

    assertEquals("RIFF", wav.copyOfRange(0, 4).decodeToString())
    assertEquals("WAVE", wav.copyOfRange(8, 12).decodeToString())
    assertEquals(8_000, readIntLe(wav, 24))
    assertEquals(4, readIntLe(wav, 40))
    assertArrayEquals(pcm, wav.copyOfRange(44, 48))
  }

  private fun readIntLe(bytes: ByteArray, offset: Int): Int =
      (bytes[offset].toInt() and 0xff) or
          ((bytes[offset + 1].toInt() and 0xff) shl 8) or
          ((bytes[offset + 2].toInt() and 0xff) shl 16) or
          ((bytes[offset + 3].toInt() and 0xff) shl 24)
}
