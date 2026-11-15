package rikka.shizuku.server

import com.hamondev.shevery.api.SheveryApiConstants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerConstantsTest {

    @Test
    fun serverConstantsExposeExpectedIdentifiers() {
        assertEquals(50, ServerConstants.MANAGER_APP_NOT_FOUND)
        assertEquals(51, ServerConstants.MANAGER_APP_LOOKUP_UNAVAILABLE)
        assertEquals(SheveryApiConstants.MANAGER_APPLICATION_ID, ServerConstants.MANAGER_APPLICATION_ID)
        assertEquals(SheveryApiConstants.SHIZUKU_STUB_APPLICATION_ID, ServerConstants.SHIZUKU_STUB_APPLICATION_ID)
        assertEquals(SheveryApiConstants.PERMISSION_PRIVILEGED_API, ServerConstants.PERMISSION)
        assertEquals(SheveryApiConstants.LEGACY_SHIZUKU_PERMISSION, ServerConstants.LEGACY_SHIZUKU_PERMISSION)
        assertEquals(10001, ServerConstants.BINDER_TRANSACTION_getApplications)
    }

    @Test
    fun clientPermissionResolutionPrefersNativeOverLegacy() {
        assertFalse(ServerConstants.hasAnyClientPermission(null))
        assertNull(ServerConstants.resolveClientPermission(null))

        val both = arrayOf(
            ServerConstants.LEGACY_SHIZUKU_PERMISSION,
            ServerConstants.PERMISSION
        )
        assertTrue(ServerConstants.hasSheveryPermission(both))
        assertTrue(ServerConstants.hasLegacyShizukuPermission(both))
        assertTrue(ServerConstants.hasAnyClientPermission(both))
        assertEquals(ServerConstants.PERMISSION, ServerConstants.resolveClientPermission(both))

        val legacyOnly = arrayOf(ServerConstants.LEGACY_SHIZUKU_PERMISSION)
        assertFalse(ServerConstants.hasSheveryPermission(legacyOnly))
        assertTrue(ServerConstants.hasLegacyShizukuPermission(legacyOnly))
        assertEquals(
            ServerConstants.LEGACY_SHIZUKU_PERMISSION,
            ServerConstants.resolveClientPermission(legacyOnly)
        )
    }

    @Test
    fun managerPermissionResolutionHandlesNativeLegacyAndNone() {
        assertNull(ServerConstants.resolveManagerPermission(null))
        assertNull(ServerConstants.resolveManagerPermission(arrayOf(ServerConstants.PERMISSION)))
        assertEquals(
            ServerConstants.PERMISSION_MANAGER,
            ServerConstants.resolveManagerPermission(
                arrayOf(ServerConstants.LEGACY_PERMISSION_MANAGER, ServerConstants.PERMISSION_MANAGER)
            )
        )
        assertEquals(
            ServerConstants.LEGACY_PERMISSION_MANAGER,
            ServerConstants.resolveManagerPermission(arrayOf(ServerConstants.LEGACY_PERMISSION_MANAGER))
        )
    }
}
