package moe.shizuku.manager.stealth

internal object StealthNative {

    val isAvailable: Boolean = try {
        System.loadLibrary("adb")
        true
    } catch (_: Throwable) {
        false
    }

    @JvmStatic
    external fun nativeSwapFiltered(
        data: ByteArray,
        old: ByteArray,
        repl: ByteArray,
        isUtf16: Boolean
    ): ByteArray?

    @JvmStatic
    external fun nativePatchManifestLabel(
        axml: ByteArray,
        appName: String
    ): ByteArray?

    @JvmStatic
    external fun nativeFixDex(dex: ByteArray): ByteArray?

    @JvmStatic
    external fun nativeCrc32(data: ByteArray): Long

    @JvmStatic
    external fun nativeSha256(data: ByteArray): ByteArray?

    @JvmStatic
    external fun nativeComputeApkV2Digest(
        section1: ByteArray,
        section2: ByteArray,
        eocd: ByteArray
    ): ByteArray?
}
