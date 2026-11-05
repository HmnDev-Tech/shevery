package com.hamondev.shevery.tasker

import android.app.Activity

internal fun requiresAuth(action: String?): Boolean = when (action) {
    PluginContract.ACTION_FIRE_SETTING,
    PluginContract.ACTION_QUERY_CONDITION,
    PluginContract.ACTION_DIRECT_START,
    PluginContract.ACTION_DIRECT_STOP,
    PluginContract.ACTION_DIRECT_RESTART,
    PluginContract.ACTION_DIRECT_TOGGLE -> true
    else -> false
}

internal fun authFailureResult(action: String?): Int =
    if (action == PluginContract.ACTION_QUERY_CONDITION) {
        PluginContract.RESULT_CONDITION_UNKNOWN
    } else {
        Activity.RESULT_CANCELED
    }
