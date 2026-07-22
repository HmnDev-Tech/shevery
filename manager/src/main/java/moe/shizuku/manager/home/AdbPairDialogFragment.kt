@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package moe.shizuku.manager.home

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build.VERSION_CODES
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import moe.shizuku.manager.R
import moe.shizuku.manager.ShizukuSettings
import moe.shizuku.manager.adb.AdbInvalidPairingCodeException
import moe.shizuku.manager.adb.AdbKey
import moe.shizuku.manager.adb.AdbKeyException
import moe.shizuku.manager.adb.AdbMdns
import moe.shizuku.manager.adb.AdbPairingClient
import moe.shizuku.manager.adb.PreferenceAdbKeyStore
import moe.shizuku.manager.ui.compose.ShizukuExpressiveTheme
import rikka.lifecycle.viewModels
import java.net.ConnectException

@RequiresApi(VERSION_CODES.R)
class AdbPairDialogFragment : DialogFragment() {

    private val viewModel by viewModels { ViewModel(requireContext()) }

    private var inPairingWindow by mutableStateOf(false)
    private var discoveredPort by mutableIntStateOf(-1)
    private var portText by mutableStateOf("")
    private var pairingCode by mutableStateOf("")
    private var portError by mutableStateOf<String?>(null)
    private var pairingCodeError by mutableStateOf<String?>(null)

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val context = requireContext()
        val content = ComposeView(context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                ShizukuExpressiveTheme {
                    PairDialogContent(
                        inPairingWindow = inPairingWindow,
                        discoveredPort = discoveredPort,
                        portText = portText,
                        onPortTextChange = {
                            portText = it.filter(Char::isDigit).take(5)
                            portError = null
                        },
                        portError = portError,
                        pairingCode = pairingCode,
                        onPairingCodeChange = {
                            pairingCode = it.filter(Char::isDigit).take(6)
                            pairingCodeError = null
                        },
                        pairingCodeError = pairingCodeError
                    )
                }
            }
        }

        val builder = MaterialAlertDialogBuilder(context).apply {
            setTitle(R.string.dialog_adb_pairing_title)
            setView(content)
            setNegativeButton(android.R.string.cancel, null)
            setPositiveButton(android.R.string.ok, null)
            setNeutralButton(R.string.development_settings, null)
        }
        val dialog = builder.create()
        dialog.setCanceledOnTouchOutside(false)
        dialog.setOnShowListener { onDialogShow(dialog) }
        return dialog
    }

    private fun onDialogShow(dialog: AlertDialog) {
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).visibility = android.view.View.GONE

        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
            val intent = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            intent.putExtra(":settings:fragment_args_key", "toggle_adb_wireless")
            try {
                it.context.startActivity(intent)
            } catch (_: ActivityNotFoundException) {
            }
        }

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val context = it.context
            val port = portText.toIntOrNull() ?: -1
            if (port > 65535 || port < 1) {
                portError = context.getString(R.string.dialog_adb_invalid_port)
                return@setOnClickListener
            }

            viewModel.run(port, pairingCode)
        }

        viewModel.port.observe(this) {
            discoveredPort = it
            if (it > 65535 || it < 1) {
                dialog.setTitle(R.string.dialog_adb_pairing_discovery)
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).visibility = android.view.View.GONE
                dialog.getButton(AlertDialog.BUTTON_NEUTRAL).visibility = android.view.View.VISIBLE
            } else {
                portText = it.toString()
                portError = null
                dialog.setTitle(R.string.dialog_adb_pairing_title)
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).visibility = android.view.View.VISIBLE
                dialog.getButton(AlertDialog.BUTTON_NEUTRAL).visibility = android.view.View.GONE
            }
        }
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        val context = requireContext()
        inPairingWindow = (requireActivity().isInMultiWindowMode
                || (requireActivity().window?.decorView?.display?.displayId ?: -1) > 0)

        if (inPairingWindow) {
            dialog?.setTitle(R.string.dialog_adb_pairing_discovery)
        } else {
            dialog?.setTitle(R.string.dialog_adb_pairing_title)
        }

        viewModel.result.observe(this) {
            if (it == null) {
                dismissAllowingStateLoss()
            } else {
                when (it) {
                    is ConnectException -> {
                        portError = context.getString(R.string.cannot_connect_port)
                    }
                    is AdbInvalidPairingCodeException -> {
                        pairingCodeError = context.getString(R.string.paring_code_is_wrong)
                    }
                    is AdbKeyException -> {
                        Toast.makeText(context, context.getString(R.string.adb_error_key_store), Toast.LENGTH_LONG)
                            .apply { setGravity(Gravity.CENTER, 0, 0) }.show()
                    }
                }
            }
        }
    }

    fun show(fragmentManager: FragmentManager) {
        if (fragmentManager.isStateSaved) return
        show(fragmentManager, javaClass.simpleName)
    }
}

@Composable
private fun PairDialogContent(
    inPairingWindow: Boolean,
    discoveredPort: Int,
    portText: String,
    onPortTextChange: (String) -> Unit,
    portError: String?,
    pairingCode: String,
    onPairingCodeChange: (String) -> Unit,
    pairingCodeError: String?
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (!inPairingWindow) {
            Text(
                text = stringResource(R.string.adb_pairing_requires_multi_window),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = stringResource(R.string.adb_pairing_requires_multi_window_reason),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@Column
        }

        if (discoveredPort !in 1..65535) {
            Text(
                text = stringResource(R.string.dialog_adb_pairing_message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            LoadingIndicator()
            return@Column
        }

        OutlinedTextField(
            value = pairingCode,
            onValueChange = onPairingCodeChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.dialog_adb_pairing_paring_code)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            isError = pairingCodeError != null,
            supportingText = pairingCodeError?.let { error ->
                { Text(error) }
            }
        )
        OutlinedTextField(
            value = portText,
            onValueChange = onPortTextChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.dialog_adb_port)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            isError = portError != null,
            supportingText = portError?.let { error ->
                { Text(error) }
            }
        )
    }
}

@SuppressLint("NewApi")
private class ViewModel(context: Context) : androidx.lifecycle.ViewModel() {

    private val _result = MutableLiveData<Throwable?>()
    val result = _result as LiveData<Throwable?>

    private val _port = MutableLiveData<Int>()
    val port = _port as LiveData<Int>

    private val adbMdns: AdbMdns = AdbMdns(context, AdbMdns.TLS_PAIRING) {
        _port.postValue(it)
    }

    init {
        adbMdns.start()
    }

    fun run(port: Int, password: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val host = "127.0.0.1"

            val key = try {
                AdbKey(PreferenceAdbKeyStore(ShizukuSettings.getPreferences()), "shizuku")
            } catch (e: Throwable) {
                e.printStackTrace()
                _result.postValue(AdbKeyException(e))
                return@launch
            }

            AdbPairingClient(host, port, password, key).runCatching {
                start()
            }.onFailure {
                _result.postValue(it)
                it.printStackTrace()
            }.onSuccess {
                if (it) {
                    _result.postValue(null)
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        adbMdns.stop()
    }
}
