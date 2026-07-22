package moe.shizuku.manager.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import moe.shizuku.manager.MainActivity
import moe.shizuku.manager.R
import moe.shizuku.manager.receiver.SheveryControlReceiver
import rikka.shizuku.Shizuku

object SheveryNotificationManager {
    private const val CHANNEL_ID = "shevery_control"
    private const val NOTIFICATION_ID = 1002
    private var initialized = false

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        updateNotification(moe.shizuku.manager.application)
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        updateNotification(moe.shizuku.manager.application)
    }

    fun setup(context: Context) {
        if (initialized) return
        initialized = true

        createChannel(context)
        Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
        Shizuku.addBinderDeadListener(binderDeadListener)
        updateNotification(context)
    }

    private fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val name = context.getString(R.string.notification_control_channel_name)
            val channel = NotificationChannel(CHANNEL_ID, name, NotificationManager.IMPORTANCE_LOW).apply {
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun updateNotification(context: Context) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val isRunning = Shizuku.pingBinder()

        val title = if (isRunning) {
            context.getString(R.string.notification_control_title_running)
        } else {
            context.getString(R.string.notification_control_title_stopped)
        }

        val text = if (isRunning) {
            context.getString(R.string.notification_control_text_running)
        } else {
            context.getString(R.string.notification_control_text_stopped)
        }

        val mainIntent = Intent(context, MainActivity::class.java)
        val mainPendingIntent = PendingIntent.getActivity(
            context, 0, mainIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(if (isRunning) R.drawable.ic_server_ok_24dp else R.drawable.ic_server_error_24dp)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(mainPendingIntent)
            .setOngoing(true)
            .setShowWhen(false)

        if (isRunning) {
            val stopIntent = Intent(context, SheveryControlReceiver::class.java).apply {
                action = SheveryControlReceiver.ACTION_STOP_SERVER
            }
            val stopPendingIntent = PendingIntent.getBroadcast(
                context, 1, stopIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            builder.addAction(
                R.drawable.ic_outline_info_24,
                context.getString(R.string.notification_control_action_stop),
                stopPendingIntent
            )
        } else {
            val startIntent = Intent(context, SheveryControlReceiver::class.java).apply {
                action = SheveryControlReceiver.ACTION_START_SERVER
            }
            val startPendingIntent = PendingIntent.getBroadcast(
                context, 2, startIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            builder.addAction(
                R.drawable.ic_server_restart,
                context.getString(R.string.notification_control_action_start),
                startPendingIntent
            )
        }

        notificationManager.notify(NOTIFICATION_ID, builder.build())
    }
}
