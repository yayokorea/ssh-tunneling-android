package com.yayo.sshtunneling.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Router
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yayo.sshtunneling.model.AuthMode
import com.yayo.sshtunneling.model.TunnelConnectionState

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun SshTunnelingApp(
    viewModel: TunnelViewModel,
    isExpanded: Boolean,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.status.state, uiState.status.message) {
        val errorMessage = uiState.status.message
        if (uiState.status.state == TunnelConnectionState.ERROR && !errorMessage.isNullOrBlank()) {
            snackbarHostState.showSnackbar(errorMessage)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "SSH Tunneling") },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            if (isExpanded) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    TunnelStatusPane(
                        uiState = uiState,
                        onToggleTunnel = viewModel::toggleTunnel,
                        onDisconnect = viewModel::disconnectTunnel,
                        modifier = Modifier.weight(0.95f),
                    )
                    TunnelEditorPane(
                        uiState = uiState,
                        viewModel = viewModel,
                        modifier = Modifier.weight(1.3f),
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    item {
                        TunnelStatusPane(
                            uiState = uiState,
                            onToggleTunnel = viewModel::toggleTunnel,
                            onDisconnect = viewModel::disconnectTunnel,
                        )
                    }
                    item {
                        TunnelEditorPane(
                            uiState = uiState,
                            viewModel = viewModel,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TunnelStatusPane(
    uiState: TunnelUiState,
    onToggleTunnel: () -> Unit,
    onDisconnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = when (uiState.status.state) {
                TunnelConnectionState.CONNECTED -> MaterialTheme.colorScheme.primaryContainer
                TunnelConnectionState.CONNECTING -> MaterialTheme.colorScheme.secondaryContainer
                TunnelConnectionState.ERROR -> MaterialTheme.colorScheme.errorContainer
                TunnelConnectionState.IDLE -> MaterialTheme.colorScheme.surfaceVariant
            },
        ),
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
                    Icon(
                        imageVector = Icons.Rounded.Router,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = when (uiState.status.state) {
                            TunnelConnectionState.CONNECTED -> "터널 연결됨"
                            TunnelConnectionState.CONNECTING -> "터널 연결 중"
                            TunnelConnectionState.ERROR -> "연결 실패"
                            TunnelConnectionState.IDLE -> "대기 중"
                        },
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Text(
                        text = uiState.status.message ?: "홈 화면 위젯을 추가하면 한 번 눌러 바로 토글할 수 있습니다.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Button(
                onClick = onToggleTunnel,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Rounded.PowerSettingsNew, contentDescription = null)
                Spacer(modifier = Modifier.size(8.dp))
                Text(if (uiState.status.state == TunnelConnectionState.CONNECTED) "터널 끊기" else "터널 연결")
            }

            if (uiState.status.state == TunnelConnectionState.CONNECTED) {
                FilledTonalButton(
                    onClick = onDisconnect,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("서비스 종료")
                }
            }
        }
    }
}

@Composable
private fun TunnelEditorPane(
    uiState: TunnelUiState,
    viewModel: TunnelViewModel,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "터널 프로필",
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = "저장한 설정은 위젯과 포그라운드 서비스가 그대로 사용합니다.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HorizontalDivider()

            TunnelField(
                value = uiState.profile.host,
                label = "SSH Host",
                onValueChange = { value -> viewModel.updateProfile { it.copy(host = value.trim()) } },
            )
            TunnelNumberField(
                value = uiState.profile.port.toString(),
                label = "SSH Port",
                onValueChange = { value -> value.toIntOrNull()?.let { port -> viewModel.updateProfile { current -> current.copy(port = port) } } },
            )
            TunnelField(
                value = uiState.profile.username,
                label = "Username",
                onValueChange = { value -> viewModel.updateProfile { it.copy(username = value.trim()) } },
            )
            AuthModeChips(
                selectedMode = uiState.profile.authMode,
                onSelected = viewModel::setAuthMode,
            )

            if (uiState.profile.authMode == AuthMode.PASSWORD) {
                TunnelField(
                    value = uiState.profile.password,
                    label = "Password",
                    onValueChange = { value -> viewModel.updateProfile { it.copy(password = value) } },
                    isSecret = true,
                )
            } else {
                TunnelField(
                    value = uiState.profile.privateKey,
                    label = "Private Key (PEM)",
                    onValueChange = { value -> viewModel.updateProfile { it.copy(privateKey = value) } },
                    singleLine = false,
                )
            }

            TunnelNumberField(
                value = uiState.profile.localPort.toString(),
                label = "Local Port",
                onValueChange = { value -> value.toIntOrNull()?.let { port -> viewModel.updateProfile { current -> current.copy(localPort = port) } } },
            )
            TunnelField(
                value = uiState.profile.remoteHost,
                label = "Remote Host",
                onValueChange = { value -> viewModel.updateProfile { it.copy(remoteHost = value.trim()) } },
            )
            TunnelNumberField(
                value = uiState.profile.remotePort.toString(),
                label = "Remote Port",
                onValueChange = { value -> value.toIntOrNull()?.let { port -> viewModel.updateProfile { current -> current.copy(remotePort = port) } } },
            )
            TunnelNumberField(
                value = uiState.profile.keepAliveSeconds.toString(),
                label = "Keep Alive Seconds",
                onValueChange = { value -> value.toIntOrNull()?.let { seconds -> viewModel.updateProfile { current -> current.copy(keepAliveSeconds = seconds) } } },
            )

            FilledTonalButton(
                onClick = viewModel::saveProfile,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Rounded.Save, contentDescription = null)
                Spacer(modifier = Modifier.size(8.dp))
                Text("프로필 저장")
            }

            Text(
                text = "위젯에서는 마지막으로 저장된 프로필로 바로 연결합니다.",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AuthModeChips(
    selectedMode: AuthMode,
    onSelected: (AuthMode) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = selectedMode == AuthMode.PASSWORD,
            onClick = { onSelected(AuthMode.PASSWORD) },
            label = { Text("Password") },
            leadingIcon = { Icon(Icons.Rounded.Lock, contentDescription = null) },
        )
        FilterChip(
            selected = selectedMode == AuthMode.PRIVATE_KEY,
            onClick = { onSelected(AuthMode.PRIVATE_KEY) },
            label = { Text("Private Key") },
            leadingIcon = { Icon(Icons.Rounded.Lock, contentDescription = null) },
        )
    }
}

@Composable
private fun TunnelField(
    value: String,
    label: String,
    onValueChange: (String) -> Unit,
    singleLine: Boolean = true,
    isSecret: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 6,
        visualTransformation = if (isSecret) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
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
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Number,
            imeAction = ImeAction.Next,
        ),
    )
}
