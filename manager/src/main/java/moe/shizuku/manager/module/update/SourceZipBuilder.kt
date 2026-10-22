package moe.shizuku.manager.module.update

import android.content.Context
import android.util.Log
import moe.shizuku.manager.module.discovery.ContentItem
import moe.shizuku.manager.module.discovery.RateLimitTracker
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
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
        subPath: String? = null,
        onLog: (String) -> Unit = {},
        onProgress: (Float?) -> Unit = {}
    ): File? {
        val cacheDir = File(context.cacheDir, "module_zips").apply { mkdirs() }
        val zipFile = File(cacheDir, "$moduleId-source.zip")
        val prefix = subPath?.trim('/')?.let { "$it/" } ?: ""
        val packed = linkedSetOf<String>()
        val downloadable = files.filter { it.downloadUrl != null }
        val totalFiles = downloadable.size.coerceAtLeast(1)

        return try {
            ZipOutputStream(zipFile.outputStream()).use { zip ->
                downloadable.forEachIndexed { index, file ->
                    val downloadUrl = file.downloadUrl ?: return@forEachIndexed
                    val entryName = relativeEntryName(file.path, prefix)
                    require(packed.add(entryName)) { "Duplicate module file: $entryName" }
                    onLog("- [${index + 1}/$totalFiles] Downloading $entryName...")
                    onProgress((index.toFloat() / totalFiles.toFloat()) * 0.65f + 0.10f)
                    val content = downloadFile(downloadUrl, githubPat)
                    onLog("  ✓ $entryName (${formatBytes(content.size.toLong())})")
                    zip.putNextEntry(ZipEntry(entryName))
                    zip.write(content)
                    zip.closeEntry()
                }
            }

            if (packed.isEmpty()) {
                zipFile.delete()
                null
            } else {
                onLog("- Packed ${packed.size} files into ${zipFile.name} (${formatBytes(zipFile.length())})")
                onProgress(0.78f)
                zipFile
            }
        } catch (e: Exception) {
            Log.e(TAG, "ZIP build failed for $moduleId", e)
            zipFile.delete()
            throw e
        }
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes < 1024L) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024.0) return String.format(java.util.Locale.US, "%.1f KB", kb)
        return String.format(java.util.Locale.US, "%.2f MB", kb / 1024.0)
    }

    private fun downloadFile(url: String, githubPat: String?): ByteArray {
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

            val body = resp.body ?: throw IOException("Empty response for $url")
            if (!resp.isSuccessful) {
                throw IOException("Download failed: HTTP ${resp.code} for $url")
            }

            val length = body.contentLength()
            if (length > MAX_FILE_BYTES) {
                throw IOException("File exceeds size limit: $url")
            }

            val output = ByteArrayOutputStream(if (length > 0) length.toInt() else 8192)
            body.byteStream().use { input ->
                val buffer = ByteArray(8192)
                var total = 0L
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    total += read
                    if (total > MAX_FILE_BYTES) {
                        throw IOException("File exceeds size limit: $url")
                    }
                    output.write(buffer, 0, read)
                }
            }
            return output.toByteArray()
        }
    }

    private fun relativeEntryName(path: String, prefix: String): String {
        val clean = path.trimStart('/').replace('\\', '/')
        require(clean.isNotBlank()) { "Invalid module file path." }
        require(!clean.startsWith("/") && !clean.contains("../") && clean != "..") {
            "Unsafe module file path: $path"
        }
        val relative = if (prefix.isNotEmpty()) {
            require(clean.startsWith(prefix)) { "Module file outside module directory: $clean" }
            clean.removePrefix(prefix)
        } else {
            clean
        }
        require(relative.isNotBlank()) { "Invalid module file path: $path" }
        return relative
    }



    companion object {
        private const val TAG = "SourceZipBuilder"
        private const val MAX_FILE_BYTES = 200L * 1024L * 1024L

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
