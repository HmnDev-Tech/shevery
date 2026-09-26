@file:OptIn(ExperimentalMaterial3Api::class)

package moe.shizuku.manager.settings

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.shizuku.manager.R
import moe.shizuku.manager.deviceowner.DeviceOwnerManager
import moe.shizuku.manager.security.AuthManager
import moe.shizuku.manager.security.SecuritySettings
import moe.shizuku.manager.ui.compose.LocalFloatingNavBarVisible

@Composable
fun DeviceOwnerDelegationScreen(
    onNavigateUp: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val navBarState = LocalFloatingNavBarVisible.current
    val scope = rememberCoroutineScope()

    DisposableEffect(Unit) {
        navBarState.value = false
        val watcher = scope.launch {
            snapshotFlow { navBarState.value }.collect { visible ->
                if (visible) navBarState.value = false
            }
        }
        onDispose {
            watcher.cancel()
        }
    }

    var loading by remember { mutableStateOf(true) }
    var apps by remember { mutableStateOf<List<DeviceOwnerManager.DelegatedAppInfo>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var configuringApp by remember { mutableStateOf<DeviceOwnerManager.DelegatedAppInfo?>(null) }
    var dhizukuOnly by remember { mutableStateOf(true) }
    var whitelistMode by remember { mutableStateOf(DeviceOwnerManager.isDeviceOwnerWhitelistEnabled()) }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    var refreshTick by remember { mutableIntStateOf(0) }

    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                refreshTick++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(dhizukuOnly, refreshTick) {
        if (apps.isEmpty()) loading = true
        val loaded = withContext(Dispatchers.IO) {
            DeviceOwnerManager.getDelegationApps(context, dhizukuOnly = dhizukuOnly)
        }
        apps = loaded
        loading = false
    }

    val filteredApps = remember(apps, searchQuery) {
        if (searchQuery.isBlank()) {
            apps
        } else {
            val q = searchQuery.trim().lowercase()
            apps.filter {
                it.label.lowercase().contains(q) || it.packageName.lowercase().contains(q)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.device_owner_delegation_title),
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(android.R.string.cancel)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        if (loading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 140.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Strict Whitelist Card
                item {
                    Spacer(modifier = Modifier.height(4.dp))
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.whitelist_mode_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = stringResource(R.string.device_owner_whitelist_summary),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Switch(
                                checked = whitelistMode,
                                onCheckedChange = { checked ->
                                    whitelistMode = checked
                                    DeviceOwnerManager.setDeviceOwnerWhitelistEnabled(checked)
                                }
                            )
                        }
                    }
                }

                item {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text(stringResource(R.string.device_owner_transfer_search_hint)) },
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = null)
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = null)
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        )
                    )
                }

                // Filter chips
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = dhizukuOnly,
                            onClick = { dhizukuOnly = true },
                            label = { Text(stringResource(R.string.device_owner_delegation_filter_dhizuku)) }
                        )
                        FilterChip(
                            selected = !dhizukuOnly,
                            onClick = { dhizukuOnly = false },
                            label = { Text(stringResource(R.string.device_owner_delegation_all_apps)) }
                        )
                    }
                }

                if (filteredApps.isEmpty()) {
                    item {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = if (dhizukuOnly)
                                        stringResource(R.string.device_owner_delegation_no_dhizuku_apps)
                                    else
                                        stringResource(R.string.device_owner_delegation_empty),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (dhizukuOnly) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    OutlinedButton(onClick = { dhizukuOnly = false }) {
                                        Text(stringResource(R.string.device_owner_delegation_all_apps))
                                    }
                                }
                            }
                        }
                    }
                } else {
                    items(filteredApps, key = { it.packageName }) { app ->
                        val isDelegated = app.scopes.isNotEmpty() || app.isDhizukuGranted
                        val isOneTime = app.isOneTime && isDelegated
                        val iconBitmap = remember(app) {
                            try {
                                app.icon?.toBitmap(44, 44)?.asImageBitmap()
                            } catch (_: Throwable) {
                                null
                            }
                        }

                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isDelegated)
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                                else
                                    MaterialTheme.colorScheme.surfaceContainerLow
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (iconBitmap != null) {
                                    Image(
                                        bitmap = iconBitmap,
                                        contentDescription = app.label,
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .background(
                                                color = MaterialTheme.colorScheme.surfaceVariant,
                                                shape = RoundedCornerShape(10.dp)
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = app.label.take(1).uppercase(),
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { configuringApp = app }
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = app.label,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false)
                                        )
                                        if (isOneTime) {
                                            Box(
                                                modifier = Modifier
                                                    .size(24.dp)
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(MaterialTheme.colorScheme.tertiaryContainer)
                                                    .clickable {
                                                        val newScopes = if (app.scopes.isEmpty()) {
                                                            DeviceOwnerManager.ALL_SCOPES.map { it.scopeName }
                                                        } else {
                                                            app.scopes
                                                        }
                                                        DeviceOwnerManager.setDelegatedScopes(context, app.packageName, newScopes)
                                                        moe.shizuku.manager.dhizuku.DhizukuAuthManager.grant(context, app.uid, onetime = false)
                                                        apps = apps.map {
                                                            if (it.packageName == app.packageName) it.copy(
                                                                scopes = newScopes,
                                                                isDhizukuGranted = true,
                                                                isOneTime = false
                                                            )
                                                            else it
                                                        }
                                                        Toast.makeText(
                                                            context,
                                                            R.string.app_management_made_permanent,
                                                            Toast.LENGTH_SHORT
                                                        ).show()
                                                    },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    painter = painterResource(R.drawable.ic_schedule_24dp),
                                                    contentDescription = stringResource(R.string.app_management_onetime_badge),
                                                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                                    modifier = Modifier.size(15.dp)
                                                )
                                            }
                                        }
                                    }
                                    Text(
                                        text = app.packageName,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = when {
                                            isOneTime -> stringResource(R.string.app_management_onetime_badge)
                                            app.scopes.isNotEmpty() -> "${app.scopes.size} ${stringResource(R.string.device_owner_delegation_scopes_count)}"
                                            app.isDhizukuGranted -> stringResource(R.string.device_owner_delegation_updated)
                                            else -> stringResource(R.string.device_owner_delegation_none)
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isDelegated)
                                            MaterialTheme.colorScheme.primary
                                        else
                                            MaterialTheme.colorScheme.outline
                                    )
                                }

                                // Quick toggle checkbox
                                Checkbox(
                                    checked = isDelegated,
                                    onCheckedChange = { checked ->
                                        fun applyQuickToggle() {
                                            val newScopes = if (checked) {
                                                DeviceOwnerManager.ALL_SCOPES.map { it.scopeName }
                                            } else {
                                                emptyList()
                                            }
                                            val ok = DeviceOwnerManager.setDelegatedScopes(
                                                context,
                                                app.packageName,
                                                newScopes
                                            )
                                            if (ok) {
                                                if (checked) {
                                                    moe.shizuku.manager.dhizuku.DhizukuAuthManager.grant(context, app.uid, onetime = false)
                                                } else {
                                                    moe.shizuku.manager.dhizuku.DhizukuAuthManager.revoke(context, app.uid)
                                                }
                                                apps = apps.map {
                                                    if (it.packageName == app.packageName) it.copy(
                                                        scopes = newScopes,
                                                        isDhizukuGranted = checked,
                                                        isOneTime = false
                                                    )
                                                    else it
                                                }
                                                Toast.makeText(
                                                    context,
                                                    if (checked) R.string.device_owner_delegation_updated else R.string.device_owner_delegation_revoked,
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        }

                                        if (activity != null) {
                                            AuthManager.executeWithAuth(
                                                activity = activity,
                                                action = SecuritySettings.ProtectedAction.DEVICE_OWNER,
                                                onSuccess = { applyQuickToggle() }
                                            )
                                        } else {
                                            applyQuickToggle()
                                        }
                                    }
                                )

                                // Chevron to open granular scopes dialog
                                IconButton(onClick = { configuringApp = app }) {
                                    Icon(
                                        imageVector = Icons.Default.ChevronRight,
                                        contentDescription = stringResource(R.string.device_owner_delegation_configure),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(32.dp).navigationBarsPadding())
                }
            }
        }
    }

    configuringApp?.let { app ->
        GranularScopesDialog(
            app = app,
            onDismiss = { configuringApp = null },
            onSave = { updatedScopes ->
                fun applyGranular() {
                    val ok = DeviceOwnerManager.setDelegatedScopes(
                        context,
                        app.packageName,
                        updatedScopes
                    )
                    if (ok) {
                        if (updatedScopes.isNotEmpty()) {
                            moe.shizuku.manager.dhizuku.DhizukuAuthManager.grant(context, app.uid, onetime = false)
                        } else {
                            moe.shizuku.manager.dhizuku.DhizukuAuthManager.revoke(context, app.uid)
                        }
                        apps = apps.map {
                            if (it.packageName == app.packageName) it.copy(
                                scopes = updatedScopes,
                                isDhizukuGranted = updatedScopes.isNotEmpty(),
                                isOneTime = false
                            )
                            else it
                        }
                        Toast.makeText(
                            context,
                            R.string.device_owner_delegation_updated,
                            Toast.LENGTH_SHORT
                        ).show()
                        configuringApp = null
                    }
                }

                if (activity != null) {
                    AuthManager.executeWithAuth(
                        activity = activity,
                        action = SecuritySettings.ProtectedAction.DEVICE_OWNER,
                        onSuccess = { applyGranular() }
                    )
                } else {
                    applyGranular()
                }
            }
        )
    }
}

@Composable
private fun GranularScopesDialog(
    app: DeviceOwnerManager.DelegatedAppInfo,
    onDismiss: () -> Unit,
    onSave: (List<String>) -> Unit
) {
    var selectedScopes by remember(app) { mutableStateOf(app.scopes.toSet()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = app.label,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = app.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.device_owner_delegation_dialog_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                DeviceOwnerManager.ALL_SCOPES.forEach { scope ->
                    val isChecked = selectedScopes.contains(scope.scopeName)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                selectedScopes = if (isChecked) {
                                    selectedScopes - scope.scopeName
                                } else {
                                    selectedScopes + scope.scopeName
                                }
                            }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = isChecked,
                            onCheckedChange = { checked ->
                                selectedScopes = if (checked) {
                                    selectedScopes + scope.scopeName
                                } else {
                                    selectedScopes - scope.scopeName
                                }
                            }
                        )
                        Column(modifier = Modifier.padding(start = 8.dp)) {
                            Text(
                                text = scope.label,
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = scope.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(selectedScopes.toList()) }
            ) {
                Text(stringResource(R.string.device_owner_delegation_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.extraLarge
    )
}
