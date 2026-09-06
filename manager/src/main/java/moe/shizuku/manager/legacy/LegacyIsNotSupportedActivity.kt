package moe.shizuku.manager.legacy

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import moe.shizuku.manager.MainActivity
import moe.shizuku.manager.R
import moe.shizuku.manager.app.AppActivity
import moe.shizuku.manager.ui.compose.ShizukuExpressiveTheme
import moe.shizuku.manager.ui.compose.htmlToPlainText

class LegacyIsNotSupportedActivity : AppActivity() {

    companion object {

        /**
         * Activity result: user denied request (only API pre-23).
         */
        private inline val RESULT_CANCELED get() = Activity.RESULT_CANCELED

        /**
         * Activity result: error, such as manager app itself not authorized.
         */
        private const val RESULT_ERROR = 1
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val callingComponent = callingActivity
        if (callingComponent == null) {
            setResult(RESULT_CANCELED)
            finish()
            return
        }

        val ai = try {
            packageManager.getApplicationInfo(callingComponent.packageName, PackageManager.GET_META_DATA)
        } catch (e: Throwable) {
            finish()
            return
        }

        val label = try {
            ai.loadLabel(packageManager)
        } catch (e: Exception) {
            ai.packageName
        }

        val v3Support = ai.metaData?.getBoolean("moe.shizuku.client.V3_SUPPORT") == true

        setContent {
            ShizukuExpressiveTheme {
                AlertDialog(
                    onDismissRequest = {
                        setResult(RESULT_ERROR)
                        finish()
                    },
                    icon = {
                        Icon(
                            painter = painterResource(R.drawable.ic_system_icon),
                            contentDescription = null
                        )
                    },
                    title = {
                        Text(
                            text = if (v3Support) {
                                stringResource(R.string.dialog_requesting_legacy_title, label)
                            } else {
                                stringResource(R.string.dialog_legacy_not_support_title, label)
                            }
                        )
                    },
                    text = {
                        Text(
                            text = htmlToPlainText(
                                if (v3Support) {
                                    getString(R.string.dialog_requesting_legacy_message, label)
                                } else {
                                    getString(R.string.dialog_legacy_not_support_message, label)
                                }
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    confirmButton = {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (v3Support) {
                                OutlinedButton(
                                    onClick = {
                                        startActivity(
                                            Intent(this@LegacyIsNotSupportedActivity, MainActivity::class.java)
                                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        )
                                        setResult(RESULT_ERROR)
                                        finish()
                                    }
                                ) {
                                    Text(stringResource(R.string.dialog_requesting_legacy_button_open_shizuku))
                                }
                            }
                            Button(
                                onClick = {
                                    setResult(RESULT_ERROR)
                                    finish()
                                }
                            ) {
                                Text(stringResource(android.R.string.ok))
                            }
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = MaterialTheme.shapes.extraLarge
                )
            }
        }
    }
}
