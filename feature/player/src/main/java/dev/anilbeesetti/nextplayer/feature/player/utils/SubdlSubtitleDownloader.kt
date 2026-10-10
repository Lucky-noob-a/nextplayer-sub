package dev.anilbeesetti.nextplayer.feature.player.utils

import android.content.Context
import android.net.Uri
import dev.anilbeesetti.nextplayer.feature.player.BuildConfig
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Downloads an English subtitle from SubDL. The API key is supplied through the
 * local Gradle property SUBDL_API_KEY and is deliberately not stored in source control.
 */
internal object SubdlSubtitleDownloader {
    suspend fun downloadEnglishSubtitle(context: Context, videoUri: Uri, displayName: String? = null): Uri? =
        withContext(Dispatchers.IO) {
            val apiKey = BuildConfig.SUBDL_API_KEY.trim()
            if (apiKey.isEmpty()) return@withContext null

            val rawName = displayName?.takeIf { it.isNotBlank() }
                ?: videoUri.lastPathSegment?.substringAfterLast('/')?.substringBeforeLast('.')
                ?: return@withContext null
            val searchName = rawName.substringBeforeLast('.').replace(Regex("[._]+"), " ").trim()
            if (searchName.isBlank()) return@withContext null

            val cacheDir = File(context.cacheDir, "subdl").apply { mkdirs() }
            val safeName = searchName.replace(Regex("[^A-Za-z0-9._-]"), "_").take(100)
            val cached = File(cacheDir, "$safeName.en.srt")
            if (cached.isFile && cached.length() > 0L) return@withContext Uri.fromFile(cached)

            try {
                val searchUrl = "https://api.subdl.com/api/v1/subtitles?api_key=" +
                    URLEncoder.encode(apiKey, "UTF-8") +
                    "&file_name=" + URLEncoder.encode(rawName, "UTF-8") +
                    "&languages=EN&unpack=1&subs_per_page=10"
                val response = JSONObject(readUrl(searchUrl))
                if (!response.optBoolean("status")) return@withContext null
                val subtitles = response.optJSONArray("subtitles") ?: return@withContext null

                var downloadUrl: String? = null
                for (i in 0 until subtitles.length()) {
                    val subtitle = subtitles.optJSONObject(i) ?: continue
                    val files = subtitle.optJSONArray("unpack_files")
                    if (files != null) {
                        for (j in 0 until files.length()) {
                            val item = files.optJSONObject(j) ?: continue
                            val language = item.optString("language", "")
                            val format = item.optString("format", "").lowercase()
                            val url = item.optString("url", "")
                            if (language.equals("EN", true) &&
                                format in setOf("srt", "vtt", "ass", "ssa") &&
                                url.isNotBlank()
                            ) {
                                downloadUrl = if (url.startsWith("http")) url else "https://dl.subdl.com$url"
                                break
                            }
                        }
                    }
                    if (downloadUrl != null) break
                }
                val selectedUrl = downloadUrl ?: return@withContext null
                val bytes = readBytes(selectedUrl)
                if (bytes.isEmpty()) return@withContext null
                val extension = selectedUrl.substringBefore('?').substringAfterLast('.', "srt")
                    .lowercase().takeIf { it in setOf("srt", "vtt", "ass", "ssa") } ?: "srt"
                val output = File(cacheDir, "$safeName.en.$extension")
                output.writeBytes(bytes)
                Uri.fromFile(output)
            } catch (_: Exception) {
                null
            }
        }

    private fun readUrl(url: String): String = readBytes(url).toString(Charsets.UTF_8)

    private fun readBytes(url: String): ByteArray {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 12000
            readTimeout = 20000
            requestMethod = "GET"
            setRequestProperty("Accept", "application/json, text/plain, */*")
        }
        return try {
            if (connection.responseCode !in 200..299) return ByteArray(0)
            connection.inputStream.use { it.readBytes() }
        } finally {
            connection.disconnect()
        }
    }
}
