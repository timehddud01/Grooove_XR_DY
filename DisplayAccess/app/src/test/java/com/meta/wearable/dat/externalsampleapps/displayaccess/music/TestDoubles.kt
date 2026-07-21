package com.meta.wearable.dat.externalsampleapps.displayaccess.music

import com.meta.wearable.dat.externalsampleapps.displayaccess.music.domain.AudioSample
import com.meta.wearable.dat.externalsampleapps.displayaccess.music.domain.DisplayGateway
import com.meta.wearable.dat.externalsampleapps.displayaccess.music.domain.MusicRecognitionUiState
import com.meta.wearable.dat.externalsampleapps.displayaccess.music.domain.MusicRecognizer
import com.meta.wearable.dat.externalsampleapps.displayaccess.music.domain.RecognitionOutcome
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class FakeMusicRecognizer(
    private val outcome: RecognitionOutcome,
    private val delayMillis: Long = 0,
    private val ignoreCancellation: Boolean = false,
) : MusicRecognizer {
  var callCount: Int = 0
    private set

  var lastSample: AudioSample? = null
    private set

  override suspend fun recognize(sample: AudioSample): RecognitionOutcome {
    callCount += 1
    lastSample = sample
    if (delayMillis > 0) {
      if (ignoreCancellation) {
        withContext(NonCancellable) { delay(delayMillis) }
      } else {
        delay(delayMillis)
      }
    }
    return outcome
  }
}

class RecordingDisplayGateway : DisplayGateway {
  val states = mutableListOf<MusicRecognitionUiState>()

  override suspend fun present(state: MusicRecognitionUiState) {
    states += state
  }
}
