package moe.shizuku.common.util

import android.os.Build

/**
 * SDK version compatibility checks.
 *
 * TODO: Replace it with [rikka.core.util.BuildUtils].
 */
object BuildUtils {

    private val SDK = Build.VERSION.SDK_INT
    private val PREVIEW_SDK = if (SDK >= 23) Build.VERSION.PREVIEW_SDK_INT else 0

    @JvmStatic
    internal fun isAtLeast(sdk: Int, previewSdk: Int, requiredSdk: Int): Boolean {
        return when (requiredSdk) {
            34 -> sdk >= 34 || (sdk == 33 && previewSdk > 0)
            31 -> sdk >= 31 || (sdk == 30 && previewSdk > 0)
            else -> sdk >= requiredSdk
        }
    }

    @JvmStatic
    fun atLeast34(): Boolean = isAtLeast(SDK, PREVIEW_SDK, 34)

    @JvmStatic
    fun atLeast31(): Boolean = isAtLeast(SDK, PREVIEW_SDK, 31)

    @JvmStatic
    fun atLeast30(): Boolean = isAtLeast(SDK, PREVIEW_SDK, 30)

    @JvmStatic
    fun atLeast29(): Boolean = isAtLeast(SDK, PREVIEW_SDK, 29)

    @JvmStatic
    fun atLeast28(): Boolean = isAtLeast(SDK, PREVIEW_SDK, 28)

    @JvmStatic
    fun atLeast26(): Boolean = isAtLeast(SDK, PREVIEW_SDK, 26)

    @JvmStatic
    fun atLeast24(): Boolean = isAtLeast(SDK, PREVIEW_SDK, 24)

    @JvmStatic
    fun atLeast23(): Boolean = isAtLeast(SDK, PREVIEW_SDK, 23)
}
