package rikka.shizuku.server.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import rikka.shizuku.server.ClientManager

class ClientPackageValidatorTest {

    @Test
    fun ownedPackagePassesValidation() {
        val owned = listOf("com.example.client", "com.example.shared")
        assertTrue(ClientPackageValidator.verifyPackageOwnership(10123, "com.example.client", owned))
        ClientPackageValidator.enforcePackageOwnership(10123, "com.example.client", owned)
        ClientManager.enforcePackageOwnership(10123, "com.example.shared", owned)
    }

    @Test
    fun spoofedOrEmptyPackageFailsValidation() {
        val owned = listOf("com.example.real")
        assertFalse(ClientPackageValidator.verifyPackageOwnership(10123, "com.example.spoof", owned))
        assertFalse(ClientPackageValidator.verifyPackageOwnership(10123, "", owned))
        assertFalse(ClientPackageValidator.verifyPackageOwnership(10123, null, owned))
        assertFalse(ClientPackageValidator.verifyPackageOwnership(10123, "com.example.real", emptyList()))
        assertFalse(ClientPackageValidator.verifyPackageOwnership(10123, "com.example.real", null))

        try {
            ClientPackageValidator.enforcePackageOwnership(10123, "com.example.spoof", owned)
            fail("Expected SecurityException for spoofed package")
        } catch (e: SecurityException) {
            assertEquals("Request package com.example.spoof does not belong to uid 10123", e.message)
        }

        try {
            ClientManager.enforcePackageOwnership(10123, "", owned)
            fail("Expected SecurityException for empty package")
        } catch (e: SecurityException) {
            assertEquals("Request package must not be empty for uid 10123", e.message)
        }
    }
}
