package com.meta.wearable.dat.externalsampleapps.displayaccess.music.network

import com.meta.wearable.dat.externalsampleapps.displayaccess.music.domain.AudioSample
import java.io.ByteArrayOutputStream

object WavEncoder {
  fun encode(sample: AudioSample): ByteArray {
    val pcm = sample.pcm16Le
    val output = ByteArrayOutputStream(44 + pcm.size)
    output.ascii("RIFF")
    output.intLe(36 + pcm.size)
    output.ascii("WAVEfmt ")
    output.intLe(16)
    output.shortLe(1)
    output.shortLe(sample.channelCount)
    output.intLe(sample.sampleRateHz)
    output.intLe(sample.sampleRateHz * sample.channelCount * 2)
    output.shortLe(sample.channelCount * 2)
    output.shortLe(16)
    output.ascii("data")
    output.intLe(pcm.size)
    output.write(pcm)
    return output.toByteArray()
  }

  private fun ByteArrayOutputStream.ascii(value: String) = write(value.encodeToByteArray())
  private fun ByteArrayOutputStream.shortLe(value: Int) {
    write(value and 0xff)
    write((value ushr 8) and 0xff)
  }
  private fun ByteArrayOutputStream.intLe(value: Int) {
    shortLe(value)
    shortLe(value ushr 16)
  }
}
