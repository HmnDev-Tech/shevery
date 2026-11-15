package moe.shizuku.manager.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class UserHandleCompatTest {

    @Test
    fun perUserRangeMatchesAndroidUserHandleContract() {
        assertEquals(100000, UserHandleCompat.PER_USER_RANGE)
    }

    @Test
    fun getUserIdAndGetAppIdSplitMultiUserUids() {
        assertEquals(0, UserHandleCompat.getUserId(0))
        assertEquals(0, UserHandleCompat.getAppId(0))

        assertEquals(0, UserHandleCompat.getUserId(2000))
        assertEquals(2000, UserHandleCompat.getAppId(2000))

        assertEquals(0, UserHandleCompat.getUserId(10123))
        assertEquals(10123, UserHandleCompat.getAppId(10123))

        assertEquals(10, UserHandleCompat.getUserId(1010123))
        assertEquals(10123, UserHandleCompat.getAppId(1010123))

        assertEquals(100, UserHandleCompat.getUserId(10010456))
        assertEquals(10456, UserHandleCompat.getAppId(10010456))
    }
}
