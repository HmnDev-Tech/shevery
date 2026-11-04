package moe.shizuku.manager.module

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewAssetLoader
import moe.shizuku.manager.R
import moe.shizuku.manager.app.AppActivity
import moe.shizuku.manager.security.AuthManager
import moe.shizuku.manager.security.SecuritySettings
import moe.shizuku.manager.ui.compose.ShizukuExpressiveTheme
import moe.shizuku.manager.ui.compose.ShizukuScaffold
import java.io.File
import kotlin.coroutines.resume
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine

private data class PendingCommand(
    val request: ModuleCommandRequest,
    val continuation: kotlinx.coroutines.CancellableContinuation<Boolean>
)

class ModuleWebViewActivity : AppActivity() {

    private val pendingCommands = androidx.compose.runtime.mutableStateListOf<PendingCommand>()
    private var isAuthenticating = false

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        recreate()
    }

    override fun onResume() {
        super.onResume()
        if (!isAuthenticating &&
            SecuritySettings.isActionProtected(SecuritySettings.ProtectedAction.APP_OPEN) &&
            !AuthManager.isAppOpenSessionValid()
        ) {
            isAuthenticating = true
            AuthManager.authenticate(
                activity = this,
                title = getString(R.string.security_auth_prompt_title),
                subtitle = getString(R.string.security_auth_prompt_app_open),
                onResult = { authenticated ->
                    isAuthenticating = false
                    if (authenticated) {
                        AuthManager.markAppOpenAuthenticated()
                    } else {
                        finish()
                    }
                }
            )
        }
    }

    private fun extractModuleId(intent: Intent): String {
        val uri = intent.data

        // 1. Explicit string extras: EXTRA_MODULE_ID ("module_id"), "id", or fallback "EXTRA_MODULE_ID"
        intent.getStringExtra(EXTRA_MODULE_ID)?.takeIf { it.isNotBlank() }?.let { return it }
        intent.getStringExtra("id")?.takeIf { it.isNotBlank() }?.let { return it }
        intent.getStringExtra("EXTRA_MODULE_ID")?.takeIf { it.isNotBlank() }?.let { return it }

        // 2. URI query parameters
        uri?.getQueryParameter("module_id")?.takeIf { it.isNotBlank() }?.let { return it }
        uri?.getQueryParameter("id")?.takeIf { it.isNotBlank() }?.let { return it }

        // 3. URI host and path resolution:
        // If uri.host == "module", read the first path segment (shevery://module/<moduleId>).
        // Otherwise use uri.host (shevery://<moduleId> or shevery://<moduleId>/subpath).
        if (uri != null) {
            val host = uri.host
            if (host.equals("module", ignoreCase = true)) {
                uri.pathSegments?.firstOrNull()?.takeIf { it.isNotBlank() }?.let { return it }
            } else if (!host.isNullOrBlank()) {
                return host
            }
        }

        return ""
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (SecuritySettings.isActionProtected(SecuritySettings.ProtectedAction.APP_OPEN) &&
            !AuthManager.isAppOpenSessionValid()
        ) {
            isAuthenticating = true
            AuthManager.authenticate(
                activity = this,
                title = getString(R.string.security_auth_prompt_title),
                subtitle = getString(R.string.security_auth_prompt_app_open),
                onResult = { authenticated ->
                    isAuthenticating = false
                    if (authenticated) {
                        AuthManager.markAppOpenAuthenticated()
                        initContent()
                    } else {
                        finish()
                    }
                }
            )
        } else {
            initContent()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun initContent() {
        val moduleId = extractModuleId(intent)
        if (moduleId.isBlank() || !AdbModuleManager.isValidModuleId(moduleId)) {
            Toast.makeText(this, R.string.modules_webui_not_found, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val modulesRoot = AdbModuleManager.modulesRoot(this).canonicalFile
        val targetDir = modulesRoot.resolve(moduleId).canonicalFile
        if (!targetDir.toPath().startsWith(modulesRoot.toPath()) || targetDir == modulesRoot) {
            Toast.makeText(this, R.string.modules_webui_not_found, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val module = AdbModuleManager.readModule(targetDir)
        val index = module?.webRoot?.resolve("index.html")?.canonicalFile
        if (module == null || index?.isFile != true || !index.toPath().startsWith(targetDir.toPath())) {
            Toast.makeText(this, R.string.modules_webui_not_found, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Serve module files from a real HTTPS origin instead of file://. This makes
        // ES module imports same-origin (issue #120) without enabling universal file
        // access, and keeps the bridge origin check meaningful.
        val assetLoader = WebViewAssetLoader.Builder()
            .addPathHandler(
                "/modules/${module.id}/",
                WebViewAssetLoader.InternalStoragePathHandler(this, module.directory)
            )
            .build()
        val indexRelative = index.relativeTo(module.directory).invariantSeparatorsPath
        val indexUrl = "https://appassets.androidplatform.net/modules/${module.id}/" +
            indexRelative.split('/').joinToString("/") { Uri.encode(it) ?: it }

        setContent {
            val trusted = ModuleSettings.isModuleTrusted(module.id)
            val webNetworkAllowed = ModuleSettings.canUseWebNetwork(module)
            val exposeBridge = module.enabled &&
                ModuleSettings.canExposeWebBridge(module)

            ShizukuExpressiveTheme {
                ShizukuScaffold(
                    title = module.name,
                    onNavigateUp = { finish() }
                ) { padding ->
                    AndroidView(
                        factory = { context ->
                            WebView(context).apply {
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.allowFileAccess = false
                                settings.allowContentAccess = false
                                settings.allowFileAccessFromFileURLs = false
                                settings.allowUniversalAccessFromFileURLs = false
                                settings.blockNetworkLoads = false
                                settings.cacheMode = WebSettings.LOAD_DEFAULT
                                settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                                CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
                                webViewClient = LocalModuleWebViewClient(
                                    module,
                                    webNetworkAllowed,
                                    assetLoader
                                )
                                if (exposeBridge) {
                                    val bridge = ModuleJsBridge(
                                        module,
                                        webView = this,
                                        commandReviewer = { request ->
                                            runBlocking {
                                                confirmCommand(request)
                                            }
                                        }
                                    )
                                    addJavascriptInterface(bridge, "Shizuku")
                                    addJavascriptInterface(bridge, "shizuku")
                                    addJavascriptInterface(bridge, "Shevery")
                                    addJavascriptInterface(bridge, "shevery")
                                    addJavascriptInterface(bridge, "ksu")
                                    addJavascriptInterface(bridge, "Axeron")
                                    addJavascriptInterface(bridge, "axeron")
                                }
                                loadUrl(indexUrl)
                            }
                        },
                        modifier = androidx.compose.ui.Modifier
                            .padding(padding)
                    )
                }

                val currentPending = pendingCommands.firstOrNull()
                currentPending?.let { pending ->
                    ReCommandDialog(
                        request = pending.request,
                        onDismiss = {
                            if (pending.continuation.isActive) {
                                pending.continuation.resume(false)
                            }
                            pendingCommands.remove(pending)
                        },
                        onReject = {
                            if (pending.continuation.isActive) {
                                pending.continuation.resume(false)
                            }
                            pendingCommands.remove(pending)
                        },
                        onApprove = {
                            if (pending.continuation.isActive) {
                                pending.continuation.resume(true)
                            }
                            pendingCommands.remove(pending)
                        }
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        pendingCommands.forEach { pending ->
            if (pending.continuation.isActive) {
                pending.continuation.resume(false)
            }
        }
        pendingCommands.clear()
    }

    private suspend fun confirmCommand(request: ModuleCommandRequest): Boolean = suspendCancellableCoroutine { continuation ->
        runOnUiThread {
            val item = PendingCommand(request, continuation)
            pendingCommands.add(item)
            continuation.invokeOnCancellation {
                runOnUiThread {
                    pendingCommands.remove(item)
                }
            }
        }
    }

    private class LocalModuleWebViewClient(
        private val module: AdbModule,
        private val webNetworkAllowed: Boolean,
        private val assetLoader: WebViewAssetLoader
    ) : WebViewClient() {

        override fun shouldInterceptRequest(
            view: WebView,
            request: WebResourceRequest
        ): WebResourceResponse? {
            val url = request.url
            if (url.host == "appassets.androidplatform.net") {
                return assetLoader.shouldInterceptRequest(url)
            }
            if (!webNetworkAllowed) {
                // Hard network block for untrusted modules, including iframes and fetch().
                return WebResourceResponse(
                    "text/plain",
                    "utf-8",
                    403,
                    "Forbidden",
                    emptyMap(),
                    java.io.ByteArrayInputStream(ByteArray(0))
                )
            }
            return null
        }

        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val uri = request.url
            val scheme = uri.scheme?.lowercase()
            if (scheme == "https" && uri.host == "appassets.androidplatform.net") {
                return false
            }
            if (scheme == "http" || scheme == "https") {
                try {
                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, uri).apply {
                        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    view.context.startActivity(intent)
                } catch (e: Exception) {
                    // Ignore
                }
                return true
            }
            // file:, content:, intent:, javascript: and everything else stays inside.
            return true
        }
    }

    companion object {
        const val EXTRA_MODULE_ID = "module_id"
    }
}
