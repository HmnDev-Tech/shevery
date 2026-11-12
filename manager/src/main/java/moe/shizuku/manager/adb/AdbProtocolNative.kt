package moe.shizuku.manager.adb

internal object AdbProtocolNative {

    val isAvailable: Boolean = try {
        System.loadLibrary("adb")
        true
    } catch (_: Throwable) {
        false
    }

    @JvmStatic
    external fun nativeEncodeMessage(
        command: Int,
        arg0: Int,
        arg1: Int,
        data: ByteArray?
    ): ByteArray?

    @JvmStatic
    external fun nativeChecksum(data: ByteArray?): Int

    @JvmStatic
    external fun nativeValidateMessage(
        command: Int,
        dataLength: Int,
        dataCrc32: Int,
        magic: Int,
        data: ByteArray?
    ): Boolean

    @JvmStatic
    external fun nativeEncodeRsaPublicKey(
        modulusBigEndian: ByteArray,
        exponent: Int,
        name: ByteArray
    ): ByteArray?
}
