package moe.shizuku.manager.security

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import android.os.SystemClock
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

object AuthManager : Application.ActivityLifecycleCallbacks {

    private const val AUTH_TYPES = BIOMETRIC_STRONG or DEVICE_CREDENTIAL

    @Volatile
    private var isAppOpenUnlocked: Boolean = false

    private val authenticatedActionsInSession = java.util.Collections.synchronizedSet(
        mutableSetOf<SecuritySettings.ProtectedAction>()
    )

    @Volatile
    private var startedActivityCount: Int = 0

    @Volatile
    private var backgroundTimestamp: Long = 0L

    @Volatile
    private var isChangingConfig: Boolean = false

    fun canAuthenticate(context: Context): Boolean {
        val biometricManager = BiometricManager.from(context)
        val canAuth = biometricManager.canAuthenticate(AUTH_TYPES)
        return canAuth == BiometricManager.BIOMETRIC_SUCCESS
    }

    fun isAppOpenSessionValid(): Boolean {
        if (!SecuritySettings.isAuthEnabled || !SecuritySettings.authOnAppOpen) return true
        if (!isAppOpenUnlocked) return false

        // If the app is currently in background or returned from background, verify timeout
        if (backgroundTimestamp > 0L) {
            val elapsedMs = SystemClock.elapsedRealtime() - backgroundTimestamp
            if (elapsedMs >= 1000L) {
                val timeoutSec = SecuritySettings.timeoutSeconds
                if (timeoutSec <= 0) return false
                val elapsedSec = elapsedMs / 1000
                if (elapsedSec >= timeoutSec) return false
            }
        }
        return true
    }

    fun isSessionValid(): Boolean = isAppOpenSessionValid()

    fun markAppOpenAuthenticated() {
        isAppOpenUnlocked = true
        backgroundTimestamp = 0L
    }

    fun markAuthenticated() {
        markAppOpenAuthenticated()
    }

    fun isActionAuthenticatedInSession(action: SecuritySettings.ProtectedAction): Boolean {
        return authenticatedActionsInSession.contains(action)
    }

    fun markActionAuthenticatedInSession(action: SecuritySettings.ProtectedAction) {
        authenticatedActionsInSession.add(action)
    }

    fun invalidateSession() {
        isAppOpenUnlocked = false
        authenticatedActionsInSession.clear()
        backgroundTimestamp = 0L
    }

    override fun onActivityStarted(activity: Activity) {
        if (startedActivityCount == 0 && !isChangingConfig) {
            // App returning from background
            if (backgroundTimestamp > 0L) {
                val elapsedMs = SystemClock.elapsedRealtime() - backgroundTimestamp
                if (elapsedMs >= 1000L) {
                    val timeoutSec = SecuritySettings.timeoutSeconds
                    val elapsedSec = elapsedMs / 1000
                    if (timeoutSec <= 0 || elapsedSec >= timeoutSec) {
                        invalidateSession()
                    } else {
                        backgroundTimestamp = 0L
                    }
                } else {
                    backgroundTimestamp = 0L
                }
            }
        }
        startedActivityCount++
        isChangingConfig = false
    }

    override fun onActivityStopped(activity: Activity) {
        isChangingConfig = activity.isChangingConfigurations
        startedActivityCount--
        if (startedActivityCount <= 0 && !isChangingConfig) {
            startedActivityCount = 0
            backgroundTimestamp = SystemClock.elapsedRealtime()
        }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivityResumed(activity: Activity) {}
    override fun onActivityPaused(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {}

    fun executeWithAuth(
        activity: FragmentActivity,
        action: SecuritySettings.ProtectedAction,
        onSuccess: () -> Unit
    ) {
        val title = activity.getString(moe.shizuku.manager.R.string.security_auth_prompt_title)
        val subtitle = when (action) {
            SecuritySettings.ProtectedAction.DEVICE_OWNER -> activity.getString(moe.shizuku.manager.R.string.security_action_device_owner)
            SecuritySettings.ProtectedAction.PERMISSIONS -> activity.getString(moe.shizuku.manager.R.string.security_action_permissions)
            SecuritySettings.ProtectedAction.SERVER_TOGGLE -> activity.getString(moe.shizuku.manager.R.string.security_action_server)
            SecuritySettings.ProtectedAction.STUB_MANAGEMENT -> activity.getString(moe.shizuku.manager.R.string.security_action_stubs)
            SecuritySettings.ProtectedAction.APP_OPEN -> null
        }
        executeWithAuth(
            activity = activity,
            action = action,
            title = title,
            subtitle = subtitle,
            onSuccess = onSuccess
        )
    }

    fun executeWithAuth(
        activity: FragmentActivity,
        action: SecuritySettings.ProtectedAction,
        title: String,
        subtitle: String? = null,
        onSuccess: () -> Unit
    ) {
        if (!SecuritySettings.isActionProtected(action) || isActionAuthenticatedInSession(action)) {
            onSuccess()
            return
        }

        authenticate(
            activity = activity,
            title = title,
            subtitle = subtitle,
            onResult = { authenticated ->
                if (authenticated) {
                    markActionAuthenticatedInSession(action)
                    onSuccess()
                }
            }
        )
    }

    fun authenticate(
        activity: FragmentActivity,
        title: String,
        subtitle: String? = null,
        onResult: (Boolean) -> Unit
    ) {
        if (!canAuthenticate(activity)) {
            // If device cannot authenticate (no lock screen set), allow gracefully
            markAuthenticated()
            onResult(true)
            return
        }

        val executor = ContextCompat.getMainExecutor(activity)
        val prompt = BiometricPrompt(activity, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                markAuthenticated()
                onResult(true)
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                onResult(false)
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                // Called on soft failure (e.g. fingerprint not recognized, user can retry)
            }
        })

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .apply {
                if (!subtitle.isNullOrBlank()) {
                    setSubtitle(subtitle)
                }
            }
            .setAllowedAuthenticators(AUTH_TYPES)
            .build()

        try {
            prompt.authenticate(promptInfo)
        } catch (_: Exception) {
            onResult(false)
        }
    }
}
