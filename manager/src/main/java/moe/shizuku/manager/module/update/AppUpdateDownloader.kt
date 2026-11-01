package moe.shizuku.manager.module.update

import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Downloads the Shevery update APK in-app so the install session can stream it.
 *
 * This mirrors the way MorpheApp's manager pulls release assets (direct stream +
 * progress), without their JSON metadata layer: SheveryUpdateChecker already
 * locates the release reliably, so this only has to fetch the bytes.
 */
object AppUpdateDownloader {

    private const val MAX_APK_BYTES = 300L * 1024L * 1024L

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS) // per-chunk timeout — the APK streams;60s is plenty between chunks
        .build()

    /**
     * Downloads [url] into [targetFile]. Any partial download is deleted on failure,
     * and a stale file from a previous attempt is removed up front. Progress callbacks
     * fire with bytes-read so far + total when the server sent a Content-Length.
     */

    suspend fun downloadToFile(
        url: String,
        targetFile: File,
        onProgress: (bytesRead: Long, contentLength: Long?) -> Unit
    ) = withContext(Dispatchers.IO) {
        targetFile.delete()
        try {
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("HTTP ${response.code}: ${response.message}")
                }
                val body = response.body ?: throw IOException("Empty response body")
                val total = body.contentLength().takeIf { it > 0 }
                if (total != null && total > MAX_APK_BYTES) {
                    throw IOException("Update APK is too large: $total bytes")
                }
                body.byteStream().use { input ->
                    targetFile.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var read = 0L
                        while (true) {
                            val n = input.read(buffer)
                            if (n < 0) break
                            read += n
                            if (read > MAX_APK_BYTES) {
                                throw IOException("Update APK exceeded the size limit")
                            }
                            output.write(buffer, 0, n)
                            onProgress(read, total)
                        }
                        output.flush()
                    }
                }
            }
        } catch (e: Exception) {
            targetFile.delete()
            throw e
        }
    }
}