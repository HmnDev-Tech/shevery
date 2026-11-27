package moe.shizuku.manager.utils

import java.io.InputStream
import java.io.OutputStream
import java.util.zip.CRC32
import java.util.zip.CheckedInputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

internal fun InputStream.copyZipBytesTo(output: OutputStream, maxBytes: Long): Long {
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var written = 0L
    while (true) {
        val count = read(buffer)
        if (count == -1) return written
        written += count
        require(written <= maxBytes) { "ZIP exceeds the size limit." }
        output.write(buffer, 0, count)
    }
}

internal fun ZipFile.copyVerifiedEntryTo(entry: ZipEntry, output: OutputStream, maxBytes: Long): Long {
    require(entry.size in 0..maxBytes) { "ZIP entry exceeds the size limit: ${entry.name}" }
    return CheckedInputStream(getInputStream(entry), CRC32()).use { input ->
        val written = input.copyZipBytesTo(output, maxBytes)
        require(written == entry.size && input.checksum.value == entry.crc) {
            "Damaged ZIP entry: ${entry.name}"
        }
        written
    }
}
