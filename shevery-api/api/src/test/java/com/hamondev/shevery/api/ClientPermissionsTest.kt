package com.hamondev.shevery.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClientPermissionsTest {

    @Test
    fun classifyHandlesNullEmptyAndSingleFlags() {
        assertEquals(ClientPermissions.NONE, ClientPermissions.classify(null))
        assertEquals(ClientPermissions.NONE, ClientPermissions.classify(emptyArray()))
        assertEquals(
            ClientPermissions.NONE,
            ClientPermissions.classify(arrayOf("android.permission.INTERNET"))
        )

        assertEquals(
            ClientPermissions.SHEVERY_API,
            ClientPermissions.classify(arrayOf(SheveryApi.PERMISSION_API))
        )
        assertEquals(
            ClientPermissions.LEGACY_V23,
            ClientPermissions.classify(arrayOf(LegacyShizukuStub.PERMISSION_API_V23))
        )
        assertEquals(
            ClientPermissions.MANAGER,
            ClientPermissions.classify(arrayOf(SheveryApi.PERMISSION_MANAGER))
        )
    }

    @Test
    fun classifyCombinesAllFlagsAndHelpersEvaluateBitmask() {
        val flags = ClientPermissions.classify(
            arrayOf(
                "android.permission.QUERY_ALL_PACKAGES",
                SheveryApi.PERMISSION_API,
                LegacyShizukuStub.PERMISSION_API_V23,
                SheveryApi.PERMISSION_MANAGER
            )
        )

        assertTrue(ClientPermissions.has(flags, ClientPermissions.SHEVERY_API))
        assertTrue(ClientPermissions.has(flags, ClientPermissions.LEGACY_V23))
        assertTrue(ClientPermissions.has(flags, ClientPermissions.MANAGER))
        assertTrue(ClientPermissions.isClient(flags))

        assertTrue(ClientPermissions.isClient(ClientPermissions.SHEVERY_API))
        assertTrue(ClientPermissions.isClient(ClientPermissions.LEGACY_V23))
        assertFalse(ClientPermissions.isClient(ClientPermissions.MANAGER))
        assertFalse(ClientPermissions.isClient(ClientPermissions.NONE))
    }
}
