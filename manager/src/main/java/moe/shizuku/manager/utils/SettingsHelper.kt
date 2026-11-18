package moe.shizuku.manager.utils

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.service.quicksettings.TileService

object SettingsHelper {

    fun launchWirelessDebugging(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val tileIntent = Intent(TileService.ACTION_QS_TILE_PREFERENCES).apply {
                putExtra(
                    Intent.EXTRA_COMPONENT_NAME,
                    ComponentName(
                        "com.android.settings",
                        "com.android.settings.development.qstile.DevelopmentTiles\$WirelessDebugging"
                    )
                )
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_NO_HISTORY or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP
                )
            }
            try {
                context.startActivity(tileIntent)
                return
            } catch (_: ActivityNotFoundException) {
            } catch (_: SecurityException) {
            }
        }

        val devIntent = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_NO_HISTORY or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
            )
            putExtra(":settings:fragment_args_key", "toggle_adb_wireless")
        }
        try {
            context.startActivity(devIntent)
        } catch (_: ActivityNotFoundException) {
        }
    }
}
