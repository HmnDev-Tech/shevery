package moe.shizuku.manager.module.update

import android.content.Context
import android.util.Log
import moe.shizuku.manager.module.discovery.ContentItem
import moe.shizuku.manager.module.discovery.RateLimitTracker
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class SourceZipBuilder private constructor() {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val rateLimit = RateLimitTracker()

    suspend fun buildZip(
        context: Context,
        moduleId: String,
        files: List<ContentItem>,
        githubPat: String? = null,
        subPath: String? = null
    ): File? {
        val cacheDir = File(context.cacheDir, "module_zips").apply { mkdirs() }
        val zipFile = File(cacheDir, "$moduleId-source.zip")
        val prefix = subPath?.trim('/')?.let { "$it/" } ?: ""

        return try {
            ZipOutputStream(zipFile.outputStream()).use { zip ->
                for (file in files) {
                    if (!isModuleFile(file.path)) continue
                    if (file.downloadUrl == null) continue

                    val content = downloadFile(file.downloadUrl, githubPat) ?: continue
                    val entryName = file.path.trimStart('/').let { fullPath ->
                        if (prefix.isNotEmpty() && fullPath.startsWith(prefix)) fullPath.removePrefix(prefix) else fullPath.substringAfterLast('/')
                    }
                    val entry = ZipEntry(entryName)
                    entry.size = content.size.toLong()
                    zip.putNextEntry(entry)
                    zip.write(content)
                    zip.closeEntry()
                }
            }

            if (zipFile.length() > 0) {
                zipFile
            } else {
                zipFile.delete()
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "ZIP build failed for $moduleId", e)
            zipFile.delete()
            null
        }
    }

    private fun downloadFile(url: String, githubPat: String?): ByteArray? {
        return try {
            val builder = Request.Builder()
                .url(url)
                .header("Accept", "application/vnd.github.v3.raw")

            if (!githubPat.isNullOrBlank()) {
                builder.header("Authorization", "Bearer $githubPat")
            }

            val request = builder.build()
            val response = client.newCall(request).execute()

            response.use { resp ->
                rateLimit.update(resp.headers)

                if (!resp.isSuccessful) {
                    Log.w(TAG, "Download failed: ${resp.code} for $url")
                    return null
                }

                resp.body?.bytes()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Download error for $url", e)
            null
        }
    }

    private fun isModuleFile(name: String): Boolean {
        val cleanName = name.trimStart('/').lowercase()
        val fileName = cleanName.substringAfterLast('/')
        return fileName == "module.prop" ||
                fileName.endsWith(".sh") ||
                fileName.startsWith("banner.") ||
                cleanName.contains("webui/") ||
                cleanName.contains("webroot/") ||
                cleanName.contains("web/") ||
                WEB_EXTENSIONS.any { ext -> fileName.endsWith(ext) }
    }

    fun getRateLimit(): RateLimitTracker = rateLimit

    companion object {
        private const val TAG = "SourceZipBuilder"
        private val WEB_EXTENSIONS = setOf(
            ".js", ".css", ".json", ".png", ".svg", ".webp",
            ".woff", ".woff2", ".ttf", ".html", ".htm"
        )

        @Volatile
        private var instance: SourceZipBuilder? = null

        fun getInstance(): SourceZipBuilder {
            return instance ?: synchronized(this) {
                instance ?: SourceZipBuilder().also {
                    instance = it
                }
            }
        }
    }
}
