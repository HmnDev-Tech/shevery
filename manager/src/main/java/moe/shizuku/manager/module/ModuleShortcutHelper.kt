package moe.shizuku.manager.module

import android.app.ActivityManager
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
import java.io.File

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
        // 1. Detect icon.png in module files
        val iconFile = findIconFile(context, module)
        if (iconFile != null) {
            val bitmap = decodeSquareBitmap(context, iconFile)
            if (bitmap != null) {
                return IconCompat.createWithBitmap(bitmap)
            }
        }

        // 2. If icon.png isn't available, use banner.png
        val bannerFile = findBannerFile(context, module)
        if (bannerFile != null) {
            val bitmap = decodeSquareBitmap(context, bannerFile)
            if (bitmap != null) {
                return IconCompat.createWithBitmap(bitmap)
            }
        }

        // 3. If both unavailable, use default app icon
        return IconCompat.createWithResource(context, R.mipmap.ic_launcher)
    }

    private fun findIconFile(context: Context, module: AdbModule): File? {
        val dir = if (module.directory.exists()) module.directory else AdbModuleManager.modulesRoot(context).resolve(module.id)
        if (!dir.exists() || !dir.isDirectory) return null

        val directIcon = dir.resolve("icon.png")
        if (directIcon.isFile) return directIcon

        module.webRoot?.resolve("icon.png")?.takeIf { it.isFile }?.let { return it }

        module.icon?.takeIf { it.isFile && it.name.equals("icon.png", ignoreCase = true) }?.let { return it }

        return runCatching {
            dir.walkTopDown()
                .maxDepth(4)
                .filter { it.isFile }
                .firstOrNull { it.name.equals("icon.png", ignoreCase = true) }
        }.getOrNull()
    }

    private fun findBannerFile(context: Context, module: AdbModule): File? {
        val dir = if (module.directory.exists()) module.directory else AdbModuleManager.modulesRoot(context).resolve(module.id)
        if (!dir.exists() || !dir.isDirectory) return null

        val directBanner = dir.resolve("banner.png")
        if (directBanner.isFile) return directBanner

        module.webRoot?.resolve("banner.png")?.takeIf { it.isFile }?.let { return it }

        module.banner?.takeIf { it.isFile && it.name.equals("banner.png", ignoreCase = true) }?.let { return it }

        return runCatching {
            dir.walkTopDown()
                .maxDepth(4)
                .filter { it.isFile }
                .firstOrNull { it.name.equals("banner.png", ignoreCase = true) }
        }.getOrNull()
    }

    private fun decodeSquareBitmap(context: Context, file: File): Bitmap? {
        return try {
            val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return null
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            val targetSize = am?.launcherLargeIconSize?.takeIf { it > 0 } ?: 192
            createSquareBitmap(bitmap, targetSize)
        } catch (_: Throwable) {
            null
        }
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
