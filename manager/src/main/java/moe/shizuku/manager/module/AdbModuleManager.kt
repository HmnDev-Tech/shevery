package moe.shizuku.manager.module

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import moe.shizuku.server.IShizukuService
import rikka.shizuku.Shizuku
import java.io.BufferedInputStream
import java.io.File
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.zip.ZipFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

object AdbModuleManager {

    private const val MODULES_DIR = "adb_modules"
    private const val DISABLE_FILE = "disable"
    private const val MAX_OUTPUT_CHARS = 32 * 1024
    private const val MAX_EXTRACTED_BYTES = 16L * 1024L * 1024L
    private const val MAX_ENTRY_COUNT = 512
    private const val SCRIPT_TIMEOUT_SECONDS = 30L
    @Volatile
    private var servicesStartedForBinder = false

    private val idRegex = Regex("[A-Za-z][A-Za-z0-9._-]{1,63}")
    private val installMutexes = ConcurrentHashMap<String, Mutex>()

    fun cleanupStagingDirs(context: Context) {
        modulesRoot(context).listFiles { file ->
            file.isDirectory && file.name.startsWith(".") && file.name.endsWith(".installing")
        }?.forEach { it.deleteRecursively() }
    }

    // Live output for streaming module actions
    private val _liveOutput = MutableStateFlow<LiveModuleOutput?>(null)
    val liveOutput: StateFlow<LiveModuleOutput?> = _liveOutput.asStateFlow()

    data class LiveModuleOutput(
        val moduleId: String,
        val moduleName: String,
        val text: String,
        val running: Boolean
    )

    fun modulesRoot(context: Context): File {
        val filesDir = context.filesDir
        filesDir.parentFile?.apply {
            setReadable(true, false)
            setExecutable(true, false)
        }
        filesDir.apply {
            mkdirs()
            setReadable(true, false)
            setExecutable(true, false)
        }
        return File(filesDir, MODULES_DIR).apply {
            mkdirs()
            setReadable(true, false)
            setExecutable(true, false)
        }
    }

    suspend fun listModules(context: Context): List<AdbModule> = withContext(Dispatchers.IO) {
        modulesRoot(context)
            .listFiles { file -> file.isDirectory && !file.name.startsWith(".") }
            ?.mapNotNull(::readModule)
            ?.sortedWith(compareBy<AdbModule> { !it.enabled }.thenBy { it.name.lowercase(Locale.ROOT) })
            .orEmpty()
    }

    suspend fun install(context: Context, uri: Uri): AdbModule = withContext(Dispatchers.IO) {
        cleanupStagingDirs(context)
        val temp = File.createTempFile("adb_module_", ".zip", context.cacheDir)
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                temp.outputStream().use { output -> input.copyTo(output) }
            } ?: error("Unable to read selected ZIP file.")

            ZipFile(temp).use { zip ->
                val allEntries = zip.entries().asSequence().toList()
                val propEntry = allEntries.firstOrNull {
                    !it.isDirectory && it.name.replace('\\', '/').trim('/') == "module.prop"
                } ?: allEntries
                    .filter { !it.isDirectory && it.name.replace('\\', '/').trim('/').endsWith("/module.prop") }
                    .minByOrNull { it.name.count { ch -> ch == '/' || ch == '\\' } }
                    ?: error("module.prop is missing.")

                val propPath = propEntry.name.replace('\\', '/').trim('/')
                val rootPrefix = if (propPath == "module.prop") {
                    ""
                } else {
                    propPath.removeSuffix("module.prop")
                }

                val props = zip.getInputStream(propEntry).use { parseModuleProp(it.bufferedReader().readText()) }
                val id = props["id"]?.trim().orEmpty()
                require(idRegex.matches(id)) { "Invalid module id: $id" }

                installMutexes.getOrPut(id) { Mutex() }.withLock {
                    val modulesDir = modulesRoot(context)
                    val target = File(modulesDir, id)
                    val staging = File(modulesDir, ".$id.installing")
                    staging.deleteRecursively()
                    staging.mkdirs()

                    var entryCount = 0
                    var extractedBytes = 0L
                    allEntries.forEach { entry ->
                        val normalizedName = entry.name.replace('\\', '/').trim('/')
                        if (normalizedName.isBlank()) return@forEach
                        val relativeName = if (rootPrefix.isNotEmpty()) {
                            if (!normalizedName.startsWith(rootPrefix)) return@forEach
                            normalizedName.removePrefix(rootPrefix).trimStart('/')
                        } else {
                            normalizedName
                        }
                        if (relativeName.isBlank()) return@forEach

                        entryCount++
                        require(entryCount <= MAX_ENTRY_COUNT) { "Module ZIP has too many files." }
                        val cleanName = cleanZipName(relativeName)
                        val outFile = File(staging, cleanName)
                        ensureInside(staging, outFile)
                        if (entry.isDirectory) {
                            outFile.mkdirs()
                        } else {
                            outFile.parentFile?.mkdirs()
                            BufferedInputStream(zip.getInputStream(entry)).use { input ->
                                outFile.outputStream().use { output ->
                                    val buffer = ByteArray(8192)
                                    while (true) {
                                        val read = input.read(buffer)
                                        if (read <= 0) break
                                        extractedBytes += read.toLong()
                                        require(extractedBytes <= MAX_EXTRACTED_BYTES) { "Module ZIP is too large." }
                                        output.write(buffer, 0, read)
                                    }
                                }
                            }
                        }
                    }

                    // Preserve existing .source.prop across updates if not included in new ZIP
                    val existingSourceProp = target.resolve(".source.prop")
                    val stagingSourceProp = staging.resolve(".source.prop")
                    if (existingSourceProp.isFile && !stagingSourceProp.exists()) {
                        runCatching { existingSourceProp.copyTo(stagingSourceProp, overwrite = false) }
                    }

                    markScriptsExecutable(staging, props["action"])
                    target.deleteRecursively()
                    check(staging.renameTo(target)) { "Unable to move module into storage." }
                    markScriptsExecutable(target, props["action"])
                    readModule(target) ?: error("Installed module is unreadable.")
                }
            }
        } finally {
            temp.delete()
        }
    }

    suspend fun setEnabled(module: AdbModule, enabled: Boolean) = withContext(Dispatchers.IO) {
        val disableFile = module.directory.resolve(DISABLE_FILE)
        if (enabled) {
            disableFile.delete()
        } else {
            disableFile.writeText("disabled")
        }
    }

    suspend fun delete(module: AdbModule) = withContext(Dispatchers.IO) {
        ModuleSettings.setModuleTrusted(module.id, false)
        module.directory.deleteRecursively()
    }

    suspend fun runAction(module: AdbModule): ModuleActionResult = withContext(Dispatchers.IO) {
        check(ModuleSettings.canRunAction(module)) { "actionScript is blocked by module access policy." }
        val script = module.actionScript?.takeIf { it.isFile } ?: error("This module has no action script.")
        runModuleScript(module, script, module.lastActionLog)
    }

    suspend fun runActionStreaming(module: AdbModule): ModuleActionResult = withContext(Dispatchers.IO) {
        check(ModuleSettings.canRunAction(module)) { "actionScript is blocked by module access policy." }
        val script = module.actionScript?.takeIf { it.isFile } ?: error("This module has no action script.")
        runModuleScriptStreaming(module, script, module.lastActionLog)
    }

    suspend fun runService(module: AdbModule): ModuleActionResult = withContext(Dispatchers.IO) {
        check(ModuleSettings.canRunService(module)) { "serviceScript is blocked by module access policy." }
        check(ModuleSettings.canRunBackground(module)) { "Background actions are disabled." }
        val script = module.serviceScript?.takeIf { it.isFile } ?: error("This module has no service script.")
        runModuleScript(module, script, module.lastServiceLog)
    }

    suspend fun runEnabledServicesIfAllowed(context: Context): List<Pair<AdbModule, ModuleActionResult>> =
        withContext(Dispatchers.IO) {
            if (!Shizuku.pingBinder() || !ModuleSettings.canRunService() || servicesStartedForBinder) {
                return@withContext emptyList()
            }
            servicesStartedForBinder = true
            listModules(context)
                .filter { it.enabled && it.hasService && ModuleSettings.canRunService(it) && ModuleSettings.canRunBackground(it) }
                .mapNotNull { module ->
                    runCatching { module to runService(module) }.getOrNull()
                }
        }

    fun resetServiceRunGuard() {
        servicesStartedForBinder = false
    }

    private fun runModuleScript(module: AdbModule, script: File, logFile: File): ModuleActionResult =
        runModuleScriptStreaming(module, script, logFile, live = false)

    private fun runModuleScriptStreaming(module: AdbModule, script: File, logFile: File, live: Boolean = false): ModuleActionResult {
        check(module.enabled) { "Module is disabled." }
        module.logsDir.mkdirs()

        val scriptContent = try {
            val bytes = script.readBytes()
            var text = String(bytes, Charsets.UTF_8)
            if (text.startsWith("\uFEFF")) text = text.substring(1)
            text.replace("\r\n", "\n").replace("\r", "\n")
        } catch (e: Exception) {
            return ModuleActionResult(exitCode = -1, stdout = "", stderr = "Cannot read script: ${e.message}")
        }

        if (live) {
            _liveOutput.value = LiveModuleOutput(
                moduleId = module.id,
                moduleName = module.name,
                text = "Starting ${script.name}...\n",
                running = true
            )
        }

        val binder = Shizuku.getBinder() ?: error("Shizuku service is not running.")
        val service = IShizukuService.Stub.asInterface(binder)
        val env = arrayOf(
            "MODDIR=${module.directory.absolutePath}",
            "MODPATH=${module.directory.absolutePath}",
            "ASH_STANDALONE=1",
            "SHIZUKU_MODULE_ID=${module.id}",
            "SHIZUKU_MODULE_MODE=${ModuleSettings.getAccessMode().value}",
            "SHIZUKU_MODULE_TRUSTED=${if (ModuleSettings.isModuleTrusted(module.id)) "1" else "0"}",
            "SHIZUKU_MODULE_BACKGROUND=${if (ModuleSettings.canRunBackground(module)) "1" else "0"}",
            "ARCH=${android.os.Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a"}"
        )
        val wrappedScript = "cd \"\$MODDIR\" 2>/dev/null || true\n$scriptContent"
        val remote = service.newProcess(
            arrayOf("sh", "-c", wrappedScript),
            env,
            "/data/local/tmp"
        )

        runCatching { ParcelFileDescriptor.AutoCloseOutputStream(remote.getOutputStream()).close() }
        val stdoutPfd = remote.getInputStream()
        val stderrPfd = remote.getErrorStream()
        val streamingBuffer = StringBuilder()
        var stdout = ""
        var stderr = ""
        val stdoutThread = Thread {
            try {
                if (live) {
                    val reader = ParcelFileDescriptor.AutoCloseInputStream(stdoutPfd).bufferedReader(Charsets.UTF_8)
                    reader.use { r ->
                        val buffer = CharArray(1024)
                        while (true) {
                            val read = r.read(buffer)
                            if (read <= 0) break
                            val chunk = String(buffer, 0, read)
                            synchronized(streamingBuffer) {
                                streamingBuffer.append(chunk)
                                stdout = streamingBuffer.toString()
                            }
                            _liveOutput.value = LiveModuleOutput(
                                moduleId = module.id,
                                moduleName = module.name,
                                text = streamingBuffer.toString().takeLast(MAX_OUTPUT_CHARS),
                                running = true
                            )
                        }
                    }
                } else {
                    stdout = readStreamTail(ParcelFileDescriptor.AutoCloseInputStream(stdoutPfd))
                }
            } catch (ignore: Exception) { }
        }
        val stderrThread = Thread {
            try {
                stderr = readStreamTail(ParcelFileDescriptor.AutoCloseInputStream(stderrPfd))
            } catch (ignore: Exception) { }
        }
        stdoutThread.start()
        stderrThread.start()
        val finished = remote.waitForTimeout(SCRIPT_TIMEOUT_SECONDS, TimeUnit.SECONDS.name)
        val exitCode = if (finished) {
            remote.exitValue()
        } else {
            remote.destroy()
            try { stdoutPfd.close() } catch (ignore: Exception) {}
            try { stderrPfd.close() } catch (ignore: Exception) {}
            124
        }
        stdoutThread.join(1000)
        stderrThread.join(1000)

        val finalOutput = if (live) synchronized(streamingBuffer) { streamingBuffer.toString() } else stdout
        if (live) {
            _liveOutput.value = LiveModuleOutput(
                moduleId = module.id,
                moduleName = module.name,
                text = finalOutput.takeLast(MAX_OUTPUT_CHARS) + "\n\n--- Exit code: $exitCode ---",
                running = false
            )
        }

        val result = ModuleActionResult(
            exitCode = exitCode,
            stdout = finalOutput.takeLast(MAX_OUTPUT_CHARS),
            stderr = stderr.takeLast(MAX_OUTPUT_CHARS)
        )
        writeLastLog(logFile, module, script, result, finished)
        return result
    }

    fun clearLiveOutput() {
        _liveOutput.value = null
    }

    fun readModule(directory: File): AdbModule? {
        val propFile = directory.resolve("module.prop")
        if (!propFile.isFile) return null

        val props = parseModuleProp(propFile.readText())
        val id = props["id"]?.trim().orEmpty()
        if (!idRegex.matches(id)) return null

        val name = props["name"]?.trim().takeUnless { it.isNullOrEmpty() } ?: id
        val version = props["version"]?.trim().takeUnless { it.isNullOrEmpty() } ?: "1.0"
        val versionCode = props["versionCode"]?.trim()?.toLongOrNull() ?: 1L

        return AdbModule(
            id = id,
            name = name,
            version = version,
            versionCode = versionCode,
            author = props["author"]?.takeIf { it.isNotBlank() },
            description = props["description"]?.takeIf { it.isNotBlank() },
            directory = directory,
            banner = findFirstExistingFile(
                directory,
                props["banner"],
                "banner.png",
                "banner.jpg",
                "banner.jpeg",
                "banner.webp"
            ),
            icon = findFirstExistingFile(
                directory,
                props["icon"],
                "icon.png",
                "icon.jpg",
                "icon.jpeg",
                "icon.webp"
            ) ?: findFirstExistingDir(
                directory,
                props["webui"],
                "webroot",
                "webui",
                "web"
            )?.let { webRoot ->
                findFirstExistingFile(
                    webRoot,
                    "icon.png",
                    "icon.jpg",
                    "icon.webp",
                    "favicon.png",
                    "favicon.ico"
                )
            },
            webRoot = findFirstExistingDir(
                directory,
                props["webui"],
                "webroot",
                "webui",
                "web"
            ),
            declaresShellBridge = props["usesShellBridge"]?.toBooleanStrictOrNull()
                ?: props["shellBridge"]?.toBooleanStrictOrNull()
                ?: false,
            actionScript = findFirstExistingFile(
                directory,
                props["action"],
                "action.sh",
                "common/action.sh",
                "run.sh",
                "main.sh",
                "exec.sh"
            ),
            serviceScript = findFirstExistingFile(
                directory,
                props["service"],
                "service.sh",
                "common/service.sh",
                "late_start.sh"
            ),
            logsDir = directory.resolve("logs"),
            sizeBytes = directory.walkTopDown()
                .filter { it.isFile }
                .sumOf { it.length() },
            enabled = !directory.resolve(DISABLE_FILE).exists(),
            url = props["url"]?.takeIf { it.isNotBlank() } ?: props["github"]?.takeIf { it.isNotBlank() },
            updateJson = props["updateJson"]?.takeIf { it.isNotBlank() }
        )
    }

    internal fun parseModuleProp(content: String): Map<String, String> {
        return content.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") && '=' in it }
            .associate {
                val index = it.indexOf('=')
                it.substring(0, index).trim() to it.substring(index + 1).trim()
            }
    }

    private fun cleanZipName(name: String): String {
        val clean = name.replace('\\', '/').trim('/')
        require(clean.isNotBlank()) { "Invalid ZIP entry." }
        val parts = clean.split('/')
        require(parts.none { it.isBlank() || it == "." || it == ".." }) {
            "Unsafe ZIP entry: $name"
        }
        return clean
    }

    private fun ensureInside(root: File, child: File) {
        val rootPath = root.canonicalPath
        val childPath = child.canonicalPath
        require(childPath == rootPath || childPath.startsWith("$rootPath/")) {
            "Unsafe module path: ${child.name}"
        }
    }

    private fun markScriptsExecutable(directory: File, customAction: String?) {
        val declared = customAction?.trim()?.trim('/')
        directory.walkTopDown().forEach { file ->
            file.setReadable(true, false)
            if (file.isDirectory ||
                file.extension.equals("sh", ignoreCase = true) ||
                (declared != null && file.isFile && file.toRelativeString(directory) == declared)
            ) {
                file.setExecutable(true, false)
            }
        }
    }

    private fun writeLastLog(
        logFile: File,
        module: AdbModule,
        script: File,
        result: ModuleActionResult,
        finished: Boolean
    ) {
        logFile.writeText(
            buildString {
                appendLine("module=${module.id}")
                appendLine("script=${script.name}")
                appendLine("exitCode=${result.exitCode}")
                appendLine("timedOut=${!finished}")
                if (result.stdout.isNotBlank()) {
                    appendLine("--- stdout ---")
                    appendLine(result.stdout.trimEnd())
                }
                if (result.stderr.isNotBlank()) {
                    appendLine("--- stderr ---")
                    appendLine(result.stderr.trimEnd())
                }
            }
        )
    }

    private fun findFirstExistingFile(directory: File, vararg paths: String?): File? {
        return paths.asSequence()
            .filterNotNull()
            .map { it.replace('\\', '/').trim().trim('/') }
            .filter { it.isNotBlank() }
            .mapNotNull { rel ->
                val candidate = directory.resolve(rel)
                runCatching {
                    ensureInside(directory, candidate)
                    candidate.takeIf { it.isFile }
                }.getOrNull()
            }
            .firstOrNull()
    }

    private fun findFirstExistingDir(directory: File, vararg paths: String?): File? {
        return paths.asSequence()
            .filterNotNull()
            .map { it.replace('\\', '/').trim().trim('/') }
            .filter { it.isNotBlank() }
            .mapNotNull { rel ->
                val candidate = directory.resolve(rel)
                runCatching {
                    ensureInside(directory, candidate)
                    candidate.takeIf { it.isDirectory }
                }.getOrNull()
            }
            .firstOrNull()
    }

    private fun readStreamTail(inputStream: java.io.InputStream): String {
        return inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
            val buffer = CharArray(8192)
            val tail = StringBuilder()
            while (true) {
                val read = reader.read(buffer)
                if (read <= 0) break
                tail.append(buffer, 0, read)
                if (tail.length > MAX_OUTPUT_CHARS * 2) {
                    tail.delete(0, tail.length - MAX_OUTPUT_CHARS)
                }
            }
            if (tail.length > MAX_OUTPUT_CHARS) {
                tail.substring(tail.length - MAX_OUTPUT_CHARS)
            } else {
                tail.toString()
            }
        }
    }
}
