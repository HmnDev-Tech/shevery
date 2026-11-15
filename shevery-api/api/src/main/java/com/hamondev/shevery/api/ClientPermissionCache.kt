package com.hamondev.shevery.api

import android.os.SystemClock
import java.util.concurrent.ConcurrentHashMap

/**
 * Lock-free cache of [ClientPermissions] flags keyed by `(uid, packageName)`.
 *
 * The server is notified about uid state changes (active / idle / cached) many times per
 * second; previously every notification cost one `getPackageInfo(GET_PERMISSIONS)` binder
 * call per package of the uid. With this cache a hot uid costs a map lookup.
 *
 * Invalidation: callers must call [invalidateUid] when a uid goes away (package updates
 * and uninstalls always kill the uid first). A TTL bounds staleness for anything that slips through.
 */
class ClientPermissionCache internal constructor(
    private val source: Source,
    private val ttlMs: Long,
    private val uptimeMillisProvider: () -> Long
) {

    /** Source of truth, typically `PackageManager.getPackageInfo(GET_PERMISSIONS)`. */
    fun interface Source {
        /**
         * @return requested permissions of the package, or `null` if unknown.
         * @throws Exception on transient failures; the result is not cached then.
         */
        @Throws(Exception::class)
        fun getRequestedPermissions(packageName: String, userId: Int): Array<String>?
    }

    private class Entry(
        @JvmField val flags: Int,
        @JvmField val expiresAt: Long
    )

    private val entries = ConcurrentHashMap<Int, ConcurrentHashMap<String, Entry>>()

    @JvmOverloads
    constructor(
        source: Source,
        ttlMs: Long = DEFAULT_TTL_MS
    ) : this(source, ttlMs, { SystemClock.uptimeMillis() })

    /** Returns [ClientPermissions] flags of [packageName] running as [uid]. */
    fun get(uid: Int, packageName: String): Int {
        val now = uptimeMillisProvider()
        val entry = entries[uid]?.get(packageName)
        if (entry != null && entry.expiresAt > now) {
            return entry.flags
        }

        val requested = try {
            source.getRequestedPermissions(packageName, SheveryApi.getUserId(uid))
        } catch (_: Throwable) {
            return entry?.flags ?: ClientPermissions.NONE
        }
        val flags = ClientPermissions.classify(requested)
        store(uid, packageName, flags, now)
        return flags
    }

    /** Seeds the cache from an already fetched permission list (e.g. bulk enumeration). */
    fun put(uid: Int, packageName: String, requestedPermissions: Array<String>?): Int {
        val flags = ClientPermissions.classify(requestedPermissions)
        store(uid, packageName, flags, uptimeMillisProvider())
        return flags
    }

    fun invalidateUid(uid: Int) {
        entries.remove(uid)
    }

    fun invalidate(uid: Int, packageName: String) {
        entries[uid]?.remove(packageName)
    }

    fun clear() {
        entries.clear()
    }

    private fun store(uid: Int, packageName: String, flags: Int, now: Long) {
        var byPackage = entries[uid]
        if (byPackage == null) {
            val created = ConcurrentHashMap<String, Entry>(2)
            byPackage = entries.putIfAbsent(uid, created) ?: created
        }
        byPackage[packageName] = Entry(flags, now + ttlMs)
    }

    companion object {
        const val DEFAULT_TTL_MS = 5 * 60 * 1000L
    }
}
