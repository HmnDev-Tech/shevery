package moe.shizuku.manager.stealth

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import moe.shizuku.manager.ktx.logd
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.cert.X509CertificateHolder
import org.bouncycastle.cert.X509v3CertificateBuilder
import org.bouncycastle.cms.CMSProcessableByteArray
import org.bouncycastle.cms.CMSSignedDataGenerator
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder
import org.bouncycastle.operator.ContentSigner
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream
import java.math.BigInteger
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.Signature
import java.util.Date
import java.util.zip.CRC32
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

/**
 * Generates a randomized-package copy of Shevery directly from the installed
 * base APK ([android.content.pm.ApplicationInfo.sourceDir]) without bundling a
 * duplicate APK inside `assets/`.
 *
 * Key properties for Android 11–17 (API 30–37) compatibility and clean package
 * verification:
 * 1. Clones `context.applicationInfo.sourceDir` directly (eliminating the
 *    embedded `assets/shevery-stealth.apk` APK-in-APK payload).
 * 2. Rewrites the 20-char package ID (`com.hamondev.shevery` or the current
 *    stealth package ID) to a fresh 20-char `com.<8chars>.<7chars>` ID across
 *    `AndroidManifest.xml`, `resources.arsc`, `classes*.dex`,
 *    `assets/rish_shizuku.dex`, and `lib/<abi>/libshizuku.so` while preserving
 *    `.tasker` class descriptors (`com.hamondev.shevery.tasker.*`).
 * 3. Updates the launcher label in `AndroidManifest.xml` in-place via the
 *    dedicated 32-char AXML StringPool placeholder (`STEALTH_LABEL_PLACEHOLDER`)
 *    and switches `<application android:label>` to `TYPE_STRING` so custom
 *    names up to 32 characters work without trailing spaces or broken offsets.
 * 4. Recalculates DEX SHA-1 signature and Adler32 checksum on all modified DEX
 *    files.
 * 5. Writes a 4-byte aligned (`zipalign 4`) ZIP archive for all `STORED`
 *    entries (such as `resources.arsc`, required when `targetSdk >= 30`) and
 *    signs the output with BOTH v1 JAR signing and **APK Signature Scheme v2**
 *    (`APK Sig Block 42`, ID `0x7109871a`), required on Android 11+.
 */
object StealthApkGenerator {

    const val DEFAULT_PACKAGE = "com.hamondev.shevery"
    const val TEMPLATE_PACKAGE = DEFAULT_PACKAGE
    const val TEMPLATE_APP_NAME = "System Toolkit"
    const val STEALTH_LABEL_PLACEHOLDER = "SheveryStealthLabelPlaceholder32"

        private const val TASKER_SUFFIX = ".tasker"
    private val PRESERVED_SUFFIXES_ASCII = arrayOf(
        ".tasker".toByteArray(Charsets.US_ASCII),
        ".api".toByteArray(Charsets.US_ASCII),
        ".permission.PRIVILEGED_API".toByteArray(Charsets.US_ASCII)
    )
    private val PRESERVED_SUFFIXES_UTF16 = arrayOf(
        ".tasker".toByteArray(Charsets.UTF_16LE),
        ".api".toByteArray(Charsets.UTF_16LE),
        ".permission.PRIVILEGED_API".toByteArray(Charsets.UTF_16LE)
    )
    private const val APK_SIG_BLOCK_V2_ID = 0x7109871a
    private const val VERITY_PADDING_BLOCK_ID = 0x42726577
    private const val SIG_RSA_PKCS1_V1_5_WITH_SHA256 = 0x0103
    private const val CHUNK_SIZE_BYTES = 1024 * 1024

    private val random = SecureRandom()
    private val LETTERS = ('a'..'z').toList()
    private val ALNUM = (('a'..'z') + ('0'..'9')).toList()

    /**
     * Random package ID with the exact same 20-byte length as [DEFAULT_PACKAGE]
     * (`com.<8chars>.<7chars>`).
     */
    fun randomPackage(): String {
        fun seg(len: Int): String = buildString(len) {
            append(LETTERS[random.nextInt(LETTERS.size)])
            repeat(len - 1) { append(ALNUM[random.nextInt(ALNUM.size)]) }
        }
        return "com.${seg(8)}.${seg(7)}"
    }

    fun saveToDownloads(
        context: Context,
        fileName: String,
        packageName: String,
        appName: String = TEMPLATE_APP_NAME,
    ): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        if (packageName.length != DEFAULT_PACKAGE.length) {
            logd("Stealth package length mismatch: ${packageName.length} != ${DEFAULT_PACKAGE.length}")
            return null
        }
        val baseName = fileName.replace(Regex("[\\\\/:*?\"<>|]"), "").trim().takeIf { it.isNotEmpty() }
            ?: return null
        val displayName = if (baseName.endsWith(".apk", ignoreCase = true)) baseName else "$baseName.apk"

        val sourceApk = File(context.applicationInfo.sourceDir)
        if (!sourceApk.isFile || !sourceApk.canRead()) {
            logd("Cannot read installed sourceDir APK: ${sourceApk.absolutePath}")
            return null
        }

        val currentPkg = context.packageName.takeIf { it.length == DEFAULT_PACKAGE.length } ?: DEFAULT_PACKAGE
        val cleanAppName = appName.trim().ifEmpty { TEMPLATE_APP_NAME }.take(STEALTH_LABEL_PLACEHOLDER.length)

        val keyPair = KeyPairGenerator.getInstance("RSA").also { it.initialize(2048, random) }.generateKeyPair()
        val signer = JcaContentSignerBuilder("SHA256withRSA").build(keyPair.private)
        val certHolder = selfSignedCert(keyPair)

        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, displayName)
            put(MediaStore.Downloads.MIME_TYPE, "application/vnd.android.package-archive")
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(
            MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
            values
        ) ?: return null

        return try {
            resolver.openOutputStream(uri)?.use { rawOut ->
                buildSignedStealthApk(
                    sourceApk = sourceApk,
                    output = rawOut,
                    currentPkg = currentPkg,
                    newPkg = packageName,
                    appName = cleanAppName,
                    keyPair = keyPair,
                    certHolder = certHolder,
                    signer = signer
                )
            } ?: error("openOutputStream returned null")
            logd("Stealth APK saved as $displayName ($packageName)")
            displayName
        } catch (e: Throwable) {
            resolver.delete(uri, null, null)
            throw e
        }
    }

    private data class PreparedEntry(
        val name: String,
        val method: Int,
        val time: Long,
        val uncompressedData: ByteArray,
        val compressedData: ByteArray,
        val crc: Long
    )

    private fun buildSignedStealthApk(
        sourceApk: File,
        output: OutputStream,
        currentPkg: String,
        newPkg: String,
        appName: String,
        keyPair: KeyPair,
        certHolder: X509CertificateHolder,
        signer: ContentSigner,
    ) {
        val oldPkgs = linkedSetOf(DEFAULT_PACKAGE, currentPkg)
        val newPkgAscii = newPkg.toByteArray(Charsets.US_ASCII)
        val newPkgUtf16 = newPkg.toByteArray(Charsets.UTF_16LE)
        val taskerSuffixAscii = TASKER_SUFFIX.toByteArray(Charsets.US_ASCII)
        val taskerSuffixUtf16 = TASKER_SUFFIX.toByteArray(Charsets.UTF_16LE)

        val preparedEntries = ArrayList<PreparedEntry>()

        ZipFile(sourceApk).use { zf ->
            val rawEntries = java.util.Collections.list(zf.entries())
                .filter { !it.isDirectory && !it.name.startsWith("META-INF/") }

            val manifestOut = ByteArrayOutputStream()
            manifestOut.write("Manifest-Version: 1.0\r\nCreated-By: Android Gradle 9.4.0\r\n\r\n".toByteArray())
            val sfSections = ArrayList<Pair<String, ByteArray>>(rawEntries.size)

            val payloadEntries = ArrayList<PreparedEntry>(rawEntries.size)
            for (entry in rawEntries) {
                val transformed = readTransformedEntry(
                    zf = zf,
                    entry = entry,
                    oldPkgs = oldPkgs,
                    newPkgAscii = newPkgAscii,
                    newPkgUtf16 = newPkgUtf16,
                    taskerSuffixAscii = taskerSuffixAscii,
                    taskerSuffixUtf16 = taskerSuffixUtf16,
                    appName = appName
                )
                val digestB64 = b64(sha256(transformed))
                val section = manifestSection(entry.name, digestB64)
                manifestOut.write(section)
                sfSections.add(entry.name to section)

                val storeUncompressed = entry.method == ZipEntry.STORED || entry.name == "resources.arsc"
                val method = if (storeUncompressed) ZipEntry.STORED else ZipEntry.DEFLATED
                val compressed = if (method == ZipEntry.STORED) transformed else deflateRaw(transformed)
                payloadEntries.add(
                    PreparedEntry(
                        name = entry.name,
                        method = method,
                        time = entry.time.takeIf { it > 0 } ?: 315532800000L,
                        uncompressedData = transformed,
                        compressedData = compressed,
                        crc = crc32(transformed)
                    )
                )
            }

            val manifestBytes = manifestOut.toByteArray()
            val sfOut = ByteArrayOutputStream()
            sfOut.write(
                ("Signature-Version: 1.0\r\nCreated-By: Android Gradle 9.4.0\r\n" +
                    "SHA-256-Digest-Manifest: ${b64(sha256(manifestBytes))}\r\n\r\n").toByteArray()
            )
            for ((name, section) in sfSections) {
                sfOut.write(manifestSection(name, b64(sha256(section))))
            }
            val sfBytes = sfOut.toByteArray()
            val rsaBytes = cmsBlock(sfBytes, certHolder, signer)

            fun metaEntry(name: String, bytes: ByteArray) = PreparedEntry(
                name = name,
                method = ZipEntry.STORED,
                time = 315532800000L,
                uncompressedData = bytes,
                compressedData = bytes,
                crc = crc32(bytes)
            )

            preparedEntries.add(metaEntry("META-INF/MANIFEST.MF", manifestBytes))
            preparedEntries.add(metaEntry("META-INF/CERT.SF", sfBytes))
            preparedEntries.add(metaEntry("META-INF/CERT.RSA", rsaBytes))
            preparedEntries.addAll(payloadEntries)
        }

        // Build Section 1 (Local File Headers + aligned data) and Section 2 (Central Directory)
        val section1 = ByteArrayOutputStream()
        val centralDir = ByteArrayOutputStream()
        var currentOffset = 0

        for (entry in preparedEntries) {
            val nameBytes = entry.name.toByteArray(Charsets.UTF_8)
            val extraLen = if (entry.method == ZipEntry.STORED) {
                val alignment = if (entry.name.endsWith(".so")) 4096 else 4
                val headerEnd = currentOffset + 30 + nameBytes.size
                (alignment - (headerEnd % alignment)) % alignment
            } else {
                0
            }
            val extraBytes = ByteArray(extraLen)
            val localHeaderOffset = currentOffset
            val dosTime = toDosTime(entry.time)

            // Local File Header (30 bytes + name + extra)
            val lfh = ByteBuffer.allocate(30 + nameBytes.size + extraBytes.size).order(ByteOrder.LITTLE_ENDIAN)
            lfh.putInt(0x04034b50)
            lfh.putShort(20) // version needed
            lfh.putShort(0x0800.toShort()) // UTF-8 flag, no data descriptor
            lfh.putShort(entry.method.toShort())
            lfh.putInt(dosTime)
            lfh.putInt(entry.crc.toInt())
            lfh.putInt(entry.compressedData.size)
            lfh.putInt(entry.uncompressedData.size)
            lfh.putShort(nameBytes.size.toShort())
            lfh.putShort(extraBytes.size.toShort())
            lfh.put(nameBytes)
            lfh.put(extraBytes)
            val lfhBytes = lfh.array()
            section1.write(lfhBytes)
            section1.write(entry.compressedData)
            currentOffset += lfhBytes.size + entry.compressedData.size

            // Central Directory File Header (46 bytes + name)
            val cdh = ByteBuffer.allocate(46 + nameBytes.size).order(ByteOrder.LITTLE_ENDIAN)
            cdh.putInt(0x02014b50)
            cdh.putShort(20) // version made by
            cdh.putShort(20) // version needed
            cdh.putShort(0x0800.toShort()) // UTF-8 flag
            cdh.putShort(entry.method.toShort())
            cdh.putInt(dosTime)
            cdh.putInt(entry.crc.toInt())
            cdh.putInt(entry.compressedData.size)
            cdh.putInt(entry.uncompressedData.size)
            cdh.putShort(nameBytes.size.toShort())
            cdh.putShort(0) // extra length
            cdh.putShort(0) // comment length
            cdh.putShort(0) // disk number
            cdh.putShort(0) // internal attrs
            cdh.putInt(0) // external attrs
            cdh.putInt(localHeaderOffset)
            cdh.put(nameBytes)
            centralDir.write(cdh.array())
        }

        val section1Bytes = section1.toByteArray()
        val section2Bytes = centralDir.toByteArray()
        val entryCount = preparedEntries.size

        // For APK Signature Scheme v2 digest calculation, EOCD has Central Directory offset = section1Bytes.size
        val eocdForDigest = buildEocd(entryCount, section2Bytes.size, section1Bytes.size)
        val v2SigningBlock = buildApkSigningBlockV2(
            section1 = section1Bytes,
            section2 = section2Bytes,
            eocd = eocdForDigest,
            keyPair = keyPair,
            certBytes = certHolder.encoded
        )
        val finalEocd = buildEocd(
            entryCount = entryCount,
            cdSize = section2Bytes.size,
            cdOffset = section1Bytes.size + v2SigningBlock.size
        )

        output.write(section1Bytes)
        output.write(v2SigningBlock)
        output.write(section2Bytes)
        output.write(finalEocd)
        output.flush()
    }

    private fun buildEocd(entryCount: Int, cdSize: Int, cdOffset: Int): ByteArray {
        val buf = ByteBuffer.allocate(22).order(ByteOrder.LITTLE_ENDIAN)
        buf.putInt(0x06054b50)
        buf.putShort(0)
        buf.putShort(0)
        buf.putShort(entryCount.toShort())
        buf.putShort(entryCount.toShort())
        buf.putInt(cdSize)
        buf.putInt(cdOffset)
        buf.putShort(0)
        return buf.array()
    }

    /**
     * Builds an Android 11+ compliant APK Signature Scheme v2 block (`0x7109871a`)
     * padded to a 4096-byte multiple with `VERITY_PADDING_BLOCK_ID` (`0x42726577`).
     */
    private fun buildApkSigningBlockV2(
        section1: ByteArray,
        section2: ByteArray,
        eocd: ByteArray,
        keyPair: KeyPair,
        certBytes: ByteArray
    ): ByteArray {
        val chunkDigests = ByteArrayOutputStream()
        var totalChunks = 0

        fun digestSection(data: ByteArray) {
            var offset = 0
            while (offset < data.size) {
                val len = minOf(CHUNK_SIZE_BYTES, data.size - offset)
                val md = MessageDigest.getInstance("SHA-256")
                val header = ByteBuffer.allocate(5).order(ByteOrder.LITTLE_ENDIAN)
                header.put(0xa5.toByte())
                header.putInt(len)
                md.update(header.array())
                md.update(data, offset, len)
                chunkDigests.write(md.digest())
                totalChunks++
                offset += len
            }
        }

        digestSection(section1)
        digestSection(section2)
        digestSection(eocd)

        val topMd = MessageDigest.getInstance("SHA-256")
        val topHeader = ByteBuffer.allocate(5).order(ByteOrder.LITTLE_ENDIAN)
        topHeader.put(0x5a.toByte())
        topHeader.putInt(totalChunks)
        topMd.update(topHeader.array())
        topMd.update(chunkDigests.toByteArray())
        val topLevelDigest = topMd.digest()

        // digest entry: uint32(algoId) + lengthPrefixed(digest)
        val digestEntry = ByteBuffer.allocate(4 + 4 + topLevelDigest.size).order(ByteOrder.LITTLE_ENDIAN).apply {
            putInt(SIG_RSA_PKCS1_V1_5_WITH_SHA256)
            putInt(topLevelDigest.size)
            put(topLevelDigest)
        }.array()
        val digestsSeq = lengthPrefixed(lengthPrefixed(digestEntry))
        val certsSeq = lengthPrefixed(lengthPrefixed(certBytes))
        val additionalAttrs = ByteArray(4) // empty uint32(0)

        val signedData = ByteArrayOutputStream().apply {
            write(digestsSeq)
            write(certsSeq)
            write(additionalAttrs)
        }.toByteArray()

        val sig = Signature.getInstance("SHA256withRSA")
        sig.initSign(keyPair.private)
        sig.update(signedData)
        val signatureBytes = sig.sign()

        val signatureEntry = ByteBuffer.allocate(4 + 4 + signatureBytes.size).order(ByteOrder.LITTLE_ENDIAN).apply {
            putInt(SIG_RSA_PKCS1_V1_5_WITH_SHA256)
            putInt(signatureBytes.size)
            put(signatureBytes)
        }.array()
        val signaturesSeq = lengthPrefixed(lengthPrefixed(signatureEntry))
        val publicKeyBytes = keyPair.public.encoded

        val signerBlock = ByteArrayOutputStream().apply {
            write(lengthPrefixed(signedData))
            write(signaturesSeq)
            write(lengthPrefixed(publicKeyBytes))
        }.toByteArray()

        val v2Value = lengthPrefixed(lengthPrefixed(signerBlock))
        // ID-value pair: uint64(4 + v2Value.size) + uint32(APK_SIG_BLOCK_V2_ID) + v2Value
        val v2PairSize = 4 + v2Value.size
        val baseBlockSize = 8 + (8 + v2PairSize) + 8 + 16
        val remainder = baseBlockSize % 4096
        val paddingPairBytes = if (remainder == 0) {
            ByteArray(0)
        } else {
            val needed = (4096 - remainder).let { if (it < 12) it + 4096 else it }
            val padValueLen = needed - 12
            ByteBuffer.allocate(needed).order(ByteOrder.LITTLE_ENDIAN).apply {
                putLong((4 + padValueLen).toLong())
                putInt(VERITY_PADDING_BLOCK_ID)
                // remaining padValueLen bytes are 0
            }.array()
        }

        val totalPairsSize = (8 + v2PairSize) + paddingPairBytes.size
        val sizeField = (totalPairsSize + 8 + 16).toLong()

        val block = ByteBuffer.allocate(8 + totalPairsSize + 8 + 16).order(ByteOrder.LITTLE_ENDIAN)
        block.putLong(sizeField)
        block.putLong(v2PairSize.toLong())
        block.putInt(APK_SIG_BLOCK_V2_ID)
        block.put(v2Value)
        block.put(paddingPairBytes)
        block.putLong(sizeField)
        block.put("APK Sig Block 42".toByteArray(Charsets.US_ASCII))
        return block.array()
    }

    private fun lengthPrefixed(data: ByteArray): ByteArray {
        return ByteBuffer.allocate(4 + data.size).order(ByteOrder.LITTLE_ENDIAN).apply {
            putInt(data.size)
            put(data)
        }.array()
    }

    private fun readTransformedEntry(
        zf: ZipFile,
        entry: ZipEntry,
        oldPkgs: Set<String>,
        newPkgAscii: ByteArray,
        newPkgUtf16: ByteArray,
        taskerSuffixAscii: ByteArray,
        taskerSuffixUtf16: ByteArray,
        appName: String
    ): ByteArray {
        val raw = zf.getInputStream(entry).use { it.readBytes() }
        // Never modify nested compatibility stub APKs so their signatures stay intact
        if (entry.name.startsWith("assets/") && entry.name.endsWith(".apk", ignoreCase = true)) {
            return raw
        }

        if (isDex(raw)) {
            var data = raw
            for (oldPkg in oldPkgs) {
                data = swapFiltered(
                    data = data,
                    old = oldPkg.toByteArray(Charsets.US_ASCII),
                    repl = newPkgAscii,
                    skipFollowedBy = taskerSuffixAscii
                )
            }
            return fixDex(data)
        }

        if (entry.name == "AndroidManifest.xml") {
            var data = raw
            for (oldPkg in oldPkgs) {
                data = swapFiltered(
                    data = data,
                    old = oldPkg.toByteArray(Charsets.US_ASCII),
                    repl = newPkgAscii,
                    skipFollowedBy = taskerSuffixAscii
                )
                data = swapFiltered(
                    data = data,
                    old = oldPkg.toByteArray(Charsets.UTF_16LE),
                    repl = newPkgUtf16,
                    skipFollowedBy = taskerSuffixUtf16
                )
            }
            data = patchManifestLabel(data, appName)
            verifyManifestRewrite(data, newPkgAscii, newPkgUtf16)
            return data
        }

        if (entry.name == "resources.arsc" || entry.name.endsWith("/libshizuku.so")) {
            var data = raw
            for (oldPkg in oldPkgs) {
                data = swapFiltered(
                    data = data,
                    old = oldPkg.toByteArray(Charsets.US_ASCII),
                    repl = newPkgAscii,
                    skipFollowedBy = taskerSuffixAscii
                )
                data = swapFiltered(
                    data = data,
                    old = oldPkg.toByteArray(Charsets.UTF_16LE),
                    repl = newPkgUtf16,
                    skipFollowedBy = taskerSuffixUtf16
                )
            }
            return data
        }

        return raw
    }

    /**
     * Patches the binary `AndroidManifest.xml` (AXML) in-place:
     * 1. Finds the [STEALTH_LABEL_PLACEHOLDER] string inside the AXML `ResStringPool`
     *    and writes [appName] into that slot with its exact character length and
     *    null terminator (leaving chunk size and all string offsets unchanged).
     * 2. Finds the `<application>` start element and updates its `android:label`
     *    attribute (`0x01010001`) from `TYPE_REFERENCE` (`@string/app_name`) to
     *    `TYPE_STRING` (`0x03`) pointing to the patched placeholder string index.
     */
    internal fun patchManifestLabel(axml: ByteArray, appName: String): ByteArray {
        if (axml.size < 36) return axml
        val out = axml.copyOf()
        val buf = ByteBuffer.wrap(out).order(ByteOrder.LITTLE_ENDIAN)

        if ((buf.getShort(0).toInt() and 0xFFFF) != 0x0003) return axml
        val spOffset = 8
        if ((buf.getShort(spOffset).toInt() and 0xFFFF) != 0x0001) return axml

        val spChunkSize = buf.getInt(spOffset + 4)
        val stringCount = buf.getInt(spOffset + 8)
        val flags = buf.getInt(spOffset + 16)
        val stringsStart = buf.getInt(spOffset + 20)
        val isUtf8 = (flags and 0x100) != 0

        val offsetsBase = spOffset + 28
        val poolDataBase = spOffset + stringsStart
        var placeholderIdx = -1

        for (i in 0 until stringCount) {
            val strOffset = poolDataBase + buf.getInt(offsetsBase + i * 4)
            if (strOffset < 0 || strOffset >= out.size) continue
            if (isUtf8) {
                val charLen = out[strOffset].toInt() and 0xFF
                val byteLen = out[strOffset + 1].toInt() and 0xFF
                if (strOffset + 2 + byteLen <= out.size) {
                    val str = String(out, strOffset + 2, byteLen, Charsets.UTF_8)
                    if (str == STEALTH_LABEL_PLACEHOLDER) {
                        val newBytes = appName.toByteArray(Charsets.UTF_8)
                            .let { if (it.size > byteLen) appName.take(byteLen / 2).toByteArray(Charsets.UTF_8) else it }
                        val newCharLen = appName.length.coerceAtMost(newBytes.size)
                        out[strOffset] = newCharLen.toByte()
                        out[strOffset + 1] = newBytes.size.toByte()
                        for (k in 0..byteLen) out[strOffset + 2 + k] = 0
                        newBytes.copyInto(out, strOffset + 2)
                        placeholderIdx = i
                        break
                    }
                }
            } else {
                val charLen = buf.getShort(strOffset).toInt() and 0xFFFF
                if (strOffset + 2 + charLen * 2 <= out.size) {
                    val str = String(out, strOffset + 2, charLen * 2, Charsets.UTF_16LE)
                    if (str == STEALTH_LABEL_PLACEHOLDER) {
                        val trimmed = appName.take(charLen)
                        val newBytes = trimmed.toByteArray(Charsets.UTF_16LE)
                        buf.putShort(strOffset, trimmed.length.toShort())
                        for (k in 0 until (charLen + 1) * 2) {
                            if (strOffset + 2 + k < out.size) out[strOffset + 2 + k] = 0
                        }
                        newBytes.copyInto(out, strOffset + 2)
                        placeholderIdx = i
                        break
                    }
                }
            }
        }

        if (placeholderIdx < 0) return out

        // Locate RES_XML_RESOURCE_MAP_TYPE (0x0180) to find which string index corresponds to android:label (0x01010001)
        var pos = spOffset + spChunkSize
        var labelAttrNameIdx = -1
        while (pos + 8 <= out.size) {
            val chunkType = buf.getShort(pos).toInt() and 0xFFFF
            val chunkSize = buf.getInt(pos + 4)
            if (chunkSize <= 0 || pos + chunkSize > out.size) break

            if (chunkType == 0x0180) {
                val count = (chunkSize - 8) / 4
                for (i in 0 until count) {
                    if (buf.getInt(pos + 8 + i * 4) == 0x01010001) {
                        labelAttrNameIdx = i
                        break
                    }
                }
            } else if (chunkType == 0x0102 && labelAttrNameIdx >= 0) {
                // RES_XML_START_ELEMENT_TYPE
                val attrStart = buf.getShort(pos + 20).toInt() and 0xFFFF
                val attrSize = buf.getShort(pos + 22).toInt() and 0xFFFF
                val attrCount = buf.getShort(pos + 24).toInt() and 0xFFFF
                val attrsBase = pos + 16 + attrStart
                for (a in 0 until attrCount) {
                    val attrPos = attrsBase + a * attrSize
                    if (attrPos + 20 > out.size) break
                    val attrNameIdx = buf.getInt(attrPos + 4)
                    val attrDataType = out[attrPos + 15].toInt() and 0xFF
                    // Patch @string/app_name reference (dataType == 0x01) on <application> to TYPE_STRING (0x03)
                    if (attrNameIdx == labelAttrNameIdx && attrDataType == 0x01) {
                        buf.putInt(attrPos + 8, placeholderIdx) // rawValue
                        out[attrPos + 15] = 0x03 // TYPE_STRING
                        buf.putInt(attrPos + 16, placeholderIdx) // data
                        return out
                    }
                }
            }
            pos += chunkSize
        }

        return out
    }

    private fun verifyManifestRewrite(
        manifest: ByteArray,
        newPkg: ByteArray,
        newUtf16: ByteArray,
    ) {
        val hasNew = indexOf(manifest, newPkg, 0) >= 0 || indexOf(manifest, newUtf16, 0) >= 0
        if (!hasNew) {
            error("Stealth package rewrite verification failed")
        }
    }

    private fun isDex(data: ByteArray): Boolean {
        if (data.size < 8) return false
        return data[0] == 'd'.code.toByte() && data[1] == 'e'.code.toByte() &&
            data[2] == 'x'.code.toByte() && data[3] == '\n'.code.toByte()
    }

    private fun fixDex(data: ByteArray): ByteArray {
        if (data.size < 32 || !isDex(data)) return data
        return try {
            val sha1 = MessageDigest.getInstance("SHA-1")
            sha1.update(data, 32, data.size - 32)
            val sig = sha1.digest()
            sig.copyInto(data, 12)
            val adler = java.util.zip.Adler32()
            adler.update(data, 12, data.size - 12)
            val checksum = adler.value.toInt()
            data[8] = (checksum and 0xFF).toByte()
            data[9] = ((checksum ushr 8) and 0xFF).toByte()
            data[10] = ((checksum ushr 16) and 0xFF).toByte()
            data[11] = ((checksum ushr 24) and 0xFF).toByte()
            data
        } catch (_: Throwable) {
            data
        }
    }

    private fun swapFiltered(
        data: ByteArray,
        old: ByteArray,
        repl: ByteArray,
        skipFollowedBy: ByteArray
    ): ByteArray {
        var i = indexOf(data, old, 0)
        if (i < 0) return data
        val out = data.copyOf()
        val preservedList = if (skipFollowedBy.contentEquals(TASKER_SUFFIX.toByteArray(Charsets.UTF_16LE))) {
            PRESERVED_SUFFIXES_UTF16
        } else {
            PRESERVED_SUFFIXES_ASCII
        }
        while (i >= 0) {
            val afterIdx = i + old.size
            val isSkipped = preservedList.any { suffix ->
                suffix.isNotEmpty() &&
                    afterIdx + suffix.size <= out.size &&
                    matchesAt(out, suffix, afterIdx)
            }
            if (!isSkipped) {
                repl.copyInto(out, i)
            }
            i = indexOf(out, old, i + old.size)
        }
        return out
    }

    private fun matchesAt(data: ByteArray, pattern: ByteArray, offset: Int): Boolean {
        for (j in pattern.indices) {
            if (data[offset + j] != pattern[j]) return false
        }
        return true
    }

    private fun indexOf(data: ByteArray, pattern: ByteArray, from: Int): Int {
        if (pattern.isEmpty() || from > data.size - pattern.size) return -1
        outer@ for (i in from..data.size - pattern.size) {
            for (j in pattern.indices) {
                if (data[i + j] != pattern[j]) continue@outer
            }
            return i
        }
        return -1
    }

    private fun deflateRaw(input: ByteArray): ByteArray {
        val deflater = Deflater(Deflater.DEFAULT_COMPRESSION, true)
        return try {
            deflater.setInput(input)
            deflater.finish()
            val out = ByteArrayOutputStream(input.size)
            val buf = ByteArray(8192)
            while (!deflater.finished()) {
                val count = deflater.deflate(buf)
                if (count > 0) out.write(buf, 0, count)
            }
            out.toByteArray()
        } finally {
            deflater.end()
        }
    }

    private fun toDosTime(timeMillis: Long): Int {
        val cal = java.util.Calendar.getInstance()
        cal.timeInMillis = timeMillis
        val year = cal.get(java.util.Calendar.YEAR)
        if (year < 1980) return (1 shl 21) or (1 shl 16)
        val dosDate = ((year - 1980) shl 9) or
            ((cal.get(java.util.Calendar.MONTH) + 1) shl 5) or
            cal.get(java.util.Calendar.DAY_OF_MONTH)
        val dosTime = (cal.get(java.util.Calendar.HOUR_OF_DAY) shl 11) or
            (cal.get(java.util.Calendar.MINUTE) shl 5) or
            (cal.get(java.util.Calendar.SECOND) shr 1)
        return (dosDate shl 16) or (dosTime and 0xFFFF)
    }

    private fun manifestSection(name: String, digestB64: String): ByteArray {
        val out = ByteArrayOutputStream()
        writeWrapped(out, "Name: $name")
        writeWrapped(out, "SHA-256-Digest: $digestB64")
        out.write("\r\n".toByteArray())
        return out.toByteArray()
    }

    private fun writeWrapped(out: ByteArrayOutputStream, line: String) {
        var bytes = line.toByteArray(Charsets.UTF_8)
        var first = true
        while (bytes.isNotEmpty()) {
            var n = minOf(if (first) 72 else 71, bytes.size)
            while (n > 0 && n < bytes.size && bytes[n].toInt() and 0xC0 == 0x80) n--
            if (n == 0) n = minOf(if (first) 72 else 71, bytes.size)
            if (!first) out.write(' '.code)
            out.write(bytes, 0, n)
            out.write("\r\n".toByteArray())
            bytes = bytes.copyOfRange(n, bytes.size)
            first = false
        }
    }

    private fun selfSignedCert(keyPair: KeyPair): X509CertificateHolder {
        val dn = X500Name("C=US, ST=California, L=Mountain View, O=Android, OU=Android, CN=Android")
        val now = System.currentTimeMillis()
        val serial = BigInteger(64, random).abs().coerceAtLeast(BigInteger.ONE)
        return X509v3CertificateBuilder(
            dn,
            serial,
            Date(now - 86400000L),
            Date(now + 25L * 365 * 86400000L),
            dn,
            SubjectPublicKeyInfo.getInstance(keyPair.public.encoded)
        ).build(JcaContentSignerBuilder("SHA256withRSA").build(keyPair.private))
    }

    private fun cmsBlock(
        sfBytes: ByteArray,
        certHolder: X509CertificateHolder,
        signer: ContentSigner,
    ): ByteArray {
        val gen = CMSSignedDataGenerator()
        gen.addSignerInfoGenerator(
            JcaSignerInfoGeneratorBuilder(JcaDigestCalculatorProviderBuilder().build()).build(signer, certHolder)
        )
        gen.addCertificate(certHolder)
        return gen.generate(CMSProcessableByteArray(sfBytes), false).encoded
    }

    private fun sha256(data: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(data)

    private fun b64(data: ByteArray): String = Base64.encodeToString(data, Base64.NO_WRAP)

    private fun crc32(data: ByteArray): Long {
        val crc = CRC32()
        crc.update(data)
        return crc.value
    }
}
