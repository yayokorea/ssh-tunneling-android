package com.yayo.sshtunneling.ui

import android.app.Application
import com.yayo.sshtunneling.R
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yayo.sshtunneling.data.TunnelPreferences
import com.yayo.sshtunneling.model.AuthMode
import com.yayo.sshtunneling.model.ForwardMode
import com.yayo.sshtunneling.model.ForwardStatus
import com.yayo.sshtunneling.model.HostProfile
import com.yayo.sshtunneling.model.PortForwardRule
import com.yayo.sshtunneling.model.TunnelAppData
import com.yayo.sshtunneling.model.TunnelDataValidation
import com.yayo.sshtunneling.model.WidgetSlots
import com.yayo.sshtunneling.service.TunnelForegroundService
import com.yayo.sshtunneling.service.TunnelRuntime
import com.yayo.sshtunneling.service.SshHostKeyProbe
import com.yayo.sshtunneling.update.AppUpdateState
import com.yayo.sshtunneling.update.GitHubReleaseUpdater
import com.yayo.sshtunneling.widget.TunnelWidgetProvider
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class TunnelUiState(
    val appData: TunnelAppData = TunnelAppData(),
    val statuses: Map<String, ForwardStatus> = emptyMap(),
    val selectedHostId: String? = null,
    val selectedForwardId: String? = null,
    val updateState: AppUpdateState = AppUpdateState(),
    val hostKeyVerification: HostKeyVerificationState = HostKeyVerificationState(),
    val deletionConfirmation: DeletionConfirmation? = null,
)

data class HostKeyVerificationState(
    val isChecking: Boolean = false,
    val hostId: String? = null,
    val endpoint: String? = null,
    val fingerprint: String? = null,
    val errorMessage: String? = null,
    val pendingForwardId: String? = null,
)

enum class DeleteTarget { HOST, FORWARD }

data class DeletionConfirmation(
    val target: DeleteTarget,
    val label: String,
)

private data class TunnelEditorState(
    val appData: TunnelAppData,
    val selectedHostId: String?,
    val selectedForwardId: String?,
)

class TunnelViewModel(application: Application) : AndroidViewModel(application) {
    private val preferences = TunnelPreferences(application)
    private val updater = GitHubReleaseUpdater(application)
    private val editorState = MutableStateFlow(loadInitialState())
    private val updateState = MutableStateFlow(AppUpdateState())
    private val hostKeyVerification = MutableStateFlow(HostKeyVerificationState())
    private val deletionConfirmation = MutableStateFlow<DeletionConfirmation?>(null)
    private var hostKeyProbeJob: Job? = null
    private val hostKeyProbeRequestId = AtomicInteger()
    private var hasCheckedForUpdate = false

    val uiState: StateFlow<TunnelUiState>

    init {
        TunnelRuntime.initialize(application)
        uiState = combine(
            editorState,
            TunnelRuntime.statuses,
            updateState,
            combine(hostKeyVerification, deletionConfirmation) { hostKey, deletion -> hostKey to deletion },
        ) { editor, statuses, appUpdate, confirmations ->
            TunnelUiState(
                appData = editor.appData,
                statuses = statuses,
                selectedHostId = editor.selectedHostId,
                selectedForwardId = editor.selectedForwardId,
                updateState = appUpdate,
                hostKeyVerification = confirmations.first,
                deletionConfirmation = confirmations.second,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = TunnelUiState(
                appData = editorState.value.appData,
                statuses = TunnelRuntime.statuses.value,
                selectedHostId = editorState.value.selectedHostId,
                selectedForwardId = editorState.value.selectedForwardId,
                updateState = updateState.value,
                hostKeyVerification = hostKeyVerification.value,
                deletionConfirmation = deletionConfirmation.value,
            ),
        )
    }

    fun checkForAppUpdate(force: Boolean = false) {
        if (hasCheckedForUpdate && !force) return
        hasCheckedForUpdate = true
        updateState.value = updateState.value.copy(isChecking = true, errorMessage = null, statusMessage = null)

        viewModelScope.launch {
            runCatching { updater.fetchAvailableUpdate() }
                .onSuccess { availableUpdate ->
                    updateState.value = updateState.value.copy(
                        availableUpdate = availableUpdate,
                        isChecking = false,
                        statusMessage = if (force && availableUpdate == null) {
                            getApplication<Application>().getString(R.string.update_latest)
                        } else {
                            null
                        },
                    )
                }
                .onFailure { error ->
                    updateState.value = updateState.value.copy(
                        isChecking = false,
                        errorMessage = error.message,
                    )
                }
        }
    }

    fun dismissAvailableUpdate() {
        updateState.value = updateState.value.copy(availableUpdate = null)
    }

    fun clearUpdateError() {
        updateState.value = updateState.value.copy(errorMessage = null)
    }

    fun clearUpdateStatusMessage() {
        updateState.value = updateState.value.copy(statusMessage = null)
    }

    fun downloadAndInstallUpdate() {
        val availableUpdate = updateState.value.availableUpdate ?: return
        if (updateState.value.isDownloading) return

        updateState.value = updateState.value.copy(
            isDownloading = true,
            downloadProgressPercent = 0,
            errorMessage = null,
            statusMessage = null,
        )
        viewModelScope.launch {
            runCatching {
                updater.downloadAndLaunchInstaller(availableUpdate) { progress ->
                    updateState.value = updateState.value.copy(downloadProgressPercent = progress)
                }
            }.onSuccess {
                updateState.value = updateState.value.copy(
                    availableUpdate = null,
                    isDownloading = false,
                    downloadProgressPercent = null,
                )
            }.onFailure { error ->
                updateState.value = updateState.value.copy(
                    isDownloading = false,
                    downloadProgressPercent = null,
                    errorMessage = error.message,
                )
            }
        }
    }

    fun selectHost(hostId: String) {
        val forwards = editorState.value.appData.forwards.filter { it.hostId == hostId }
        val selectedForwardId = forwards.firstOrNull()?.id
        editorState.value = editorState.value.copy(selectedHostId = hostId, selectedForwardId = selectedForwardId)
    }

    fun addHost() {
        val currentData = editorState.value.appData
        val host = createHost(currentData.hosts.size + 1)
        val forward = createForward(host.id, 1)
        persist(
            editorState.value.copy(
                appData = currentData.copy(
                    hosts = currentData.hosts + host,
                    forwards = currentData.forwards + forward,
                ),
                selectedHostId = host.id,
                selectedForwardId = forward.id,
            )
        )
    }

    fun deleteSelectedHost() {
        val selectedHostId = editorState.value.selectedHostId ?: return
        val removedForwardIds = editorState.value.appData.forwards
            .filter { it.hostId == selectedHostId }
            .map { it.id }
        removedForwardIds.forEach { forwardId ->
            TunnelForegroundService.start(getApplication(), TunnelForegroundService.ACTION_DISCONNECT, forwardId)
        }

        val updatedData = editorState.value.appData.copy(
            hosts = editorState.value.appData.hosts.filterNot { it.id == selectedHostId },
            forwards = editorState.value.appData.forwards.filterNot { it.hostId == selectedHostId },
        )
        val normalized = ensureSeedData(updatedData)
        val nextHost = normalized.hosts.firstOrNull()
        val nextForward = normalized.forwards.firstOrNull { it.hostId == nextHost?.id }

        persist(
            TunnelEditorState(
                appData = normalized,
                selectedHostId = nextHost?.id,
                selectedForwardId = nextForward?.id,
            ),
            removedForwardIds = removedForwardIds,
        )
    }

    fun updateSelectedHost(transform: (HostProfile) -> HostProfile) {
        val hostId = editorState.value.selectedHostId ?: return
        persist(
            editorState.value.copy(
                appData = editorState.value.appData.copy(
                    hosts = editorState.value.appData.hosts.map { host ->
                        if (host.id == hostId) transform(host) else host
                    }
                )
            )
        )
    }

    fun updateSelectedHostAddress(address: String) {
        updateSelectedHost { current ->
            val normalized = address.trim()
            current.copy(
                host = normalized,
                hostKeyFingerprint = current.hostKeyFingerprint.takeIf { normalized == current.host },
            )
        }
        dismissHostKeyVerification()
    }

    fun updateSelectedHostPort(port: Int) {
        updateSelectedHost { current ->
            current.copy(
                port = port,
                hostKeyFingerprint = current.hostKeyFingerprint.takeIf { port == current.port },
            )
        }
        dismissHostKeyVerification()
    }

    fun setSelectedHostAuthMode(authMode: AuthMode) {
        updateSelectedHost { current ->
            current.copy(authMode = authMode)
        }
    }

    fun addForward(mode: ForwardMode = ForwardMode.LOCAL) {
        val hostId = editorState.value.selectedHostId ?: return
        val nextIndex = editorState.value.appData.forwards.count { it.hostId == hostId } + 1
        val forward = createForward(hostId, nextIndex).copy(
            mode = mode,
            name = when (mode) {
                ForwardMode.LOCAL -> "SSH 터널 $nextIndex"
                ForwardMode.ADB_CONNECT -> "ADB Connect"
                ForwardMode.ADB_PAIRING -> "ADB Pair"
            },
            reverseBindPort = mode.defaultReversePort,
        )
        persist(
            editorState.value.copy(
                appData = editorState.value.appData.copy(
                    forwards = editorState.value.appData.forwards + forward,
                ),
                selectedForwardId = forward.id,
            )
        )
    }

    fun selectForward(forwardId: String) {
        editorState.value = editorState.value.copy(selectedForwardId = forwardId)
    }

    fun deleteSelectedForward() {
        val selectedForwardId = editorState.value.selectedForwardId ?: return
        val selectedHostId = editorState.value.selectedHostId ?: return
        TunnelForegroundService.start(getApplication(), TunnelForegroundService.ACTION_DISCONNECT, selectedForwardId)
        val remainingForwards = editorState.value.appData.forwards.filterNot { it.id == selectedForwardId }
        val hostForwards = remainingForwards.filter { it.hostId == selectedHostId }
        val nextForward = hostForwards.firstOrNull() ?: createForward(selectedHostId, 1)
        val updatedForwards = if (hostForwards.isEmpty()) remainingForwards + nextForward else remainingForwards

        persist(
            editorState.value.copy(
                appData = editorState.value.appData.copy(forwards = updatedForwards),
                selectedForwardId = nextForward.id,
            ),
            removedForwardIds = listOf(selectedForwardId),
        )
    }

    fun updateSelectedForward(transform: (PortForwardRule) -> PortForwardRule) {
        val forwardId = editorState.value.selectedForwardId ?: return
        persist(
            editorState.value.copy(
                appData = editorState.value.appData.copy(
                    forwards = editorState.value.appData.forwards.map { forward ->
                        if (forward.id == forwardId) transform(forward) else forward
                    }
                )
            )
        )
    }

    fun assignWidgetSlot(slot: Int?) {
        val forwardId = editorState.value.selectedForwardId ?: return
        assignWidgetSlot(forwardId, slot)
    }

    fun assignWidgetSlot(forwardId: String, slot: Int?) {
        if (editorState.value.appData.forwards.none { it.id == forwardId }) return
        val updatedForwards = editorState.value.appData.forwards.map { forward ->
            when {
                forward.id == forwardId -> forward.copy(widgetSlot = slot)
                slot != null && forward.widgetSlot == slot -> forward.copy(widgetSlot = null)
                else -> forward
            }
        }

        persist(editorState.value.copy(appData = editorState.value.appData.copy(forwards = updatedForwards)))
    }

    fun disconnectAll() {
        TunnelForegroundService.start(getApplication(), TunnelForegroundService.ACTION_DISCONNECT_ALL)
    }

    fun toggleForward(forwardId: String) {
        val currentState = TunnelRuntime.statuses.value[forwardId]?.state
        val isStopping = currentState == com.yayo.sshtunneling.model.TunnelConnectionState.CONNECTED ||
            currentState == com.yayo.sshtunneling.model.TunnelConnectionState.CONNECTING
        val forward = editorState.value.appData.forwards.firstOrNull { it.id == forwardId }
        val host = editorState.value.appData.hosts.firstOrNull { it.id == forward?.hostId }
        if (
            !isStopping &&
            forward != null &&
            host != null &&
            forward.mode != ForwardMode.LOCAL &&
            host.isComplete() &&
            forward.isComplete() &&
            host.hostKeyFingerprint.isNullOrBlank()
        ) {
            requestHostKeyVerification(host, pendingForwardId = forwardId)
            return
        }
        TunnelForegroundService.start(getApplication(), TunnelForegroundService.ACTION_TOGGLE, forwardId)
            .onFailure { error ->
                TunnelRuntime.upsert(
                    getApplication(),
                    ForwardStatus(
                        forwardId = forwardId,
                        state = com.yayo.sshtunneling.model.TunnelConnectionState.ERROR,
                        message = error.message ?: getApplication<Application>().getString(com.yayo.sshtunneling.R.string.status_service_start_failed),
                    ),
                )
            }
    }

    fun verifySelectedHostKey() {
        val hostId = editorState.value.selectedHostId ?: return
        val host = editorState.value.appData.hosts.firstOrNull { it.id == hostId } ?: return
        requestHostKeyVerification(host)
    }

    fun confirmHostKey() {
        val verification = hostKeyVerification.value
        val hostId = verification.hostId ?: return
        val fingerprint = verification.fingerprint ?: return
        val currentHost = editorState.value.appData.hosts.firstOrNull { it.id == hostId } ?: return
        if (verification.endpoint != "${currentHost.host}:${currentHost.port}") {
            hostKeyVerification.value = HostKeyVerificationState(
                errorMessage = "서버 주소가 변경되었습니다. 서버 키를 다시 확인하세요.",
            )
            return
        }
        updateSelectedHostById(hostId) { it.copy(hostKeyFingerprint = fingerprint) }
        val pendingForwardId = verification.pendingForwardId
        hostKeyVerification.value = HostKeyVerificationState()
        if (pendingForwardId != null) {
            TunnelForegroundService.start(getApplication(), TunnelForegroundService.ACTION_CONNECT, pendingForwardId)
                .onFailure { error ->
                    TunnelRuntime.upsert(
                        getApplication(),
                        ForwardStatus(
                            forwardId = pendingForwardId,
                            state = com.yayo.sshtunneling.model.TunnelConnectionState.ERROR,
                            message = error.message ?: getApplication<Application>().getString(R.string.status_service_start_failed),
                        ),
                    )
                }
        }
    }

    fun dismissHostKeyVerification() {
        hostKeyProbeRequestId.incrementAndGet()
        hostKeyProbeJob?.cancel()
        hostKeyProbeJob = null
        hostKeyVerification.value = HostKeyVerificationState()
    }

    fun retryHostKeyVerification() {
        val previous = hostKeyVerification.value
        val host = editorState.value.appData.hosts.firstOrNull { it.id == previous.hostId }
        requestHostKeyVerification(host, previous.pendingForwardId)
    }

    fun requestDeleteSelectedHost() {
        if (editorState.value.appData.hosts.size <= 1) return
        val host = editorState.value.appData.hosts.firstOrNull { it.id == editorState.value.selectedHostId } ?: return
        deletionConfirmation.value = DeletionConfirmation(DeleteTarget.HOST, host.name.ifBlank { "이름 없는 호스트" })
    }

    fun requestDeleteSelectedForward() {
        val forward = editorState.value.appData.forwards.firstOrNull { it.id == editorState.value.selectedForwardId } ?: return
        if (editorState.value.appData.forwards.count { it.hostId == forward.hostId } <= 1) return
        deletionConfirmation.value = DeletionConfirmation(DeleteTarget.FORWARD, forward.name.ifBlank { "이름 없는 포워딩" })
    }

    fun dismissDeletionConfirmation() {
        deletionConfirmation.value = null
    }

    fun confirmDeletion() {
        when (deletionConfirmation.value?.target) {
            DeleteTarget.HOST -> deleteSelectedHost()
            DeleteTarget.FORWARD -> deleteSelectedForward()
            null -> return
        }
        deletionConfirmation.value = null
    }

    fun exportSettingsJson(): String {
        if (TunnelDataValidation.errors(editorState.value.appData).isNotEmpty()) {
            throw IllegalArgumentException("빨간색으로 표시된 입력값을 먼저 수정하세요.")
        }
        return preferences.exportAppData(editorState.value.appData)
    }

    fun importSettingsJson(rawJson: String): Result<Unit> = runCatching {
        val importedData = preferences.parseAppData(rawJson)
        TunnelForegroundService.start(getApplication(), TunnelForegroundService.ACTION_DISCONNECT_ALL)
        TunnelRuntime.replace(getApplication(), emptyMap())
        persist(
            TunnelEditorState(
                appData = importedData,
                selectedHostId = importedData.hosts.firstOrNull()?.id,
                selectedForwardId = importedData.forwards.firstOrNull()?.id,
            ),
        )
    }

    private fun persist(nextState: TunnelEditorState, removedForwardIds: List<String> = emptyList()) {
        val normalized = ensureSeedData(nextState.appData)
        val selectedHostId = nextState.selectedHostId?.takeIf { hostId -> normalized.hosts.any { it.id == hostId } }
            ?: normalized.hosts.firstOrNull()?.id
        val selectedForwardId = nextState.selectedForwardId?.takeIf { forwardId -> normalized.forwards.any { it.id == forwardId } }
            ?: normalized.forwards.firstOrNull { it.hostId == selectedHostId }?.id

        val editor = TunnelEditorState(
            appData = normalized,
            selectedHostId = selectedHostId,
            selectedForwardId = selectedForwardId,
        )
        preferences.saveAppData(editor.appData)
        removedForwardIds.forEach { forwardId -> TunnelRuntime.remove(getApplication(), forwardId) }
        editorState.value = editor
        TunnelWidgetProvider.refreshAll(getApplication())
    }

    private fun updateSelectedHostById(hostId: String, transform: (HostProfile) -> HostProfile) {
        persist(
            editorState.value.copy(
                appData = editorState.value.appData.copy(
                    hosts = editorState.value.appData.hosts.map { host ->
                        if (host.id == hostId) transform(host) else host
                    },
                ),
            ),
        )
    }

    private fun requestHostKeyVerification(host: HostProfile?, pendingForwardId: String? = null) {
        if (host == null || host.host.isBlank() || host.port !in 1..65535) {
            hostKeyVerification.value = HostKeyVerificationState(
                errorMessage = "SSH 서버 주소와 포트를 먼저 확인하세요.",
                pendingForwardId = pendingForwardId,
            )
            return
        }
        val hostId = host.id
        val endpoint = "${host.host}:${host.port}"
        hostKeyVerification.value = HostKeyVerificationState(
            isChecking = true,
            hostId = hostId,
            endpoint = endpoint,
            pendingForwardId = pendingForwardId,
        )
        hostKeyProbeJob?.cancel()
        val requestId = hostKeyProbeRequestId.incrementAndGet()
        hostKeyProbeJob = viewModelScope.launch(Dispatchers.IO) {
            runCatching { SshHostKeyProbe.probe(host.host, host.port) }
                .onSuccess { observed ->
                    if (requestId != hostKeyProbeRequestId.get()) return@onSuccess
                    hostKeyVerification.value = HostKeyVerificationState(
                        hostId = hostId,
                        endpoint = observed.endpoint,
                        fingerprint = observed.fingerprint,
                        pendingForwardId = pendingForwardId,
                    )
                }
                .onFailure { error ->
                    if (requestId != hostKeyProbeRequestId.get()) return@onFailure
                    hostKeyVerification.value = HostKeyVerificationState(
                        hostId = hostId,
                        endpoint = endpoint,
                        errorMessage = error.toUserMessage(),
                        pendingForwardId = pendingForwardId,
                    )
                }
        }
    }

    private fun Throwable.toUserMessage(): String = when {
        message?.contains("timeout", ignoreCase = true) == true -> "SSH 서버 응답 시간이 초과되었습니다. 주소, 포트, 네트워크를 확인하세요."
        message?.contains("refused", ignoreCase = true) == true -> "SSH 서버가 연결을 거부했습니다. 주소와 포트를 확인하세요."
        else -> "SSH 서버 키를 확인하지 못했습니다. 주소, 포트, 네트워크를 확인하세요."
    }

    private fun loadInitialState(): TunnelEditorState {
        val data = ensureSeedData(preferences.loadAppData())
        preferences.saveAppData(data)
        val selectedHost = data.hosts.firstOrNull()
        val selectedForward = data.forwards.firstOrNull { it.hostId == selectedHost?.id }
        return TunnelEditorState(
            appData = data,
            selectedHostId = selectedHost?.id,
            selectedForwardId = selectedForward?.id,
        )
    }

    private fun ensureSeedData(data: TunnelAppData): TunnelAppData {
        val seededHosts = if (data.hosts.isEmpty()) listOf(createHost(1)) else data.hosts
        val initialHost = seededHosts.first()
        val seededForwards = if (data.forwards.isEmpty()) {
            listOf(createForward(initialHost.id, 1))
        } else {
            data.forwards
        }
        val validForwards = seededForwards.filter { forward -> seededHosts.any { it.id == forward.hostId } }
        val ensuredForwards = if (validForwards.isEmpty()) {
            listOf(createForward(initialHost.id, 1))
        } else {
            validForwards
        }

        return TunnelAppData(
            hosts = seededHosts,
            forwards = ensuredForwards.ensureEveryWidgetSlotIsUnique(),
        )
    }

    private fun List<PortForwardRule>.ensureEveryWidgetSlotIsUnique(): List<PortForwardRule> {
        val seenSlots = mutableSetOf<Int>()
        return map { forward ->
            val slot = forward.widgetSlot
            if (slot == null || slot !in 0 until WidgetSlots.COUNT || seenSlots.add(slot)) {
                forward.copy(widgetSlot = slot?.takeIf { it in 0 until WidgetSlots.COUNT })
            } else {
                forward.copy(widgetSlot = null)
            }
        }
    }

    private fun createHost(index: Int): HostProfile {
        return HostProfile(
            id = UUID.randomUUID().toString(),
            name = "호스트 $index",
        )
    }

    private fun createForward(hostId: String, index: Int): PortForwardRule {
        return PortForwardRule(
            id = UUID.randomUUID().toString(),
            hostId = hostId,
            name = "포워딩 $index",
        )
    }
}
