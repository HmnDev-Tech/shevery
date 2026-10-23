package moe.shizuku.manager.module

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import moe.shizuku.manager.MainActivity
import moe.shizuku.manager.R
import moe.shizuku.manager.home.HomeActivity
import java.io.File

/**
 * Entry point behind the "Install ZIP Module" label.
 *
 * Any app can hand over a module ZIP with ACTION_VIEW or ACTION_SEND. The URI
 * grant from the sender only lives as long as this activity, so the file is
 * copied into our own cache first, then the main screen is opened on the ADB
 * Modules tab with the cached path.
 */
class InstallZipModuleActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val source = resolveZipUri(intent)
        if (source == null) {
            finish()
            return
        }

        val cached = copyToCache(source)
        if (cached == null) {
            Toast.makeText(
                this,
                getString(R.string.modules_install_failed, getString(R.string.modules_zip_unreadable)),
                Toast.LENGTH_LONG
            ).show()
            finish()
            return
        }

        startActivity(
            Intent(this, MainActivity::class.java).apply {
                putExtra(HomeActivity.EXTRA_ZIP_PATH, cached.absolutePath)
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                )
            }
        )
        finish()
    }

    private fun resolveZipUri(intent: Intent?): Uri? {
        intent ?: return null
        val directUri = when (intent.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_STREAM)
            }
            Intent.ACTION_SEND_MULTIPLE -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)?.firstOrNull()
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)?.firstOrNull()
            }
            else -> null
        }
        if (directUri != null) return directUri

        val clipData = intent.clipData
        if (clipData != null && clipData.itemCount > 0) {
            for (i in 0 until clipData.itemCount) {
                clipData.getItemAt(i)?.uri?.let { return it }
            }
        }
        return intent.data
    }

    private fun copyToCache(uri: Uri): File? {
        val target = File(cacheDir, "shared_module_${System.currentTimeMillis()}.zip")
        return try {
            val input = if (uri.scheme.equals("file", ignoreCase = true)) {
                uri.path?.let { File(it).inputStream() }
            } else {
                contentResolver.openInputStream(uri)
            } ?: return null

            input.use { source ->
                target.outputStream().use { output -> source.copyTo(output) }
            }
            if (!isLikelyZipFile(target)) {
                target.delete()
                return null
            }
            target
        } catch (ignore: Exception) {
            target.delete()
            null
        }
    }

    private fun isLikelyZipFile(file: File): Boolean {
        if (!file.isFile || file.length() < 4L) return false
        return try {
            file.inputStream().use { stream ->
                val header = ByteArray(4)
                val read = stream.read(header)
                read == 4 &&
                    header[0] == 0x50.toByte() &&
                    header[1] == 0x4B.toByte() &&
                    (header[2] == 0x03.toByte() || header[2] == 0x05.toByte() || header[2] == 0x07.toByte())
            }
        } catch (ignore: Exception) {
            false
        }
    }
}
