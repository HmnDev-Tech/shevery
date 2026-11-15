package rikka.shizuku.server.util

import java.util.concurrent.Callable

/** Only repeated successful absence queries can stop the native supervisor. */
object ManagerApplicationLookup {

    class Result<T> internal constructor(
        @JvmField val application: T?,
        @JvmField val confirmedMissing: Boolean,
        @JvmField val failure: Exception?
    )

    @JvmStatic
    fun <T> resolve(query: Callable<T>, beforeRetry: Runnable): Result<T> {
        var consecutiveMissing = 0
        var failure: Exception? = null
        for (attempt in 0 until 5) {
            if (attempt > 0) beforeRetry.run()
            try {
                val application = query.call()
                if (application != null) {
                    return Result(application, confirmedMissing = false, failure = null)
                }
                consecutiveMissing++
            } catch (e: Exception) {
                failure = e
                consecutiveMissing = 0
            }
        }
        return Result(null, confirmedMissing = consecutiveMissing >= 3, failure = failure)
    }
}
