package moe.shizuku.manager.guide

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import moe.shizuku.manager.Helps
import moe.shizuku.manager.R
import moe.shizuku.manager.ui.compose.ExpressiveCard
import moe.shizuku.manager.ui.compose.GroupDivider
import moe.shizuku.manager.ui.compose.SettingsGroup
import moe.shizuku.manager.ui.compose.SettingsRow
import moe.shizuku.manager.utils.CustomTabsHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GuideBottomSheet(initialTopic: GuideTopic?, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
    )
    var selection by remember { mutableStateOf(initialTopic) }
    val topic = selection

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        key(topic) {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(topic?.title ?: R.string.guide_catalog),
                            style = MaterialTheme.typography.headlineSmall,
                            modifier = Modifier.weight(1f).semantics { heading() }
                        )
                        TextButton(onClick = {
                            scope.launch {
                                sheetState.hide()
                                if (!sheetState.isVisible) onDismiss()
                            }
                        }) {
                            Text(stringResource(R.string.guide_close))
                        }
                    }
                }
                if (topic == null) {
                    item {
                        SettingsGroup(title = "") {
                            GuideTopic.entries.forEachIndexed { index, entry ->
                                if (index > 0) GroupDivider()
                                SettingsRow(
                                    icon = null,
                                    title = stringResource(entry.title),
                                    onClick = { selection = entry }
                                )
                            }
                        }
                    }
                } else {
                    item {
                        ExpressiveCard(
                            icon = R.drawable.ic_outline_info_24,
                            title = stringResource(R.string.guide_overview),
                            body = stringResource(topic.overview)
                        )
                    }
                    item {
                        ExpressiveCard(
                            icon = R.drawable.ic_outline_play_arrow_24,
                            title = stringResource(R.string.guide_get_started),
                            body = stringResource(topic.instructions)
                        )
                    }
                    item {
                        ExpressiveCard(
                            icon = R.drawable.ic_help_outline_24dp,
                            title = stringResource(R.string.guide_limits),
                            body = stringResource(topic.caution)
                        )
                    }
                }
                val url = if (topic == null) Helps.SHEVERY_WIKI else topic.url
                if (url != null) {
                    item {
                        FilledTonalButton(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { CustomTabsHelper.launchUrlOrCopy(context, url) }
                        ) {
                            Text(stringResource(R.string.guide_read_docs))
                        }
                    }
                }
                if (topic != null) {
                    item {
                        TextButton(modifier = Modifier.fillMaxWidth(), onClick = { selection = null }) {
                            Text(stringResource(R.string.guide_catalog))
                        }
                    }
                }
            }
        }
    }
}
