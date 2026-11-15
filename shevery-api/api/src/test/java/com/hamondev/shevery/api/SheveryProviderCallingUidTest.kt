package com.hamondev.shevery.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SheveryProviderCallingUidTest {

    @Test
    fun trustedUidsAreAllowed() {
        val myUid = 10150
        assertTrue(SheveryApi.isTrustedProviderCallerUid(0, myUid))
        assertTrue(SheveryApi.isTrustedProviderCallerUid(2000, myUid))
        assertTrue(SheveryApi.isTrustedProviderCallerUid(myUid, myUid))

        SheveryApi.enforceCallingUid(0, myUid)
        SheveryApi.enforceCallingUid(2000, myUid)
        SheveryApi.enforceCallingUid(myUid, myUid)
    }

    @Test
    fun untrustedUidsThrowSecurityException() {
        val myUid = 10150
        val rejectedUids = intArrayOf(10234, 1000, -1, 99999, 100010150)
        for (uid in rejectedUids) {
            assertFalse("Expected uid $uid to be untrusted", SheveryApi.isTrustedProviderCallerUid(uid, myUid))
            try {
                SheveryApi.enforceCallingUid(uid, myUid)
                org.junit.Assert.fail("Expected SecurityException for uid=$uid")
            } catch (e: SecurityException) {
                assertEquals("Unauthorized binder delivery from uid=$uid", e.message)
            }
        }
    }
}
