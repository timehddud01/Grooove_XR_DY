package com.meta.wearable.dat.externalsampleapps.displayaccess.music

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meta.wearable.dat.externalsampleapps.displayaccess.music.domain.AudioCaptureOutcome
import com.meta.wearable.dat.externalsampleapps.displayaccess.music.domain.AudioInput
import com.meta.wearable.dat.externalsampleapps.displayaccess.music.domain.DisplayGateway
import com.meta.wearable.dat.externalsampleapps.displayaccess.music.domain.MusicRecognitionError
import com.meta.wearable.dat.externalsampleapps.displayaccess.music.domain.MusicRecognitionUiState
import com.meta.wearable.dat.externalsampleapps.displayaccess.music.domain.MusicRecognizer
import com.meta.wearable.dat.externalsampleapps.displayaccess.music.domain.NoOpDisplayGateway
import com.meta.wearable.dat.externalsampleapps.displayaccess.music.domain.RecognitionOutcome
import com.meta.wearable.dat.externalsampleapps.displayaccess.music.validation.AudioSampleValidator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MusicRecognitionViewModel(
    private val audioInput: AudioInput,
    private val musicRecognizer: MusicRecognizer,
    private val displayGateway: DisplayGateway = NoOpDisplayGateway,
    private val validator: AudioSampleValidator = AudioSampleValidator(),
) : ViewModel() {
  companion object {
    const val DEFAULT_CAPTURE_DURATION_MILLIS = 12_000L
  }

  private val _uiState = MutableStateFlow<MusicRecognitionUiState>(MusicRecognitionUiState.Idle)
  val uiState: StateFlow<MusicRecognitionUiState> = _uiState.asStateFlow()

  private var recognitionJob: Job? = null
  private var requestGeneration: Long = 0

  fun startRecognition() {
    if (recognitionJob?.isActive == true) return

    val generation = ++requestGeneration
    recognitionJob =
        viewModelScope.launch {
          try {
            updateState(MusicRecognitionUiState.Listening, generation)
            when (val capture = audioInput.capture(DEFAULT_CAPTURE_DURATION_MILLIS)) {
              AudioCaptureOutcome.Cancelled -> updateState(MusicRecognitionUiState.Idle, generation)
              is AudioCaptureOutcome.Failure -> {
                updateState(MusicRecognitionUiState.Error(capture.error), generation)
              }
              is AudioCaptureOutcome.Captured -> {
                val validationError = validator.validate(capture.sample)
                if (validationError != null) {
                  updateState(MusicRecognitionUiState.Error(validationError), generation)
                  return@launch
                }

                updateState(MusicRecognitionUiState.Recognizing, generation)
                when (val outcome = musicRecognizer.recognize(capture.sample)) {
                  is RecognitionOutcome.Match -> {
                    updateState(MusicRecognitionUiState.Matched(outcome.result), generation)
                  }
                  RecognitionOutcome.NoMatch ->
                      updateState(MusicRecognitionUiState.NoMatch, generation)
                  is RecognitionOutcome.Failure ->
                      updateState(MusicRecognitionUiState.Error(outcome.error), generation)
                }
              }
            }
          } catch (_: CancellationException) {
            // cancelRecognition owns the user-visible terminal state.
          } catch (throwable: Throwable) {
            updateState(
                MusicRecognitionUiState.Error(
                    MusicRecognitionError.Unexpected(throwable.message)),
                generation,
            )
          }
        }
  }

  fun cancelRecognition() {
    requestGeneration += 1
    recognitionJob?.cancel()
    recognitionJob = null
    audioInput.cancel()
    _uiState.value = MusicRecognitionUiState.Idle
    viewModelScope.launch { runCatching { displayGateway.present(MusicRecognitionUiState.Idle) } }
  }

  fun onPermissionDenied() {
    if (recognitionJob?.isActive == true) return
    _uiState.value = MusicRecognitionUiState.Error(MusicRecognitionError.PermissionDenied)
  }

  private suspend fun updateState(state: MusicRecognitionUiState, generation: Long) {
    if (generation != requestGeneration) return
    _uiState.value = state
    runCatching { displayGateway.present(state) }
  }

  override fun onCleared() {
    cancelRecognition()
    super.onCleared()
  }
}
