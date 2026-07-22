package moe.shizuku.manager.module

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.viewinterop.AndroidView
import moe.shizuku.manager.app.AppActivity
import moe.shizuku.manager.ui.compose.ShizukuExpressiveTheme
import moe.shizuku.manager.ui.compose.ShizukuScaffold
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
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

        val moduleId = intent.getStringExtra(EXTRA_MODULE_ID).orEmpty()
        val module = AdbModuleManager.readModule(AdbModuleManager.modulesRoot(this).resolve(moduleId))
        val index = module?.webRoot?.resolve("index.html")
        if (module == null || index?.isFile != true) {
            finish()
            return
        }

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
                                settings.allowFileAccess = true
                                settings.allowContentAccess = false
                                settings.allowFileAccessFromFileURLs = trusted
                                settings.allowUniversalAccessFromFileURLs = trusted
                                settings.blockNetworkLoads = !webNetworkAllowed
                                settings.cacheMode = WebSettings.LOAD_DEFAULT
                                settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                                CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
                                webViewClient = LocalModuleWebViewClient(module, webNetworkAllowed)
                                if (exposeBridge) {
                                    addJavascriptInterface(
                                        ModuleJsBridge(
                                            module,
                                            webView = this,
                                            commandReviewer = { request ->
                                                runBlocking {
                                                    confirmCommand(request)
                                                }
                                            }
                                        ),
                                        "Shizuku"
                                    )
                                }
                                loadUrl(index.toURI().toString())
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
        private val webNetworkAllowed: Boolean
    ) : WebViewClient() {

        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val uri = request.url
            val scheme = uri.scheme?.lowercase()
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
            if (scheme == "file") {
                return !isInsideWebRoot(uri.path.orEmpty())
            }
            return true
        }

        private fun isInsideWebRoot(path: String): Boolean {
            val root = module.webRoot ?: return false
            return runCatching {
                val rootPath = root.canonicalPath
                val targetPath = File(path).canonicalPath
                targetPath == rootPath || targetPath.startsWith(rootPath + File.separator)
            }.getOrDefault(false)
        }
    }

    companion object {
        const val EXTRA_MODULE_ID = "module_id"
    }
}
