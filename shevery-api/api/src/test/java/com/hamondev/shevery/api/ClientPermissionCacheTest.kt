package com.hamondev.shevery.api

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

class ClientPermissionCacheTest {

    @Test
    fun cachesPermissionsWithinTtlAndRefreshesAfterExpiry() {
        val clock = AtomicLong(1_000L)
        val fetches = AtomicInteger(0)
        var returnedPerms: Array<String>? = arrayOf(SheveryApi.PERMISSION_API)

        val cache = ClientPermissionCache(
            source = { _, _ ->
                fetches.incrementAndGet()
                returnedPerms
            },
            ttlMs = 5_000L,
            uptimeMillisProvider = { clock.get() }
        )

        assertEquals(ClientPermissions.SHEVERY_API, cache.get(10123, "com.example.app"))
        assertEquals(1, fetches.get())

        clock.addAndGet(4_999L)
        returnedPerms = arrayOf(SheveryApi.PERMISSION_API, SheveryApi.PERMISSION_MANAGER)
        assertEquals(ClientPermissions.SHEVERY_API, cache.get(10123, "com.example.app"))
        assertEquals(1, fetches.get())

        clock.addAndGet(1L)
        assertEquals(
            ClientPermissions.SHEVERY_API or ClientPermissions.MANAGER,
            cache.get(10123, "com.example.app")
        )
        assertEquals(2, fetches.get())
    }

    @Test
    fun sourceFailureKeepsStaleEntryOrReturnsNoneWhenUncached() {
        val clock = AtomicLong(10_000L)
        var shouldFail = true
        val cache = ClientPermissionCache(
            source = { _, _ ->
                if (shouldFail) throw IOException("PM unavailable")
                arrayOf(LegacyShizukuStub.PERMISSION_API_V23)
            },
            ttlMs = 1_000L,
            uptimeMillisProvider = { clock.get() }
        )

        assertEquals(ClientPermissions.NONE, cache.get(10200, "com.example.uncached"))

        shouldFail = false
        assertEquals(ClientPermissions.LEGACY_V23, cache.get(10200, "com.example.uncached"))

        clock.addAndGet(2_000L)
        shouldFail = true
        assertEquals(ClientPermissions.LEGACY_V23, cache.get(10200, "com.example.uncached"))
    }

    @Test
    fun seedingAndInvalidationHelpersWorkForMultiUserUids() {
        val clock = AtomicLong(500L)
        val seenUsers = mutableListOf<Int>()
        val cache = ClientPermissionCache(
            source = { _, userId ->
                seenUsers.add(userId)
                arrayOf(SheveryApi.PERMISSION_API)
            },
            ttlMs = 10_000L,
            uptimeMillisProvider = { clock.get() }
        )

        val user10Uid = 10 * SheveryApi.PER_USER_RANGE + 10111
        val seeded = cache.put(user10Uid, "com.example.a", arrayOf(LegacyShizukuStub.PERMISSION_API_V23))
        assertEquals(ClientPermissions.LEGACY_V23, seeded)
        assertEquals(ClientPermissions.LEGACY_V23, cache.get(user10Uid, "com.example.a"))
        assertEquals(0, seenUsers.size)

        cache.put(user10Uid, "com.example.b", arrayOf(SheveryApi.PERMISSION_MANAGER))
        cache.invalidate(user10Uid, "com.example.a")
        assertEquals(ClientPermissions.SHEVERY_API, cache.get(user10Uid, "com.example.a"))
        assertEquals(listOf(10), seenUsers)
        assertEquals(ClientPermissions.MANAGER, cache.get(user10Uid, "com.example.b"))

        cache.invalidateUid(user10Uid)
        assertEquals(ClientPermissions.SHEVERY_API, cache.get(user10Uid, "com.example.b"))
        assertEquals(listOf(10, 10), seenUsers)

        cache.clear()
        assertEquals(ClientPermissions.SHEVERY_API, cache.get(10111, "com.example.root"))
        assertEquals(listOf(10, 10, 0), seenUsers)
    }
}
