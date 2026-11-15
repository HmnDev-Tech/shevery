package moe.shizuku.manager.utils

import android.system.Os

/** Android multi-user UID/AppID calculation helpers for the manager app. */
object UserHandleCompat {

    const val PER_USER_RANGE = 100000

    private val MY_USER_ID: Int by lazy { getUserId(Os.getuid()) }

    @JvmStatic
    fun getUserId(uid: Int): Int = uid / PER_USER_RANGE

    @JvmStatic
    fun getAppId(uid: Int): Int = uid % PER_USER_RANGE

    @JvmStatic
    fun myUserId(): Int = MY_USER_ID
}
