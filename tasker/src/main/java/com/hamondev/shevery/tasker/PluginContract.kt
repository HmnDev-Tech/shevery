package com.hamondev.shevery.tasker

import androidx.annotation.StringRes

object PluginContract {

    const val ACTION_EDIT_SETTING = "com.twofortyfouram.locale.intent.action.EDIT_SETTING"
    const val ACTION_EDIT_CONDITION = "com.twofortyfouram.locale.intent.action.EDIT_CONDITION"
    const val ACTION_FIRE_SETTING = "com.twofortyfouram.locale.intent.action.FIRE_SETTING"
    const val ACTION_QUERY_CONDITION = "com.twofortyfouram.locale.intent.action.QUERY_CONDITION"

    const val EXTRA_STRING_BLURB = "com.twofortyfouram.locale.intent.extra.BLURB"
    const val EXTRA_BUNDLE = "com.twofortyfouram.locale.intent.extra.BUNDLE"
    const val EXTRA_STRING_JSON = "com.twofortyfouram.locale.extra.STRING"

    const val RESULT_CONDITION_SATISFIED = 16
    const val RESULT_CONDITION_UNSATISFIED = 17
    const val RESULT_CONDITION_UNKNOWN = 18

    const val KEY_COMMAND = "command"
    const val KEY_CONDITION = "condition"
    const val KEY_CONNECTOR_ENABLED = "shizuku_connector_enabled"
    const val KEY_AUTH_TOKEN = "auth_token"
    const val EXTRA_AUTH = "auth"
    const val VALUE_CONDITION_RUNNING = "running"

    const val MANAGER_PACKAGE = "com.hamondev.shevery"
    const val MANAGER_CONTROL_RECEIVER = "moe.shizuku.manager.receiver.SheveryControlReceiver"
    const val ACTION_START_SERVER = "moe.shizuku.manager.action.START_SERVER"
    const val ACTION_STOP_SERVER = "moe.shizuku.manager.action.STOP_SERVER"

    const val ACTION_DIRECT_START = "com.hamondev.shevery.action.START_SERVER"
    const val ACTION_DIRECT_STOP = "com.hamondev.shevery.action.STOP_SERVER"
    const val ACTION_DIRECT_RESTART = "com.hamondev.shevery.action.RESTART_SERVER"
    const val ACTION_DIRECT_TOGGLE = "com.hamondev.shevery.action.TOGGLE_SERVER"
}

enum class Command(
    val value: String,
    @StringRes val labelRes: Int,
    @StringRes val blurbRes: Int
) {
    START("start", R.string.tasker_command_start, R.string.tasker_blurb_start),
    STOP("stop", R.string.tasker_command_stop, R.string.tasker_blurb_stop),
    RESTART("restart", R.string.tasker_command_restart, R.string.tasker_blurb_restart),
    TOGGLE("toggle", R.string.tasker_command_toggle, R.string.tasker_blurb_toggle);

    companion object {
        fun from(value: String?): Command? = entries.firstOrNull { it.value == value }
    }
}
