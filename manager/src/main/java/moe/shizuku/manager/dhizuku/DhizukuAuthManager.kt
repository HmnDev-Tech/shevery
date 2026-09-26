package moe.shizuku.manager.dhizuku

import android.content.Context
import android.os.Process
import java.util.concurrent.ConcurrentHashMap

object DhizukuAuthManager {

    private const val PREFS_NAME = "dhizuku_permissions"
    private const val KEY_GRANTED_UIDS = "granted_uids"

    // In-memory set for one-time (session-only, until app terminates or reboot) grants
    private val sessionGrantedUids = ConcurrentHashMap.newKeySet<Int>()

    fun grant(context: Context, uid: Int, onetime: Boolean) {
        if (onetime) {
            sessionGrantedUids.add(uid)
        } else {
            sessionGrantedUids.remove(uid)
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val current = prefs.getStringSet(KEY_GRANTED_UIDS, emptySet())?.toMutableSet() ?: mutableSetOf()
            current.add(uid.toString())
            prefs.edit().putStringSet(KEY_GRANTED_UIDS, current).apply()
        }
    }

    fun isOneTime(uid: Int): Boolean {
        return sessionGrantedUids.contains(uid)
    }

    fun revoke(context: Context, uid: Int) {
        sessionGrantedUids.remove(uid)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val current = prefs.getStringSet(KEY_GRANTED_UIDS, emptySet())?.toMutableSet() ?: return
        if (current.remove(uid.toString())) {
            prefs.edit().putStringSet(KEY_GRANTED_UIDS, current).apply()
        }
    }

    fun isGranted(context: Context, uid: Int): Boolean {
        if (uid == Process.myUid()) return true
        if (sessionGrantedUids.contains(uid)) return true
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val persistent = prefs.getStringSet(KEY_GRANTED_UIDS, emptySet()) ?: emptySet()
        if (persistent.contains(uid.toString())) return true

        val pkgs = context.packageManager.getPackagesForUid(uid) ?: return false
        for (pkg in pkgs) {
            if (moe.shizuku.manager.deviceowner.DeviceOwnerManager.getDelegatedScopes(context, pkg).isNotEmpty()) {
                return true
            }
        }
        return false
    }

    fun getAllGrantedUids(context: Context): Set<Int> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val persistent = prefs.getStringSet(KEY_GRANTED_UIDS, emptySet()) ?: emptySet()
        val result = sessionGrantedUids.toMutableSet()
        persistent.mapNotNullTo(result) { it.toIntOrNull() }
        return result
    }
}
