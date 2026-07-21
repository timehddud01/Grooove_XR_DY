package com.meta.wearable.dat.externalsampleapps.displayaccess.music

import com.meta.wearable.dat.externalsampleapps.displayaccess.music.domain.InvalidAudioReason
import com.meta.wearable.dat.externalsampleapps.displayaccess.music.domain.MusicRecognitionError
import com.meta.wearable.dat.externalsampleapps.displayaccess.music.domain.MusicRecognitionStatus
import com.meta.wearable.dat.externalsampleapps.displayaccess.music.domain.MusicRecognitionUiState
import com.meta.wearable.dat.externalsampleapps.displayaccess.music.domain.RecognitionOutcome
import com.meta.wearable.dat.externalsampleapps.displayaccess.music.domain.RecognitionProvider
import com.meta.wearable.dat.externalsampleapps.displayaccess.music.domain.RecognitionResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MusicRecognitionViewModelTest {
  private val dispatcher = StandardTestDispatcher()

  @Before
  fun setUp() {
    Dispatchers.setMain(dispatcher)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun successfulRecognitionTransitionsThroughListeningAndRecognizing() = runTest(dispatcher) {
    val result =
        RecognitionResult(
            title = "Test Song",
            artist = "Test Artist",
            provider = RecognitionProvider.CUSTOM,
        )
    val recognizer = FakeMusicRecognizer(RecognitionOutcome.Match(result))
    val display = RecordingDisplayGateway()
    val viewModel =
        MusicRecognitionViewModel(
            audioInput = FixtureAudioInput(TestAudioFixtures.normal()),
            musicRecognizer = recognizer,
            displayGateway = display,
        )

    viewModel.startRecognition()
    advanceUntilIdle()

    assertEquals(MusicRecognitionUiState.Matched(result), viewModel.uiState.value)
    assertEquals(
        listOf(
            MusicRecognitionStatus.LISTENING,
            MusicRecognitionStatus.RECOGNIZING,
            MusicRecognitionStatus.MATCHED,
        ),
        display.states.map { it.status },
    )
    assertEquals(1, recognizer.callCount)
  }

  @Test
  fun noMatchIsExposedWithoutProviderDto() = runTest(dispatcher) {
    val viewModel =
        MusicRecognitionViewModel(
            FixtureAudioInput(TestAudioFixtures.normal()),
            FakeMusicRecognizer(RecognitionOutcome.NoMatch),
        )

    viewModel.startRecognition()
    advanceUntilIdle()

    assertEquals(MusicRecognitionUiState.NoMatch, viewModel.uiState.value)
  }

  @Test
  fun typedProviderFailuresReachErrorState() = runTest(dispatcher) {
    val errors =
        listOf(
            MusicRecognitionError.Timeout,
            MusicRecognitionError.Authentication,
            MusicRecognitionError.RateLimited(30),
            MusicRecognitionError.Network("offline"),
            MusicRecognitionError.Provider("500", "server error"),
        )

    errors.forEach { error ->
      val viewModel =
          MusicRecognitionViewModel(
              FixtureAudioInput(TestAudioFixtures.normal()),
              FakeMusicRecognizer(RecognitionOutcome.Failure(error)),
          )
      viewModel.startRecognition()
      advanceUntilIdle()
      assertEquals(MusicRecognitionUiState.Error(error), viewModel.uiState.value)
    }
  }

  @Test
  fun silentAudioIsRejectedBeforeRecognizer() = runTest(dispatcher) {
    val recognizer = FakeMusicRecognizer(RecognitionOutcome.NoMatch)
    val viewModel =
        MusicRecognitionViewModel(FixtureAudioInput(TestAudioFixtures.silence()), recognizer)

    viewModel.startRecognition()
    advanceUntilIdle()

    assertEquals(
        MusicRecognitionUiState.Error(
            MusicRecognitionError.InvalidAudio(InvalidAudioReason.SILENT)),
        viewModel.uiState.value,
    )
    assertEquals(0, recognizer.callCount)
  }

  @Test
  fun shortAudioIsRejectedBeforeRecognizer() = runTest(dispatcher) {
    val recognizer = FakeMusicRecognizer(RecognitionOutcome.NoMatch)
    val viewModel =
        MusicRecognitionViewModel(FixtureAudioInput(TestAudioFixtures.tooShort()), recognizer)

    viewModel.startRecognition()
    advanceUntilIdle()

    assertEquals(
        MusicRecognitionUiState.Error(
            MusicRecognitionError.InvalidAudio(InvalidAudioReason.TOO_SHORT)),
        viewModel.uiState.value,
    )
    assertEquals(0, recognizer.callCount)
  }

  @Test
  fun clippedAudioIsRejectedBeforeRecognizer() = runTest(dispatcher) {
    val recognizer = FakeMusicRecognizer(RecognitionOutcome.NoMatch)
    val viewModel =
        MusicRecognitionViewModel(FixtureAudioInput(TestAudioFixtures.clipped()), recognizer)

    viewModel.startRecognition()
    advanceUntilIdle()

    assertEquals(
        MusicRecognitionUiState.Error(
            MusicRecognitionError.InvalidAudio(InvalidAudioReason.DISTORTED)),
        viewModel.uiState.value,
    )
    assertEquals(0, recognizer.callCount)
  }

  @Test
  fun cancelledLateResultCannotOverwriteIdle() = runTest(dispatcher) {
    val result =
        RecognitionResult("Late Song", "Late Artist", provider = RecognitionProvider.CUSTOM)
    val audioInput = FixtureAudioInput(TestAudioFixtures.normal())
    val recognizer =
        FakeMusicRecognizer(
            outcome = RecognitionOutcome.Match(result),
            delayMillis = 5_000,
            ignoreCancellation = true,
        )
    val viewModel = MusicRecognitionViewModel(audioInput, recognizer)

    viewModel.startRecognition()
    advanceTimeBy(1)
    assertEquals(MusicRecognitionStatus.RECOGNIZING, viewModel.uiState.value.status)

    viewModel.cancelRecognition()
    assertEquals(MusicRecognitionUiState.Idle, viewModel.uiState.value)
    advanceUntilIdle()

    assertEquals(MusicRecognitionUiState.Idle, viewModel.uiState.value)
    assertEquals(1, audioInput.cancelCount)
  }

  @Test
  fun generatedWavFixturesAreDeterministicAndDistinct() {
    val normalA = TestAudioFixtures.normal()
    val normalB = TestAudioFixtures.normal()
    val noise = TestAudioFixtures.noise()

    assertEquals(normalA.sha256, normalB.sha256)
    assertNotEquals(normalA.sha256, noise.sha256)
    assertFalse(normalA.bytes.isEmpty())
  }

  @Test
  fun repeatedRecognitionAlwaysCapturesFreshAudio() = runTest(dispatcher) {
    val input = FixtureAudioInput(TestAudioFixtures.normal())
    val result = RecognitionResult("Song", "Artist", provider = RecognitionProvider.ACRCLOUD)
    val recognizer = FakeMusicRecognizer(RecognitionOutcome.Match(result))
    val viewModel = MusicRecognitionViewModel(input, recognizer)

    viewModel.startRecognition()
    advanceUntilIdle()
    viewModel.startRecognition()
    advanceUntilIdle()

    assertEquals(2, input.captureCount)
    assertEquals(2, recognizer.callCount)
    assertEquals(listOf(12_000L, 12_000L), input.requestedDurationsMillis)
  }

  @Test
  fun permissionDenialIsRecoverableAndDoesNotStartCapture() = runTest(dispatcher) {
    val input = FixtureAudioInput(TestAudioFixtures.normal())
    val viewModel = MusicRecognitionViewModel(input, FakeMusicRecognizer(RecognitionOutcome.NoMatch))

    viewModel.onPermissionDenied()

    assertEquals(
        MusicRecognitionUiState.Error(MusicRecognitionError.PermissionDenied),
        viewModel.uiState.value,
    )
    assertEquals(0, input.captureCount)
  }
}
