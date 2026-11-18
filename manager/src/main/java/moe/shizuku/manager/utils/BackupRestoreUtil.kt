package moe.shizuku.manager.utils

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.sync.withLock
import moe.shizuku.manager.ShizukuSettings
import moe.shizuku.manager.authorization.AuthorizationManager
import moe.shizuku.manager.module.AdbModuleManager
import moe.shizuku.manager.module.ModuleSettings
import org.json.JSONArray
import org.json.JSONObject
import rikka.shizuku.Shizuku
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

object BackupRestoreUtil {

    private const val MAX_ENTRIES = 4096
    private const val MAX_TOTAL_BYTES = 128L * 1024L * 1024L
    private const val MAX_JSON_BYTES = 2L * 1024L * 1024L
    private const val MAX_ARCHIVE_BYTES = 144L * 1024L * 1024L

    suspend fun backup(context: Context, outputStream: OutputStream) = AdbModuleManager.storageMutex.withLock {
        val zipOutputStream = ZipOutputStream(outputStream)
        try {
            // 1. Backup Settings (the connector token has its own file and is never exported)
            val settingsJson = JSONObject()
            val allPrefs = ShizukuSettings.getPreferences().all
            for ((key, value) in allPrefs) {
                if (value == null) continue
                if (isLocalOnlySetting(key)) continue
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
            if (Shizuku.pingBinder()) {
                val allowedAppsJson = JSONArray()
                for (pi in AuthorizationManager.getPackages()) {
                    val uid = pi.applicationInfo?.uid ?: continue
                    if (!AuthorizationManager.isOneTime(uid) && AuthorizationManager.granted(pi.packageName, uid)) {
                        allowedAppsJson.put(pi.packageName)
                    }
                }
                zipOutputStream.putNextEntry(ZipEntry("allowed_apps.json"))
                zipOutputStream.write(allowedAppsJson.toString(2).toByteArray(Charsets.UTF_8))
                zipOutputStream.closeEntry()
            }

            // 3. Backup Modules
            val modulesRoot = AdbModuleManager.modulesRoot(context)
            if (modulesRoot.exists() && modulesRoot.isDirectory) {
                modulesRoot.walkTopDown()
                    .onEnter { it.parentFile != modulesRoot || !it.name.startsWith(".") }
                    .forEach { file ->
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

    suspend fun restore(context: Context, inputStream: InputStream) {
        val archive = File.createTempFile("shevery-backup-", ".zip", context.cacheDir)
        try {
            archive.outputStream().use { inputStream.copyZipBytesTo(it, MAX_ARCHIVE_BYTES) }
            AdbModuleManager.storageMutex.withLock { restoreArchive(context, archive) }
        } finally {
            archive.delete()
        }
    }

    private fun restoreArchive(context: Context, archive: File) {
        val modulesRoot = AdbModuleManager.modulesRoot(context)
        val parent = checkNotNull(modulesRoot.parentFile)
        val staging = File(parent, ".restore-${System.nanoTime()}")
        check(staging.mkdir()) { "Unable to create the restore staging directory." }

        var extractedBytes = 0L
        val extractedPaths = mutableSetOf<String>()
        var hasModules = false
        var settings: SharedPreferences.Editor? = null
        var allowedApps: Set<String>? = null

        try {
            ZipFile(archive).use { zip ->
                require(zip.size() in 1..MAX_ENTRIES) { "Backup has an invalid number of entries." }
                for (entry in zip.entries().asSequence()) {
                    val cleanName = entry.name.replace('\\', '/').trimEnd('/')
                    require(cleanName.split('/').none { it.isBlank() || it == "." || it == ".." }) {
                        "Unsafe path in backup: ${entry.name}"
                    }
                    require(extractedPaths.add(cleanName)) { "Duplicate backup entry: ${entry.name}" }
                    when {
                        cleanName == "settings.json" -> {
                            settings = prepareSettings(JSONObject(readEntryText(zip, entry)))
                        }
                        cleanName == "allowed_apps.json" -> {
                            val apps = JSONArray(readEntryText(zip, entry))
                            allowedApps = (0 until apps.length()).map { apps.getString(it) }.toSet()
                        }
                        cleanName.startsWith("modules/") -> {
                            val relativePath = cleanName.removePrefix("modules/")
                            // Older backups could include leftover installation directories.
                            if (relativePath.startsWith(".")) continue
                            hasModules = true
                            val outFile = File(staging, relativePath)
                            ensureInside(staging, outFile)
                            if (entry.isDirectory) {
                                check(outFile.isDirectory || outFile.mkdirs())
                            } else {
                                val directory = checkNotNull(outFile.parentFile)
                                check(directory.isDirectory || directory.mkdirs())
                                extractedBytes += outFile.outputStream().use {
                                    zip.copyVerifiedEntryTo(entry, it, MAX_TOTAL_BYTES - extractedBytes)
                                }
                            }
                        }
                    }
                }
            }
            require(settings != null || allowedApps != null || hasModules) { "No Shevery backup data found." }
            check(allowedApps == null || Shizuku.pingBinder()) {
                "Start the Shevery service before restoring app permissions."
            }

            if (hasModules) {
                staging.listFiles().orEmpty().forEach { directory ->
                    if (directory.resolve("module.prop").exists()) {
                        val module = requireNotNull(AdbModuleManager.readModule(directory)) {
                            "Invalid module metadata in ${directory.name}."
                        }
                        require(module.id == directory.name) { "Module ID does not match its directory." }
                        val action = module.actionScript?.relativeTo(directory)?.path
                        AdbModuleManager.markScriptsExecutable(directory, action)
                    }
                }
                staging.setReadable(true, false)
                staging.setExecutable(true, false)
                synchronized(AdbModuleManager) {
                    val backup = File(parent, AdbModuleManager.MODULES_RESTORE_BACKUP)
                    check(backup.deleteRecursively()) { "Unable to clear the old restore directory." }
                    // Full Trust must be granted to the restored content by the current user.
                    ModuleSettings.clearTrustedModules(persistNow = true)
                    check(modulesRoot.renameTo(backup)) { "Unable to stage the current modules directory." }
                    if (!staging.renameTo(modulesRoot)) {
                        check(backup.renameTo(modulesRoot)) { "Unable to recover modules from ${backup.name}." }
                        error("Unable to activate the restored modules directory.")
                    }
                    backup.deleteRecursively()
                }
                AdbModuleManager.notifyModulesChanged()
            }

            settings?.let { check(it.commit()) { "Unable to save restored settings." } }
            allowedApps?.let { applyAllowedApps(it) }
        } finally {
            staging.deleteRecursively()
        }
    }

    private fun readEntryText(zip: ZipFile, entry: ZipEntry): String {
        val out = ByteArrayOutputStream()
        zip.copyVerifiedEntryTo(entry, out, MAX_JSON_BYTES)
        return out.toString("UTF-8")
    }

    private fun isLocalOnlySetting(key: String): Boolean =
        key == ShizukuSettings.AUTH_TOKEN || key == ModuleSettings.KEY_TRUSTED_MODULES

    private fun ensureInside(root: File, file: File) {
        val rootPath = root.canonicalFile.toPath()
        val filePath = file.canonicalFile.toPath()
        require(filePath.startsWith(rootPath)) { "Path escapes the restore directory." }
    }

    private fun prepareSettings(json: JSONObject): SharedPreferences.Editor {
        val editor = ShizukuSettings.getPreferences().edit()
        editor.clear()
        val keys = json.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            if (isLocalOnlySetting(key)) continue
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
                else -> error("Unsupported setting type for $key.")
            }
        }
        return editor
    }

    private fun applyAllowedApps(allowedSet: Set<String>) {
        for (pi in AuthorizationManager.getPackages()) {
            val uid = pi.applicationInfo?.uid ?: continue
            if (allowedSet.contains(pi.packageName)) {
                AuthorizationManager.grant(pi.packageName, uid)
            } else {
                AuthorizationManager.revoke(pi.packageName, uid)
            }
        }
    }
}
