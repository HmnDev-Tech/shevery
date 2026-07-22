package moe.shizuku.manager.utils

import android.content.Context
import moe.shizuku.manager.ShizukuSettings
import moe.shizuku.manager.authorization.AuthorizationManager
import moe.shizuku.manager.module.AdbModuleManager
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object BackupRestoreUtil {

    fun backup(context: Context, outputStream: OutputStream) {
        val zipOutputStream = ZipOutputStream(outputStream)
        try {
            // 1. Backup Settings
            val settingsJson = JSONObject()
            val allPrefs = ShizukuSettings.getPreferences().all
            for ((key, value) in allPrefs) {
                if (value == null) continue
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

    fun restore(context: Context, inputStream: InputStream) {
        val zipInputStream = ZipInputStream(inputStream)
        
        // Before extracting modules, clear current modules
        val modulesRoot = AdbModuleManager.modulesRoot(context)
        modulesRoot.deleteRecursively()
        modulesRoot.mkdirs()

        var entry = zipInputStream.getNextEntry()
        while (entry != null) {
            val name = entry.name
            val cleanName = name.replace('\\', '/').trim('/')
            if (cleanName.contains("../") || cleanName.startsWith("/")) {
                zipInputStream.closeEntry()
                entry = zipInputStream.getNextEntry()
                continue
            }

            if (cleanName == "settings.json") {
                val byteStream = ByteArrayOutputStream()
                val buffer = ByteArray(4096)
                var len: Int
                while (zipInputStream.read(buffer).also { len = it } > 0) {
                    byteStream.write(buffer, 0, len)
                }
                val jsonStr = byteStream.toString("UTF-8")
                if (jsonStr.isNotBlank()) {
                    val json = JSONObject(jsonStr)
                    val editor = ShizukuSettings.getPreferences().edit()
                    editor.clear()
                    val keys = json.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        val entryObj = json.getJSONObject(key)
                        val type = entryObj.getString("type")
                        when (type) {
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
            } else if (cleanName == "allowed_apps.json") {
                val byteStream = ByteArrayOutputStream()
                val buffer = ByteArray(4096)
                var len: Int
                while (zipInputStream.read(buffer).also { len = it } > 0) {
                    byteStream.write(buffer, 0, len)
                }
                val jsonStr = byteStream.toString("UTF-8")
                if (jsonStr.isNotBlank()) {
                    val array = JSONArray(jsonStr)
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
            } else if (cleanName.startsWith("modules/")) {
                val relativePath = cleanName.substring("modules/".length)
                if (relativePath.isNotEmpty()) {
                    val outFile = File(modulesRoot, relativePath)
                    val rootPath = modulesRoot.canonicalFile.toPath()
                    val childPath = outFile.canonicalFile.toPath()
                    if (childPath.startsWith(rootPath)) {
                        if (entry.isDirectory) {
                            outFile.mkdirs()
                        } else {
                            outFile.parentFile?.mkdirs()
                            outFile.outputStream().use { output ->
                                val buffer = ByteArray(4096)
                                var len: Int
                                while (zipInputStream.read(buffer).also { len = it } > 0) {
                                    output.write(buffer, 0, len)
                                }
                            }
                        }
                    }
                }
            }

            zipInputStream.closeEntry()
            entry = zipInputStream.getNextEntry()
        }

        markScriptsExecutable(modulesRoot)
    }

    private fun markScriptsExecutable(directory: File) {
        directory.walkTopDown()
            .filter { it.isFile && it.extension == "sh" }
            .forEach { it.setExecutable(true, false) }
    }
}
