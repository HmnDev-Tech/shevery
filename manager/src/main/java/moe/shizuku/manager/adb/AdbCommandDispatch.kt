package moe.shizuku.manager.adb

import java.io.OutputStream

/** Preparation and cancellation can prove "not sent"; a failed write generally cannot. */
internal object AdbCommandDispatch {
    fun write(output: OutputStream, packet: ByteArray, checkAllowed: () -> Unit,
              markPossiblyDispatched: () -> Unit) {
        checkAllowed()
        // Keep this immediately next to the first transport write. A partial write/flush
        // error may still have delivered a complete command to adbd, so retain observation.
        markPossiblyDispatched()
        output.write(packet)
        output.flush()
    }
}
