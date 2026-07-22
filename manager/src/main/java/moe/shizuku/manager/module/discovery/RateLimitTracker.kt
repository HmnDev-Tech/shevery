package moe.shizuku.manager.module.discovery

import okhttp3.Headers

class RateLimitTracker {
    @Volatile
    private var remaining = 60
    @Volatile
    private var resetAt = 0L

    fun update(headers: Headers) {
        headers["x-ratelimit-remaining"]?.toIntOrNull()?.let { remaining = it }
        headers["x-ratelimit-reset"]?.toLongOrNull()?.let { resetAt = it * 1000 }
    }

    fun isExhausted(): Boolean = remaining < 5

    fun secondsUntilReset(): Long {
        val wait = (resetAt - System.currentTimeMillis()) / 1000
        return wait.coerceAtLeast(0)
    }

    fun getRemaining(): Int = remaining
}
