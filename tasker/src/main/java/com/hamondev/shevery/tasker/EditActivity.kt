package com.hamondev.shevery.tasker

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import org.json.JSONObject

class EditActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val isSetting = intent.action == PluginContract.ACTION_EDIT_SETTING
        val initialSelection = restoreSelection()

        setContent {
            MaterialTheme {
                var selectedCommand by remember { mutableStateOf(initialSelection) }

                AlertDialog(
                    onDismissRequest = { finish() },
                    title = {
                        Text(
                            text = stringResource(R.string.tasker_plugin_name),
                            style = MaterialTheme.typography.titleLarge
                        )
                    },
                    text = {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectableGroup(),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            if (isSetting) {
                                Command.entries.forEach { command ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .selectable(
                                                selected = command == selectedCommand,
                                                onClick = { selectedCommand = command },
                                                role = Role.RadioButton
                                            )
                                            .padding(vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        RadioButton(
                                            selected = command == selectedCommand,
                                            onClick = null
                                        )
                                        Spacer(Modifier.width(12.dp))
                                        Text(
                                            text = stringResource(command.labelRes),
                                            style = MaterialTheme.typography.bodyLarge
                                        )
                                    }
                                }
                            } else {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = true,
                                        onClick = null
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Text(
                                        text = stringResource(R.string.tasker_condition_running),
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                }
                            }
                        }
                    },
                    confirmButton = {
                        Button(onClick = { save(isSetting, selectedCommand) }) {
                            Text(stringResource(R.string.tasker_save))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { finish() }) {
                            Text(stringResource(R.string.tasker_cancel))
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = MaterialTheme.shapes.extraLarge
                )
            }
        }
    }

    private fun restoreSelection(): Command {
        val bundle = intent.getBundleExtra(PluginContract.EXTRA_BUNDLE) ?: return Command.START
        val json = bundle.getString(PluginContract.EXTRA_STRING_JSON) ?: return Command.START
        return runCatching {
            Command.from(JSONObject(json).optString(PluginContract.KEY_COMMAND))
        }.getOrNull() ?: Command.START
    }

    private fun save(isSetting: Boolean, command: Command) {
        val json = if (isSetting) {
            JSONObject().put(PluginContract.KEY_COMMAND, command.value).toString()
        } else {
            JSONObject().put(PluginContract.KEY_CONDITION, PluginContract.VALUE_CONDITION_RUNNING).toString()
        }
        val blurb = if (isSetting) {
            getString(command.blurbRes)
        } else {
            getString(R.string.tasker_blurb_condition)
        }
        val resultBundle = Bundle().apply {
            putString(PluginContract.EXTRA_STRING_JSON, json)
        }
        val resultIntent = Intent().apply {
            putExtra(PluginContract.EXTRA_BUNDLE, resultBundle)
            putExtra(PluginContract.EXTRA_STRING_BLURB, blurb)
        }
        setResult(RESULT_OK, resultIntent)
        finish()
    }
}
