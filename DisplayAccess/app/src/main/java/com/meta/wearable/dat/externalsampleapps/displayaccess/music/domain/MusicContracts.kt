package com.meta.wearable.dat.externalsampleapps.displayaccess.music.domain

interface AudioInput {
  suspend fun capture(durationMillis: Long): AudioCaptureOutcome

  fun cancel()
}

interface MusicRecognizer {
  suspend fun recognize(sample: AudioSample): RecognitionOutcome
}

interface DisplayGateway {
  suspend fun present(state: MusicRecognitionUiState)
}

object NoOpDisplayGateway : DisplayGateway {
  override suspend fun present(state: MusicRecognitionUiState) = Unit
}
