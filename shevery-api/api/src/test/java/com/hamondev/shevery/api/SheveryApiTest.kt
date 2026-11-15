package com.hamondev.shevery.api

import org.junit.Assert.assertEquals
import org.junit.Test

class SheveryApiTest {

    @Test
    fun contractConstantsMatchExpectedIdentifiers() {
        assertEquals(1, SheveryApi.VERSION)
        assertEquals("com.hamondev.shevery", SheveryApi.MANAGER_PACKAGE)
        assertEquals("com.hamondev.shevery.permission.PRIVILEGED_API", SheveryApi.PERMISSION_API)
        assertEquals("com.hamondev.shevery.permission-group.API", SheveryApi.PERMISSION_GROUP_API)
        assertEquals("com.hamondev.shevery.permission.MANAGER", SheveryApi.PERMISSION_MANAGER)
        assertEquals(100000, SheveryApi.PER_USER_RANGE)

        assertEquals("moe.shizuku.privileged.api", LegacyShizukuStub.PACKAGE)
        assertEquals("moe.shizuku.manager.permission.API_V23", LegacyShizukuStub.PERMISSION_API_V23)
    }

    @Test
    fun getUserIdExtractsAndroidUserFromUid() {
        assertEquals(0, SheveryApi.getUserId(0))
        assertEquals(0, SheveryApi.getUserId(2000))
        assertEquals(0, SheveryApi.getUserId(10123))
        assertEquals(10, SheveryApi.getUserId(1010123))
        assertEquals(999, SheveryApi.getUserId(99910123))
    }
}
