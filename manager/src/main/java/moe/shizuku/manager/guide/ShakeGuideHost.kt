package moe.shizuku.manager.guide

import android.content.SharedPreferences
import android.view.HapticFeedbackConstants
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.lifecycle.compose.LifecycleResumeEffect
import moe.shizuku.manager.ShizukuSettings

// The pager can keep neighboring tabs composed. Only the visible tab can host a guide.
internal val LocalShakeGuideActive = staticCompositionLocalOf { true }

private object ShakeHostStack {
    val resumedHosts = mutableStateListOf<Any>()
    var sheetOwner by mutableStateOf<Any?>(null)

    fun isTop(token: Any): Boolean = resumedHosts.lastOrNull() === token

    fun dismiss(token: Any) {
        if (sheetOwner === token) sheetOwner = null
    }
}

@Composable
internal fun rememberShakeGuideEnabled(): Boolean {
    val prefs = ShizukuSettings.getPreferences()
    var enabled by remember(prefs) { mutableStateOf(ShizukuSettings.isShakeForGuideEnabled()) }
    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == ShizukuSettings.SHAKE_FOR_GUIDE || key == null) {
                enabled = ShizukuSettings.isShakeForGuideEnabled()
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        enabled = ShizukuSettings.isShakeForGuideEnabled()
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return enabled
}

@Composable
internal fun ShakeGuideHost(topic: GuideTopic? = null) {
    if (!LocalShakeGuideActive.current || !rememberShakeGuideEnabled()) return

    val context = LocalContext.current
    val view = LocalView.current
    val focused = LocalWindowInfo.current.isWindowFocused
    val token = remember { Any() }
    val currentView by rememberUpdatedState(view)
    val currentTopic by rememberUpdatedState(topic)
    var openedTopic by remember { mutableStateOf<GuideTopic?>(null) }

    // Modal windows take focus from their underlying host, even when the activity stays resumed.
    LifecycleResumeEffect(token) {
        onPauseOrDispose { ShakeHostStack.dismiss(token) }
    }
    LifecycleResumeEffect(token, focused) {
        if (focused) ShakeHostStack.resumedHosts.add(token)
        onPauseOrDispose { ShakeHostStack.resumedHosts.remove(token) }
    }

    val detector = remember(context, token) {
        ShakeDetector(context.applicationContext) {
            if (currentView.hasWindowFocus() && ShakeHostStack.isTop(token) &&
                ShakeHostStack.sheetOwner == null && ShizukuSettings.isShakeForGuideEnabled()
            ) {
                openedTopic = currentTopic
                ShakeHostStack.sheetOwner = token
                currentView.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            }
        }
    }
    val listen = focused && ShakeHostStack.isTop(token) && ShakeHostStack.sheetOwner == null
    LifecycleResumeEffect(detector, listen) {
        if (listen) detector.start()
        onPauseOrDispose { detector.stop() }
    }

    if (ShakeHostStack.sheetOwner === token) {
        GuideBottomSheet(openedTopic, onDismiss = { ShakeHostStack.dismiss(token) })
    }
}
