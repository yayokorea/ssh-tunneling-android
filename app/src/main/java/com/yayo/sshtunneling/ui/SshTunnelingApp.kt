package com.yayo.sshtunneling.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.rounded.AddCircle
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Router
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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
    val activityEntries = remember { mutableStateListOf<TunnelActivityEntry>() }
    val lastLoggedStatuses = remember { mutableMapOf<String, ForwardStatus>() }
    var showAppInfo by rememberSaveable { mutableStateOf(false) }
    var currentScreen by rememberSaveable { mutableStateOf(AppScreen.HOME) }
    var pendingImportJson by remember { mutableStateOf<String?>(null) }
    var pendingForwardAfterNotificationPermission by remember { mutableStateOf<String?>(null) }
    var pendingForwardAfterLocalNetworkPermission by remember { mutableStateOf<String?>(null) }
    var notificationPermissionHandled by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(uiState.statuses) {
        uiState.statuses.forEach { (forwardId, status) ->
            if (lastLoggedStatuses[forwardId] != status) {
                lastLoggedStatuses[forwardId] = status
                activityEntries.add(
                    index = 0,
                    element = TunnelActivityEntry(
                        timeLabel = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date()),
                        forwardId = forwardId,
                        status = status,
                    ),
                )
            }
        }
        while (activityEntries.size > 50) activityEntries.removeAt(activityEntries.lastIndex)
    }

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

    val navigateBack: () -> Unit = {
        currentScreen = when (currentScreen) {
            AppScreen.HOST_EDIT,
            AppScreen.LOCAL_EDIT,
            AppScreen.ADB_CONNECT,
            AppScreen.ADB_PAIR -> AppScreen.HOSTS
            AppScreen.WIDGETS -> AppScreen.SETTINGS
            AppScreen.ACTIVITY -> AppScreen.HOME
            else -> currentScreen
        }
    }
    BackHandler(enabled = !currentScreen.isRoot, onBack = navigateBack)

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    if (!currentScreen.isRoot) {
                        IconButton(onClick = navigateBack) {
                            Icon(Icons.Rounded.ArrowBack, contentDescription = "뒤로가기")
                        }
                    }
                },
                title = {
                    Column {
                        Text(currentScreen.label, style = MaterialTheme.typography.titleLarge)
                        Text(
                            currentScreen.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    if (currentScreen != AppScreen.ACTIVITY) {
                        TextButton(onClick = { currentScreen = AppScreen.ACTIVITY }) {
                            Text("로그")
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (!isExpanded) {
                AppNavigation(
                    selectedSection = currentScreen.rootSection,
                    onSectionSelected = { currentScreen = it.screen },
                    expanded = false,
                )
            }
        },
        floatingActionButton = {
            if (!isExpanded && currentScreen.isRoot && currentScreen != AppScreen.ADD) {
                FloatingActionButton(
                    onClick = { currentScreen = AppScreen.ADD },
                    modifier = Modifier.size(52.dp),
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = "새 터널 만들기")
                }
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
                        selectedSection = currentScreen.rootSection,
                        onSectionSelected = { currentScreen = it.screen },
                        expanded = true,
                    )
                    SectionContent(
                        screen = currentScreen,
                        uiState = uiState,
                        viewModel = viewModel,
                        activityEntries = activityEntries,
                        onToggleForward = onToggleForward,
                        onExport = { exportLauncher.launch("ssh-tunneling-settings.json") },
                        onImport = { importLauncher.launch(arrayOf("application/json", "text/*")) },
                        onCheckUpdate = { viewModel.checkForAppUpdate(force = true) },
                        onShowAppInfo = { showAppInfo = true },
                        onNavigate = { currentScreen = it },
                    )
                }
            } else {
                SectionContent(
                    screen = currentScreen,
                    uiState = uiState,
                    viewModel = viewModel,
                    activityEntries = activityEntries,
                    onToggleForward = onToggleForward,
                    onExport = { exportLauncher.launch("ssh-tunneling-settings.json") },
                    onImport = { importLauncher.launch(arrayOf("application/json", "text/*")) },
                    onCheckUpdate = { viewModel.checkForAppUpdate(force = true) },
                    onShowAppInfo = { showAppInfo = true },
                    onNavigate = { currentScreen = it },
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

private data class TunnelActivityEntry(
    val timeLabel: String,
    val forwardId: String,
    val status: ForwardStatus,
)

private enum class AppSection(val label: String) {
    HOME("터널"),
    HOSTS("호스트"),
    ADD("추가"),
    SETTINGS("설정");

    val screen: AppScreen
        get() = when (this) {
            HOME -> AppScreen.HOME
            HOSTS -> AppScreen.HOSTS
            ADD -> AppScreen.ADD
            SETTINGS -> AppScreen.SETTINGS
        }
}

private enum class AppScreen(val label: String, val subtitle: String) {
    HOME("터널", "실행 상태 중심 홈"),
    HOSTS("SSH 호스트", "서버와 인증 정보"),
    ADD("터널 추가", "종류를 선택하세요"),
    SETTINGS("설정", "위젯 · 백업 · 업데이트"),
    HOST_EDIT("SSH 호스트 편집", "접속 정보와 서버 신원"),
    LOCAL_EDIT("일반 터널", "SSH 로컬 포워딩 설정"),
    ADB_CONNECT("ADB Connect", "무선 디버깅 reverse tunnel"),
    ADB_PAIR("ADB Pair", "페어링 작업 흐름"),
    WIDGETS("위젯 슬롯", "터널별 빠른 연결/해제"),
    ACTIVITY("실행 로그", "최근 상태 전환");

    val isRoot: Boolean
        get() = this == HOME || this == HOSTS || this == ADD || this == SETTINGS

    val rootSection: AppSection
        get() = when (this) {
            HOME, ACTIVITY -> AppSection.HOME
            HOSTS, HOST_EDIT, LOCAL_EDIT, ADB_CONNECT, ADB_PAIR -> AppSection.HOSTS
            ADD -> AppSection.ADD
            SETTINGS, WIDGETS -> AppSection.SETTINGS
        }
}

private fun ForwardMode.editorScreen(): AppScreen = when (this) {
    ForwardMode.LOCAL -> AppScreen.LOCAL_EDIT
    ForwardMode.ADB_CONNECT -> AppScreen.ADB_CONNECT
    ForwardMode.ADB_PAIRING -> AppScreen.ADB_PAIR
}

@Composable
private fun AppNavigation(
    selectedSection: AppSection,
    onSectionSelected: (AppSection) -> Unit,
    expanded: Boolean,
) {
    val items = listOf(
        AppSection.HOME to Icons.Rounded.Home,
        AppSection.HOSTS to Icons.Rounded.Cloud,
        AppSection.ADD to Icons.Rounded.AddCircle,
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
        androidx.compose.material3.NavigationBar(modifier = Modifier.height(64.dp), tonalElevation = 0.dp) {
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
    screen: AppScreen,
    uiState: TunnelUiState,
    viewModel: TunnelViewModel,
    activityEntries: List<TunnelActivityEntry>,
    onToggleForward: (String) -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onCheckUpdate: () -> Unit,
    onShowAppInfo: () -> Unit,
    onNavigate: (AppScreen) -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp),
        contentPadding = PaddingValues(vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        when (screen) {
            AppScreen.HOME -> item {
                OverviewPane(
                    uiState = uiState,
                    onToggleForward = onToggleForward,
                    onManageForward = { forwardId ->
                        uiState.appData.forwards.firstOrNull { it.id == forwardId }?.let { forward ->
                            viewModel.selectHost(forward.hostId)
                            viewModel.selectForward(forwardId)
                            onNavigate(forward.mode.editorScreen())
                        }
                    },
                    onAdd = { onNavigate(AppScreen.ADD) },
                    onDisconnectAll = viewModel::disconnectAll,
                )
            }
            AppScreen.HOSTS -> item {
                HostsPane(
                    uiState = uiState,
                    onAddHost = {
                        viewModel.addHost()
                        onNavigate(AppScreen.HOST_EDIT)
                    },
                    onOpenHost = { hostId ->
                        viewModel.selectHost(hostId)
                        onNavigate(AppScreen.HOST_EDIT)
                    },
                )
            }
            AppScreen.ADD -> item {
                AddTunnelPane(
                    host = uiState.appData.hosts.firstOrNull { it.id == uiState.selectedHostId },
                    onSelectHost = viewModel::selectHost,
                    hosts = uiState.appData.hosts,
                    onAdd = { mode ->
                        viewModel.addForward(mode)
                        onNavigate(mode.editorScreen())
                    },
                )
            }
            AppScreen.SETTINGS -> item {
                SettingsPane(
                    uiState = uiState,
                    onExport = onExport,
                    onImport = onImport,
                    onCheckUpdate = onCheckUpdate,
                    onShowAppInfo = onShowAppInfo,
                    onManageWidgets = { onNavigate(AppScreen.WIDGETS) },
                )
            }
            AppScreen.WIDGETS -> item {
                WidgetSlotsPane(
                    uiState = uiState,
                    onManageForward = { forwardId ->
                        uiState.appData.forwards.firstOrNull { it.id == forwardId }?.let { forward ->
                            viewModel.selectHost(forward.hostId)
                            viewModel.selectForward(forwardId)
                            onNavigate(forward.mode.editorScreen())
                        }
                    },
                )
            }
            AppScreen.HOST_EDIT -> item {
                val selectedHost = uiState.appData.hosts.firstOrNull { it.id == uiState.selectedHostId }
                if (selectedHost != null) {
                    HostEditorCard(
                        host = selectedHost,
                        requiresHostKey = uiState.appData.forwards.any {
                            it.hostId == selectedHost.id && it.mode != ForwardMode.LOCAL
                        },
                        verification = uiState.hostKeyVerification,
                        viewModel = viewModel,
                        onDone = { onNavigate(AppScreen.HOSTS) },
                        onDelete = {
                            viewModel.requestDeleteSelectedHost()
                            onNavigate(AppScreen.HOSTS)
                        },
                    )
                }
            }
            AppScreen.LOCAL_EDIT -> item {
                val forward = uiState.appData.forwards.firstOrNull { it.id == uiState.selectedForwardId }
                val host = uiState.appData.hosts.firstOrNull { it.id == forward?.hostId }
                if (forward != null && host != null) {
                    ForwardEditorCard(
                        host = host,
                        forward = forward,
                        status = uiState.statuses[forward.id],
                        viewModel = viewModel,
                        onToggleForward = onToggleForward,
                        onDone = { onNavigate(AppScreen.HOME) },
                        onDelete = {
                            viewModel.requestDeleteSelectedForward()
                            onNavigate(AppScreen.HOME)
                        },
                    )
                }
            }
            AppScreen.ADB_CONNECT,
            AppScreen.ADB_PAIR -> item {
                val forward = uiState.appData.forwards.firstOrNull { it.id == uiState.selectedForwardId }
                val host = uiState.appData.hosts.firstOrNull { it.id == forward?.hostId }
                if (forward != null && host != null) {
                    AdbDetailPane(
                        host = host,
                        forward = forward,
                        status = uiState.statuses[forward.id],
                        onToggle = { onToggleForward(forward.id) },
                        onAssignWidget = { slot -> viewModel.assignWidgetSlot(slot) },
                        onDelete = {
                            viewModel.requestDeleteSelectedForward()
                            onNavigate(AppScreen.HOME)
                        },
                    )
                }
            }
            AppScreen.ACTIVITY -> item {
                ActivityPane(uiState = uiState, entries = activityEntries)
            }
        }
    }
}

@Composable
private fun HostsPane(
    uiState: TunnelUiState,
    onAddHost: () -> Unit,
    onOpenHost: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("SSH 호스트", style = MaterialTheme.typography.titleLarge)
                Text(
                    "자격 증명과 서버 신원을 관리합니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Button(onClick = onAddHost) {
                Icon(Icons.Rounded.Add, contentDescription = null)
                Spacer(Modifier.size(6.dp))
                Text("호스트")
            }
        }

        uiState.appData.hosts.forEach { host ->
            val hostForwards = uiState.appData.forwards.count { it.hostId == host.id }
            Surface(
                modifier = Modifier.fillMaxWidth().clickable { onOpenHost(host.id) },
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(host.name.ifBlank { "이름 없는 호스트" }, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "${host.host.ifBlank { "주소 미입력" }}:${host.port} · ${host.username.ifBlank { "사용자 미입력" }}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            "${if (host.hostKeyFingerprint.isNullOrBlank()) "Fingerprint 미승인" else "Fingerprint 승인됨"} · Keep-alive ${host.keepAliveSeconds}초 · 터널 ${hostForwards}개",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(Icons.Rounded.OpenInNew, contentDescription = "${host.name} 편집")
                }
            }
        }
    }
}

@Composable
private fun ActivityPane(
    uiState: TunnelUiState,
    entries: List<TunnelActivityEntry>,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("실행 로그 / 상태 이력", style = MaterialTheme.typography.titleLarge)
            Text(
                "현재 세션의 최근 상태를 터널별로 확인합니다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (entries.isEmpty()) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Text(
                    "아직 기록된 실행 상태가 없습니다.",
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            entries.forEach { entry ->
                val forward = uiState.appData.forwards.firstOrNull { it.id == entry.forwardId }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(entry.timeLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            "${forward?.name ?: "삭제된 터널"} · ${statusLabel(entry.status)}",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            entry.status.message ?: forward?.pathSummary().orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WidgetSlotsPane(
    uiState: TunnelUiState,
    onManageForward: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("터널 편집 화면에서 각 슬롯의 배정을 변경할 수 있습니다.", style = MaterialTheme.typography.bodyMedium)
        repeat(WidgetSlots.COUNT) { slot ->
            val forward = uiState.appData.forwards.firstOrNull { it.widgetSlot == slot }
            val host = uiState.appData.hosts.firstOrNull { it.id == forward?.hostId }
            WidgetSlotRow(
                slot = slot,
                title = forward?.name ?: "미지정",
                subtitle = if (forward == null) {
                    "배정된 터널이 없습니다"
                } else {
                    "${host?.name.orEmpty()} · ${forward.pathSummary()}"
                },
                onClick = forward?.let { { onManageForward(it.id) } },
            )
        }
    }
}

@Composable
private fun SettingsPane(
    uiState: TunnelUiState,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onCheckUpdate: () -> Unit,
    onShowAppInfo: () -> Unit,
    onManageWidgets: () -> Unit,
) {
    val context = LocalContext.current
    val packageInfo = remember(context) { context.packageManager.getPackageInfo(context.packageName, 0) }
    val versionName = packageInfo.versionName ?: "unknown"
    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        SectionHeading("연결", "실행 중인 터널은 백그라운드에서 유지됩니다")
        Surface(
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("Foreground service", style = MaterialTheme.typography.titleMedium)
                    Text("실행 중 터널을 백그라운드에서 유지", style = MaterialTheme.typography.bodySmall)
                }
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant) {
                    Text("자동", modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp), style = MaterialTheme.typography.labelMedium)
                }
            }
        }

        SectionHeading("홈 화면 위젯", "6개 슬롯에서 자주 쓰는 터널을 바로 전환합니다")
        Surface(shape = RoundedCornerShape(12.dp), border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("6개 슬롯 배정", style = MaterialTheme.typography.titleMedium)
                Text("${uiState.appData.forwards.count { it.widgetSlot != null }}개 슬롯 사용 중", style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = onManageWidgets, modifier = Modifier.fillMaxWidth()) {
                    Text("슬롯 관리")
                }
            }
        }

        SectionHeading("백업 및 복원", "서버 구성은 옮기고 비밀 정보는 기기에 남깁니다")
        Surface(shape = RoundedCornerShape(12.dp), tonalElevation = 1.dp) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                    Text(
                        "내보내기 파일에는 비밀번호와 개인 키가 포함되지 않습니다.",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Button(onClick = onExport, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Icon(Icons.Rounded.Download, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text("안전한 백업 내보내기")
                }
                OutlinedButton(onClick = onImport, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Icon(Icons.Rounded.Upload, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text("백업에서 복원")
                }
            }
        }

        SectionHeading("앱 관리", "GitHub Release 채널 · 포그라운드 서비스 자동 유지")
        ConsoleActionRow(
            Icons.Rounded.Refresh,
            if (uiState.updateState.isChecking) "업데이트 확인 중" else "업데이트 확인",
            "현재 버전 $versionName · GitHub Release 채널",
            onCheckUpdate,
        )
        ConsoleActionRow(Icons.Rounded.Info, "앱 정보", "버전, 제작자, 소스 코드", onShowAppInfo)
    }
}

@Composable
private fun OverviewPane(
    uiState: TunnelUiState,
    onToggleForward: (String) -> Unit,
    onManageForward: (String) -> Unit,
    onAdd: () -> Unit,
    onDisconnectAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val connectedCount = uiState.statuses.values.count { it.state == TunnelConnectionState.CONNECTED }
    val connectingCount = uiState.statuses.values.count { it.state == TunnelConnectionState.CONNECTING }
    val pendingCount = uiState.appData.forwards.count { forward ->
        uiState.statuses[forward.id]?.state != TunnelConnectionState.CONNECTED
    }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("현재 상태", style = MaterialTheme.typography.titleMedium)
                Text(
                    "${connectedCount}개 연결됨 · ${pendingCount}개 대기 중",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedButton(
                onClick = onDisconnectAll,
                enabled = connectedCount + connectingCount > 0,
            ) {
                Text("모두 끄기")
            }
        }

        if (uiState.appData.forwards.isEmpty()) {
            EmptyConsole(onAdd)
        } else {
            uiState.appData.hosts.forEach { host ->
                val forwards = uiState.appData.forwards.filter { it.hostId == host.id }
                if (forwards.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            host.name.ifBlank { "이름 없는 호스트" }.uppercase(),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        forwards.forEach { forward ->
                            TunnelStatusRow(
                                forward = forward,
                                status = uiState.statuses[forward.id],
                                onToggle = { onToggleForward(forward.id) },
                                onOpen = { onManageForward(forward.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AddTunnelPane(
    host: HostProfile?,
    hosts: List<HostProfile>,
    onSelectHost: (String) -> Unit,
    onAdd: (ForwardMode) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("새 터널 만들기", style = MaterialTheme.typography.titleLarge)
            Text(
                "터널 종류에 따라 설정 흐름이 달라집니다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        SectionHeading("SSH 호스트", "새 터널이 사용할 서버를 먼저 선택하세요")
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(hosts, key = { it.id }) { item ->
                FilterChip(
                    selected = item.id == host?.id,
                    onClick = { onSelectHost(item.id) },
                    label = { Text(item.name.ifBlank { "이름 없는 호스트" }) },
                    leadingIcon = { Icon(Icons.Rounded.Cloud, contentDescription = null) },
                )
            }
        }
        TunnelTypeChoice(
            icon = Icons.Rounded.Terminal,
            eyebrow = "LOCAL FORWARD",
            title = "일반 SSH 터널",
            description = "휴대전화의 로컬 포트를 SSH를 거쳐 원격 서비스에 연결합니다.",
            path = "Android :포트  →  SSH  →  원격 주소:포트",
            enabled = host != null,
            onClick = { onAdd(ForwardMode.LOCAL) },
        )
        TunnelTypeChoice(
            icon = Icons.Rounded.Router,
            eyebrow = "ADB CONNECT",
            title = "무선 디버깅 연결",
            description = "현재 휴대전화의 ADB endpoint를 자동 발견해 서버 5555번으로 전달합니다.",
            path = "서버 :5555  →  이 기기의 ADB 포트",
            enabled = host != null,
            onClick = { onAdd(ForwardMode.ADB_CONNECT) },
        )
        TunnelTypeChoice(
            icon = Icons.Rounded.Link,
            eyebrow = "ADB PAIR",
            title = "ADB 페어링",
            description = "Android 페어링 화면이 열릴 때 나타나는 임시 포트를 추적합니다.",
            path = "서버 :5556  →  페어링 임시 포트",
            enabled = host != null,
            onClick = { onAdd(ForwardMode.ADB_PAIRING) },
        )
    }
}

@Composable
private fun TunnelTypeChoice(
    icon: ImageVector,
    eyebrow: String,
    title: String,
    description: String,
    path: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 104.dp)
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(
                modifier = Modifier.size(44.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(eyebrow, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(path, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SectionHeading(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun TunnelStatusRow(
    forward: PortForwardRule,
    status: ForwardStatus?,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 14.dp, end = 8.dp, bottom = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(forward.name.ifBlank { "이름 없는 터널" }, style = MaterialTheme.typography.titleMedium)
                Text(forward.pathSummary(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    status?.message ?: statusLabel(status),
                    style = MaterialTheme.typography.labelMedium,
                    color = statusColor(status),
                )
            }
            Switch(
                checked = status?.state == TunnelConnectionState.CONNECTED || status?.state == TunnelConnectionState.CONNECTING,
                onCheckedChange = { onToggle() },
                modifier = Modifier.semantics {
                    contentDescription = "${forward.name} ${actionLabel(status)}"
                },
            )
        }
    }
}

@Composable
private fun StatusGlyph(status: ForwardStatus?) {
    val icon = when {
        status?.state == TunnelConnectionState.ERROR -> Icons.Rounded.ErrorOutline
        status?.state == TunnelConnectionState.CONNECTED -> Icons.Rounded.CheckCircle
        status?.state == TunnelConnectionState.CONNECTING -> Icons.Rounded.Sync
        else -> Icons.Rounded.CloudOff
    }
    Box(
        modifier = Modifier.size(40.dp).background(statusColor(status).copy(alpha = 0.13f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = statusLabel(status), tint = statusColor(status), modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun statusColor(status: ForwardStatus?): Color = when {
    status?.state == TunnelConnectionState.ERROR -> MaterialTheme.colorScheme.error
    status?.state == TunnelConnectionState.CONNECTED -> MaterialTheme.colorScheme.secondary
    status?.state == TunnelConnectionState.CONNECTING -> MaterialTheme.colorScheme.tertiary
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
private fun EmptyConsole(onAdd: () -> Unit) {
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(Icons.Rounded.CloudOff, contentDescription = null, modifier = Modifier.size(36.dp))
            Text("아직 구성된 터널이 없습니다", style = MaterialTheme.typography.titleMedium)
            Button(onClick = onAdd, modifier = Modifier.heightIn(min = 48.dp)) { Text("첫 터널 만들기") }
        }
    }
}

@Composable
private fun ConsoleActionRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun WidgetSlotRow(
    slot: Int,
    title: String,
    subtitle: String,
    onClick: (() -> Unit)? = null,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(modifier = Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(36.dp).background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text("${slot + 1}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    onDone: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
) {
    var privateKeyExpanded by rememberSaveable(host.id, host.authMode.name) { mutableStateOf(false) }
    var sshPortInput by rememberSaveable(host.id) { mutableStateOf(host.port.toString()) }
    var keepAliveInput by rememberSaveable(host.id) { mutableStateOf(host.keepAliveSeconds.toString()) }

    Card(shape = RoundedCornerShape(12.dp)) {
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
                    label = { Text("PEM 키") },
                    leadingIcon = { Icon(Icons.Rounded.Lock, contentDescription = null) },
                )
                FilterChip(
                    selected = host.authMode == AuthMode.NONE,
                    onClick = { viewModel.setSelectedHostAuthMode(AuthMode.NONE) },
                    label = { Text("없음") },
                )
            }

            when (host.authMode) {
                AuthMode.PASSWORD -> TunnelField(
                    value = host.password,
                    label = "비밀번호",
                    onValueChange = { value -> viewModel.updateSelectedHost { it.copy(password = value) } },
                    isSecret = true,
                    required = true,
                )
                AuthMode.PRIVATE_KEY -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
                            shape = RoundedCornerShape(12.dp),
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
                AuthMode.NONE -> Text(
                    "서버가 인증 없는 SSH 연결을 허용하는 경우에만 사용할 수 있습니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            TunnelNumberField(keepAliveInput, "연결 유지 간격(초)", maxValue = 86_400) { value ->
                keepAliveInput = value
                viewModel.updateSelectedHost { current -> current.copy(keepAliveSeconds = value.toIntOrNull() ?: 0) }
            }
            onDone?.let {
                Button(onClick = it, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text("저장")
                }
            }
            onDelete?.let {
                TextButton(onClick = it, modifier = Modifier.fillMaxWidth()) {
                    Text("호스트 삭제", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun AdbDetailPane(
    host: HostProfile,
    forward: PortForwardRule,
    status: ForwardStatus?,
    onToggle: () -> Unit,
    onAssignWidget: (Int?) -> Unit,
    onDelete: () -> Unit,
) {
    val isActive = status?.state == TunnelConnectionState.CONNECTED ||
        status?.state == TunnelConnectionState.CONNECTING
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(forward.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${host.name} · 서버 포트 ${forward.reverseBindPort}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(4.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Column(modifier = Modifier.padding(start = 14.dp, top = 12.dp, end = 14.dp, bottom = 12.dp)) {
                Text("현재 상태: ${statusLabel(status)}", style = MaterialTheme.typography.titleMedium)
                status?.message?.takeIf { it.isNotBlank() }?.let { message ->
                    Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        AdbOperationsPanel(forward = forward, status = status)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("위젯 슬롯", style = MaterialTheme.typography.titleMedium)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    FilterChip(
                        selected = forward.widgetSlot == null,
                        onClick = { onAssignWidget(null) },
                        label = { Text("미지정") },
                    )
                }
                items((0 until WidgetSlots.COUNT).toList()) { slot ->
                    FilterChip(
                        selected = forward.widgetSlot == slot,
                        onClick = { onAssignWidget(slot) },
                        label = { Text("${slot + 1}") },
                    )
                }
            }
        }
        Button(onClick = onToggle, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text(
                when {
                    isActive -> "터널 끄기"
                    forward.mode == ForwardMode.ADB_PAIRING -> "시작 / 다시 검색"
                    else -> "터널 켜기"
                }
            )
        }
        TextButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) {
            Text("터널 삭제", color = MaterialTheme.colorScheme.error)
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
    onDone: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
) {
    var localPortInput by rememberSaveable(forward.id, "local") { mutableStateOf(forward.localPort.toString()) }
    var remotePortInput by rememberSaveable(forward.id, "remote") { mutableStateOf(forward.remotePort.toString()) }

    Card(shape = RoundedCornerShape(12.dp)) {
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

            OutlinedTextField(
                value = host.name,
                onValueChange = {},
                modifier = Modifier.fillMaxWidth(),
                label = { Text("SSH 호스트") },
                readOnly = true,
                singleLine = true,
            )
            TunnelField(
                value = forward.name,
                label = "포워딩 이름",
                onValueChange = { value ->
                    viewModel.updateSelectedForward { it.copy(name = value) }
                },
                required = true,
            )
            TunnelNumberField(localPortInput, "Android 로컬 포트") { value ->
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
                onDone?.let {
                    Button(onClick = it, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Text("저장")
                    }
                }
                OutlinedButton(onClick = { onToggleForward(forward.id) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.Link, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text(
                        if (status?.state != TunnelConnectionState.CONNECTED) {
                            "저장 후 연결"
                        } else {
                            actionLabel(status)
                        }
                    )
                }
                TextButton(onClick = { viewModel.assignWidgetSlot(null) }, modifier = Modifier.fillMaxWidth()) {
                    Text("위젯 해제")
                }
                onDelete?.let {
                    TextButton(onClick = it, modifier = Modifier.fillMaxWidth()) {
                        Text("터널 삭제", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@Composable
private fun AdbOperationsPanel(forward: PortForwardRule, status: ForwardStatus?) {
    val isPairing = forward.mode == ForwardMode.ADB_PAIRING
    val steps = if (isPairing) {
        listOf(
            "무선 디버깅 설정 열기" to "개발자 옵션 → 무선 디버깅",
            "페어링 코드 화면 유지" to "앱은 페어링 코드 자체를 읽거나 저장하지 않습니다",
            "페어링 endpoint 검색" to "_adb-tls-pairing._tcp 서비스를 기다립니다",
            "SSH reverse forwarding" to "127.0.0.1:${forward.reverseBindPort}에 안전하게 바인딩",
        )
    } else {
        listOf(
            "Wi-Fi ADB 검색" to "_adb-tls-connect._tcp 서비스 탐색",
            "내 기기 endpoint 확인" to "주소와 열린 TCP 포트를 검사",
            "SSH 서버 인증" to "저장된 host key fingerprint와 대조",
            "Reverse forwarding" to "127.0.0.1:${forward.reverseBindPort} → 휴대전화",
        )
    }
    val activeIndex = when (status?.phase) {
        TunnelPhase.WAITING_FOR_PERMISSION, TunnelPhase.WAITING_FOR_WIFI, TunnelPhase.DISCOVERING_ADB -> if (isPairing) 1 else 0
        TunnelPhase.PROBING_ADB, TunnelPhase.WAITING_FOR_PAIRING -> 2
        TunnelPhase.CONNECTING_SSH -> 2
        TunnelPhase.BINDING_FORWARD, TunnelPhase.CONNECTED -> 3
        TunnelPhase.RECONNECTING -> 2
        TunnelPhase.ERROR, TunnelPhase.IDLE, null -> -1
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatusGlyph(status)
                Column {
                    Text(if (isPairing) "페어링 작업 흐름" else "자동 연결 단계", style = MaterialTheme.typography.titleMedium)
                    Text(status?.message ?: "시작하면 endpoint 탐색을 자동으로 진행합니다", style = MaterialTheme.typography.bodySmall)
                }
            }
            steps.forEachIndexed { index, (title, detail) ->
                val completed = status?.state == TunnelConnectionState.CONNECTED || (activeIndex > index)
                val active = activeIndex == index
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .background(
                                if (completed || active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                CircleShape,
                            )
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(if (completed) "✓" else "${index + 1}", style = MaterialTheme.typography.labelMedium)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(title, style = MaterialTheme.typography.titleSmall)
                        Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (active) Text("현재 단계", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary)
                    }
                }
            }
            if (isPairing) {
                Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.tertiaryContainer) {
                    Text(
                        "페어링 화면을 닫으면 임시 포트가 사라집니다. SSH 세션은 유지하며 새 포트가 나타날 때 자동으로 이어집니다.",
                        modifier = Modifier.padding(14.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                }
            }
            Text("서버에서 실행", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            SelectionContainer {
                Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surface) {
                    Text(
                        if (isPairing) "adb pair 127.0.0.1:${forward.reverseBindPort}" else "adb connect 127.0.0.1:${forward.reverseBindPort}",
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
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
    ForwardMode.LOCAL -> "LOCAL · :$localPort → $remoteHost:$remotePort"
    ForwardMode.ADB_CONNECT -> "REVERSE · 서버 :$reverseBindPort"
    ForwardMode.ADB_PAIRING -> "REVERSE · 서버 :$reverseBindPort"
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
