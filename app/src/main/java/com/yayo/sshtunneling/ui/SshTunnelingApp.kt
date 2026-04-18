package com.yayo.sshtunneling.ui

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Router
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import com.yayo.sshtunneling.R
import com.yayo.sshtunneling.model.AuthMode
import com.yayo.sshtunneling.model.ForwardStatus
import com.yayo.sshtunneling.model.HostProfile
import com.yayo.sshtunneling.model.PortForwardRule
import com.yayo.sshtunneling.model.TunnelConnectionState
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
    var menuExpanded by rememberSaveable { mutableStateOf(false) }
    var showAppInfo by rememberSaveable { mutableStateOf(false) }
    var pendingImportJson by remember { mutableStateOf<String?>(null) }

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
                title = { Text("SSH Tunneling") },
                actions = {
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Rounded.MoreVert, contentDescription = "More options")
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("설정 내보내기") },
                                leadingIcon = { Icon(Icons.Rounded.Download, contentDescription = null) },
                                onClick = {
                                    menuExpanded = false
                                    exportLauncher.launch("ssh-tunneling-settings.json")
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("설정 불러오기") },
                                leadingIcon = { Icon(Icons.Rounded.Upload, contentDescription = null) },
                                onClick = {
                                    menuExpanded = false
                                    importLauncher.launch(arrayOf("application/json", "text/*"))
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("업데이트 확인") },
                                leadingIcon = { Icon(Icons.Rounded.Refresh, contentDescription = null) },
                                onClick = {
                                    menuExpanded = false
                                    viewModel.checkForAppUpdate(force = true)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("앱 정보") },
                                leadingIcon = { Icon(Icons.Rounded.Info, contentDescription = null) },
                                onClick = {
                                    menuExpanded = false
                                    showAppInfo = true
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("GitHub 보기") },
                                leadingIcon = { Icon(Icons.Rounded.OpenInNew, contentDescription = null) },
                                onClick = {
                                    menuExpanded = false
                                    uriHandler.openUri(context.getString(R.string.github_repository_url))
                                },
                            )
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { innerPadding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            if (isExpanded) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    item {
                        OverviewPane(uiState = uiState, viewModel = viewModel)
                    }
                    item {
                        EditorPane(uiState = uiState, viewModel = viewModel)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    item {
                        OverviewPane(uiState = uiState, viewModel = viewModel)
                    }
                    item {
                        EditorPane(uiState = uiState, viewModel = viewModel)
                    }
                }
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
                    .onFailure { error ->
                        scope.launch { snackbarHostState.showSnackbar(error.message ?: context.getString(R.string.import_invalid_file)) }
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
}

@Composable
private fun OverviewPane(
    uiState: TunnelUiState,
    viewModel: TunnelViewModel,
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
                                append(forward.localPort)
                                append(" → ")
                                append(forward.remoteHost)
                                append(":")
                                append(forward.remotePort)
                                append(" · ")
                                append(statusLabel(status))
                            }
                        } else {
                            "앱에서 포워딩을 선택해 이 슬롯에 배치하세요."
                        },
                    )
                }
            }

            FilledTonalButton(
                onClick = {
                    uiState.selectedForwardId?.let(viewModel::toggleForward)
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Rounded.PowerSettingsNew, contentDescription = null)
                Spacer(modifier = Modifier.size(8.dp))
                Text("선택한 포워딩 토글")
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
                    onDeleteHost = viewModel::deleteSelectedHost,
                )
            }

            selectedHost?.let { host ->
                item {
                    HostEditorCard(host = host, viewModel = viewModel)
                }

                item {
                    ForwardListCard(
                        forwards = hostForwards,
                        statuses = uiState.statuses,
                        selectedForwardId = selectedForward?.id,
                        onSelectForward = viewModel::selectForward,
                        onAddForward = viewModel::addForward,
                        onToggleForward = viewModel::toggleForward,
                        onDeleteForward = viewModel::deleteSelectedForward,
                    )
                }

                selectedForward?.let { forward ->
                    item {
                        ForwardEditorCard(
                            host = host,
                            forward = forward,
                            status = uiState.statuses[forward.id],
                            viewModel = viewModel,
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
                onDeleteHost = viewModel::deleteSelectedHost,
            )

            selectedHost?.let { host ->
                HostEditorCard(host = host, viewModel = viewModel)
                ForwardListCard(
                    forwards = hostForwards,
                    statuses = uiState.statuses,
                    selectedForwardId = selectedForward?.id,
                    onSelectForward = viewModel::selectForward,
                    onAddForward = viewModel::addForward,
                    onToggleForward = viewModel::toggleForward,
                    onDeleteForward = viewModel::deleteSelectedForward,
                )
                selectedForward?.let { forward ->
                    ForwardEditorCard(
                        host = host,
                        forward = forward,
                        status = uiState.statuses[forward.id],
                        viewModel = viewModel,
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
                    TextButton(onClick = onDeleteHost) {
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
    viewModel: TunnelViewModel,
) {
    var privateKeyExpanded by rememberSaveable(host.id, host.authMode.name) { mutableStateOf(false) }
    var sshPortInput by rememberSaveable(host.id, host.port) { mutableStateOf(host.port.toString()) }
    var keepAliveInput by rememberSaveable(host.id, host.keepAliveSeconds) { mutableStateOf(host.keepAliveSeconds.toString()) }

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
                label = "Host Label",
                onValueChange = { value ->
                    viewModel.updateSelectedHost { it.copy(name = value) }
                },
            )
            TunnelField(
                value = host.host,
                label = "SSH Host",
                onValueChange = { value ->
                    viewModel.updateSelectedHost { it.copy(host = value.trim()) }
                },
            )
            TunnelNumberField(sshPortInput, "SSH Port") { value ->
                sshPortInput = value
                value.toIntOrNull()?.let { port -> viewModel.updateSelectedHost { current -> current.copy(port = port) } }
            }
            TunnelField(
                value = host.username,
                label = "Username",
                onValueChange = { value ->
                    viewModel.updateSelectedHost { it.copy(username = value.trim()) }
                },
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = host.authMode == AuthMode.PASSWORD,
                    onClick = { viewModel.setSelectedHostAuthMode(AuthMode.PASSWORD) },
                    label = { Text("Password") },
                    leadingIcon = { Icon(Icons.Rounded.Lock, contentDescription = null) },
                )
                FilterChip(
                    selected = host.authMode == AuthMode.PRIVATE_KEY,
                    onClick = { viewModel.setSelectedHostAuthMode(AuthMode.PRIVATE_KEY) },
                    label = { Text("Private Key") },
                    leadingIcon = { Icon(Icons.Rounded.Lock, contentDescription = null) },
                )
            }

            if (host.authMode == AuthMode.PASSWORD) {
                TunnelField(
                    value = host.password,
                    label = "Password",
                    onValueChange = { value ->
                        viewModel.updateSelectedHost { it.copy(password = value) }
                    },
                    isSecret = true,
                )
            } else {
                if (privateKeyExpanded) {
                    TunnelField(
                        value = host.privateKey,
                        label = "Private Key (PEM)",
                        onValueChange = { value -> viewModel.updateSelectedHost { it.copy(privateKey = value) } },
                        singleLine = false,
                        maxLinesWhenExpanded = 8,
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
                            Text("Private Key (PEM)", style = MaterialTheme.typography.titleSmall)
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
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = host.privateKey.previewLabel(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = { privateKeyExpanded = !privateKeyExpanded }) {
                        Text(if (privateKeyExpanded) "접기" else "펼치기")
                    }
                }
            }

            TunnelNumberField(keepAliveInput, "Keep Alive Seconds") { value ->
                keepAliveInput = value
                value.toIntOrNull()?.let { seconds -> viewModel.updateSelectedHost { current -> current.copy(keepAliveSeconds = seconds) } }
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
                    TextButton(onClick = onDeleteForward) {
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
                            Text(forward.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${forward.localPort} → ${forward.remoteHost}:${forward.remotePort} · ${statusLabel(status)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        FilledTonalButton(onClick = { onToggleForward(forward.id) }) {
                            Icon(Icons.Rounded.PowerSettingsNew, contentDescription = null)
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
) {
    var localPortInput by rememberSaveable(forward.id, forward.localPort) { mutableStateOf(forward.localPort.toString()) }
    var remotePortInput by rememberSaveable(forward.id, forward.remotePort) { mutableStateOf(forward.remotePort.toString()) }

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

            TunnelField(
                value = forward.name,
                label = "Forward Label",
                onValueChange = { value ->
                    viewModel.updateSelectedForward { it.copy(name = value) }
                },
            )
            TunnelNumberField(localPortInput, "Local Port") { value ->
                localPortInput = value
                value.toIntOrNull()?.let { port -> viewModel.updateSelectedForward { current -> current.copy(localPort = port) } }
            }
            TunnelField(
                value = forward.remoteHost,
                label = "Remote Host",
                onValueChange = { value ->
                    viewModel.updateSelectedForward { it.copy(remoteHost = value.trim()) }
                },
            )
            TunnelNumberField(remotePortInput, "Remote Port") { value ->
                remotePortInput = value
                value.toIntOrNull()?.let { port -> viewModel.updateSelectedForward { current -> current.copy(remotePort = port) } }
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
                Button(onClick = { viewModel.toggleForward(forward.id) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.Link, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text("토글")
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
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = singleLine,
        minLines = if (singleLine) 1 else maxLinesWhenExpanded,
        maxLines = if (singleLine) 1 else maxLinesWhenExpanded,
        visualTransformation = if (isSecret) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Next),
    )
}

@Composable
private fun TunnelNumberField(
    value: String,
    label: String,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { input ->
            onValueChange(input.filter { it.isDigit() })
        },
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
    )
}

private fun statusLabel(status: ForwardStatus?): String {
    return when (status?.state) {
        TunnelConnectionState.CONNECTED -> "연결됨"
        TunnelConnectionState.CONNECTING -> "연결 중"
        TunnelConnectionState.ERROR -> "실패"
        TunnelConnectionState.IDLE, null -> "대기"
    }
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
                    contentDescription = "Yayo credit image",
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(24.dp))
                        .padding(24.dp),
                    contentScale = ContentScale.Fit,
                )
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Built by Yayo", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "SSH Tunneling for one-tap multi-host forwarding.",
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
                    Text("GitHub Repository")
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
            Text("현재 호스트, 포워딩, 위젯 슬롯, 비밀번호, private key를 모두 새 파일 내용으로 교체합니다.")
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
