package moe.shizuku.common.util

import android.os.SELinux
import android.system.Os

/** Process identity and SELinux context accessors. */
object OsUtils {

    private val UID: Int = Os.getuid()
    private val PID: Int = Os.getpid()
    private val SELINUX_CONTEXT: String? = try {
        SELinux.getContext()
    } catch (_: Throwable) {
        null
    }

    @JvmStatic
    fun getUid(): Int = UID

    @JvmStatic
    fun getPid(): Int = PID

    @JvmStatic
    fun getSELinuxContext(): String? = SELINUX_CONTEXT
}
