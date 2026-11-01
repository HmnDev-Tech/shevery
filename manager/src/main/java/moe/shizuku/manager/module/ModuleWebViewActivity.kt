package moe.shizuku.manager.module

import android.annotation.SuppressLint
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

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val moduleId = intent.getStringExtra(EXTRA_MODULE_ID)
            ?: intent.data?.lastPathSegment.orEmpty()
        val modulesRoot = AdbModuleManager.modulesRoot(this)
        val module = AdbModuleManager.readModule(modulesRoot.resolve(moduleId))
        val index = module?.webRoot?.resolve("index.html")
        if (module == null || index?.isFile != true) {
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
                ModuleSettings.canExposeWebBridge(module) &&
                (module.declaresShellBridge || trusted) &&
                (!webNetworkAllowed || trusted)

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
                                    addJavascriptInterface(bridge, "Shevery")
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
