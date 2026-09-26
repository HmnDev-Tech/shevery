@file:OptIn(
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class
)

package moe.shizuku.manager.ui.compose

import android.os.Build
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Spacer

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DeveloperMode
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LooksOne
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SettingsBackupRestore
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi

import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults

import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.ToggleButtonShapes
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.text.HtmlCompat
import moe.shizuku.manager.R
import moe.shizuku.manager.app.ThemeHelper

val LocalFloatingNavBarVisible = compositionLocalOf<MutableState<Boolean>> {
    mutableStateOf(true)
}

data class NavItem(
    val title: String,
    val icon: ImageVector
)

@Composable
fun ExpressiveFloatingNavigationBar(
    items: List<NavItem>,
    selectedIndex: Int,
    onItemSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val navBarState = LocalFloatingNavBarVisible.current
    val isKeyboardVisible = WindowInsets.ime.asPaddingValues().calculateBottomPadding() > 0.dp
    AnimatedVisibility(
        visible = !isKeyboardVisible && navBarState.value,
        modifier = modifier,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))
                .padding(bottom = 16.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
        val buttonBounds = remember { mutableStateMapOf<Int, Rect>() }
        val targetRect = buttonBounds[selectedIndex]
        val firstRect = buttonBounds[0]

        val pillRelativeX = (targetRect?.left ?: 0f) - (firstRect?.left ?: 0f)

        val pillAnimatedX by animateFloatAsState(
            targetValue = pillRelativeX,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioLowBouncy,
                stiffness = Spring.StiffnessLow
            ),
            label = "pillX"
        )
        val pillAnimatedWidth by animateFloatAsState(
            targetValue = targetRect?.width ?: 0f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioLowBouncy,
                stiffness = Spring.StiffnessLow
            ),
            label = "pillWidth"
        )

        val pillColor = MaterialTheme.colorScheme.primary

        HorizontalFloatingToolbar(
            expanded = true,
            colors = FloatingToolbarDefaults.vibrantFloatingToolbarColors(
                toolbarContainerColor = MaterialTheme.colorScheme.primaryContainer,
                toolbarContentColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        ) {
            CompositionLocalProvider(LocalRippleConfiguration provides null) {
                items.forEachIndexed { index, item ->
                    val selected = index == selectedIndex

                    ToggleButton(
                        checked = selected,
                        onCheckedChange = { if (!selected) onItemSelected(index) },
                        modifier = Modifier
                            .height(56.dp)
                            .semantics { this.selected = selected; role = Role.Tab }
                            .onGloballyPositioned { coords ->
                                buttonBounds[index] = coords.boundsInParent()
                            }
                            .then(
                                if (index == 0) {
                                    Modifier.drawWithContent {
                                        if (pillAnimatedWidth > 0f) {
                                            drawRoundRect(
                                                color = pillColor,
                                                topLeft = Offset(pillAnimatedX, 0f),
                                                size = Size(pillAnimatedWidth, size.height),
                                                cornerRadius = CornerRadius(size.height / 2f)
                                            )
                                        }
                                        drawContent()
                                    }
                                } else {
                                    Modifier
                                }
                            ),
                        colors = ToggleButtonDefaults.toggleButtonColors(
                            checkedContainerColor = Color.Transparent,
                            checkedContentColor = MaterialTheme.colorScheme.onPrimary,
                            containerColor = Color.Transparent,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        shapes = ToggleButtonShapes(
                            shape = CircleShape,
                            pressedShape = CircleShape,
                            checkedShape = CircleShape
                        )
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = item.title
                            )
                            AnimatedVisibility(
                                visible = selected,
                                enter = expandHorizontally(
                                    animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec()
                                ),
                                exit = shrinkHorizontally(
                                    animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec()
                                )
                            ) {
                                Text(
                                    text = item.title,
                                    modifier = Modifier.padding(start = ButtonDefaults.IconSpacing),
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontWeight = FontWeight.Bold
                                    )
                                )
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
fun RefreshedNavigationBar(
    items: List<NavItem>,
    selectedIndex: Int,
    onItemSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val navBarState = LocalFloatingNavBarVisible.current
    val isKeyboardVisible = WindowInsets.ime.asPaddingValues().calculateBottomPadding() > 0.dp
    AnimatedVisibility(
        visible = !isKeyboardVisible && navBarState.value,
        modifier = modifier,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        NavigationBar(
            modifier = Modifier.fillMaxWidth(),
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 3.dp,
            windowInsets = WindowInsets.navigationBars
        ) {
            items.forEachIndexed { index, item ->
                val selected = index == selectedIndex
                NavigationBarItem(
                    selected = selected,
                    onClick = { if (!selected) onItemSelected(index) },
                    icon = {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = item.title
                        )
                    },
                    label = {
                        Text(
                            text = item.title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }
        }
    }
}

@Composable
fun ShizukuExpressiveTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    // Same detector as the activity system bars (ThemeHelper.resolveAppDark):
    // one source of truth, so bars and content can never disagree after a switch.
    val dark = remember { ThemeHelper.resolveAppDark(context) }
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
    bottomBar: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit
) {
    if (onNavigateUp != null) {
        val navBarState = LocalFloatingNavBarVisible.current
        DisposableEffect(Unit) {
            navBarState.value = false
            onDispose {}
        }
    }

    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0.dp),
        bottomBar = bottomBar,
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
    contentPadding: PaddingValues = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 16.dp),
    bottomInset: Dp = 0.dp,
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(12.dp),
    isRefreshing: Boolean = false,
    onRefresh: (() -> Unit)? = null,
    listState: LazyListState = rememberLazyListState(),
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
        val navigationBarPadding = WindowInsets.navigationBars.asPaddingValues()
        val list = @Composable {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(
                    start = contentPadding.calculateStartPadding(LayoutDirection.Ltr),
                    top = contentPadding.calculateTopPadding(),
                    end = contentPadding.calculateEndPadding(LayoutDirection.Ltr),
                    bottom = contentPadding.calculateBottomPadding() +
                        navigationBarPadding.calculateBottomPadding() + bottomInset
                ),
                verticalArrangement = verticalArrangement,
                content = content
            )
        }
        if (onRefresh != null) {
            val pullToRefreshState = rememberPullToRefreshState()
            Box(modifier = Modifier.fillMaxSize()) {
                PullToRefreshBox(
                    modifier = Modifier.fillMaxSize(),
                    isRefreshing = isRefreshing,
                    onRefresh = onRefresh,
                    state = pullToRefreshState,
                    indicator = {}
                ) {
                    list()
                }
                PullToRefreshDefaults.LoadingIndicator(
                    state = pullToRefreshState,
                    isRefreshing = isRefreshing,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        // The expressive Scaffold lays content out edge-to-edge from the window
                        // top, so without the top bar padding the indicator used to hover over
                        // the toolbar/status bar. Keep it centered below the app bar (tab line).
                        .padding(top = innerPadding.calculateTopPadding() + 12.dp)
                )
            }
        } else {
            list()
        }
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
                shape = RoundedCornerShape(14.dp),
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
fun SettingsGroup(
    title: String,
    content: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (title.isNotBlank()) {
            Text(
                text = title,
                modifier = Modifier.padding(horizontal = 4.dp),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
        }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Column {
                content()
            }
        }
    }
}

@Composable
fun SectionHeader(title: String) {
    Text(
        text = title,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 4.dp),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold
    )
}

@Composable
fun SettingsRow(
    @DrawableRes icon: Int?,
    title: String,
    modifier: Modifier = Modifier,
    summary: String? = null,
    stateDescription: String? = null,
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
                .semantics(mergeDescendants = true) { stateDescription?.let { this.stateDescription = it } } // AFTER clickable, so the merged node wraps the click action too
                .alpha(if (enabled) 1f else  0.56f)
                .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        MaterialTheme.colorScheme.primaryContainer,
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                ShizukuIcon(
                    icon = icon,
                    contentDescription = null,
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
        stateDescription = if (checked) "On" else "Off",
        enabled = enabled,
        onClick = { if (enabled) onCheckedChange(!checked) },
        trailing = {
            Box(modifier = Modifier.clearAndSetSemantics {}) {
                ExpressiveSwitch(
                    checked = checked,
                    enabled = enabled,
                    onCheckedChange = null // Row's clickable handles the toggle; hide the switch's own node so it doesn't fight the merged row
                )
            }
        }
    )
}

@Composable
fun GroupDivider() {
    Spacer(
        modifier = Modifier
            .fillMaxWidth()
            .height(2.dp)
            .background(MaterialTheme.colorScheme.surface)
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
        R.drawable.ic_server_ok_24dp -> Icons.Rounded.CheckCircle
        R.drawable.ic_server_error_24dp -> Icons.Rounded.Cancel
        R.drawable.ic_wadb_24 -> Icons.Rounded.Wifi
        R.drawable.ic_numeric_1_circle_outline_24 -> Icons.Rounded.LooksOne
        R.drawable.ic_adb_24dp -> Icons.Rounded.DeveloperMode
        R.drawable.ic_learn_more_24dp -> Icons.Rounded.School
        R.drawable.ic_root_24dp -> Icons.Rounded.AdminPanelSettings
        R.drawable.ic_system_update_24 -> Icons.Rounded.SystemUpdate
        R.drawable.ic_settings_backup_restore_24dp -> Icons.Rounded.SettingsBackupRestore
        R.drawable.ic_backup_24dp -> Icons.Rounded.Backup
        else -> null
    }
}
