package moe.shizuku.manager.utils

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.widget.Toast
import androidx.browser.customtabs.CustomTabsIntent
import moe.shizuku.manager.R
import rikka.core.util.ClipboardUtils
import rikka.html.text.HtmlCompat

/**
 * Helper for launching Chrome Custom Tabs or falling back to external browser / clipboard copy.
 *
 * Created by fytho on 2017/12/15.
 */
object CustomTabsHelper {

    fun interface OnCreateIntentBuilderListener {
        fun onCreateHelpIntentBuilder(context: Context, builder: CustomTabsIntent.Builder)
    }

    private var sOnCreateIntentBuilderListener: OnCreateIntentBuilderListener? = null

    @JvmStatic
    fun setOnCreateIntentBuilderListener(listener: OnCreateIntentBuilderListener?) {
        sOnCreateIntentBuilderListener = listener
    }

    @JvmStatic
    fun createBuilder(): CustomTabsIntent.Builder {
        return CustomTabsIntent.Builder().setShowTitle(true)
    }

    @JvmStatic
    fun launchHelp(context: Context, uri: Uri): Boolean {
        val builder = createBuilder()
        sOnCreateIntentBuilderListener?.onCreateHelpIntentBuilder(context, builder)

        val uriBuilder = uri.buildUpon()
        if ((context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_YES) > 0) {
            uriBuilder.appendQueryParameter("night", "1")
        }

        return launchUrl(context, builder.build(), uriBuilder.build())
    }

    @JvmStatic
    fun launchUrl(context: Context, uri: Uri): Boolean {
        return launchUrl(context, createBuilder().build(), uri)
    }

    private fun launchUrl(context: Context, customTabsIntent: CustomTabsIntent, uri: Uri): Boolean {
        return try {
            customTabsIntent.launchUrl(context, uri)
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
    }

    @JvmStatic
    fun launchUrlOrCopy(context: Context, url: String?) {
        if (url.isNullOrBlank()) return
        val uri = Uri.parse(url)
        if (!launchHelp(context, uri)) {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = uri
            }
            try {
                context.startActivity(intent)
            } catch (_: Throwable) {
                try {
                    ClipboardUtils.put(context, url)
                    @Suppress("DEPRECATION")
                    Toast.makeText(
                        context,
                        HtmlCompat.fromHtml(context.getString(R.string.toast_copied_to_clipboard_with_text, url)),
                        Toast.LENGTH_LONG
                    ).show()
                } catch (_: Throwable) {
                }
            }
        }
    }
}
