package moe.shizuku.manager.security

import android.content.Context
import android.os.SystemClock
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

object AuthManager {

    private const val AUTH_TYPES = BIOMETRIC_STRONG or DEVICE_CREDENTIAL

    @Volatile
    private var lastAuthenticatedTimestamp: Long = 0L

    fun canAuthenticate(context: Context): Boolean {
        val biometricManager = BiometricManager.from(context)
        val canAuth = biometricManager.canAuthenticate(AUTH_TYPES)
        return canAuth == BiometricManager.BIOMETRIC_SUCCESS
    }

    fun isSessionValid(): Boolean {
        if (!SecuritySettings.isAuthEnabled) return true
        val timeout = SecuritySettings.timeoutSeconds
        if (timeout <= 0) return false
        val elapsed = (SystemClock.elapsedRealtime() - lastAuthenticatedTimestamp) / 1000
        return elapsed < timeout
    }

    fun markAuthenticated() {
        lastAuthenticatedTimestamp = SystemClock.elapsedRealtime()
    }

    fun invalidateSession() {
        lastAuthenticatedTimestamp = 0L
    }

    fun executeWithAuth(
        activity: FragmentActivity,
        action: SecuritySettings.ProtectedAction,
        title: String,
        subtitle: String? = null,
        onSuccess: () -> Unit
    ) {
        if (!SecuritySettings.isActionProtected(action) || isSessionValid()) {
            onSuccess()
            return
        }

        authenticate(
            activity = activity,
            title = title,
            subtitle = subtitle,
            onResult = { authenticated ->
                if (authenticated) {
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
