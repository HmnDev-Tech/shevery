package moe.shizuku.manager.utils

import android.util.Log
import java.util.Locale

/** Structured wrapper around [android.util.Log] preserving R8 `-assumenosideeffects` strips. */
class Logger(private val TAG: String) {

    fun isLoggable(tag: String?, level: Int): Boolean = true

    fun v(msg: String) {
        if (isLoggable(TAG, Log.VERBOSE)) {
            Log.v(TAG, msg)
        }
    }

    fun v(fmt: String, vararg args: Any?) {
        if (isLoggable(TAG, Log.VERBOSE)) {
            Log.v(TAG, String.format(Locale.ENGLISH, fmt, *args))
        }
    }

    fun v(msg: String, tr: Throwable?) {
        if (isLoggable(TAG, Log.VERBOSE)) {
            Log.v(TAG, msg, tr)
        }
    }

    fun d(msg: String) {
        if (isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, msg)
        }
    }

    fun d(fmt: String, vararg args: Any?) {
        if (isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, String.format(Locale.ENGLISH, fmt, *args))
        }
    }

    fun d(msg: String, tr: Throwable?) {
        if (isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, msg, tr)
        }
    }

    fun i(msg: String) {
        if (isLoggable(TAG, Log.INFO)) {
            Log.i(TAG, msg)
        }
    }

    fun i(fmt: String, vararg args: Any?) {
        if (isLoggable(TAG, Log.INFO)) {
            Log.i(TAG, String.format(Locale.ENGLISH, fmt, *args))
        }
    }

    fun i(msg: String, tr: Throwable?) {
        if (isLoggable(TAG, Log.INFO)) {
            Log.i(TAG, msg, tr)
        }
    }

    fun w(msg: String) {
        if (isLoggable(TAG, Log.WARN)) {
            Log.w(TAG, msg)
        }
    }

    fun w(fmt: String, vararg args: Any?) {
        if (isLoggable(TAG, Log.WARN)) {
            Log.w(TAG, String.format(Locale.ENGLISH, fmt, *args))
        }
    }

    fun w(tr: Throwable?, fmt: String, vararg args: Any?) {
        if (isLoggable(TAG, Log.WARN)) {
            Log.w(TAG, String.format(Locale.ENGLISH, fmt, *args), tr)
        }
    }

    fun w(msg: String, tr: Throwable?) {
        if (isLoggable(TAG, Log.WARN)) {
            Log.w(TAG, msg, tr)
        }
    }

    fun e(msg: String) {
        if (isLoggable(TAG, Log.ERROR)) {
            Log.e(TAG, msg)
        }
    }

    fun e(fmt: String, vararg args: Any?) {
        if (isLoggable(TAG, Log.ERROR)) {
            Log.e(TAG, String.format(Locale.ENGLISH, fmt, *args))
        }
    }

    fun e(msg: String, tr: Throwable?) {
        if (isLoggable(TAG, Log.ERROR)) {
            Log.e(TAG, msg, tr)
        }
    }

    fun e(tr: Throwable?, fmt: String, vararg args: Any?) {
        if (isLoggable(TAG, Log.ERROR)) {
            Log.e(TAG, String.format(Locale.ENGLISH, fmt, *args), tr)
        }
    }

    companion object {
        @JvmField
        val LOGGER = Logger("ShizukuManager")
    }
}
