package moe.shizuku.manager.home

import android.content.pm.PackageManager
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import moe.shizuku.manager.BuildConfig
import moe.shizuku.manager.Manifest
import moe.shizuku.manager.model.ServiceStatus
import moe.shizuku.manager.utils.Logger.Companion.LOGGER
import moe.shizuku.manager.utils.ShizukuStateMachine
import moe.shizuku.manager.utils.ShizukuSystemApis
import rikka.lifecycle.Resource
import rikka.shizuku.Shizuku

class HomeViewModel : ViewModel() {

    val serverState: StateFlow<ShizukuStateMachine.State> = ShizukuStateMachine.asFlow()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = ShizukuStateMachine.get()
        )

    private val _serviceStatus = MutableLiveData<Resource<ServiceStatus>>()
    val serviceStatus = _serviceStatus as LiveData<Resource<ServiceStatus>>

    init {
        viewModelScope.launch {
            ShizukuStateMachine.asFlow().collect {
                reload()
            }
        }
    }

    private fun load(): ServiceStatus {
        if (!Shizuku.pingBinder()) {
            return ServiceStatus()
        }

        val uid = Shizuku.getUid()
        val apiVersion = Shizuku.getVersion()
        val patchVersion = Shizuku.getServerPatchVersion().let { if (it < 0) 0 else it }
        val seContext = if (apiVersion >= 6) {
            try {
                Shizuku.getSELinuxContext()
            } catch (tr: Throwable) {
                LOGGER.w(tr, "getSELinuxContext")
                null
            }
        } else null
        val permissionTest =
            Shizuku.checkRemotePermission("android.permission.GRANT_RUNTIME_PERMISSIONS") == PackageManager.PERMISSION_GRANTED

        ShizukuSystemApis.checkPermission(Manifest.permission.PRIVILEGED_API, BuildConfig.APPLICATION_ID, 0)
        return ServiceStatus(uid, apiVersion, patchVersion, seContext, permissionTest, readServerPid())
    }

    private fun readServerPid(): Int? = runCatching {
        val binder = Shizuku.getBinder() ?: return null
        val service = moe.shizuku.server.IShizukuService.Stub.asInterface(binder)
        // The Shizuku service directly spawns this short-lived child process.
        val remote = service.newProcess(arrayOf("sh", "-c", "printf '%s' \"\$PPID\""), null, null)
        try {
            if (!remote.waitForTimeout(1, java.util.concurrent.TimeUnit.SECONDS.name) || remote.exitValue() != 0) {
                return null
            }
            android.os.ParcelFileDescriptor.AutoCloseInputStream(remote.inputStream).bufferedReader().use {
                it.readText().toIntOrNull()?.takeIf { pid -> pid > 0 }
            }
        } finally {
            runCatching { remote.destroy() }
        }
    }.getOrNull()

    private var reloadJob: Job? = null

    fun reload() {
        reloadJob?.cancel()
        reloadJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val status = load()
                ensureActive()
                _serviceStatus.postValue(Resource.success(status))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                ensureActive()
                _serviceStatus.postValue(Resource.error(e, ServiceStatus()))
            }
        }
    }
}
