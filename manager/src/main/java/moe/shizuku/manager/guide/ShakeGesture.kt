package moe.shizuku.manager.guide

/** Counts distinct acceleration peaks using the sensor's monotonic clock. */
internal class ShakeGesture {
    private var armed = true
    private val peaks = ArrayDeque<Long>(REQUIRED_JERKS)
    private var lastShakeMs: Long? = null

    fun onSample(gForce: Float, timestampMs: Long): Boolean {
        if (!gForce.isFinite()) return false
        if (gForce <= RESET_THRESHOLD_G) armed = true
        if (!armed || gForce < SHAKE_THRESHOLD_G) return false
        armed = false

        lastShakeMs?.let { if (timestampMs - it < COOLDOWN_MS) return false }
        while (peaks.isNotEmpty() && timestampMs - peaks.first() > WINDOW_MS) {
            peaks.removeFirst()
        }
        if (peaks.isNotEmpty() && timestampMs - peaks.last() < MIN_JERK_GAP_MS) return false

        peaks.addLast(timestampMs)
        if (peaks.size < REQUIRED_JERKS) return false

        peaks.clear()
        lastShakeMs = timestampMs
        return true
    }

    fun reset() {
        armed = true
        peaks.clear()
    }

    private companion object {
        const val SHAKE_THRESHOLD_G = 1.9f
        const val RESET_THRESHOLD_G = 1.35f
        const val MIN_JERK_GAP_MS = 100L
        const val WINDOW_MS = 1_000L
        const val REQUIRED_JERKS = 3
        const val COOLDOWN_MS = 1_500L
    }
}
