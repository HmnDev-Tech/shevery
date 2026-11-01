package moe.shizuku.manager.utils

import android.content.Context
import moe.shizuku.manager.ShizukuSettings
import moe.shizuku.manager.authorization.AuthorizationManager
import moe.shizuku.manager.module.AdbModuleManager
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object BackupRestoreUtil {

    private const val MAX_ENTRIES = 4096
    private const val MAX_TOTAL_BYTES = 128L * 1024L * 1024L
    private const val MAX_JSON_BYTES = 2 * 1024 * 1024

    fun backup(context: Context, outputStream: OutputStream) {
        val zipOutputStream = ZipOutputStream(outputStream)
        try {
            // 1. Backup Settings (the connector token has its own file and is never exported)
            val settingsJson = JSONObject()
            val allPrefs = ShizukuSettings.getPreferences().all
            for ((key, value) in allPrefs) {
                if (value == null) continue
                if (key == ShizukuSettings.AUTH_TOKEN) continue
                val entryObj = JSONObject()
                when (value) {
                    is Boolean -> {
                        entryObj.put("type", "Boolean")
                        entryObj.put("value", value)
                    }
                    is Int -> {
                        entryObj.put("type", "Int")
                        entryObj.put("value", value)
                    }
                    is Long -> {
                        entryObj.put("type", "Long")
                        entryObj.put("value", value)
                    }
                    is Float -> {
                        entryObj.put("type", "Float")
                        entryObj.put("value", value.toDouble())
                    }
                    is String -> {
                        entryObj.put("type", "String")
                        entryObj.put("value", value)
                    }
                    is Set<*> -> {
                        entryObj.put("type", "StringSet")
                        val array = JSONArray()
                        for (item in value) {
                            if (item is String) {
                                array.put(item)
                            }
                        }
                        entryObj.put("value", array)
                    }
                }
                settingsJson.put(key, entryObj)
            }
            zipOutputStream.putNextEntry(ZipEntry("settings.json"))
            zipOutputStream.write(settingsJson.toString(2).toByteArray(Charsets.UTF_8))
            zipOutputStream.closeEntry()

            // 2. Backup Allowed Apps
            val allowedAppsJson = JSONArray()
            for (pi in AuthorizationManager.getPackages()) {
                val uid = pi.applicationInfo?.uid ?: continue
                if (AuthorizationManager.granted(pi.packageName, uid)) {
                    allowedAppsJson.put(pi.packageName)
                }
            }
            zipOutputStream.putNextEntry(ZipEntry("allowed_apps.json"))
            zipOutputStream.write(allowedAppsJson.toString(2).toByteArray(Charsets.UTF_8))
            zipOutputStream.closeEntry()

            // 3. Backup Modules
            val modulesRoot = AdbModuleManager.modulesRoot(context)
            if (modulesRoot.exists() && modulesRoot.isDirectory) {
                modulesRoot.walkTopDown().forEach { file ->
                    if (file == modulesRoot) return@forEach
                    val relativePath = file.relativeTo(modulesRoot).path.replace('\\', '/')
                    val entryName = "modules/$relativePath" + if (file.isDirectory) "/" else ""
                    zipOutputStream.putNextEntry(ZipEntry(entryName))
                    if (file.isFile) {
                        file.inputStream().use { input ->
                            input.copyTo(zipOutputStream)
                        }
                    }
                    zipOutputStream.closeEntry()
                }
            }
        } finally {
            zipOutputStream.finish()
        }
    }

    /**
     * Restore is transactional: the archive is fully validated and extracted into a
     * staging directory first. Existing modules and preferences are replaced only after
     * that succeeds, so a truncated or malicious archive cannot destroy current data.
     */
    fun restore(context: Context, inputStream: InputStream) {
        val modulesRoot = AdbModuleManager.modulesRoot(context)
        val parent = modulesRoot.parentFile ?: error("Modules root has no parent directory.")
        val staging = File(parent, ".restore-${System.nanoTime()}")
        staging.deleteRecursively()
        check(staging.mkdirs()) { "Unable to create the restore staging directory." }

        var entryCount = 0
        var extractedBytes = 0L
        var hasModules = false
        var settingsJson: JSONObject? = null
        var allowedApps: JSONArray? = null

        try {
            ZipInputStream(BufferedInputStream(inputStream)).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    entryCount++
                    require(entryCount <= MAX_ENTRIES) { "Backup contains too many entries." }

                    val cleanName = entry.name.replace('\\', '/').trim('/')
                    require(cleanName.isNotEmpty()) { "Backup contains an empty entry name." }
                    require(!cleanName.startsWith("/") && !cleanName.contains("../")) {
                        "Unsafe path in backup: ${entry.name}"
                    }

                    when {
                        cleanName == "settings.json" -> {
                            settingsJson = JSONObject(readEntryText(zip, MAX_JSON_BYTES))
                        }
                        cleanName == "allowed_apps.json" -> {
                            allowedApps = JSONArray(readEntryText(zip, MAX_JSON_BYTES))
                        }
                        cleanName.startsWith("modules/") -> {
                            hasModules = true
                            val relativePath = cleanName.substring("modules/".length)
                            if (relativePath.isNotEmpty()) {
                                val outFile = File(staging, relativePath)
                                ensureInside(staging, outFile)
                                if (entry.isDirectory) {
                                    outFile.mkdirs()
                                } else {
                                    outFile.parentFile?.mkdirs()
                                    extractedBytes += copyEntry(
                                        zip,
                                        outFile,
                                        MAX_TOTAL_BYTES - extractedBytes
                                    )
                                }
                            }
                        }
                    }

                    zip.closeEntry()
                }
            }

            if (hasModules) {
                val backup = File(parent, ".modules-before-restore")
                backup.deleteRecursively()
                if (modulesRoot.exists()) {
                    check(modulesRoot.renameTo(backup)) {
                        "Unable to stage the current modules directory."
                    }
                }
                if (!staging.renameTo(modulesRoot)) {
                    if (backup.exists()) backup.renameTo(modulesRoot)
                    error("Unable to activate the restored modules directory.")
                }
                backup.deleteRecursively()
                markScriptsExecutable(modulesRoot)
            }

            settingsJson?.let { applySettings(it) }
            allowedApps?.let { applyAllowedApps(it) }
        } catch (t: Throwable) {
            staging.deleteRecursively()
            throw t
        }
    }

    private fun readEntryText(input: InputStream, maxBytes: Int): String {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(4096)
        while (true) {
            val read = input.read(buffer)
            if (read <= 0) break
            require(out.size() + read <= maxBytes) { "Backup metadata is too large." }
            out.write(buffer, 0, read)
        }
        return out.toString("UTF-8")
    }

    private fun copyEntry(input: InputStream, outFile: File, remaining: Long): Long {
        require(remaining > 0L) { "Backup is too large." }
        var written = 0L
        outFile.outputStream().use { output ->
            val buffer = ByteArray(8192)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                written += read
                require(written <= remaining) { "Backup is too large." }
                output.write(buffer, 0, read)
            }
        }
        return written
    }

    private fun ensureInside(root: File, file: File) {
        val rootPath = root.canonicalFile.toPath()
        val filePath = file.canonicalFile.toPath()
        require(filePath.startsWith(rootPath)) { "Path escapes the restore directory." }
    }

    private fun applySettings(json: JSONObject) {
        val editor = ShizukuSettings.getPreferences().edit()
        editor.clear()
        val keys = json.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            if (key == ShizukuSettings.AUTH_TOKEN) continue
            val entryObj = json.getJSONObject(key)
            when (entryObj.getString("type")) {
                "Boolean" -> editor.putBoolean(key, entryObj.getBoolean("value"))
                "Int" -> editor.putInt(key, entryObj.getInt("value"))
                "Long" -> editor.putLong(key, entryObj.getLong("value"))
                "Float" -> editor.putFloat(key, entryObj.getDouble("value").toFloat())
                "String" -> editor.putString(key, entryObj.getString("value"))
                "StringSet" -> {
                    val array = entryObj.getJSONArray("value")
                    val set = mutableSetOf<String>()
                    for (i in 0 until array.length()) {
                        set.add(array.getString(i))
                    }
                    editor.putStringSet(key, set)
                }
            }
        }
        editor.commit()
    }

    private fun applyAllowedApps(array: JSONArray) {
        val allowedSet = mutableSetOf<String>()
        for (i in 0 until array.length()) {
            allowedSet.add(array.getString(i))
        }
        for (pi in AuthorizationManager.getPackages()) {
            val uid = pi.applicationInfo?.uid ?: continue
            if (allowedSet.contains(pi.packageName)) {
                AuthorizationManager.grant(pi.packageName, uid)
            } else {
                AuthorizationManager.revoke(pi.packageName, uid)
            }
        }
    }

    private fun markScriptsExecutable(directory: File) {
        directory.walkTopDown()
            .filter { it.isFile && it.extension == "sh" }
            .forEach { it.setExecutable(true, false) }
    }
}
