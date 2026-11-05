package com.hamondev.shevery.tasker

import android.app.Activity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginAuthPolicyTest {
    @Test fun conditionQueriesRequireAuthAndFailUnknown() {
        assertTrue(requiresAuth(PluginContract.ACTION_QUERY_CONDITION))
        assertEquals(
            PluginContract.RESULT_CONDITION_UNKNOWN,
            authFailureResult(PluginContract.ACTION_QUERY_CONDITION)
        )
    }

    @Test fun settingsAndDirectActionsStillFailCanceled() {
        assertTrue(requiresAuth(PluginContract.ACTION_FIRE_SETTING))
        assertTrue(requiresAuth(PluginContract.ACTION_DIRECT_START))
        assertTrue(requiresAuth(PluginContract.ACTION_DIRECT_STOP))
        assertTrue(requiresAuth(PluginContract.ACTION_DIRECT_RESTART))
        assertTrue(requiresAuth(PluginContract.ACTION_DIRECT_TOGGLE))
        assertEquals(Activity.RESULT_CANCELED, authFailureResult(PluginContract.ACTION_FIRE_SETTING))
        assertFalse(requiresAuth(null))
    }
}
