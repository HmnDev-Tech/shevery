package com.hamondev.shevery.tasker

import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import java.util.concurrent.atomic.AtomicBoolean
import org.json.JSONObject
import rikka.shizuku.Shizuku

class PluginReceiver : BroadcastReceiver() {

    private val controlComponent = ComponentName(
        PluginContract.MANAGER_PACKAGE,
        PluginContract.MANAGER_CONTROL_RECEIVER
    )

    override fun onReceive(context: Context, intent: Intent) {
        if (!isConnectorEnabled(context)) {
            Log.w(TAG, "Shevery connectors are disabled in settings; ignoring ${intent.action}")
            if (intent.action != PluginContract.ACTION_QUERY_CONDITION) {
                postAlertNotification(
                    context = context,
                    notificationId = PluginContract.NOTIFICATION_ID_CONNECTOR,
                    title = context.getString(R.string.notification_connectors_disabled_title),
                    message = context.getString(R.string.notification_connectors_disabled_message)
                )
            }
            resultCode = if (intent.action == PluginContract.ACTION_QUERY_CONDITION) {
                PluginContract.RESULT_CONDITION_UNKNOWN
            } else {
                Activity.RESULT_CANCELED
            }
            return
        }

        if (isDirectAction(intent.action)) {
            val expectedToken = getAuthToken(context)
            val providedToken = intent.getStringExtra(PluginContract.EXTRA_AUTH)
            if (expectedToken.isNotEmpty() && providedToken != expectedToken) {
                Log.w(TAG, "Rejected intent ${intent.action}: invalid or missing auth token")
                val isMissing = providedToken.isNullOrEmpty()
                val titleRes = if (isMissing) {
                    R.string.notification_auth_missing_title
                } else {
                    R.string.notification_auth_invalid_title
                }
                val msgRes = if (isMissing) {
                    R.string.notification_auth_missing_message
                } else {
                    R.string.notification_auth_invalid_message
                }
                postAlertNotification(
                    context = context,
                    notificationId = PluginContract.NOTIFICATION_ID_AUTH,
                    title = context.getString(titleRes),
                    message = context.getString(msgRes)
                )
                resultCode = Activity.RESULT_CANCELED
                return
            }
        }

        when (intent.action) {
            PluginContract.ACTION_FIRE_SETTING -> handleFire(context, intent)
            PluginContract.ACTION_QUERY_CONDITION -> handleQuery(intent)
            PluginContract.ACTION_DIRECT_START -> handleDirect(context, Command.START)
            PluginContract.ACTION_DIRECT_STOP -> handleDirect(context, Command.STOP)
            PluginContract.ACTION_DIRECT_RESTART -> handleDirect(context, Command.RESTART)
            PluginContract.ACTION_DIRECT_TOGGLE -> handleDirect(context, Command.TOGGLE)
        }
    }

    private fun isDirectAction(action: String?): Boolean = when (action) {
        PluginContract.ACTION_DIRECT_START,
        PluginContract.ACTION_DIRECT_STOP,
        PluginContract.ACTION_DIRECT_RESTART,
        PluginContract.ACTION_DIRECT_TOGGLE -> true
        else -> false
    }

    private fun getAuthToken(context: Context): String {
        return runCatching {
            val storageContext = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                context.createDeviceProtectedStorageContext()
            } else {
                context
            }
            storageContext.getSharedPreferences("settings", Context.MODE_PRIVATE)
                .getString(PluginContract.KEY_AUTH_TOKEN, "") ?: ""
        }.getOrDefault("")
    }

    private fun isConnectorEnabled(context: Context): Boolean {
        return runCatching {
            val storageContext = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                context.createDeviceProtectedStorageContext()
            } else {
                context
            }
            storageContext.getSharedPreferences("settings", Context.MODE_PRIVATE)
                .getBoolean(PluginContract.KEY_CONNECTOR_ENABLED, false)
        }.getOrDefault(false)
    }

    private fun handleDirect(context: Context, command: Command) {
        executeCommand(context, command)
        resultCode = Activity.RESULT_OK
    }

    private fun handleFire(context: Context, intent: Intent) {
        if (!isExplicit(intent)) return
        val command = parseCommand(intent.getBundleExtra(PluginContract.EXTRA_BUNDLE)) ?: return
        executeCommand(context, command)
        resultCode = Activity.RESULT_OK
    }

    private fun executeCommand(context: Context, command: Command) {
        when (command) {
            Command.START -> {
                if (Shizuku.pingBinder()) {
                    Log.i(TAG, "Server already running; ignoring START command")
                } else {
                    sendControl(context, PluginContract.ACTION_START_SERVER)
                }
            }
            Command.STOP -> sendControl(context, PluginContract.ACTION_STOP_SERVER)
            Command.RESTART -> restartServer(context)
            Command.TOGGLE -> {
                if (isRestarting.get()) {
                    Log.i(TAG, "Server restart in progress; ignoring TOGGLE command")
                } else if (Shizuku.pingBinder()) {
                    sendControl(context, PluginContract.ACTION_STOP_SERVER)
                } else {
                    sendControl(context, PluginContract.ACTION_START_SERVER)
                }
            }
        }
    }

    private fun handleQuery(intent: Intent) {
        if (!isExplicit(intent)) return
        val bundle = intent.getBundleExtra(PluginContract.EXTRA_BUNDLE) ?: return
        val json = bundle.getString(PluginContract.EXTRA_STRING_JSON) ?: return
        val condition = runCatching {
            JSONObject(json).optString(PluginContract.KEY_CONDITION)
        }.getOrNull()
        if (condition != PluginContract.VALUE_CONDITION_RUNNING) return
        resultCode = if (Shizuku.pingBinder()) {
            PluginContract.RESULT_CONDITION_SATISFIED
        } else {
            PluginContract.RESULT_CONDITION_UNSATISFIED
        }
    }

    private fun restartServer(context: Context) {
        if (!Shizuku.pingBinder()) {
            sendControl(context, PluginContract.ACTION_START_SERVER)
            return
        }

        if (!isRestarting.compareAndSet(false, true)) {
            Log.i(TAG, "Server restart already in progress; ignoring duplicate RESTART command")
            return
        }

        val pendingResult = goAsync()
        val appContext = context.applicationContext
        val executed = AtomicBoolean(false)
        val handler = Handler(Looper.getMainLooper())

        lateinit var deadListener: Shizuku.OnBinderDeadListener

        val finishRestart = Runnable {
            if (executed.compareAndSet(false, true)) {
                try {
                    Shizuku.removeBinderDeadListener(deadListener)
                } catch (_: Throwable) {}
                handler.removeCallbacksAndMessages(null)
                try {
                    sendControl(appContext, PluginContract.ACTION_START_SERVER)
                } finally {
                    isRestarting.set(false)
                    pendingResult.finish()
                }
            }
        }

        deadListener = Shizuku.OnBinderDeadListener {
            finishRestart.run()
        }

        Shizuku.addBinderDeadListener(deadListener)
        sendControl(appContext, PluginContract.ACTION_STOP_SERVER)

        handler.postDelayed(finishRestart, RESTART_WAIT_MS)
    }

    private fun sendControl(context: Context, action: String) {
        val intent = Intent(action).apply {
            setPackage(PluginContract.MANAGER_PACKAGE)
            component = controlComponent
        }
        context.sendBroadcast(intent)
    }

    private fun isExplicit(intent: Intent): Boolean {
        val component = intent.component ?: return false
        return component.packageName == PluginContract.MANAGER_PACKAGE &&
            component.className == PluginReceiver::class.java.name
    }

    private fun parseCommand(bundle: Bundle?): Command? {
        val json = bundle?.getString(PluginContract.EXTRA_STRING_JSON) ?: return null
        return runCatching {
            Command.from(JSONObject(json).optString(PluginContract.KEY_COMMAND))
        }.getOrNull()
    }

    private fun postAlertNotification(
        context: Context,
        notificationId: Int,
        title: String,
        message: String
    ) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelName = context.getString(R.string.notification_channel_automation_alerts)
            val channel = NotificationChannel(
                PluginContract.CHANNEL_ID_ALERTS,
                channelName,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = channelName
            }
            nm.createNotificationChannel(channel)
        }

        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
            putExtra(PluginContract.EXTRA_TARGET_SECTION, PluginContract.TARGET_SECTION_AUTOMATION)
        }

        val pendingIntent = launchIntent?.let {
            PendingIntent.getActivity(
                context,
                notificationId,
                it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        val notification = NotificationCompat.Builder(context, PluginContract.CHANNEL_ID_ALERTS)
            .setSmallIcon(R.drawable.ic_notification_alert)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .apply {
                if (pendingIntent != null) {
                    setContentIntent(pendingIntent)
                }
            }
            .build()

        nm.notify(notificationId, notification)
    }

    companion object {
        private const val TAG = "SheveryPluginReceiver"
        private const val RESTART_WAIT_MS = 10_000L
        private val isRestarting = AtomicBoolean(false)
    }
}
