@file:OptIn(
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class
)

package moe.shizuku.manager.ui.compose

import android.os.Build
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.HelpOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.text.HtmlCompat
import moe.shizuku.manager.R
import moe.shizuku.manager.app.ThemeHelper

data class ExpressiveButtonSpec(
    @param:StringRes val label: Int,
    @param:DrawableRes val icon: Int,
    val primary: Boolean = false,
    val enabled: Boolean = true,
    val onClick: () -> Unit
)

@Composable
fun ShizukuExpressiveTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()
    val baseScheme = when {
        ThemeHelper.isUsingSystemColor() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && dark ->
            dynamicDarkColorScheme(context)
        ThemeHelper.isUsingSystemColor() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            dynamicLightColorScheme(context)
        dark -> darkColorScheme(
            primary = Color(0xFFB1B8DF),
            secondary = Color(0xFFB9C7E8),
            tertiary = Color(0xFFE2B8C8)
        )
        else -> lightColorScheme(
            primary = Color(0xFF3F51B5),
            secondary = Color(0xFF52669B),
            tertiary = Color(0xFF8C4A62)
        )
    }
    val colorScheme = if (dark && ThemeHelper.isBlackNightTheme(context)) {
        baseScheme.copy(
            background = Color.Black,
            surface = Color.Black,
            surfaceContainer = Color(0xFF0B0B0B)
        )
    } else {
        baseScheme
    }

    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        motionScheme = MotionScheme.expressive(),
        content = content
    )
}

@Composable
fun ShizukuScaffold(
    title: String,
    modifier: Modifier = Modifier,
    onNavigateUp: (() -> Unit)? = null,
    navigationIcon: Int = R.drawable.ic_arrow_back_24,
    @StringRes navigationContentDescription: Int = R.string.accessibility_navigate_up,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable (PaddingValues) -> Unit
) {
    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    if (onNavigateUp != null) {
                        IconButton(onClick = onNavigateUp) {
                            ShizukuIcon(
                                navigationIcon,
                                contentDescription = stringResource(navigationContentDescription)
                            )
                        }
                    }
                },
                actions = actions,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )
        },
        content = content
    )
}

@Composable
fun ShizukuLazyScaffold(
    title: String,
    modifier: Modifier = Modifier,
    onNavigateUp: (() -> Unit)? = null,
    navigationIcon: Int = R.drawable.ic_arrow_back_24,
    @StringRes navigationContentDescription: Int = R.string.accessibility_navigate_up,
    actions: @Composable RowScope.() -> Unit = {},
    contentPadding: PaddingValues = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 16.dp),
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(10.dp),
    content: LazyListScope.() -> Unit
) {
    ShizukuScaffold(
        title = title,
        modifier = modifier,
        onNavigateUp = onNavigateUp,
        navigationIcon = navigationIcon,
        navigationContentDescription = navigationContentDescription,
        actions = actions
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .windowInsetsPadding(WindowInsets.navigationBars),
            contentPadding = contentPadding,
            verticalArrangement = verticalArrangement,
            content = content
        )
    }
}

@Composable
fun ExpressiveCard(
    @DrawableRes icon: Int,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    danger: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit = {}
) {
    val clickableModifier = if (onClick != null) {
        Modifier.clickable(enabled = enabled, onClick = onClick)
    } else {
        Modifier
    }
    val container = if (danger) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainer
    val onContainer = if (danger) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface
    val iconContainer = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primaryContainer
    val onIconContainer = if (danger) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onPrimaryContainer

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .then(clickableModifier)
            .alpha(if (enabled) 1f else 0.56f),
        shape = MaterialTheme.shapes.extraLarge,
        color = container,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Surface(
                modifier = Modifier.size(44.dp),
                shape = CircleShape,
                color = iconContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    ShizukuIcon(
                        icon = icon,
                        contentDescription = null,
                        tint = onIconContainer,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = onContainer
                )
                if (body.isNotBlank()) {
                    Text(
                        text = body,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (danger) onContainer else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                content()
            }
        }
    }
}

@Composable
fun ExpressiveButtons(buttons: List<ExpressiveButtonSpec>) {
    if (buttons.isEmpty()) return

    Spacer(Modifier.height(8.dp))
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        buttons.forEach { button ->
            if (button.primary) {
                Button(
                    enabled = button.enabled,
                    onClick = button.onClick,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    ButtonIcon(button.icon)
                    Text(stringResource(button.label))
                }
            } else if (button.enabled) {
                FilledTonalButton(
                    onClick = button.onClick,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    ButtonIcon(button.icon)
                    Text(stringResource(button.label))
                }
            } else {
                OutlinedButton(
                    enabled = false,
                    onClick = button.onClick,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    ButtonIcon(button.icon)
                    Text(stringResource(button.label))
                }
            }
        }
    }
}

@Composable
fun SettingsGroup(
    title: String,
    content: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            modifier = Modifier.padding(horizontal = 4.dp),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 1.dp
        ) {
            Column {
                content()
            }
        }
    }
}

@Composable
fun SettingsRow(
    @DrawableRes icon: Int?,
    title: String,
    modifier: Modifier = Modifier,
    summary: String? = null,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null
) {
    val clickableModifier = if (onClick != null) {
        Modifier.clickable(enabled = enabled, onClick = onClick)
    } else {
        Modifier
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(clickableModifier)
            .alpha(if (enabled) 1f else 0.56f)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            ShizukuIcon(
                icon = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium
            )
            if (!summary.isNullOrBlank()) {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        trailing?.invoke()
    }
}

@Composable
fun SwitchSettingsRow(
    @DrawableRes icon: Int?,
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    enabled: Boolean = true
) {
    SettingsRow(
        icon = icon,
        title = title,
        modifier = modifier,
        summary = summary,
        enabled = enabled,
        onClick = { if (enabled) onCheckedChange(!checked) },
        trailing = {
            ExpressiveSwitch(
                checked = checked,
                enabled = enabled,
                onCheckedChange = onCheckedChange
            )
        }
    )
}

@Composable
fun GroupDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 56.dp),
        color = MaterialTheme.colorScheme.outlineVariant
    )
}

@Composable
fun StepRow(
    number: Int,
    title: String,
    body: String? = null,
    action: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Surface(
            modifier = Modifier.size(34.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = number.toString(),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (!body.isNullOrBlank()) {
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            action?.invoke()
        }
    }
}

@Composable
fun MonospaceLog(text: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHighest
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
fun HtmlText(@StringRes id: Int, vararg formatArgs: Any): String {
    val raw = stringResource(id, *formatArgs)
    return remember(raw) { htmlToPlainText(raw) }
}

fun htmlToPlainText(value: String): String {
    return HtmlCompat.fromHtml(value, HtmlCompat.FROM_HTML_MODE_LEGACY).toString().trim()
}

@Composable
private fun ButtonIcon(@DrawableRes icon: Int) {
    ShizukuIcon(
        icon = icon,
        contentDescription = null,
        modifier = Modifier
            .padding(end = 8.dp)
            .size(18.dp)
    )
}

@Composable
fun ExpressiveSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        enabled = enabled,
        thumbContent = if (checked) {
            {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    modifier = Modifier.size(SwitchDefaults.IconSize)
                )
            }
        } else {
            null
        }
    )
}

@Composable
fun ShizukuIcon(
    @DrawableRes icon: Int,
    contentDescription: String? = null,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current
) {
    val imageVector = roundedIconFor(icon)
    if (imageVector != null) {
        Icon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            modifier = modifier,
            tint = tint
        )
    } else {
        Icon(
            painter = painterResource(icon),
            contentDescription = contentDescription,
            modifier = modifier,
            tint = tint
        )
    }
}

private fun roundedIconFor(@DrawableRes icon: Int): ImageVector? {
    return when (icon) {
        R.drawable.ic_arrow_back_24 -> Icons.AutoMirrored.Rounded.ArrowBack
        R.drawable.ic_action_settings_24dp,
        R.drawable.ic_settings_outline_24dp -> Icons.Rounded.Settings
        R.drawable.ic_server_restart -> Icons.Rounded.Refresh
        R.drawable.ic_more_vert_24 -> Icons.Rounded.MoreVert
        R.drawable.ic_close_24 -> Icons.Rounded.Close
        R.drawable.ic_outline_info_24,
        R.drawable.ic_action_about_24dp -> Icons.Rounded.Info
        R.drawable.ic_system_icon -> Icons.Rounded.Apps
        R.drawable.ic_warning_24 -> Icons.Rounded.Warning
        R.drawable.ic_help_outline_24dp -> Icons.AutoMirrored.Rounded.HelpOutline
        R.drawable.ic_outline_translate_24 -> Icons.Rounded.Translate
        R.drawable.ic_baseline_link_24 -> Icons.Rounded.Link
        R.drawable.ic_outline_dark_mode_24 -> Icons.Rounded.DarkMode
        R.drawable.ic_outline_light_mode_24 -> Icons.Rounded.LightMode
        R.drawable.ic_outline_notifications_active_24 -> Icons.Rounded.NotificationsActive
        R.drawable.ic_outline_open_in_new_24 -> Icons.AutoMirrored.Rounded.OpenInNew
        R.drawable.ic_outline_play_arrow_24,
        R.drawable.ic_server_start_24dp -> Icons.Rounded.PlayArrow
        R.drawable.ic_content_copy_24 -> Icons.Rounded.ContentCopy
        R.drawable.ic_terminal_24 -> Icons.Rounded.Terminal
        R.drawable.ic_code_24dp -> Icons.Rounded.Code
        else -> null
    }
}
