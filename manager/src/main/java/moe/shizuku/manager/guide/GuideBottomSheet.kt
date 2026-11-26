@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class
)

package moe.shizuku.manager.guide

import android.view.HapticFeedbackConstants
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import moe.shizuku.manager.Helps
import moe.shizuku.manager.R
import moe.shizuku.manager.ui.compose.LocalReducedMotion
import moe.shizuku.manager.ui.compose.SectionHeader
import moe.shizuku.manager.ui.compose.ShizukuIcon
import moe.shizuku.manager.utils.CustomTabsHelper
import rikka.core.util.ClipboardUtils

@Composable
internal fun GuideBottomSheet(initialTopic: GuideTopic?, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
    )
    var selection by remember { mutableStateOf(initialTopic) }
    var selectedCategory by remember { mutableStateOf<GuideCategory?>(null) }
    val reducedMotion = LocalReducedMotion.current
    val motion = MaterialTheme.motionScheme

    fun closeSheet() {
        scope.launch {
            sheetState.hide()
            if (!sheetState.isVisible) onDismiss()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        AnimatedContent(
            targetState = selection,
            transitionSpec = {
                if (reducedMotion) {
                    fadeIn(snap()) togetherWith fadeOut(snap())
                } else {
                    fadeIn(motion.defaultEffectsSpec()) togetherWith fadeOut(motion.defaultEffectsSpec())
                }
            },
            label = "guide-sheet-content"
        ) { topic ->
            val sectionsToShow = remember(selectedCategory) {
                val categories = selectedCategory?.let { listOf(it) } ?: GuideCategory.entries
                categories.mapNotNull { category ->
                    val topics = GuideTopic.entries.filter { it.category == category }
                    if (topics.isEmpty()) null else category to topics
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item(key = "header") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (topic != null) {
                            IconButton(onClick = { selection = null }) {
                                ShizukuIcon(
                                    icon = R.drawable.ic_arrow_back_24,
                                    contentDescription = stringResource(R.string.guide_catalog)
                                )
                            }
                        }
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            if (topic != null) {
                                Text(
                                    text = stringResource(topic.category.label),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Text(
                                text = stringResource(topic?.title ?: R.string.guide_catalog),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.semantics { heading() }
                            )
                            if (topic == null) {
                                Text(
                                    text = stringResource(R.string.guide_count_label, GuideTopic.entries.size),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        IconButton(onClick = ::closeSheet) {
                            ShizukuIcon(
                                icon = R.drawable.ic_close_24,
                                contentDescription = stringResource(R.string.guide_close)
                            )
                        }
                    }
                }

                if (topic == null) {
                    item(key = "category-filters") {
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            item {
                                FilterChip(
                                    selected = selectedCategory == null,
                                    onClick = { selectedCategory = null },
                                    label = { Text(stringResource(R.string.guide_category_all)) },
                                    leadingIcon = if (selectedCategory == null) {
                                        {
                                            ShizukuIcon(
                                                icon = R.drawable.ic_symbol_check_24,
                                                modifier = Modifier.size(FilterChipDefaults.IconSize)
                                            )
                                        }
                                    } else null
                                )
                            }
                            items(GuideCategory.entries, key = { it.name }) { category ->
                                val selected = selectedCategory == category
                                FilterChip(
                                    selected = selected,
                                    onClick = {
                                        selectedCategory = if (selected) null else category
                                    },
                                    label = { Text(stringResource(category.label)) },
                                    leadingIcon = if (selected) {
                                        {
                                            ShizukuIcon(
                                                icon = R.drawable.ic_symbol_check_24,
                                                modifier = Modifier.size(FilterChipDefaults.IconSize)
                                            )
                                        }
                                    } else null
                                )
                            }
                        }
                    }

                    sectionsToShow.forEach { (category, topics) ->
                        item(key = "section-${category.name}") {
                            Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                                SectionHeader(stringResource(category.label))
                                topics.forEachIndexed { index, entry ->
                                    GuideCatalogContainer(
                                        index = index,
                                        count = topics.size,
                                        topic = entry,
                                        onClick = { selection = entry }
                                    )
                                }
                            }
                        }
                    }
                } else {
                    item(key = "detail-sections") {
                        val copySection: (String, String) -> Unit = { title, body ->
                            ClipboardUtils.put(context, "$title\n$body")
                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            Toast.makeText(context, R.string.guide_section_copied, Toast.LENGTH_SHORT).show()
                        }
                        val overviewTitle = stringResource(R.string.guide_overview)
                        val overviewBody = stringResource(topic.overview)
                        val stepsTitle = stringResource(R.string.guide_get_started)
                        val stepsBody = stringResource(topic.instructions)
                        val limitsTitle = stringResource(R.string.guide_limits)
                        val limitsBody = stringResource(topic.caution)

                        Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                            GuideDetailContainer(
                                index = 0,
                                count = 3,
                                icon = R.drawable.ic_outline_info_24,
                                badgeColor = MaterialTheme.colorScheme.primaryContainer,
                                badgeContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                title = overviewTitle,
                                body = overviewBody,
                                onClick = { copySection(overviewTitle, overviewBody) }
                            )
                            GuideDetailContainer(
                                index = 1,
                                count = 3,
                                icon = R.drawable.ic_outline_play_arrow_24,
                                badgeColor = MaterialTheme.colorScheme.secondaryContainer,
                                badgeContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                title = stepsTitle,
                                body = stepsBody,
                                onClick = { copySection(stepsTitle, stepsBody) }
                            )
                            GuideDetailContainer(
                                index = 2,
                                count = 3,
                                icon = R.drawable.ic_warning_24,
                                badgeColor = MaterialTheme.colorScheme.tertiaryContainer,
                                badgeContentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                                title = limitsTitle,
                                body = limitsBody,
                                onClick = { copySection(limitsTitle, limitsBody) }
                            )
                        }
                    }
                }

                val url = if (topic == null) Helps.SHEVERY_WIKI else topic.url
                if (url != null || topic != null) {
                    item(key = "footer-actions") {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (url != null) {
                                FilledTonalButton(
                                    modifier = Modifier.fillMaxWidth(),
                                    onClick = { CustomTabsHelper.launchUrlOrCopy(context, url) }
                                ) {
                                    ShizukuIcon(
                                        icon = R.drawable.ic_outline_open_in_new_24,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(stringResource(R.string.guide_read_docs))
                                }
                            }
                            if (topic != null) {
                                OutlinedButton(
                                    modifier = Modifier.fillMaxWidth(),
                                    onClick = { selection = null }
                                ) {
                                    Text(stringResource(R.string.guide_catalog))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExpressivePressableSegment(
    index: Int,
    count: Int,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    val reducedMotion = LocalReducedMotion.current
    val motion = MaterialTheme.motionScheme
    val cornerSpec = if (reducedMotion) snap() else motion.fastSpatialSpec()
    val colorSpec = if (reducedMotion) snap() else motion.fastEffectsSpec()

    val topRadius by animateDpAsState(
        targetValue = if (pressed) 28.dp else if (index == 0) 24.dp else 4.dp,
        animationSpec = cornerSpec,
        label = "guide-segment-top"
    )
    val bottomRadius by animateDpAsState(
        targetValue = if (pressed) 28.dp else if (index == count - 1) 24.dp else 4.dp,
        animationSpec = cornerSpec,
        label = "guide-segment-bottom"
    )
    val containerColor by animateColorAsState(
        targetValue = if (pressed) {
            MaterialTheme.colorScheme.surfaceContainerHighest
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        animationSpec = colorSpec,
        label = "guide-segment-color"
    )

    val shape = RoundedCornerShape(
        topStart = topRadius,
        topEnd = topRadius,
        bottomStart = bottomRadius,
        bottomEnd = bottomRadius
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .clickable(
                interactionSource = interactions,
                indication = LocalIndication.current,
                onClick = onClick
            ),
        shape = shape,
        color = containerColor
    ) {
        content()
    }
}

@Composable
private fun GuideCatalogContainer(
    index: Int,
    count: Int,
    topic: GuideTopic,
    onClick: () -> Unit
) {
    ExpressivePressableSegment(
        index = index,
        count = count,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(42.dp),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    ShizukuIcon(
                        icon = topic.icon,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = stringResource(topic.title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(topic.overview),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun GuideDetailContainer(
    index: Int,
    count: Int,
    @DrawableRes icon: Int,
    badgeColor: Color,
    badgeContentColor: Color,
    title: String,
    body: String,
    onClick: () -> Unit
) {
    ExpressivePressableSegment(
        index = index,
        count = count,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = RoundedCornerShape(12.dp),
                color = badgeColor
            ) {
                Box(contentAlignment = Alignment.Center) {
                    ShizukuIcon(
                        icon = icon,
                        tint = badgeContentColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
