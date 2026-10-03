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
import org.bouncycastle.cert.X509v3CertificateBuilder
import org.bouncycastle.cms.CMSProcessableByteArray
import org.bouncycastle.cms.CMSSignedDataGenerator
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder
import org.bouncycastle.operator.jcajce.JcaSignerInfoGeneratorBuilder
import java.io.ByteArrayOutputStream
import java.io.File
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Date
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * Generates a disguised copy of Shevery on the device itself.
 *
 * The standard edition embeds a stealth template APK built with the fixed
 * [TEMPLATE_PACKAGE] id. When the user saves a stealth copy, every occurrence
 * of the template id inside the manifest, resources and dex files is replaced
 * with a freshly generated random id of the EXACT SAME LENGTH. Because no file
 * grows or shrinks, no binary format needs to be parsed: it is a pure byte
 * swap, offsets stay valid, and only the signatures have to be regenerated
 * (v1 JAR signing, the stale v2/v3 block is dropped with the zip rebuild).
 *
 * The stealth copy keeps the same Shizuku authorities, so it conflicts with
 * the normal Shevery by design: it is a replacement, not a second copy.
 */
object StealthApkGenerator {

    const val TEMPLATE_ASSET = "shevery-stealth.apk"
    const val TEMPLATE_PACKAGE = "com.stealthp.lacehold"

    private val random = SecureRandom()
    private val LETTERS = ('a'..'z').toList()
    private val ALNUM = (('a'..'z') + ('0'..'9')).toList()

    /** Random package id, always the same length as [TEMPLATE_PACKAGE]. */
    fun randomPackage(): String {
        fun seg(): String = buildString {
            append(LETTERS[random.nextInt(LETTERS.size)])
            repeat(7) { append(ALNUM[random.nextInt(ALNUM.size)]) }
        }
        return "com.${seg()}.${seg()}"
    }

    /**
     * Rewrites the embedded template with [packageName] and streams the result
     * into Downloads as [fileName]. Returns the saved display name, or null.
     */
    fun saveToDownloads(context: Context, fileName: String, packageName: String): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        if (packageName.length != TEMPLATE_PACKAGE.length) {
            logd("Stealth package length mismatch: ${packageName.length}")
            return null
        }
        val baseName = fileName.replace(Regex("[\\\\/:*?\"<>|]"), "").trim().takeIf { it.isNotEmpty() }
            ?: return null
        val displayName = if (baseName.endsWith(".apk", ignoreCase = true)) baseName else "$baseName.apk"

        val templateFile = File(context.cacheDir, "stealth-template.apk")
        try {
            context.assets.open(TEMPLATE_ASSET).use { input ->
                templateFile.outputStream().use { output -> input.copyTo(output) }
            }
            val oldPkg = TEMPLATE_PACKAGE.toByteArray(Charsets.US_ASCII)
            val newPkg = packageName.toByteArray(Charsets.US_ASCII)

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
                MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values
            ) ?: return null
            try {
                resolver.openOutputStream(uri)?.use { rawOut ->
                    ZipOutputStream(rawOut).use { zos ->
                        rewriteApk(templateFile, zos, oldPkg, newPkg, keyPair, certHolder, signer)
                    }
                } ?: error("openOutputStream returned null")
            } catch (e: Throwable) {
                resolver.delete(uri, null, null)
                throw e
            }
            logd("Stealth APK saved as $displayName ($packageName)")
            return displayName
        } finally {
            templateFile.delete()
        }
    }

    private fun rewriteApk(
        templateFile: File,
        zos: ZipOutputStream,
        oldPkg: ByteArray,
        newPkg: ByteArray,
        keyPair: KeyPair,
        certHolder: org.bouncycastle.cert.X509CertificateHolder,
        signer: org.bouncycastle.operator.ContentSigner,
    ) {
        ZipFile(templateFile).use { zf ->
            val entries = java.util.Collections.list(zf.entries()).filter { !it.name.startsWith("META-INF/") }

            // Pass 1: manifest sections (digests only, data is re-read in pass 2).
            val manifest = ByteArrayOutputStream()
            manifest.write("Manifest-Version: 1.0\r\nCreated-By: Shevery Stealth\r\n\r\n".toByteArray())
            val sections = ArrayList<Pair<String, ByteArray>>(entries.size)
            for (entry in entries) {
                val data = readTransformed(zf, entry, oldPkg, newPkg)
                val section = manifestSection(entry.name, b64(sha256(data)))
                manifest.write(section)
                sections.add(entry.name to section)
            }
            val manifestBytes = manifest.toByteArray()

            val sf = ByteArrayOutputStream()
            sf.write(
                ("Signature-Version: 1.0\r\nCreated-By: Shevery Stealth\r\n" +
                    "SHA-256-Digest-Manifest: ${b64(sha256(manifestBytes))}\r\n\r\n").toByteArray()
            )
            for ((name, section) in sections) {
                sf.write(manifestSection(name, b64(sha256(section))))
            }
            val sfBytes = sf.toByteArray()
            val rsaBytes = cmsBlock(sfBytes, keyPair, certHolder, signer)

            // Pass 2: entries + fresh signatures.
            writeStored(zos, "META-INF/MANIFEST.MF", manifestBytes)
            for (entry in entries) {
                val data = readTransformed(zf, entry, oldPkg, newPkg)
                if (entry.isDirectory) {
                    zos.putNextEntry(ZipEntry(entry.name).apply { time = entry.time })
                    zos.closeEntry()
                } else if (entry.method == ZipEntry.STORED) {
                    val out = ZipEntry(entry.name).apply {
                        method = ZipEntry.STORED
                        time = entry.time
                        size = data.size.toLong()
                        crc = crc32(data)
                    }
                    zos.putNextEntry(out)
                    zos.write(data)
                    zos.closeEntry()
                } else {
                    zos.putNextEntry(ZipEntry(entry.name).apply { time = entry.time })
                    zos.write(data)
                    zos.closeEntry()
                }
            }
            writeStored(zos, "META-INF/CERT.SF", sfBytes)
            writeStored(zos, "META-INF/CERT.RSA", rsaBytes)
        }
    }

    private fun readTransformed(
        zf: ZipFile,
        entry: ZipEntry,
        oldPkg: ByteArray,
        newPkg: ByteArray,
    ): ByteArray {
        if (entry.isDirectory) return ByteArray(0)
        val data = zf.getInputStream(entry).use { it.readBytes() }
        // Nested APKs are separate packages with their own valid signatures: never touch them.
        if (entry.name.startsWith("assets/") && entry.name.endsWith(".apk", ignoreCase = true)) return data
        return swapAll(data, oldPkg, newPkg)
    }

    private fun swapAll(data: ByteArray, old: ByteArray, repl: ByteArray): ByteArray {
        var i = indexOf(data, old, 0)
        if (i < 0) return data
        val out = data.copyOf()
        while (i >= 0) {
            repl.copyInto(out, i)
            i = indexOf(out, old, i + old.size)
        }
        return out
    }

    private fun indexOf(data: ByteArray, pattern: ByteArray, from: Int): Int {
        outer@ for (i in from..data.size - pattern.size) {
            for (j in pattern.indices) {
                if (data[i + j] != pattern[j]) continue@outer
            }
            return i
        }
        return -1
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

    private fun selfSignedCert(keyPair: KeyPair): org.bouncycastle.cert.X509CertificateHolder {
        val cn = buildString {
            append("toolkit")
            repeat(6) { append(ALNUM[random.nextInt(ALNUM.size)]) }
        }
        val dn = X500Name("CN=$cn")
        val now = System.currentTimeMillis()
        return X509v3CertificateBuilder(
            dn,
            BigInteger.valueOf(now),
            Date(now - 86400000L),
            Date(now + 30L * 365 * 86400000L),
            dn,
            SubjectPublicKeyInfo.getInstance(keyPair.public.encoded)
        ).build(JcaContentSignerBuilder("SHA256withRSA").build(keyPair.private))
    }

    private fun cmsBlock(
        sfBytes: ByteArray,
        keyPair: KeyPair,
        certHolder: org.bouncycastle.cert.X509CertificateHolder,
        signer: org.bouncycastle.operator.ContentSigner,
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
        val crc = java.util.zip.CRC32()
        crc.update(data)
        return crc.value
    }

    private fun writeStored(zos: ZipOutputStream, name: String, data: ByteArray) {
        val entry = ZipEntry(name).apply {
            method = ZipEntry.STORED
            time = System.currentTimeMillis()
            size = data.size.toLong()
            crc = crc32(data)
        }
        zos.putNextEntry(entry)
        zos.write(data)
        zos.closeEntry()
    }
}
