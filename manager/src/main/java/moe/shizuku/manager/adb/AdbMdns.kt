package moe.shizuku.manager.adb

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.lifecycle.Observer
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket

@RequiresApi(Build.VERSION_CODES.R)
class AdbMdns(
    context: Context, private val serviceType: String,
    private val observer: Observer<Int>
) {

    @Volatile
    private var running = false
    @Volatile
    private var serviceName: String? = null
    private val discoveryGeneration = java.util.concurrent.atomic.AtomicInteger()
    private val listener = DiscoveryListener(this)
    private val nsdManager: NsdManager = context.getSystemService(NsdManager::class.java)
    private val knownServices = HashSet<String>()
    private val servicesLock = Any()

    fun start() {
        if (running) return
        discoveryGeneration.incrementAndGet()
        running = true
        try {
            nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, listener)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start service discovery", e)
            running = false
        }
    }

    fun stop() {
        if (!running) return
        discoveryGeneration.incrementAndGet()
        running = false
        serviceName = null
        synchronized(servicesLock) {
            knownServices.clear()
        }
        removeQueuedResolves(this)
        try {
            nsdManager.stopServiceDiscovery(listener)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to stop service discovery: ${e.message}")
        }
    }

    private fun onServiceFound(info: NsdServiceInfo) {
        synchronized(servicesLock) {
            if (!running || !knownServices.add(info.serviceName)) return
        }
        enqueueResolve(this, info)
    }

    private fun onServiceLost(info: NsdServiceInfo) {
        synchronized(servicesLock) {
            knownServices.remove(info.serviceName)
        }
        if (running && info.serviceName == serviceName) {
            serviceName = null
            observer.onChanged(-1)
        }
    }

    private fun onServiceResolved(generation: Int, resolvedService: NsdServiceInfo) {
        // Validate that the resolved host is on a local network interface
        // before attempting to connect. This prevents connecting to stale
        // or remote mDNS announcements that somehow pass discovery.
        val host = resolvedService.host
        if (running && generation == discoveryGeneration.get() && isPortInUse(resolvedService.port)) {
            if ((host == null || isLocalAddress(host)) && running && generation == discoveryGeneration.get()) {
                serviceName = resolvedService.serviceName
                observer.onChanged(resolvedService.port)
            }
        }
    }

    // Returns true if the given InetAddress belongs to a local network interface.
    // Prevents connecting to stale or remote mDNS announcements.
    // Compares raw address bytes (not InetAddress.equals) to handle IPv6
    // scope ID differences (e.g., fe80::1%wlan0 vs fe80::1%eth0).
    // Falls back to true (allow) if enumeration is unavailable or fails —
    // a false negative would silently drop a valid local mDNS service.
    private fun isLocalAddress(target: InetAddress): Boolean = try {
        if (target.isLoopbackAddress || target.isAnyLocalAddress) {
            true
        } else {
            val targetBytes = target.address
            val interfaces = NetworkInterface.getNetworkInterfaces()
            if (interfaces == null || !interfaces.hasMoreElements()) {
                true // No interfaces to check — allow the connection
            } else {
                val localAddresses = interfaces.asSequence()
                    .flatMap { it.inetAddresses.asSequence() }
                    .toList()
                if (localAddresses.isEmpty()) {
                    true
                } else {
                    localAddresses.any { it.address.contentEquals(targetBytes) }
                }
            }
        }
    } catch (e: Exception) {
        // If we can't enumerate interfaces, allow the connection —
        // false negative is worse than false positive here.
        Log.w(TAG, "Failed to enumerate network interfaces: ${e.message}")
        true
    }

    // Preserve the existing bind check while serializing service resolution.
    private fun isPortInUse(port: Int): Boolean = try {
        ServerSocket().use {
            it.bind(InetSocketAddress("127.0.0.1", port), 1)
            false
        }
    } catch (e: IOException) {
        true
    } catch (e: SecurityException) {
        Log.w(TAG, "SecurityException checking port ${port}: ${e.message}")
        true
    }

    internal class DiscoveryListener(private val adbMdns: AdbMdns) : NsdManager.DiscoveryListener {
        override fun onDiscoveryStarted(serviceType: String) {
            Log.v(TAG, "onDiscoveryStarted: $serviceType")
        }

        override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
            Log.v(TAG, "onStartDiscoveryFailed: $serviceType, $errorCode")
        }

        override fun onDiscoveryStopped(serviceType: String) {
            Log.v(TAG, "onDiscoveryStopped: $serviceType")
        }

        override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
            Log.v(TAG, "onStopDiscoveryFailed: $serviceType, $errorCode")
        }

        override fun onServiceFound(serviceInfo: NsdServiceInfo) {
            Log.v(TAG, "onServiceFound: ${serviceInfo.serviceName}")

            adbMdns.onServiceFound(serviceInfo)
        }

        override fun onServiceLost(serviceInfo: NsdServiceInfo) {
            Log.v(TAG, "onServiceLost: ${serviceInfo.serviceName}")

            adbMdns.onServiceLost(serviceInfo)
        }
    }

    private class ResolveListener(private val request: ResolveRequest) : NsdManager.ResolveListener {
        override fun onResolveFailed(nsdServiceInfo: NsdServiceInfo, i: Int) {
            resolveFailed(request, i)
        }

        override fun onServiceResolved(nsdServiceInfo: NsdServiceInfo) {
            try {
                if (isActiveResolve(request)) {
                    request.owner.onServiceResolved(request.generation, nsdServiceInfo)
                }
            } finally {
                resolveSucceeded(request)
            }
        }

    }

    private class ResolveRequest(val owner: AdbMdns, val info: NsdServiceInfo, val generation: Int) {
        var inFlight = false
        var retryDelayMs = INITIAL_RESOLVE_RETRY_MS
        var timeout: Runnable? = null
    }

    companion object {
        const val TLS_CONNECT = "_adb-tls-connect._tcp"
        const val TLS_PAIRING = "_adb-tls-pairing._tcp"
        const val TAG = "AdbMdns"

        private const val INITIAL_RESOLVE_RETRY_MS = 250L
        private const val MAX_RESOLVE_RETRY_MS = 5_000L
        private const val RESOLVE_TIMEOUT_MS = 10_000L
        private val resolveHandler = Handler(Looper.getMainLooper())
        private val resolveLock = Any()
        private val resolveQueue = AdbResolveQueue<ResolveRequest>()
        private var activeResolve: ResolveRequest? = null
        private var delayedDispatch: Runnable? = null

        private fun enqueueResolve(owner: AdbMdns, info: NsdServiceInfo) {
            synchronized(resolveLock) {
                resolveQueue.addLast(ResolveRequest(owner, info, owner.discoveryGeneration.get()))
            }
            dispatchNextResolve()
        }

        private fun removeQueuedResolves(owner: AdbMdns) {
            synchronized(resolveLock) {
                resolveQueue.removeAll { it.owner === owner }
            }
            dispatchNextResolve()
        }

        private fun dispatchNextResolve() {
            val request = synchronized(resolveLock) {
                if (activeResolve != null || delayedDispatch != null) return
                while (resolveQueue.isNotEmpty()) {
                    val next = resolveQueue.removeFirst()
                    if (next.owner.running && next.generation == next.owner.discoveryGeneration.get()) {
                        next.inFlight = true
                        activeResolve = next
                        return@synchronized next
                    }
                }
                null
            } ?: return
            startResolve(request)
        }

        private fun startResolve(request: ResolveRequest) {
            synchronized(resolveLock) {
                if (activeResolve !== request || !request.inFlight) return
                request.timeout = Runnable { resolveTimedOut(request) }
                    .also { resolveHandler.postDelayed(it, RESOLVE_TIMEOUT_MS) }
            }
            try {
                request.owner.nsdManager.resolveService(request.info, ResolveListener(request))
            } catch (e: Exception) {
                Log.w(TAG, "resolveService failed for ${request.info.serviceName}", e)
                finishResolve(request)
            }
        }

        private fun resolveFailed(request: ResolveRequest, errorCode: Int) {
            var retryDelay: Long? = null
            synchronized(resolveLock) {
                if (activeResolve !== request) return
                request.inFlight = false
                request.timeout?.let(resolveHandler::removeCallbacks)
                request.timeout = null
                if (errorCode == NsdManager.FAILURE_ALREADY_ACTIVE && request.owner.running &&
                    request.generation == request.owner.discoveryGeneration.get()) {
                    val delayMs = request.retryDelayMs
                    retryDelay = delayMs
                    request.retryDelayMs = (delayMs * 2).coerceAtMost(MAX_RESOLVE_RETRY_MS)
                    activeResolve = null
                    // FAILURE_ALREADY_ACTIVE is resolver-wide. Retrying other
                    // announcements just creates more busy callbacks and can
                    // push this device's own ADB service out of the boot window.
                    resolveQueue.retryFirst(request)
                    delayedDispatch = Runnable {
                        synchronized(resolveLock) {
                            delayedDispatch = null
                        }
                        dispatchNextResolve()
                    }.also { resolveHandler.postDelayed(it, delayMs) }
                } else {
                    activeResolve = null
                }
            }
            if (retryDelay != null) {
                Log.w(TAG, "resolve busy for ${request.info.serviceName}; queue resumes in ${retryDelay}ms")
            } else {
                Log.w(TAG, "onResolveFailed: ${request.info.serviceName}, code=$errorCode")
                dispatchNextResolve()
            }
        }

        private fun resolveSucceeded(request: ResolveRequest) {
            finishResolve(request)
        }

        private fun isActiveResolve(request: ResolveRequest): Boolean = synchronized(resolveLock) {
            activeResolve === request && request.inFlight
        }

        private fun finishResolve(request: ResolveRequest) {
            synchronized(resolveLock) {
                if (activeResolve !== request) return
                request.timeout?.let(resolveHandler::removeCallbacks)
                activeResolve = null
            }
            dispatchNextResolve()
        }

        private fun resolveTimedOut(request: ResolveRequest) {
            synchronized(resolveLock) {
                if (activeResolve !== request || !request.inFlight) return
                request.inFlight = false
                request.timeout = null
                activeResolve = null
            }
            Log.w(TAG, "resolve timed out for ${request.info.serviceName}")
            dispatchNextResolve()
        }
    }
}
