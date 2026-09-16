package com.yayo.sshtunneling.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Router
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.content.ContextCompat
import com.yayo.sshtunneling.R
import com.yayo.sshtunneling.model.AuthMode
import com.yayo.sshtunneling.model.ForwardMode
import com.yayo.sshtunneling.model.ForwardStatus
import com.yayo.sshtunneling.model.HostProfile
import com.yayo.sshtunneling.model.PortForwardRule
import com.yayo.sshtunneling.model.TunnelConnectionState
import com.yayo.sshtunneling.model.TunnelPhase
import com.yayo.sshtunneling.model.WidgetSlots
import com.yayo.sshtunneling.update.AppUpdateInfo
import kotlinx.coroutines.launch

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun SshTunnelingApp(
    viewModel: TunnelViewModel,
    isExpanded: Boolean,
    onInstallUpdate: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showAppInfo by rememberSaveable { mutableStateOf(false) }
    var selectedSection by rememberSaveable { mutableStateOf(AppSection.HOME) }
    var pendingImportJson by remember { mutableStateOf<String?>(null) }
    var pendingForwardAfterNotificationPermission by remember { mutableStateOf<String?>(null) }
    var pendingForwardAfterLocalNetworkPermission by remember { mutableStateOf<String?>(null) }
    var notificationPermissionHandled by rememberSaveable { mutableStateOf(false) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val forwardId = pendingForwardAfterNotificationPermission
        pendingForwardAfterNotificationPermission = null
        if (!granted) {
            scope.launch { snackbarHostState.showSnackbar("알림 권한 없이 연결합니다. 실행 상태 알림이 표시되지 않을 수 있습니다.") }
        }
        forwardId?.let(viewModel::toggleForward)
    }
    val localNetworkPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val forwardId = pendingForwardAfterLocalNetworkPermission
        pendingForwardAfterLocalNetworkPermission = null
        if (!granted) {
            scope.launch { snackbarHostState.showSnackbar("ADB 기기를 찾으려면 로컬 네트워크 권한이 필요합니다.") }
        } else if (forwardId != null) {
            val needsNotificationPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                !notificationPermissionHandled &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            if (needsNotificationPermission) {
                notificationPermissionHandled = true
                pendingForwardAfterNotificationPermission = forwardId
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                viewModel.toggleForward(forwardId)
            }
        }
    }
    val onToggleForward: (String) -> Unit = { forwardId ->
        val currentState = uiState.statuses[forwardId]?.state
        val isStarting = currentState != TunnelConnectionState.CONNECTED &&
            currentState != TunnelConnectionState.CONNECTING
        val forward = uiState.appData.forwards.firstOrNull { it.id == forwardId }
        val host = uiState.appData.hosts.firstOrNull { it.id == forward?.hostId }
        val profileIsComplete = forward?.isComplete() == true && host?.isComplete() == true
        val needsLocalNetworkPermission = isStarting && profileIsComplete &&
            Build.VERSION.SDK_INT >= LOCAL_NETWORK_PERMISSION_API &&
            forward?.mode != ForwardMode.LOCAL &&
            ContextCompat.checkSelfPermission(context, LOCAL_NETWORK_PERMISSION) != PackageManager.PERMISSION_GRANTED
        val needsPermission = isStarting && profileIsComplete && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !notificationPermissionHandled &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (needsLocalNetworkPermission) {
            pendingForwardAfterLocalNetworkPermission = forwardId
            localNetworkPermissionLauncher.launch(LOCAL_NETWORK_PERMISSION)
        } else if (needsPermission) {
            notificationPermissionHandled = true
            pendingForwardAfterNotificationPermission = forwardId
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            viewModel.toggleForward(forwardId)
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { writer ->
                writer.write(viewModel.exportSettingsJson())
            } ?: error(context.getString(R.string.export_write_failed))
        }.onSuccess {
            scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.export_success)) }
        }.onFailure { error ->
            scope.launch { snackbarHostState.showSnackbar(error.message ?: context.getString(R.string.export_write_failed)) }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { reader ->
                reader.readText()
            } ?: error(context.getString(R.string.import_read_failed))
        }.onSuccess { json ->
            pendingImportJson = json
        }.onFailure { error ->
            scope.launch { snackbarHostState.showSnackbar(error.message ?: context.getString(R.string.import_read_failed)) }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("${context.getString(R.string.app_name)} · ${selectedSection.label}") },
            )
        },
        bottomBar = {
            if (!isExpanded) {
                AppNavigation(
                    selectedSection = selectedSection,
                    onSectionSelected = { selectedSection = it },
                    expanded = false,
                )
            }
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { innerPadding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            if (isExpanded) {
                Row(modifier = Modifier.fillMaxSize()) {
                    AppNavigation(
                        selectedSection = selectedSection,
                        onSectionSelected = { selectedSection = it },
                        expanded = true,
                    )
                    SectionContent(
                        section = selectedSection,
                        uiState = uiState,
                        viewModel = viewModel,
                        onToggleForward = onToggleForward,
                        onExport = { exportLauncher.launch("ssh-tunneling-settings.json") },
                        onImport = { importLauncher.launch(arrayOf("application/json", "text/*")) },
                        onCheckUpdate = { viewModel.checkForAppUpdate(force = true) },
                        onShowAppInfo = { showAppInfo = true },
                    )
                }
            } else {
                SectionContent(
                    section = selectedSection,
                    uiState = uiState,
                    viewModel = viewModel,
                    onToggleForward = onToggleForward,
                    onExport = { exportLauncher.launch("ssh-tunneling-settings.json") },
                    onImport = { importLauncher.launch(arrayOf("application/json", "text/*")) },
                    onCheckUpdate = { viewModel.checkForAppUpdate(force = true) },
                    onShowAppInfo = { showAppInfo = true },
                )
            }
        }
    }

    if (showAppInfo) {
        AppInfoDialog(
            onDismiss = { showAppInfo = false },
            onOpenGithub = { uriHandler.openUri(context.getString(R.string.github_repository_url)) },
        )
    }

    uiState.updateState.statusMessage?.let { message ->
        LaunchedEffect(message) {
            snackbarHostState.showSnackbar(message)
            viewModel.clearUpdateStatusMessage()
        }
    }

    pendingImportJson?.let { rawJson ->
        ImportConfirmDialog(
            onDismiss = { pendingImportJson = null },
            onConfirm = {
                pendingImportJson = null
                viewModel.importSettingsJson(rawJson)
                    .onSuccess {
                        scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.import_success)) }
                    }
                    .onFailure {
                        scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.import_invalid_file)) }
                    }
            },
        )
    }

    uiState.updateState.availableUpdate?.let { updateInfo ->
        AppUpdateDialog(
            updateInfo = updateInfo,
            isDownloading = uiState.updateState.isDownloading,
            downloadProgressPercent = uiState.updateState.downloadProgressPercent,
            onDismiss = viewModel::dismissAvailableUpdate,
            onInstall = onInstallUpdate,
            onOpenReleaseNotes = {
                updateInfo.releaseNotesUrl?.let(uriHandler::openUri)
            },
        )
    }

    uiState.updateState.errorMessage?.let { message ->
        UpdateErrorDialog(
            message = message,
            onDismiss = viewModel::clearUpdateError,
        )
    }

    when {
        uiState.hostKeyVerification.isChecking -> HostKeyLoadingDialog(
            endpoint = uiState.hostKeyVerification.endpoint.orEmpty(),
            onDismiss = viewModel::dismissHostKeyVerification,
        )
        uiState.hostKeyVerification.fingerprint != null -> HostKeyConfirmDialog(
            endpoint = uiState.hostKeyVerification.endpoint.orEmpty(),
            fingerprint = checkNotNull(uiState.hostKeyVerification.fingerprint),
            onDismiss = viewModel::dismissHostKeyVerification,
            onConfirm = viewModel::confirmHostKey,
        )
        uiState.hostKeyVerification.errorMessage != null -> HostKeyErrorDialog(
            message = checkNotNull(uiState.hostKeyVerification.errorMessage),
            onDismiss = viewModel::dismissHostKeyVerification,
            onRetry = viewModel::retryHostKeyVerification,
        )
    }

    uiState.deletionConfirmation?.let { confirmation ->
        DeleteConfirmDialog(
            confirmation = confirmation,
            onDismiss = viewModel::dismissDeletionConfirmation,
            onConfirm = viewModel::confirmDeletion,
        )
    }
}

private const val LOCAL_NETWORK_PERMISSION_API = 37
private const val LOCAL_NETWORK_PERMISSION = "android.permission.ACCESS_LOCAL_NETWORK"

private enum class AppSection(val label: String) {
    HOME("홈"),
    TUNNELS("터널"),
    SETTINGS("설정"),
}

@Composable
private fun AppNavigation(
    selectedSection: AppSection,
    onSectionSelected: (AppSection) -> Unit,
    expanded: Boolean,
) {
    val items = listOf(
        AppSection.HOME to Icons.Rounded.Home,
        AppSection.TUNNELS to Icons.Rounded.Tune,
        AppSection.SETTINGS to Icons.Rounded.Settings,
    )
    if (expanded) {
        androidx.compose.material3.NavigationRail {
            items.forEach { (section, icon) ->
                NavigationRailItem(
                    selected = selectedSection == section,
                    onClick = { onSectionSelected(section) },
                    icon = { Icon(icon, contentDescription = section.label) },
                    label = { Text(section.label) },
                )
            }
        }
    } else {
        androidx.compose.material3.NavigationBar {
            items.forEach { (section, icon) ->
                NavigationBarItem(
                    selected = selectedSection == section,
                    onClick = { onSectionSelected(section) },
                    icon = { Icon(icon, contentDescription = section.label) },
                    label = { Text(section.label) },
                )
            }
        }
    }
}

@Composable
private fun SectionContent(
    section: AppSection,
    uiState: TunnelUiState,
    viewModel: TunnelViewModel,
    onToggleForward: (String) -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onCheckUpdate: () -> Unit,
    onShowAppInfo: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        when (section) {
            AppSection.HOME -> item { OverviewPane(uiState = uiState, onToggleForward = onToggleForward) }
            AppSection.TUNNELS -> item {
                EditorPane(uiState = uiState, viewModel = viewModel, onToggleForward = onToggleForward)
            }
            AppSection.SETTINGS -> item {
                SettingsPane(
                    onExport = onExport,
                    onImport = onImport,
                    onCheckUpdate = onCheckUpdate,
                    onShowAppInfo = onShowAppInfo,
                )
            }
        }
    }
}

@Composable
private fun SettingsPane(
    onExport: () -> Unit,
    onImport: () -> Unit,
    onCheckUpdate: () -> Unit,
    onShowAppInfo: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Card(shape = RoundedCornerShape(28.dp)) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("설정", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "설정 파일은 기본적으로 비밀번호와 private key를 제외하고 내보냅니다.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = onExport, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.Download, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text("설정 내보내기")
                }
                OutlinedButton(onClick = onImport, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.Upload, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text("설정 불러오기")
                }
            }
        }
        Card(shape = RoundedCornerShape(28.dp)) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("유지 관리", style = MaterialTheme.typography.titleLarge)
                TextButton(onClick = onCheckUpdate) { Text("업데이트 확인") }
                TextButton(onClick = onShowAppInfo) { Text("앱 정보") }
            }
        }
    }
}

@Composable
private fun OverviewPane(
    uiState: TunnelUiState,
    onToggleForward: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val connectedCount = uiState.statuses.values.count { it.state == TunnelConnectionState.CONNECTED }
    val connectingCount = uiState.statuses.values.count { it.state == TunnelConnectionState.CONNECTING }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(MaterialTheme.colorScheme.surface, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Router, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("멀티 호스트 터널", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        text = "연결됨 ${connectedCount}개, 연결 중 ${connectingCount}개. 위젯에는 지정한 6개 포워딩만 노출됩니다.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            HorizontalDivider()
            Text("터널", style = MaterialTheme.typography.titleMedium)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                uiState.appData.forwards.forEach { forward ->
                    val host = uiState.appData.hosts.firstOrNull { it.id == forward.hostId }
                    val status = uiState.statuses[forward.id]
                    Card(shape = RoundedCornerShape(18.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(forward.name.ifBlank { "이름 없는 포워딩" }, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "${host?.name.orEmpty()} · ${forward.pathSummary()} · ${statusLabel(status)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            TextButton(onClick = { onToggleForward(forward.id) }) {
                                Text(actionLabel(status))
                            }
                        }
                    }
                }
            }

            HorizontalDivider()
            Text("위젯 6슬롯", style = MaterialTheme.typography.titleMedium)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(WidgetSlots.COUNT) { slot ->
                    val forward = uiState.appData.forwards.firstOrNull { it.widgetSlot == slot }
                    val host = uiState.appData.hosts.firstOrNull { it.id == forward?.hostId }
                    val status = forward?.let { uiState.statuses[it.id] }

                    WidgetSlotRow(
                        slot = slot,
                        title = forward?.name ?: "비어 있음",
                        subtitle = if (forward != null && host != null) {
                            buildString {
                                append(host.name)
                                append(" · ")
                                append(forward.pathSummary())
                                append(" · ")
                                append(statusLabel(status))
                            }
                        } else {
                            "앱에서 포워딩을 선택해 이 슬롯에 배치하세요."
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun WidgetSlotRow(
    slot: Int,
    title: String,
    subtitle: String,
) {
    Card(shape = RoundedCornerShape(18.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("슬롯 ${slot + 1} · $title", style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun EditorPane(
    uiState: TunnelUiState,
    viewModel: TunnelViewModel,
    onToggleForward: (String) -> Unit,
    scrollable: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val selectedHost = uiState.appData.hosts.firstOrNull { it.id == uiState.selectedHostId }
    val hostForwards = uiState.appData.forwards.filter { it.hostId == selectedHost?.id }
    val selectedForward = hostForwards.firstOrNull { it.id == uiState.selectedForwardId } ?: hostForwards.firstOrNull()

    if (scrollable) {
        LazyColumn(
            modifier = modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                HostSelectorCard(
                    uiState = uiState,
                    onSelectHost = viewModel::selectHost,
                    onAddHost = viewModel::addHost,
                    onDeleteHost = viewModel::requestDeleteSelectedHost,
                )
            }

            selectedHost?.let { host ->
                item {
                    HostEditorCard(
                        host = host,
                        requiresHostKey = hostForwards.any { it.mode != ForwardMode.LOCAL },
                        verification = uiState.hostKeyVerification,
                        viewModel = viewModel,
                    )
                }

                item {
                    ForwardListCard(
                        forwards = hostForwards,
                        statuses = uiState.statuses,
                        selectedForwardId = selectedForward?.id,
                        onSelectForward = viewModel::selectForward,
                        onAddForward = viewModel::addForward,
                        onToggleForward = onToggleForward,
                        onDeleteForward = viewModel::requestDeleteSelectedForward,
                    )
                }

                selectedForward?.let { forward ->
                    item {
                        ForwardEditorCard(
                            host = host,
                            forward = forward,
                            status = uiState.statuses[forward.id],
                            viewModel = viewModel,
                            onToggleForward = onToggleForward,
                        )
                    }
                }
            }
        }
    } else {
        Column(
            modifier = modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            HostSelectorCard(
                uiState = uiState,
                onSelectHost = viewModel::selectHost,
                onAddHost = viewModel::addHost,
                onDeleteHost = viewModel::requestDeleteSelectedHost,
            )

            selectedHost?.let { host ->
                HostEditorCard(
                    host = host,
                    requiresHostKey = hostForwards.any { it.mode != ForwardMode.LOCAL },
                    verification = uiState.hostKeyVerification,
                    viewModel = viewModel,
                )
                ForwardListCard(
                    forwards = hostForwards,
                    statuses = uiState.statuses,
                    selectedForwardId = selectedForward?.id,
                    onSelectForward = viewModel::selectForward,
                    onAddForward = viewModel::addForward,
                    onToggleForward = onToggleForward,
                    onDeleteForward = viewModel::requestDeleteSelectedForward,
                )
                selectedForward?.let { forward ->
                    ForwardEditorCard(
                        host = host,
                        forward = forward,
                        status = uiState.statuses[forward.id],
                        viewModel = viewModel,
                        onToggleForward = onToggleForward,
                    )
                }
            }
        }
    }
}

@Composable
private fun HostSelectorCard(
    uiState: TunnelUiState,
    onSelectHost: (String) -> Unit,
    onAddHost: () -> Unit,
    onDeleteHost: () -> Unit,
) {
    Card(shape = RoundedCornerShape(28.dp)) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("호스트", style = MaterialTheme.typography.titleLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onAddHost) {
                        Icon(Icons.Rounded.Add, contentDescription = null)
                        Spacer(Modifier.size(6.dp))
                        Text("추가")
                    }
                    TextButton(onClick = onDeleteHost, enabled = uiState.appData.hosts.size > 1) {
                        Icon(Icons.Rounded.Delete, contentDescription = null)
                        Spacer(Modifier.size(6.dp))
                        Text("삭제")
                    }
                }
            }

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(uiState.appData.hosts, key = { it.id }) { host ->
                    FilterChip(
                        selected = host.id == uiState.selectedHostId,
                        onClick = { onSelectHost(host.id) },
                        label = { Text(host.name.ifBlank { "이름 없는 호스트" }) },
                    )
                }
            }
        }
    }
}

@Composable
private fun HostEditorCard(
    host: HostProfile,
    requiresHostKey: Boolean,
    verification: HostKeyVerificationState,
    viewModel: TunnelViewModel,
) {
    var privateKeyExpanded by rememberSaveable(host.id, host.authMode.name) { mutableStateOf(false) }
    var sshPortInput by rememberSaveable(host.id) { mutableStateOf(host.port.toString()) }
    var keepAliveInput by rememberSaveable(host.id) { mutableStateOf(host.keepAliveSeconds.toString()) }

    Card(shape = RoundedCornerShape(28.dp)) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("선택한 호스트 설정", style = MaterialTheme.typography.titleLarge)
            Text(
                "같은 호스트 아래에 여러 포트 포워딩을 둘 수 있습니다.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HorizontalDivider()

            TunnelField(
                value = host.name,
                label = "호스트 이름",
                onValueChange = { value ->
                    viewModel.updateSelectedHost { it.copy(name = value) }
                },
                required = true,
            )
            TunnelField(
                value = host.host,
                label = "SSH 서버 주소",
                onValueChange = { value ->
                    viewModel.updateSelectedHostAddress(value)
                },
                required = true,
            )
            TunnelNumberField(sshPortInput, "SSH 포트") { value ->
                sshPortInput = value
                viewModel.updateSelectedHostPort(value.toIntOrNull() ?: 0)
            }
            if (requiresHostKey) {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (host.hostKeyFingerprint.isNullOrBlank()) {
                            MaterialTheme.colorScheme.surfaceVariant
                        } else {
                            MaterialTheme.colorScheme.primaryContainer
                        },
                    ),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            if (host.hostKeyFingerprint.isNullOrBlank()) "서버 신원 확인 필요" else "서버 신원 확인됨",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            if (host.hostKeyFingerprint.isNullOrBlank()) {
                                "ADB 터널을 처음 연결할 때 앱이 서버 키를 가져와 확인을 요청합니다. 직접 입력할 필요가 없습니다."
                            } else {
                                host.hostKeyFingerprint
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        OutlinedButton(
                            onClick = viewModel::verifySelectedHostKey,
                            enabled = !verification.isChecking && host.host.isNotBlank(),
                        ) {
                            Text(if (host.hostKeyFingerprint.isNullOrBlank()) "지금 확인" else "서버 키 다시 확인")
                        }
                    }
                }
            }
            TunnelField(
                value = host.username,
                label = "사용자 이름",
                onValueChange = { value ->
                    viewModel.updateSelectedHost { it.copy(username = value.trim()) }
                },
                required = true,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = host.authMode == AuthMode.PASSWORD,
                    onClick = { viewModel.setSelectedHostAuthMode(AuthMode.PASSWORD) },
                    label = { Text("비밀번호") },
                    leadingIcon = { Icon(Icons.Rounded.Lock, contentDescription = null) },
                )
                FilterChip(
                    selected = host.authMode == AuthMode.PRIVATE_KEY,
                    onClick = { viewModel.setSelectedHostAuthMode(AuthMode.PRIVATE_KEY) },
                    label = { Text("개인 키") },
                    leadingIcon = { Icon(Icons.Rounded.Lock, contentDescription = null) },
                )
            }

            if (host.authMode == AuthMode.PASSWORD) {
                TunnelField(
                    value = host.password,
                    label = "비밀번호",
                    onValueChange = { value ->
                        viewModel.updateSelectedHost { it.copy(password = value) }
                    },
                    isSecret = true,
                    required = true,
                )
            } else {
                if (privateKeyExpanded) {
                    TunnelField(
                        value = host.privateKey,
                        label = "개인 키 (PEM)",
                        onValueChange = { value -> viewModel.updateSelectedHost { it.copy(privateKey = value) } },
                        singleLine = false,
                        maxLinesWhenExpanded = 8,
                        required = true,
                    )
                } else {
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text("개인 키 (PEM)", style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = host.privateKey.previewLabel(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = { privateKeyExpanded = !privateKeyExpanded }) {
                        Text(if (privateKeyExpanded) "접기" else "펼치기")
                    }
                }
            }

            TunnelNumberField(keepAliveInput, "연결 유지 간격(초)", maxValue = 86_400) { value ->
                keepAliveInput = value
                viewModel.updateSelectedHost { current -> current.copy(keepAliveSeconds = value.toIntOrNull() ?: 0) }
            }
        }
    }
}

@Composable
private fun ForwardListCard(
    forwards: List<PortForwardRule>,
    statuses: Map<String, ForwardStatus>,
    selectedForwardId: String?,
    onSelectForward: (String) -> Unit,
    onAddForward: () -> Unit,
    onToggleForward: (String) -> Unit,
    onDeleteForward: () -> Unit,
) {
    Card(shape = RoundedCornerShape(28.dp)) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("포트 포워딩", style = MaterialTheme.typography.titleLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onAddForward) {
                        Icon(Icons.Rounded.Add, contentDescription = null)
                        Spacer(Modifier.size(6.dp))
                        Text("추가")
                    }
                    TextButton(onClick = onDeleteForward, enabled = forwards.size > 1) {
                        Icon(Icons.Rounded.Delete, contentDescription = null)
                        Spacer(Modifier.size(6.dp))
                        Text("삭제")
                    }
                }
            }

            forwards.forEach { forward ->
                val status = statuses[forward.id]
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (forward.id == selectedForwardId) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        }
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectForward(forward.id) },
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.fillMaxWidth(0.78f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(forward.name.ifBlank { "이름 없는 포워딩" }, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${forward.pathSummary()} · ${statusLabel(status)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        FilledTonalButton(onClick = { onToggleForward(forward.id) }) {
                            Icon(Icons.Rounded.PowerSettingsNew, contentDescription = null)
                            Spacer(Modifier.size(6.dp))
                            Text(actionLabel(status))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ForwardEditorCard(
    host: HostProfile,
    forward: PortForwardRule,
    status: ForwardStatus?,
    viewModel: TunnelViewModel,
    onToggleForward: (String) -> Unit,
) {
    var localPortInput by rememberSaveable(forward.id, "local") { mutableStateOf(forward.localPort.toString()) }
    var remotePortInput by rememberSaveable(forward.id, "remote") { mutableStateOf(forward.remotePort.toString()) }
    var reversePortInput by rememberSaveable(forward.id, "reverse") { mutableStateOf(forward.reverseBindPort.toString()) }

    Card(shape = RoundedCornerShape(28.dp)) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("포워딩 상세", style = MaterialTheme.typography.titleLarge)
            Text(
                "${host.name}를 통해 이 포워딩만 독립적으로 켜고 끌 수 있습니다.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HorizontalDivider()

            Text("터널 종류", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = forward.mode == ForwardMode.LOCAL,
                    onClick = { viewModel.updateSelectedForward { it.copy(mode = ForwardMode.LOCAL) } },
                    label = { Text("일반 SSH") },
                )
                FilterChip(
                    selected = forward.mode == ForwardMode.ADB_CONNECT,
                    onClick = {
                        viewModel.updateSelectedForward {
                            it.copy(mode = ForwardMode.ADB_CONNECT, reverseBindPort = 5555)
                        }
                    },
                    label = { Text("무선 디버깅") },
                )
                FilterChip(
                    selected = forward.mode == ForwardMode.ADB_PAIRING,
                    onClick = {
                        viewModel.updateSelectedForward {
                            it.copy(mode = ForwardMode.ADB_PAIRING, reverseBindPort = 5556)
                        }
                    },
                    label = { Text("페어링") },
                )
            }

            TunnelField(
                value = forward.name,
                label = "포워딩 이름",
                onValueChange = { value ->
                    viewModel.updateSelectedForward { it.copy(name = value) }
                },
                required = true,
            )
            if (forward.mode == ForwardMode.LOCAL) {
                TunnelNumberField(localPortInput, "내 기기 포트") { value ->
                    localPortInput = value
                    viewModel.updateSelectedForward { current -> current.copy(localPort = value.toIntOrNull() ?: 0) }
                }
                TunnelField(
                    value = forward.remoteHost,
                    label = "목적지 주소",
                    onValueChange = { value ->
                        viewModel.updateSelectedForward { it.copy(remoteHost = value.trim()) }
                    },
                    required = true,
                )
                TunnelNumberField(remotePortInput, "목적지 포트") { value ->
                    remotePortInput = value
                    viewModel.updateSelectedForward { current -> current.copy(remotePort = value.toIntOrNull() ?: 0) }
                }
            } else {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            if (forward.mode == ForwardMode.ADB_CONNECT) "무선 디버깅 자동 감지" else "페어링 주소 자동 감지",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            if (forward.mode == ForwardMode.ADB_CONNECT) {
                                "연결 후 SSH 서버에서 adb connect 127.0.0.1:${forward.reverseBindPort}를 실행하세요."
                            } else {
                                "Android에서 ‘페어링 코드로 기기 페어링’ 화면을 연 채, SSH 서버에서 adb pair 127.0.0.1:${forward.reverseBindPort}를 실행하세요."
                            },
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                TunnelNumberField(reversePortInput, "SSH 서버의 로컬 포트") { value ->
                    reversePortInput = value
                    viewModel.updateSelectedForward { current ->
                        current.copy(reverseBindHost = "127.0.0.1", reverseBindPort = value.toIntOrNull() ?: 0)
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Widgets, contentDescription = null)
                    Text("위젯 슬롯", style = MaterialTheme.typography.titleMedium)
                }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        FilterChip(
                            selected = forward.widgetSlot == null,
                            onClick = { viewModel.assignWidgetSlot(null) },
                            label = { Text("미지정") },
                        )
                    }
                    items((0 until WidgetSlots.COUNT).toList()) { slot ->
                        FilterChip(
                            selected = forward.widgetSlot == slot,
                            onClick = { viewModel.assignWidgetSlot(slot) },
                            label = { Text("${slot + 1}") },
                        )
                    }
                }
            }

            Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text("현재 상태", style = MaterialTheme.typography.titleSmall)
                    Text(statusLabel(status), style = MaterialTheme.typography.bodyMedium)
                    status?.message?.takeIf { it.isNotBlank() }?.let { message ->
                        Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onToggleForward(forward.id) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.Link, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text(actionLabel(status))
                }
                OutlinedButton(onClick = { viewModel.assignWidgetSlot(null) }, modifier = Modifier.fillMaxWidth()) {
                    Text("위젯 해제")
                }
            }
        }
    }
}

@Composable
private fun TunnelField(
    value: String,
    label: String,
    onValueChange: (String) -> Unit,
    singleLine: Boolean = true,
    isSecret: Boolean = false,
    maxLinesWhenExpanded: Int = 6,
    required: Boolean = false,
) {
    var revealSecret by rememberSaveable { mutableStateOf(false) }
    val hasError = required && value.isBlank()
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        isError = hasError,
        supportingText = if (hasError) {
            { Text("필수 항목입니다.") }
        } else {
            null
        },
        singleLine = singleLine,
        minLines = if (singleLine) 1 else maxLinesWhenExpanded,
        maxLines = if (singleLine) 1 else maxLinesWhenExpanded,
        visualTransformation = if (isSecret && !revealSecret) PasswordVisualTransformation() else VisualTransformation.None,
        trailingIcon = if (isSecret) {
            {
                IconButton(onClick = { revealSecret = !revealSecret }) {
                    Icon(
                        if (revealSecret) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                        contentDescription = if (revealSecret) "비밀번호 숨기기" else "비밀번호 보기",
                    )
                }
            }
        } else {
            null
        },
        keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Next),
    )
}

@Composable
private fun TunnelNumberField(
    value: String,
    label: String,
    minValue: Int = 1,
    maxValue: Int = 65_535,
    onValueChange: (String) -> Unit,
) {
    val number = value.toIntOrNull()
    val hasError = number == null || number !in minValue..maxValue
    OutlinedTextField(
        value = value,
        onValueChange = { input ->
            onValueChange(input.filter { it.isDigit() })
        },
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        isError = hasError,
        supportingText = if (hasError) {
            { Text("$minValue~$maxValue 사이의 숫자를 입력하세요.") }
        } else {
            null
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
    )
}

private fun statusLabel(status: ForwardStatus?): String {
    when (status?.phase) {
        TunnelPhase.WAITING_FOR_PERMISSION,
        TunnelPhase.WAITING_FOR_WIFI,
        TunnelPhase.WAITING_FOR_PAIRING -> return "대기 중"
        TunnelPhase.DISCOVERING_ADB -> return "검색 중"
        TunnelPhase.PROBING_ADB -> return "확인 중"
        TunnelPhase.CONNECTING_SSH,
        TunnelPhase.BINDING_FORWARD,
        TunnelPhase.RECONNECTING -> return "연결 중"
        TunnelPhase.ERROR,
        TunnelPhase.IDLE,
        TunnelPhase.CONNECTED,
        null -> Unit
    }
    return when (status?.state) {
        TunnelConnectionState.CONNECTED -> "연결됨"
        TunnelConnectionState.CONNECTING -> "연결 중"
        TunnelConnectionState.ERROR -> "실패"
        TunnelConnectionState.IDLE, null -> "대기"
    }
}

private fun actionLabel(status: ForwardStatus?): String = when (status?.state) {
    TunnelConnectionState.CONNECTED, TunnelConnectionState.CONNECTING -> "연결 해제"
    TunnelConnectionState.IDLE, TunnelConnectionState.ERROR, null -> "연결"
}

private fun PortForwardRule.pathSummary(): String = when (mode) {
    ForwardMode.LOCAL -> "$localPort → $remoteHost:$remotePort"
    ForwardMode.ADB_CONNECT -> "무선 디버깅 → 127.0.0.1:$reverseBindPort"
    ForwardMode.ADB_PAIRING -> "페어링 → 127.0.0.1:$reverseBindPort"
}

@Composable
private fun HostKeyLoadingDialog(
    endpoint: String,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("취소") }
        },
        icon = { CircularProgressIndicator() },
        title = { Text("SSH 서버 확인 중") },
        text = { Text("${endpoint}에서 서버 공개키를 안전하게 가져오는 중입니다.") },
    )
}

@Composable
private fun HostKeyConfirmDialog(
    endpoint: String,
    fingerprint: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("일치함, 신뢰") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("취소") }
        },
        title = { Text("이 SSH 서버를 신뢰할까요?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("$endpoint 서버가 제시한 키입니다. 서버 관리자에게 받은 값과 일치하는지 확인하세요.")
                SelectionContainer {
                    Text(
                        fingerprint,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Text(
                    "처음 한 번 승인하면 이후 연결에서는 앱이 자동으로 대조합니다. 값이 바뀌면 연결을 차단합니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
}

@Composable
private fun HostKeyErrorDialog(
    message: String,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onRetry) { Text("다시 시도") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("닫기") }
        },
        title = { Text("서버 확인 실패") },
        text = { Text(message) },
    )
}

@Composable
private fun DeleteConfirmDialog(
    confirmation: DeletionConfirmation,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val isHost = confirmation.target == DeleteTarget.HOST
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("삭제") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("취소") }
        },
        title = { Text(if (isHost) "호스트를 삭제할까요?" else "포워딩을 삭제할까요?") },
        text = {
            Text(
                if (isHost) {
                    "‘${confirmation.label}’과 여기에 속한 모든 포워딩을 삭제합니다. 이 작업은 되돌릴 수 없습니다."
                } else {
                    "‘${confirmation.label}’을 삭제합니다. 이 작업은 되돌릴 수 없습니다."
                },
            )
        },
    )
}

@Composable
private fun AppInfoDialog(
    onDismiss: () -> Unit,
    onOpenGithub: () -> Unit,
) {
    val context = LocalContext.current
    val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
    val versionName = packageInfo.versionName ?: "unknown"
    val versionCode = androidx.core.content.pm.PackageInfoCompat.getLongVersionCode(packageInfo)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("닫기")
            }
        },
        title = { Text("앱 정보") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Image(
                    painter = painterResource(id = R.drawable.credit_icon),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(24.dp))
                        .padding(24.dp),
                    contentScale = ContentScale.Fit,
                )
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Yayo 제작", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "여러 SSH 포워딩을 한 번에 관리하고 연결합니다.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                FilledTonalButton(
                    onClick = onOpenGithub,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Rounded.OpenInNew, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text("GitHub 저장소")
                }
                Text(
                    text = context.getString(R.string.app_version_format, versionName, versionCode),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
}

@Composable
private fun ImportConfirmDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("덮어쓰기")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("취소")
            }
        },
        title = { Text("설정 불러오기") },
        text = {
            Text("현재 호스트, 포워딩, 위젯 슬롯을 새 파일 내용으로 교체합니다. 비밀값이 제외된 내보내기 파일이라면 비밀번호와 개인 키를 다시 입력해야 합니다.")
        },
    )
}

@Composable
private fun AppUpdateDialog(
    updateInfo: AppUpdateInfo,
    isDownloading: Boolean,
    downloadProgressPercent: Int?,
    onDismiss: () -> Unit,
    onInstall: () -> Unit,
    onOpenReleaseNotes: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = {
            if (!isDownloading) onDismiss()
        },
        confirmButton = {
            TextButton(onClick = onInstall, enabled = !isDownloading) {
                Text(stringResourceCompat(R.string.update_install))
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                updateInfo.releaseNotesUrl?.let {
                    TextButton(onClick = onOpenReleaseNotes, enabled = !isDownloading) {
                        Text(stringResourceCompat(R.string.update_release_notes))
                    }
                }
                TextButton(onClick = onDismiss, enabled = !isDownloading) {
                    Text(stringResourceCompat(R.string.update_later))
                }
            }
        },
        title = { Text(stringResourceCompat(R.string.update_available_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResourceCompat(R.string.update_available_message, updateInfo.versionName))
                updateInfo.notes?.takeIf { it.isNotBlank() }?.let { notes ->
                    Text(notes, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (isDownloading) {
                    val progress = downloadProgressPercent?.coerceIn(0, 100)
                    if (progress != null) {
                        LinearProgressIndicator(
                            progress = progress / 100f,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    Text(
                        text = if (progress != null) {
                            stringResourceCompat(R.string.update_downloading_progress, progress)
                        } else {
                            stringResourceCompat(R.string.update_downloading)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
    )
}

@Composable
private fun UpdateErrorDialog(
    message: String,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("확인")
            }
        },
        title = { Text(stringResourceCompat(R.string.update_error_title)) },
        text = { Text(message) },
    )
}

private fun String.previewLabel(): String {
    if (isBlank()) return "키가 비어 있습니다."
    val firstLine = lineSequence().firstOrNull()?.trim().orEmpty()
    return if (length > 48) "$firstLine ... (${length} chars)" else firstLine
}

@Composable
private fun stringResourceCompat(id: Int, vararg args: Any): String {
    return LocalContext.current.getString(id, *args)
}
