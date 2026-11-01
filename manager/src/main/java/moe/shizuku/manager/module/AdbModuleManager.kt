package moe.shizuku.manager.module

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.hamondev.shevery.api.SheveryPrivilege
import moe.shizuku.server.IShizukuService
import rikka.shizuku.Shizuku
import java.io.BufferedInputStream
import java.io.File
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.zip.ZipFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import moe.shizuku.manager.module.update.ModuleInstaller
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap

object AdbModuleManager {

    private const val MODULES_DIR = "adb_modules"
    private const val DISABLE_FILE = "disable"
    private const val MAX_OUTPUT_CHARS = 48 * 1024
    private const val MAX_EXTRACTED_BYTES = 32L * 1024L * 1024L
    private const val MAX_ENTRY_COUNT = 512
    private const val SCRIPT_TIMEOUT_SECONDS = 60L
    @Volatile
    private var servicesStartedForBinder = false

    private val idRegex = Regex("[A-Za-z][A-Za-z0-9._-]{1,63}")
    private val installMutexes = ConcurrentHashMap<String, Mutex>()

    // Process-wide scope so downloads, ZIP installs, and module scripts are never
    // cancelled when a Composable dialog/screen leaves the composition.
    val operationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _modulesReloadTick = MutableStateFlow(0)
    val modulesReloadTick: StateFlow<Int> = _modulesReloadTick.asStateFlow()

    fun notifyModulesChanged() {
        _modulesReloadTick.value = _modulesReloadTick.value + 1
    }

    fun cleanupStagingDirs(context: Context) {
        modulesRoot(context).listFiles { file ->
            file.isDirectory && file.name.startsWith(".") && file.name.endsWith(".installing")
        }?.forEach { it.deleteRecursively() }
    }

    enum class ConsoleKind {
        INSTALL,
        ACTION,
        SERVICE
    }

    // Live output for streaming module actions and installations (KernelSU Next style)
    private val _liveOutput = MutableStateFlow<LiveModuleOutput?>(null)
    val liveOutput: StateFlow<LiveModuleOutput?> = _liveOutput.asStateFlow()
    private val sessionLogLock = Any()
    private val sessionLogBuffer = StringBuilder()

    data class LiveModuleOutput(
        val moduleId: String,
        val moduleName: String,
        val text: String,
        val running: Boolean,
        val kind: ConsoleKind = ConsoleKind.ACTION,
        val subtitle: String = "",
        val progress: Float? = null,
        val succeeded: Boolean = false,
        val exitCode: Int? = null,
        val module: AdbModule? = null
    )

    fun modulesRoot(context: Context): File {
        val filesDir = context.filesDir
        // Shell (uid 2000) must be able to traverse to the modules directory, but it
        // must not be able to list the rest of the app's private storage.
        filesDir.parentFile?.apply {
            setExecutable(true, false)
        }
        filesDir.apply {
            mkdirs()
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

    suspend fun install(
        context: Context,
        uri: Uri,
        onLog: (String) -> Unit = {},
        onProgress: (Float?) -> Unit = {}
    ): AdbModule = withContext(Dispatchers.IO) {
        cleanupStagingDirs(context)
        val temp = File.createTempFile("adb_module_", ".zip", context.cacheDir)
        try {
            onLog("- Copying archive to staging cache...")
            val inputStream = if (uri.scheme.equals("file", ignoreCase = true)) {
                uri.path?.let { File(it).takeIf(File::isFile)?.inputStream() }
            } else {
                context.contentResolver.openInputStream(uri)
            }
            inputStream?.use { input ->
                temp.outputStream().use { output -> input.copyTo(output) }
            } ?: error("Unable to read selected ZIP file.")

            ZipFile(temp).use { zip ->
                val allEntries = zip.entries().asSequence().toList()
                onLog("- Opening ZIP archive (${allEntries.size} entries)...")
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
                val propName = props["name"]?.trim().takeUnless { it.isNullOrEmpty() } ?: id
                val propVersion = props["version"]?.trim().takeUnless { it.isNullOrEmpty() } ?: "1.0"
                val propVersionCode = props["versionCode"]?.trim() ?: "1"
                val propAuthor = props["author"]?.trim().takeUnless { it.isNullOrEmpty() } ?: "Unknown"

                onLog("*********************************************************")
                onLog("* Name: $propName")
                onLog("* ID: $id")
                onLog("* Version: $propVersion ($propVersionCode)")
                onLog("* Author: $propAuthor")
                onLog("*********************************************************")
                onProgress(0.80f)

                val installedModule = installMutexes.getOrPut(id) { Mutex() }.withLock {
                    val modulesDir = modulesRoot(context)
                    val target = File(modulesDir, id)
                    val staging = File(modulesDir, ".$id.installing")
                    val wasTrusted = ModuleSettings.isModuleTrusted(id)
                    val previousFingerprint = if (wasTrusted) moduleFingerprint(target) else null
                    staging.deleteRecursively()
                    staging.mkdirs()
                    onLog("- Preparing staging directory .${id}.installing...")

                    var entryCount = 0
                    var extractedBytes = 0L
                    val totalEntries = allEntries.size.coerceAtLeast(1)
                    allEntries.forEachIndexed { idx, entry ->
                        val normalizedName = entry.name.replace('\\', '/').trim('/')
                        if (normalizedName.isBlank()) return@forEachIndexed
                        val relativeName = if (rootPrefix.isNotEmpty()) {
                            if (!normalizedName.startsWith(rootPrefix)) return@forEachIndexed
                            normalizedName.removePrefix(rootPrefix).trimStart('/')
                        } else {
                            normalizedName
                        }
                        if (relativeName.isBlank()) return@forEachIndexed

                        entryCount++
                        require(entryCount <= MAX_ENTRY_COUNT) { "Module ZIP has too many files." }
                        val cleanName = cleanZipName(relativeName)
                        val outFile = File(staging, cleanName)
                        ensureInside(staging, outFile)
                        if (entry.isDirectory) {
                            outFile.mkdirs()
                        } else {
                            outFile.parentFile?.mkdirs()
                            var fileBytes = 0L
                            BufferedInputStream(zip.getInputStream(entry)).use { input ->
                                outFile.outputStream().use { output ->
                                    val buffer = ByteArray(8192)
                                    while (true) {
                                        val read = input.read(buffer)
                                        if (read <= 0) break
                                        extractedBytes += read.toLong()
                                        fileBytes += read.toLong()
                                        require(extractedBytes <= MAX_EXTRACTED_BYTES) { "Module ZIP is too large." }
                                        output.write(buffer, 0, read)
                                    }
                                }
                            }
                            onLog("- Extracting: $cleanName (${formatBytes(fileBytes)})")
                            onProgress(0.80f + ((idx + 1).toFloat() / totalEntries.toFloat()) * 0.16f)
                        }
                    }

                    // Preserve existing .source.prop across updates if not included in new ZIP
                    val existingSourceProp = target.resolve(".source.prop")
                    val stagingSourceProp = staging.resolve(".source.prop")
                    if (existingSourceProp.isFile && !stagingSourceProp.exists()) {
                        runCatching { existingSourceProp.copyTo(stagingSourceProp, overwrite = false) }
                    }

                    onLog("- Setting executable permissions (chmod 0755) on scripts...")
                    markScriptsExecutable(staging, props["action"])
                    val newFingerprint = moduleFingerprint(staging)
                    onLog("- Moving module into ${target.absolutePath}...")
                    // Atomic-ish swap: keep the old tree until the new one is in place.
                    val backup = File(modulesDir, ".$id.old")
                    backup.deleteRecursively()
                    if (target.exists()) {
                        check(target.renameTo(backup)) { "Unable to move the existing module aside." }
                    }
                    if (!staging.renameTo(target)) {
                        if (backup.exists()) backup.renameTo(target)
                        error("Unable to move module into storage.")
                    }
                    backup.deleteRecursively()
                    if (wasTrusted && previousFingerprint != null &&
                        newFingerprint != null && previousFingerprint != newFingerprint
                    ) {
                        // Trust belongs to the content the user approved, never to an id.
                        ModuleSettings.setModuleTrusted(id, false)
                        onLog("- Module content changed; Full Trust revoked.")
                    }
                    markScriptsExecutable(target, props["action"])
                    onProgress(1.0f)
                    readModule(target) ?: error("Installed module is unreadable.")
                }
                notifyModulesChanged()
                installedModule
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
        notifyModulesChanged()
    }

    suspend fun delete(module: AdbModule) = withContext(Dispatchers.IO) {
        ModuleSettings.setModuleTrusted(module.id, false)
        module.directory.deleteRecursively()
        notifyModulesChanged()
    }

    suspend fun runAction(module: AdbModule): ModuleActionResult = withContext(Dispatchers.IO) {
        check(ModuleSettings.canRunAction(module)) { "actionScript is blocked by module access policy." }
        val script = module.actionScript?.takeIf { it.isFile } ?: error("This module has no action script.")
        runModuleScript(module, script, module.lastActionLog)
    }

    suspend fun runActionStreaming(module: AdbModule): ModuleActionResult = withContext(Dispatchers.IO) {
        check(ModuleSettings.canRunAction(module)) { "actionScript is blocked by module access policy." }
        val script = module.actionScript?.takeIf { it.isFile } ?: error("This module has no action script.")
        runModuleScriptStreaming(module, script, module.lastActionLog, live = true, kind = ConsoleKind.ACTION)
    }

    suspend fun runService(module: AdbModule): ModuleActionResult = withContext(Dispatchers.IO) {
        check(ModuleSettings.canRunService(module)) { "serviceScript is blocked by module access policy." }
        check(ModuleSettings.canRunBackground(module)) { "Background actions are disabled." }
        val script = module.serviceScript?.takeIf { it.isFile } ?: error("This module has no service script.")
        runModuleScript(module, script, module.lastServiceLog)
    }

    suspend fun runServiceStreaming(module: AdbModule): ModuleActionResult = withContext(Dispatchers.IO) {
        check(ModuleSettings.canRunService(module)) { "serviceScript is blocked by module access policy." }
        check(ModuleSettings.canRunBackground(module)) { "Background actions are disabled." }
        val script = module.serviceScript?.takeIf { it.isFile } ?: error("This module has no service script.")
        runModuleScriptStreaming(module, script, module.lastServiceLog, live = true, kind = ConsoleKind.SERVICE)
    }

    fun launchInstallFromUri(
        context: Context,
        uri: Uri,
        displayName: String = "ZIP Module",
        deleteFile: File? = null,
        onFinished: (Result<AdbModule>) -> Unit = {}
    ) {
        val appContext = context.applicationContext
        beginConsoleSession(
            moduleId = "install",
            moduleName = displayName,
            kind = ConsoleKind.INSTALL,
            subtitle = deleteFile?.name ?: uri.lastPathSegment ?: "module.zip",
            initialBanner = buildString {
                appendLine("*********************************************************")
                appendLine("* Shevery Module Installer")
                appendLine("* Source: ${deleteFile?.name ?: uri.lastPathSegment ?: displayName}")
                appendLine("*********************************************************")
            },
            progress = 0.05f
        )
        operationScope.launch {
            val result = runCatching {
                install(
                    context = appContext,
                    uri = uri,
                    onLog = ::appendConsoleLine,
                    onProgress = ::updateConsoleProgress
                )
            }
            try {
                deleteFile?.delete()
            } catch (_: Exception) {
            }
            result.fold(
                onSuccess = { module ->
                    appendConsoleLine("---------------------------------------------------------")
                    appendConsoleLine("[✓] Installed ${module.name} v${module.version ?: "1.0"} (${module.formattedSize})")
                    appendConsoleLine("*********************************************************")
                    completeConsoleSession(
                        moduleId = module.id,
                        moduleName = module.name,
                        module = module,
                        succeeded = true,
                        exitCode = 0
                    )
                },
                onFailure = { err ->
                    appendConsoleLine("---------------------------------------------------------")
                    appendConsoleLine("[!] Installation failed: ${err.message ?: err.javaClass.simpleName}")
                    appendConsoleLine("*********************************************************")
                    completeConsoleSession(
                        moduleId = "install",
                        moduleName = displayName,
                        module = null,
                        succeeded = false,
                        exitCode = 1
                    )
                }
            )
            onFinished(result)
        }
    }

    fun launchCatalogInstall(
        context: Context,
        moduleId: String,
        moduleName: String,
        owner: String,
        repo: String,
        subPath: String? = null,
        onFinished: (Result<AdbModule>) -> Unit = {}
    ) {
        val appContext = context.applicationContext
        val modeLabel = if (ModuleSettings.getInstallMode() == ModuleSettings.InstallMode.SOURCES) {
            "Build from sources"
        } else {
            "Release ZIP"
        }
        beginConsoleSession(
            moduleId = moduleId,
            moduleName = moduleName,
            kind = ConsoleKind.INSTALL,
            subtitle = "$owner/$repo • $modeLabel",
            initialBanner = buildString {
                appendLine("*********************************************************")
                appendLine("* Shevery Module Installer")
                appendLine("* Module: $moduleName ($moduleId)")
                appendLine("* Source: github.com/$owner/$repo")
                appendLine("*********************************************************")
            },
            progress = 0.01f
        )
        operationScope.launch {
            val result = ModuleInstaller.getInstance().installModule(
                context = appContext,
                moduleId = moduleId,
                owner = owner,
                repo = repo,
                subPath = subPath,
                onLog = ::appendConsoleLine,
                onProgress = ::updateConsoleProgress
            )
            result.fold(
                onSuccess = { module ->
                    appendConsoleLine("---------------------------------------------------------")
                    appendConsoleLine("[✓] Installed ${module.name} v${module.version ?: "1.0"} (${module.formattedSize})")
                    appendConsoleLine("*********************************************************")
                    completeConsoleSession(
                        moduleId = module.id,
                        moduleName = module.name,
                        module = module,
                        succeeded = true,
                        exitCode = 0
                    )
                },
                onFailure = { err ->
                    appendConsoleLine("---------------------------------------------------------")
                    appendConsoleLine("[!] Installation failed: ${err.message ?: err.javaClass.simpleName}")
                    appendConsoleLine("*********************************************************")
                    completeConsoleSession(
                        moduleId = moduleId,
                        moduleName = moduleName,
                        module = null,
                        succeeded = false,
                        exitCode = 1
                    )
                }
            )
            onFinished(result)
        }
    }

    fun launchUrlUpdateInstall(
        context: Context,
        module: AdbModule,
        zipUrl: String,
        newVersion: String,
        onFinished: (Result<AdbModule>) -> Unit = {}
    ) {
        val appContext = context.applicationContext
        beginConsoleSession(
            moduleId = module.id,
            moduleName = module.name,
            kind = ConsoleKind.INSTALL,
            subtitle = "Update -> v$newVersion",
            initialBanner = buildString {
                appendLine("*********************************************************")
                appendLine("* Shevery Module Updater")
                appendLine("* Module: ${module.name} (${module.id})")
                appendLine("* Target version: v$newVersion")
                appendLine("*********************************************************")
            },
            progress = 0.02f
        )
        operationScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    appendConsoleLine("- Downloading update from $zipUrl...")
                    val client = OkHttpClient.Builder()
                        .connectTimeout(15, TimeUnit.SECONDS)
                        .readTimeout(30, TimeUnit.SECONDS)
                        .build()
                    val request = Request.Builder().url(zipUrl).build()
                    val cacheDir = File(appContext.cacheDir, "module_zips").apply { mkdirs() }
                    val zipFile = File(cacheDir, "${module.id}-update-$newVersion.zip")
                    client.newCall(request).execute().use { resp ->
                        if (!resp.isSuccessful) throw IllegalStateException("Download failed: HTTP ${resp.code}")
                        val body = resp.body ?: throw IllegalStateException("Empty response body")
                        val total = body.contentLength()
                        body.byteStream().use { input ->
                            zipFile.outputStream().use { output ->
                                val buffer = ByteArray(8192)
                                var downloaded = 0L
                                var nextMilestone = 25
                                while (true) {
                                    val read = input.read(buffer)
                                    if (read <= 0) break
                                    output.write(buffer, 0, read)
                                    downloaded += read
                                    if (total > 0L) {
                                        val pct = ((downloaded * 100L) / total).toInt().coerceIn(0, 100)
                                        updateConsoleProgress(0.05f + (pct / 100f) * 0.70f)
                                        if (pct >= nextMilestone && nextMilestone <= 100) {
                                            appendConsoleLine("  ↳ $pct% (${formatBytes(downloaded)} / ${formatBytes(total)})")
                                            nextMilestone += 25
                                        }
                                    }
                                }
                            }
                        }
                    }
                    appendConsoleLine("- Downloaded ${zipFile.name} (${formatBytes(zipFile.length())})")
                    install(
                        context = appContext,
                        uri = Uri.fromFile(zipFile),
                        onLog = ::appendConsoleLine,
                        onProgress = ::updateConsoleProgress
                    )
                }
            }
            result.fold(
                onSuccess = { updated ->
                    appendConsoleLine("---------------------------------------------------------")
                    appendConsoleLine("[✓] Updated ${updated.name} to v${updated.version ?: newVersion}")
                    appendConsoleLine("*********************************************************")
                    completeConsoleSession(
                        moduleId = updated.id,
                        moduleName = updated.name,
                        module = updated,
                        succeeded = true,
                        exitCode = 0
                    )
                },
                onFailure = { err ->
                    appendConsoleLine("---------------------------------------------------------")
                    appendConsoleLine("[!] Update failed: ${err.message ?: err.javaClass.simpleName}")
                    appendConsoleLine("*********************************************************")
                    completeConsoleSession(
                        moduleId = module.id,
                        moduleName = module.name,
                        module = module,
                        succeeded = false,
                        exitCode = 1
                    )
                }
            )
            onFinished(result)
        }
    }

    fun launchScriptExecution(
        module: AdbModule,
        isService: Boolean,
        onFinished: (Result<ModuleActionResult>) -> Unit = {}
    ) {
        operationScope.launch {
            val result = runCatching {
                if (isService) {
                    runServiceStreaming(module)
                } else {
                    runActionStreaming(module)
                }
            }
            result.onFailure { err ->
                val scriptName = if (isService) {
                    module.serviceScript?.name ?: "service.sh"
                } else {
                    module.actionScript?.name ?: "action.sh"
                }
                val kind = if (isService) ConsoleKind.SERVICE else ConsoleKind.ACTION
                val currentText = _liveOutput.value?.text.orEmpty()
                val failureLog = buildString {
                    if (currentText.isBlank()) {
                        appendLine("*********************************************************")
                        appendLine("* Shevery Module Runner")
                        appendLine("* Module: ${module.name} (${module.id})")
                        appendLine("* Script: $scriptName")
                        appendLine("*********************************************************")
                    } else {
                        append(currentText)
                        if (!currentText.endsWith("\n")) appendLine()
                    }
                    appendLine("---------------------------------------------------------")
                    appendLine("[!] Execution failed: ${err.message ?: err.javaClass.simpleName}")
                    appendLine("*********************************************************")
                }
                _liveOutput.value = LiveModuleOutput(
                    moduleId = module.id,
                    moduleName = module.name,
                    text = failureLog.takeLast(MAX_OUTPUT_CHARS),
                    running = false,
                    kind = kind,
                    subtitle = scriptName,
                    progress = null,
                    succeeded = false,
                    exitCode = -1,
                    module = module
                )
            }
            onFinished(result)
        }
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
        runModuleScriptStreaming(module, script, logFile, live = false, kind = ConsoleKind.ACTION)

    private fun runModuleScriptStreaming(
        module: AdbModule,
        script: File,
        logFile: File,
        live: Boolean = false,
        kind: ConsoleKind = ConsoleKind.ACTION
    ): ModuleActionResult {
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

        val arch = android.os.Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a"
        val streamingBuffer = StringBuilder()
        if (live) {
            val header = buildString {
                appendLine("*********************************************************")
                appendLine("* Shevery Module Runner")
                appendLine("* Module: ${module.name} (${module.id}) v${module.version ?: "1.0"}")
                appendLine("* Script: ${script.name} | Arch: $arch")
                appendLine("*********************************************************")
                appendLine("$ sh ${script.name}")
            }
            streamingBuffer.append(header)
            updateLiveOutput(
                module = module,
                text = streamingBuffer.toString(),
                running = true,
                kind = kind,
                subtitle = script.name,
                succeeded = false,
                exitCode = null
            )
        }

        val binder = SheveryPrivilege.getBinder() ?: Shizuku.getBinder() ?: error("Shevery service is not running.")
        val service = IShizukuService.Stub.asInterface(binder)
        ensureAdbShim(service)
        val env = arrayOf(
            "MODDIR=${module.directory.absolutePath}",
            "MODPATH=${module.directory.absolutePath}",
            "ASH_STANDALONE=1",
            "SHIZUKU_MODULE_ID=${module.id}",
            "SHIZUKU_MODULE_MODE=${ModuleSettings.getAccessMode().value}",
            "SHIZUKU_MODULE_TRUSTED=${if (ModuleSettings.isModuleTrusted(module.id)) "1" else "0"}",
            "SHIZUKU_MODULE_BACKGROUND=${if (ModuleSettings.canRunBackground(module)) "1" else "0"}",
            "AXERON=true",
            "AXERONVER=1.0.0",
            "ARCH=$arch",
            "PATH=/data/local/tmp/shevery/bin:/product/bin:/apex/com.android.runtime/bin:/apex/com.android.art/bin:/system_ext/bin:/system/bin:/system/xbin:/odm/bin:/vendor/bin:/vendor/xbin:/sbin:/data/adb/apatch:/data/adb/ksu/bin"
        )
        val wrappedScript = buildString {
            appendLine("export PATH=\"/data/local/tmp/shevery/bin:/product/bin:/apex/com.android.runtime/bin:/apex/com.android.art/bin:/system_ext/bin:/system/bin:/system/xbin:/odm/bin:/vendor/bin:/vendor/xbin:/sbin:/data/adb/apatch:/data/adb/ksu/bin:\$PATH\"")
            appendLine("ui_print() { echo \"\$@\"; }")
            appendLine("""adb() {
    while [ ${'$'}# -gt 0 ]; do
        case "${'$'}1" in
            -s|-H|-P|-L) shift 2 ;;
            -d|-e) shift 1 ;;
            *) break ;;
        esac
    done
    case "${'$'}1" in
        shell)
            shift
            if [ ${'$'}# -eq 0 ]; then
                sh
            else
                eval "${'$'}*"
            fi
            ;;
        devices)
            echo "List of devices attached"
            echo "shevery-device	device"
            ;;
        version)
            echo "Android Debug Bridge version 1.0.41"
            echo "Version shevery-adb-shim"
            ;;
        get-state|get-serialno)
            echo "shevery-device"
            ;;
        wait-for-device)
            return 0
            ;;
        push|pull)
            shift
            cp -r "${'$'}@"
            ;;
        install)
            shift
            pm install "${'$'}@"
            ;;
        uninstall)
            shift
            pm uninstall "${'$'}@"
            ;;
        *)
            if [ ${'$'}# -eq 0 ]; then
                sh
            else
                eval "${'$'}*"
            fi
            ;;
    esac
}
export -f adb 2>/dev/null || true""")
            appendLine("cd \"\$MODDIR\" 2>/dev/null || true")
            append(scriptContent)
        }
        val remote = service.newProcess(
            arrayOf("sh", "-c", wrappedScript),
            env,
            "/data/local/tmp"
        )

        runCatching { ParcelFileDescriptor.AutoCloseOutputStream(remote.getOutputStream()).close() }
        val stdoutPfd = remote.getInputStream()
        val stderrPfd = remote.getErrorStream()
        var stdout = ""
        var stderr = ""
        val stdoutThread = Thread {
            if (live) {
                val collected = StringBuilder()
                try {
                    ParcelFileDescriptor.AutoCloseInputStream(stdoutPfd).bufferedReader(Charsets.UTF_8).use { r ->
                        val buffer = CharArray(256)
                        while (true) {
                            val read = r.read(buffer)
                            if (read <= 0) break
                            val chunk = String(buffer, 0, read)
                            val snapshot = synchronized(streamingBuffer) {
                                collected.appendBounded(chunk)
                                streamingBuffer.appendBounded(chunk)
                                stdout = collected.toString()
                                streamingBuffer.toString()
                            }
                            updateLiveOutput(
                                module = module,
                                text = snapshot.takeLast(MAX_OUTPUT_CHARS),
                                running = true,
                                kind = kind,
                                subtitle = script.name,
                                succeeded = false,
                                exitCode = null
                            )
                        }
                    }
                } catch (ignore: Exception) {
                } finally {
                    stdout = collected.toString()
                }
            } else {
                try {
                    stdout = readStreamTail(ParcelFileDescriptor.AutoCloseInputStream(stdoutPfd))
                } catch (ignore: Exception) { }
            }
        }
        val stderrThread = Thread {
            if (live) {
                val collected = StringBuilder()
                try {
                    ParcelFileDescriptor.AutoCloseInputStream(stderrPfd).bufferedReader(Charsets.UTF_8).use { r ->
                        val buffer = CharArray(256)
                        while (true) {
                            val read = r.read(buffer)
                            if (read <= 0) break
                            val chunk = String(buffer, 0, read)
                            val snapshot = synchronized(streamingBuffer) {
                                collected.appendBounded(chunk)
                                streamingBuffer.appendBounded(chunk)
                                stderr = collected.toString()
                                streamingBuffer.toString()
                            }
                            updateLiveOutput(
                                module = module,
                                text = snapshot.takeLast(MAX_OUTPUT_CHARS),
                                running = true,
                                kind = kind,
                                subtitle = script.name,
                                succeeded = false,
                                exitCode = null
                            )
                        }
                    }
                } catch (ignore: Exception) {
                } finally {
                    stderr = collected.toString()
                }
            } else {
                try {
                    stderr = readStreamTail(ParcelFileDescriptor.AutoCloseInputStream(stderrPfd))
                } catch (ignore: Exception) { }
            }
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
        stdoutThread.join(3000)
        stderrThread.join(3000)

        if (live) {
            val footer = buildString {
                if (streamingBuffer.isNotEmpty() && !streamingBuffer.endsWith("\n")) {
                    appendLine()
                }
                if (stdout.isBlank() && stderr.isBlank()) {
                    appendLine("(Process completed with no console output)")
                }
                appendLine("---------------------------------------------------------")
                if (!finished) {
                    appendLine("[!] Process timed out after ${SCRIPT_TIMEOUT_SECONDS}s (exit code 124)")
                } else if (exitCode == 0) {
                    appendLine("[✓] Process finished with exit code 0")
                } else {
                    appendLine("[!] Process exited with code $exitCode")
                }
                appendLine("*********************************************************")
            }
            val snapshot = synchronized(streamingBuffer) {
                streamingBuffer.appendBounded(footer)
                streamingBuffer.toString()
            }
            updateLiveOutput(
                module = module,
                text = snapshot.takeLast(MAX_OUTPUT_CHARS),
                running = false,
                kind = kind,
                subtitle = script.name,
                succeeded = finished && exitCode == 0,
                exitCode = exitCode
            )
        }

        val result = ModuleActionResult(
            exitCode = exitCode,
            stdout = stdout.takeLast(MAX_OUTPUT_CHARS),
            stderr = stderr.takeLast(MAX_OUTPUT_CHARS)
        )
        writeLastLog(logFile, module, script, result, finished)
        return result
    }

    private fun beginConsoleSession(
        moduleId: String,
        moduleName: String,
        kind: ConsoleKind,
        subtitle: String,
        initialBanner: String,
        progress: Float?
    ) {
        val snapshot = synchronized(sessionLogLock) {
            sessionLogBuffer.clear()
            sessionLogBuffer.append(initialBanner)
            sessionLogBuffer.toString()
        }
        _liveOutput.value = LiveModuleOutput(
            moduleId = moduleId,
            moduleName = moduleName,
            text = snapshot,
            running = true,
            kind = kind,
            subtitle = subtitle,
            progress = progress,
            succeeded = false,
            exitCode = null,
            module = null
        )
    }

    private fun appendConsoleLine(line: String) {
        val snapshot = synchronized(sessionLogLock) {
            sessionLogBuffer.appendBounded(line + "\n")
            sessionLogBuffer.toString()
        }
        val current = _liveOutput.value ?: return
        _liveOutput.value = current.copy(text = snapshot.takeLast(MAX_OUTPUT_CHARS))
    }

    private fun updateConsoleProgress(progress: Float?) {
        val current = _liveOutput.value ?: return
        _liveOutput.value = current.copy(progress = progress?.coerceIn(0f, 1f))
    }

    private fun completeConsoleSession(
        moduleId: String,
        moduleName: String,
        module: AdbModule?,
        succeeded: Boolean,
        exitCode: Int
    ) {
        val snapshot = synchronized(sessionLogLock) { sessionLogBuffer.toString() }
        val current = _liveOutput.value
        _liveOutput.value = LiveModuleOutput(
            moduleId = moduleId,
            moduleName = moduleName,
            text = snapshot.takeLast(MAX_OUTPUT_CHARS),
            running = false,
            kind = current?.kind ?: ConsoleKind.INSTALL,
            subtitle = current?.subtitle.orEmpty(),
            progress = if (succeeded) 1.0f else null,
            succeeded = succeeded,
            exitCode = exitCode,
            module = module
        )
    }

    private fun updateLiveOutput(
        module: AdbModule,
        text: String,
        running: Boolean,
        kind: ConsoleKind = ConsoleKind.ACTION,
        subtitle: String = "",
        succeeded: Boolean = false,
        exitCode: Int? = null
    ) {
        _liveOutput.value = LiveModuleOutput(
            moduleId = module.id,
            moduleName = module.name,
            text = text,
            running = running,
            kind = kind,
            subtitle = subtitle,
            progress = null,
            succeeded = succeeded,
            exitCode = exitCode,
            module = module
        )
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes < 1024L) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024.0) return String.format(Locale.US, "%.1f KB", kb)
        return String.format(Locale.US, "%.2f MB", kb / 1024.0)
    }

    private fun StringBuilder.appendBounded(chunk: String) {
        append(chunk)
        if (length > MAX_OUTPUT_CHARS * 2) {
            delete(0, length - MAX_OUTPUT_CHARS)
        }
    }

    fun clearLiveOutput() {
        _liveOutput.value = null
    }

    /**
     * Stable digest of the module payload. Used to revoke Full Trust when an update
     * replaces a trusted module with different content.
     */
    private fun moduleFingerprint(directory: File): String? {
        if (!directory.isDirectory) return null
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val files = directory.walkTopDown()
                .filter { it.isFile && it.name != DISABLE_FILE && it.name != ".source.prop" }
                .sortedBy { it.relativeTo(directory).invariantSeparatorsPath }
            for (file in files) {
                val relative = file.relativeTo(directory).invariantSeparatorsPath
                digest.update(relative.toByteArray(Charsets.UTF_8))
                digest.update(0)
                BufferedInputStream(file.inputStream()).use { input ->
                    val buffer = ByteArray(8192)
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        digest.update(buffer, 0, read)
                    }
                }
                digest.update(0)
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (_: Exception) {
            null
        }
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

    fun ensureAdbShim(service: IShizukuService) {
        runCatching {
            val shimScript = """
                mkdir -p /data/local/tmp/shevery/bin 2>/dev/null
                cat << 'EOF' > /data/local/tmp/shevery/bin/adb
#!/system/bin/sh
while [ ${'$'}# -gt 0 ]; do
    case "${'$'}1" in
        -s|-H|-P|-L) shift 2 ;;
        -d|-e) shift 1 ;;
        *) break ;;
    esac
done
case "${'$'}1" in
    shell)
        shift
        if [ ${'$'}# -eq 0 ]; then
            exec /system/bin/sh
        else
            eval "${'$'}*"
        fi
        ;;
    devices)
        echo "List of devices attached"
        echo "shevery-device	device"
        exit 0
        ;;
    version)
        echo "Android Debug Bridge version 1.0.41"
        echo "Version shevery-adb-shim"
        exit 0
        ;;
    get-state|get-serialno)
        echo "shevery-device"
        exit 0
        ;;
    wait-for-device)
        exit 0
        ;;
    push|pull)
        shift
        exec cp -r "${'$'}@"
        ;;
    install)
        shift
        exec pm install "${'$'}@"
        ;;
    uninstall)
        shift
        exec pm uninstall "${'$'}@"
        ;;
    *)
        if [ ${'$'}# -eq 0 ]; then
            exec /system/bin/sh
        else
            eval "${'$'}*"
        fi
        ;;
esac
EOF
                chmod 0755 /data/local/tmp/shevery/bin/adb 2>/dev/null
            """.trimIndent()
            val proc = service.newProcess(arrayOf("sh", "-c", shimScript), null, "/data/local/tmp")
            proc?.waitForTimeout(5, TimeUnit.SECONDS.name)
        }
    }
}
