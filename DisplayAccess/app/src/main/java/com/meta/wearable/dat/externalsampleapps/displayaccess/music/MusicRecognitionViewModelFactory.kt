package com.meta.wearable.dat.externalsampleapps.displayaccess.music

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.meta.wearable.dat.externalsampleapps.displayaccess.BuildConfig
import com.meta.wearable.dat.externalsampleapps.displayaccess.music.audio.PhoneAudioInput
import com.meta.wearable.dat.externalsampleapps.displayaccess.music.network.AcrCloudProxyMusicRecognizer
import java.util.UUID

class MusicRecognitionViewModelFactory(context: Context) : ViewModelProvider.Factory {
  private val appContext = context.applicationContext

  override fun <T : ViewModel> create(modelClass: Class<T>): T {
    require(modelClass.isAssignableFrom(MusicRecognitionViewModel::class.java))
    val preferences = appContext.getSharedPreferences("music_recognition", Context.MODE_PRIVATE)
    val userId = preferences.getString("anonymous_user_id", null) ?: UUID.randomUUID().toString().also {
      preferences.edit().putString("anonymous_user_id", it).apply()
    }
    @Suppress("UNCHECKED_CAST")
    return MusicRecognitionViewModel(
        audioInput = PhoneAudioInput(),
        musicRecognizer = AcrCloudProxyMusicRecognizer(BuildConfig.MUSIC_RECOGNITION_PROXY_URL, userId),
    ) as T
  }
}
