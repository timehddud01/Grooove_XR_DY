package com.meta.wearable.dat.externalsampleapps.displayaccess.music.network

import android.util.Log
import com.meta.wearable.dat.externalsampleapps.displayaccess.music.domain.AudioSample
import com.meta.wearable.dat.externalsampleapps.displayaccess.music.domain.MusicRecognitionError
import com.meta.wearable.dat.externalsampleapps.displayaccess.music.domain.MusicRecognizer
import com.meta.wearable.dat.externalsampleapps.displayaccess.music.domain.RecognitionOutcome
import com.meta.wearable.dat.externalsampleapps.displayaccess.music.domain.RecognitionProvider
import com.meta.wearable.dat.externalsampleapps.displayaccess.music.domain.RecognitionResult
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Sends WAV audio only to the app backend. ACRCloud credentials never enter the app. */
class AcrCloudProxyMusicRecognizer(
    baseUrl: String,
    private val userId: String,
    private val timeoutMillis: Int = 20_000,
) : MusicRecognizer {
  companion object {
    private const val TAG = "MusicRecognizer"
  }

  private val endpoint = baseUrl.trimEnd('/') + "/v1/music-recognitions"

  override suspend fun recognize(sample: AudioSample): RecognitionOutcome =
      withContext(Dispatchers.IO) {
        val boundary = "Groove-${UUID.randomUUID()}"
        val wav = WavEncoder.encode(sample)
        var connection: HttpURLConnection? = null
        try {
          connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = timeoutMillis
            readTimeout = timeoutMillis
            doOutput = true
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            setRequestProperty("X-User-Id", userId)
          }
          connection.outputStream.use { output ->
            output.write("--$boundary\r\n".encodeToByteArray())
            output.write(
                "Content-Disposition: form-data; name=\"audio\"; filename=\"capture.wav\"\r\n"
                    .encodeToByteArray())
            output.write("Content-Type: audio/wav\r\n\r\n".encodeToByteArray())
            output.write(wav)
            output.write("\r\n--$boundary--\r\n".encodeToByteArray())
          }
          val code = connection.responseCode
          val stream = if (code in 200..299) connection.inputStream else connection.errorStream
          val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
          if (code !in 200..299) {
            Log.w(TAG, "Proxy response http=$code")
            return@withContext mapHttpError(code, body, connection)
          }
          parseSuccess(body).also { outcome ->
            val status = when (outcome) {
              is RecognitionOutcome.Match -> "MATCH"
              RecognitionOutcome.NoMatch -> "NO_MATCH"
              is RecognitionOutcome.Failure -> "FAILURE"
            }
            Log.i(TAG, "Proxy response http=$code outcome=$status wavBytes=${wav.size}")
          }
        } catch (_: SocketTimeoutException) {
          RecognitionOutcome.Failure(MusicRecognitionError.Timeout)
        } catch (error: IOException) {
          RecognitionOutcome.Failure(MusicRecognitionError.Network(error.message))
        } catch (error: Throwable) {
          RecognitionOutcome.Failure(MusicRecognitionError.Unexpected(error.message))
        } finally {
          connection?.disconnect()
        }
      }

  private fun parseSuccess(body: String): RecognitionOutcome {
    val json = JSONObject(body)
    if (json.optString("status") == "NO_MATCH") return RecognitionOutcome.NoMatch
    val result = json.optJSONObject("result")
        ?: return RecognitionOutcome.Failure(
            MusicRecognitionError.Provider("INVALID_RESPONSE", "Missing result"))
    return RecognitionOutcome.Match(
        RecognitionResult(
            title = result.getString("title"),
            artist = result.getString("artist"),
            album = result.optString("album").takeIf(String::isNotBlank),
            artworkUrl = result.optString("artworkUrl").takeIf(String::isNotBlank),
            provider = RecognitionProvider.ACRCLOUD,
        ))
  }

  private fun mapHttpError(
      code: Int,
      body: String,
      connection: HttpURLConnection,
  ): RecognitionOutcome.Failure {
    val type = runCatching { JSONObject(body).optJSONObject("error")?.optString("type") }.getOrNull()
    val message = runCatching { JSONObject(body).optJSONObject("error")?.optString("message") }.getOrNull()
    val error = when {
      code == 401 || code == 403 || type == "AUTHENTICATION" -> MusicRecognitionError.Authentication
      code == 408 || code == 504 || type == "TIMEOUT" -> MusicRecognitionError.Timeout
      code == 429 || type == "RATE_LIMITED" ->
          MusicRecognitionError.RateLimited(connection.getHeaderField("Retry-After")?.toLongOrNull())
      else -> MusicRecognitionError.Provider(type ?: code.toString(), message)
    }
    return RecognitionOutcome.Failure(error)
  }
}
