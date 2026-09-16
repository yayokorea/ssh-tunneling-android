package com.yayo.sshtunneling.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
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
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.PowerSettingsNew
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
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
                title = {
                    Column {
                        Text(selectedSection.label, style = MaterialTheme.typography.titleLarge)
                        Text(
                            selectedSection.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    if (uiState.statuses.values.any {
                            it.state == TunnelConnectionState.CONNECTED || it.state == TunnelConnectionState.CONNECTING
                        }
                    ) {
                        IconButton(onClick = viewModel::disconnectAll) {
                            Icon(Icons.Rounded.PowerSettingsNew, contentDescription = "모든 터널 연결 해제")
                        }
                    }
                },
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
                        onSectionSelected = { selectedSection = it },
                        isExpanded = true,
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
                    onSectionSelected = { selectedSection = it },
                    isExpanded = false,
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

private enum class AppSection(val label: String, val subtitle: String) {
    HOME("터널", "실시간 연결 상태"),
    HOSTS("호스트", "서버 · 인증 · 터널 구성"),
    ADD("추가", "새 연결 유형 선택"),
    SETTINGS("설정", "위젯 · 백업 · 업데이트"),
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
    onSectionSelected: (AppSection) -> Unit,
    isExpanded: Boolean,
) {
    val useTwoPane = isExpanded && LocalConfiguration.current.screenWidthDp >= 840
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        when (section) {
            AppSection.HOME -> item {
                OverviewPane(
                    uiState = uiState,
                    onToggleForward = onToggleForward,
                    onManageForward = { forwardId ->
                        uiState.appData.forwards.firstOrNull { it.id == forwardId }?.let { viewModel.selectHost(it.hostId) }
                        viewModel.selectForward(forwardId)
                        onSectionSelected(AppSection.HOSTS)
                    },
                    onAdd = { onSectionSelected(AppSection.ADD) },
                    onDisconnectAll = viewModel::disconnectAll,
                )
            }
            AppSection.HOSTS -> item {
                EditorPane(
                    uiState = uiState,
                    viewModel = viewModel,
                    onToggleForward = onToggleForward,
                    expanded = useTwoPane,
                )
            }
            AppSection.ADD -> item {
                AddTunnelPane(
                    host = uiState.appData.hosts.firstOrNull { it.id == uiState.selectedHostId },
                    onSelectHost = viewModel::selectHost,
                    hosts = uiState.appData.hosts,
                    onAdd = { mode ->
                        viewModel.addForward(mode)
                        onSectionSelected(AppSection.HOSTS)
                    },
                )
            }
            AppSection.SETTINGS -> item {
                SettingsPane(
                    uiState = uiState,
                    onExport = onExport,
                    onImport = onImport,
                    onCheckUpdate = onCheckUpdate,
                    onShowAppInfo = onShowAppInfo,
                    onManageForward = { forwardId ->
                        uiState.appData.forwards.firstOrNull { it.id == forwardId }?.let { forward ->
                            viewModel.selectHost(forward.hostId)
                            viewModel.selectForward(forwardId)
                            onSectionSelected(AppSection.HOSTS)
                        }
                    },
                )
            }
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
    onManageForward: (String) -> Unit,
) {
    val context = LocalContext.current
    val packageInfo = remember(context) { context.packageManager.getPackageInfo(context.packageName, 0) }
    val versionName = packageInfo.versionName ?: "unknown"
    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        SectionHeading("홈 화면 위젯", "6개 슬롯에서 자주 쓰는 터널을 바로 전환합니다")
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                repeat(WidgetSlots.COUNT) { slot ->
                    val forward = uiState.appData.forwards.firstOrNull { it.widgetSlot == slot }
                    val host = uiState.appData.hosts.firstOrNull { it.id == forward?.hostId }
                    WidgetSlotRow(
                        slot = slot,
                        title = forward?.name ?: "미지정",
                        subtitle = if (forward == null) "터널 편집에서 이 슬롯을 배정하세요" else "${host?.name.orEmpty()} · ${forward.pathSummary()}",
                        onClick = forward?.let { { onManageForward(it.id) } },
                    )
                }
            }
        }

        SectionHeading("백업 및 복원", "서버 구성은 옮기고 비밀 정보는 기기에 남깁니다")
        Surface(shape = RoundedCornerShape(24.dp), tonalElevation = 1.dp) {
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
    val errorCount = uiState.statuses.values.count { it.state == TunnelConnectionState.ERROR }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(30.dp))
                .background(
                    Brush.linearGradient(
                        listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary),
                    )
                )
                .padding(24.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(
                        modifier = Modifier.size(52.dp).background(Color.White.copy(alpha = 0.16f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Rounded.Router, contentDescription = null, tint = Color.White)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("네트워크 콘솔", color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelMedium)
                        Text(
                            if (connectedCount > 0) "$connectedCount개 경로가 열려 있습니다" else "모든 경로가 대기 중입니다",
                            color = Color.White,
                            style = MaterialTheme.typography.headlineSmall,
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HeroMetric("연결", connectedCount, Icons.Rounded.CheckCircle)
                    HeroMetric("진행", connectingCount, Icons.Rounded.Sync)
                    HeroMetric("오류", errorCount, Icons.Rounded.ErrorOutline)
                }
                if (connectedCount + connectingCount > 0) {
                    OutlinedButton(
                        onClick = onDisconnectAll,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    ) {
                        Icon(Icons.Rounded.PowerSettingsNew, contentDescription = null, tint = Color.White)
                        Spacer(Modifier.size(8.dp))
                        Text("모든 세션 종료", color = Color.White)
                    }
                }
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            SectionHeading("터널 상태", "호스트별 실시간 세션")
            TextButton(onClick = onAdd, modifier = Modifier.heightIn(min = 48.dp)) {
                Icon(Icons.Rounded.Add, contentDescription = null)
                Spacer(Modifier.size(4.dp))
                Text("새 터널")
            }
        }

        if (uiState.appData.forwards.isEmpty()) {
            EmptyConsole(onAdd)
        } else {
            uiState.appData.hosts.forEach { host ->
                val forwards = uiState.appData.forwards.filter { it.hostId == host.id }
                if (forwards.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(host.name.ifBlank { "이름 없는 호스트" }, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

        SectionHeading("실행 상태", "아이콘과 문구로 연결 단계를 구분합니다")
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                uiState.appData.forwards.mapNotNull { forward ->
                    uiState.statuses[forward.id]?.takeIf { it.state != TunnelConnectionState.IDLE }?.let { forward to it }
                }.ifEmpty {
                    emptyList()
                }.forEach { (forward, status) ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        StatusGlyph(status)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(forward.name, style = MaterialTheme.typography.titleSmall)
                            Text(status.message ?: statusLabel(status), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                if (uiState.statuses.values.none { it.state != TunnelConnectionState.IDLE }) {
                    Text("진행 중인 작업이 없습니다. 연결을 시작하면 단계별 상태가 여기에 표시됩니다.", style = MaterialTheme.typography.bodyMedium)
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
    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("어떤 경로를 열까요?", style = MaterialTheme.typography.headlineLarge)
            Text(
                "목적에 맞는 유형을 고르면 필요한 설정만 보여드립니다.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        SectionHeading("SSH 호스트", "새 터널이 사용할 서버")
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
            .heightIn(min = 156.dp)
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (enabled) 0.6f else 0.3f),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(modifier = Modifier.padding(20.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(
                modifier = Modifier.size(52.dp).background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(eyebrow, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
                Text(title, style = MaterialTheme.typography.titleLarge)
                Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(path, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
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
private fun HeroMetric(label: String, count: Int, icon: ImageVector) {
    Row(
        modifier = Modifier
            .background(Color.White.copy(alpha = 0.14f), RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
        Text("$label $count", style = MaterialTheme.typography.labelLarge, color = Color.White)
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
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 14.dp, end = 8.dp, bottom = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatusGlyph(status)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(forward.name.ifBlank { "이름 없는 터널" }, style = MaterialTheme.typography.titleMedium)
                Text(forward.pathSummary(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(statusLabel(status), style = MaterialTheme.typography.labelMedium, color = statusColor(status))
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
        shape = RoundedCornerShape(18.dp),
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
        shape = RoundedCornerShape(16.dp),
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
private fun EditorPane(
    uiState: TunnelUiState,
    viewModel: TunnelViewModel,
    onToggleForward: (String) -> Unit,
    expanded: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val selectedHost = uiState.appData.hosts.firstOrNull { it.id == uiState.selectedHostId }
    val hostForwards = uiState.appData.forwards.filter { it.hostId == selectedHost?.id }
    val selectedForward = hostForwards.firstOrNull { it.id == uiState.selectedForwardId } ?: hostForwards.firstOrNull()

    if (expanded) {
        Row(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(0.8f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                HostSelectorCard(
                    uiState = uiState,
                    onSelectHost = viewModel::selectHost,
                    onAddHost = viewModel::addHost,
                    onDeleteHost = viewModel::requestDeleteSelectedHost,
                )
                selectedHost?.let {
                    ForwardListCard(
                        forwards = hostForwards,
                        statuses = uiState.statuses,
                        selectedForwardId = selectedForward?.id,
                        onSelectForward = viewModel::selectForward,
                        onAddForward = { viewModel.addForward() },
                        onToggleForward = onToggleForward,
                        onDeleteForward = viewModel::requestDeleteSelectedForward,
                    )
                }
            }
            selectedHost?.let { host ->
                Column(modifier = Modifier.weight(1.2f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    HostEditorCard(
                        host = host,
                        requiresHostKey = hostForwards.any { it.mode != ForwardMode.LOCAL },
                        verification = uiState.hostKeyVerification,
                        viewModel = viewModel,
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
                ForwardListCard(
                    forwards = hostForwards,
                    statuses = uiState.statuses,
                    selectedForwardId = selectedForward?.id,
                    onSelectForward = viewModel::selectForward,
                    onAddForward = { viewModel.addForward() },
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
                HostEditorCard(
                    host = host,
                    requiresHostKey = hostForwards.any { it.mode != ForwardMode.LOCAL },
                    verification = uiState.hostKeyVerification,
                    viewModel = viewModel,
                )
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
                AdbOperationsPanel(forward = forward, status = status)
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
        shape = RoundedCornerShape(22.dp),
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
