package moe.shizuku.manager.module

import android.os.ParcelFileDescriptor

internal object ModuleNative {

    val isAvailable: Boolean = try {
        System.loadLibrary("adb")
        true
    } catch (_: Throwable) {
        false
    }

    @JvmStatic
    external fun nativeModuleFingerprint(dirPath: String): String?

    @JvmStatic
    external fun nativeReadFdTail(fd: Int, maxChars: Int): String?

    fun readPfdTail(pfd: ParcelFileDescriptor, maxChars: Int): String? {
        if (!isAvailable) return null
        return try {
            val rawFd = pfd.detachFd()
            nativeReadFdTail(rawFd, maxChars)
        } catch (_: Throwable) {
            null
        }
    }
}
