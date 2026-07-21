package com.meta.wearable.dat.externalsampleapps.displayaccess.music.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meta.wearable.dat.externalsampleapps.displayaccess.music.MusicRecognitionViewModel
import com.meta.wearable.dat.externalsampleapps.displayaccess.music.domain.InvalidAudioReason
import com.meta.wearable.dat.externalsampleapps.displayaccess.music.domain.MusicRecognitionError
import com.meta.wearable.dat.externalsampleapps.displayaccess.music.domain.MusicRecognitionUiState

@Composable
fun MusicRecognitionScreen(
    viewModel: MusicRecognitionViewModel,
    modifier: Modifier = Modifier,
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  val context = LocalContext.current
  val permissionLauncher = rememberLauncherForActivityResult(
      ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.startRecognition() else viewModel.onPermissionDenied()
      }

  MusicRecognitionContent(
      state = state,
      onRecognize = {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED) {
          viewModel.startRecognition()
        } else {
          permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
      },
      onCancel = viewModel::cancelRecognition,
      modifier = modifier,
  )
}

@Composable
fun MusicRecognitionContent(
    state: MusicRecognitionUiState,
    onRecognize: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
  val busy = state is MusicRecognitionUiState.Listening || state is MusicRecognitionUiState.Recognizing
  Column(
      modifier = modifier.fillMaxSize().padding(32.dp),
      verticalArrangement = Arrangement.Center,
      horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Text("휴대폰 음악 인식", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(24.dp))
    when (state) {
      MusicRecognitionUiState.Idle -> StatusText("주변에서 재생 중인 음악을 찾아보세요.")
      MusicRecognitionUiState.Listening -> {
        CircularProgressIndicator()
        Spacer(Modifier.height(16.dp))
        StatusText("듣는 중…")
      }
      MusicRecognitionUiState.Recognizing -> {
        CircularProgressIndicator()
        Spacer(Modifier.height(16.dp))
        StatusText("검색 중…")
      }
      is MusicRecognitionUiState.Matched -> {
        Text(
            state.result.title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            state.result.artist,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
      }
      MusicRecognitionUiState.NoMatch -> StatusText("음악을 찾지 못했어요. 다시 시도해 주세요.")
      is MusicRecognitionUiState.Error -> StatusText(errorMessage(state.error))
    }
    Spacer(Modifier.height(32.dp))
    Button(
        onClick = onRecognize,
        enabled = !busy,
        modifier = Modifier.fillMaxWidth(),
    ) { Text(if (state is MusicRecognitionUiState.Matched) "다시 인식" else "음악 인식") }
    if (busy) {
      Spacer(Modifier.height(12.dp))
      OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("취소") }
    }
  }
}

@Composable
private fun StatusText(message: String) {
  Text(
      message,
      style = MaterialTheme.typography.bodyLarge,
      textAlign = TextAlign.Center,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
  )
}

internal fun errorMessage(error: MusicRecognitionError): String = when (error) {
  MusicRecognitionError.PermissionDenied -> "마이크 권한이 필요합니다. 권한을 허용한 뒤 다시 시도해 주세요."
  MusicRecognitionError.Timeout -> "요청 시간이 초과됐어요. 다시 시도해 주세요."
  MusicRecognitionError.Authentication -> "음악 인식 서비스 인증에 실패했습니다."
  is MusicRecognitionError.RateLimited -> "요청이 너무 많아요. 잠시 후 다시 시도해 주세요."
  is MusicRecognitionError.Network -> "네트워크 연결을 확인하고 다시 시도해 주세요."
  is MusicRecognitionError.InvalidAudio -> when (error.reason) {
    InvalidAudioReason.SILENT -> "소리가 들리지 않아요. 음악 가까이에서 다시 시도해 주세요."
    InvalidAudioReason.DISTORTED -> "소리가 너무 크거나 왜곡됐어요. 음악에서 조금 떨어져 다시 시도해 주세요."
    else -> "녹음된 오디오를 사용할 수 없어요. 다시 시도해 주세요."
  }
  is MusicRecognitionError.AudioCapture -> "마이크 녹음에 실패했습니다. 다시 시도해 주세요."
  is MusicRecognitionError.Provider -> "음악 인식 서비스 오류가 발생했습니다."
  is MusicRecognitionError.Unexpected -> "예상하지 못한 오류가 발생했습니다."
}
