package moe.shizuku.manager.module

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import moe.shizuku.manager.R

object ModuleShortcutHelper {

    fun addModuleShortcut(context: Context, module: AdbModule) {
        if (!ShortcutManagerCompat.isRequestPinShortcutSupported(context)) {
            Toast.makeText(context, R.string.modules_shortcut_not_supported, Toast.LENGTH_SHORT).show()
            return
        }

        val shortcutIntent = Intent(Intent.ACTION_VIEW, Uri.parse("shevery://module/${module.id}"), context, ModuleWebViewActivity::class.java).apply {
            putExtra(ModuleWebViewActivity.EXTRA_MODULE_ID, module.id)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val iconCompat = createShortcutIcon(context, module)

        val shortcutInfo = ShortcutInfoCompat.Builder(context, "module_webui_${module.id}")
            .setShortLabel(module.name)
            .setLongLabel(module.name)
            .setIcon(iconCompat)
            .setIntent(shortcutIntent)
            .build()

        val success = ShortcutManagerCompat.requestPinShortcut(context, shortcutInfo, null)
        if (success) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                Toast.makeText(context, R.string.modules_shortcut_added, Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, R.string.modules_shortcut_not_supported, Toast.LENGTH_SHORT).show()
        }
    }

    private fun createShortcutIcon(context: Context, module: AdbModule): IconCompat {
        val iconFile = module.icon ?: module.banner
        if (iconFile != null && iconFile.exists()) {
            try {
                val bitmap = BitmapFactory.decodeFile(iconFile.absolutePath)
                if (bitmap != null) {
                    val square = createSquareBitmap(bitmap, 192)
                    return IconCompat.createWithBitmap(square)
                }
            } catch (_: Throwable) {
                // Fall back to app icon
            }
        }
        return IconCompat.createWithResource(context, R.mipmap.ic_launcher)
    }

    private fun createSquareBitmap(source: Bitmap, targetSize: Int): Bitmap {
        val width = source.width
        val height = source.height
        val minDim = minOf(width, height)
        val cropX = (width - minDim) / 2
        val cropY = (height - minDim) / 2
        val cropped = Bitmap.createBitmap(source, cropX, cropY, minDim, minDim)
        return if (minDim != targetSize) {
            Bitmap.createScaledBitmap(cropped, targetSize, targetSize, true)
        } else {
            cropped
        }
    }
}
