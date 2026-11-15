package com.hamondev.shevery.tasker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PluginContractTest {

    @Test
    fun commandFromParsesAllSupportedValuesAndRejectsUnknown() {
        assertEquals(Command.START, Command.from("start"))
        assertEquals(Command.STOP, Command.from("stop"))
        assertEquals(Command.RESTART, Command.from("restart"))
        assertEquals(Command.TOGGLE, Command.from("toggle"))

        assertNull(Command.from("unknown"))
        assertNull(Command.from(""))
        assertNull(Command.from(null))
        assertEquals(4, Command.entries.size)
    }

    @Test
    fun contractConstantsMatchExpectedActionsAndResultCodes() {
        assertEquals("com.hamondev.shevery", PluginContract.MANAGER_PACKAGE)
        assertEquals("moe.shizuku.manager.receiver.SheveryControlReceiver", PluginContract.MANAGER_CONTROL_RECEIVER)
        assertEquals("com.twofortyfouram.locale.intent.action.FIRE_SETTING", PluginContract.ACTION_FIRE_SETTING)
        assertEquals("com.twofortyfouram.locale.intent.action.QUERY_CONDITION", PluginContract.ACTION_QUERY_CONDITION)
        assertEquals(16, PluginContract.RESULT_CONDITION_SATISFIED)
        assertEquals(17, PluginContract.RESULT_CONDITION_UNSATISFIED)
        assertEquals(18, PluginContract.RESULT_CONDITION_UNKNOWN)
    }
}
