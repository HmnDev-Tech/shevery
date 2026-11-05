package moe.shizuku.manager.module

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import moe.shizuku.manager.R

enum class ModuleTab {
    INSTALLED,
    CATALOG
}

@Composable
fun ModuleTabsSwitcher(
    selectedTab: ModuleTab,
    onSelectInstalled: () -> Unit,
    onSelectCatalog: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.height(36.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ModuleTabSegment(
                selected = selectedTab == ModuleTab.INSTALLED,
                icon = Icons.Rounded.CheckCircle,
                contentDescription = stringResource(R.string.modules_tab_installed),
                onClick = onSelectInstalled,
                enabled = selectedTab != ModuleTab.INSTALLED
            )
            ModuleTabSegment(
                selected = selectedTab == ModuleTab.CATALOG,
                icon = Icons.Rounded.Apps,
                contentDescription = stringResource(R.string.modules_tab_catalog),
                onClick = onSelectCatalog,
                enabled = selectedTab != ModuleTab.CATALOG
            )
        }
    }
}

@Composable
private fun ModuleTabSegment(
    selected: Boolean,
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
        contentColor = if (selected) {
            MaterialTheme.colorScheme.onSecondaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        modifier = Modifier.height(36.dp),
        onClick = onClick,
        enabled = enabled
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 14.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
