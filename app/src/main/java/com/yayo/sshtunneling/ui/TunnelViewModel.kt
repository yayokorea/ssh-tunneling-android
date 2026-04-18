package com.yayo.sshtunneling.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yayo.sshtunneling.data.TunnelPreferences
import com.yayo.sshtunneling.model.AuthMode
import com.yayo.sshtunneling.model.ForwardStatus
import com.yayo.sshtunneling.model.HostProfile
import com.yayo.sshtunneling.model.PortForwardRule
import com.yayo.sshtunneling.model.TunnelAppData
import com.yayo.sshtunneling.model.WidgetSlots
import com.yayo.sshtunneling.service.TunnelForegroundService
import com.yayo.sshtunneling.service.TunnelRuntime
import com.yayo.sshtunneling.widget.TunnelWidgetProvider
import java.util.UUID
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
)

private data class TunnelEditorState(
    val appData: TunnelAppData,
    val selectedHostId: String?,
    val selectedForwardId: String?,
)

class TunnelViewModel(application: Application) : AndroidViewModel(application) {
    private val preferences = TunnelPreferences(application)
    private val editorState = MutableStateFlow(loadInitialState())

    val uiState: StateFlow<TunnelUiState>

    init {
        TunnelRuntime.initialize(application)
        uiState = combine(editorState, TunnelRuntime.statuses) { editor, statuses ->
            TunnelUiState(
                appData = editor.appData,
                statuses = statuses,
                selectedHostId = editor.selectedHostId,
                selectedForwardId = editor.selectedForwardId,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = TunnelUiState(
                appData = editorState.value.appData,
                statuses = TunnelRuntime.statuses.value,
                selectedHostId = editorState.value.selectedHostId,
                selectedForwardId = editorState.value.selectedForwardId,
            ),
        )
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

    fun setSelectedHostAuthMode(authMode: AuthMode) {
        updateSelectedHost { current ->
            current.copy(
                authMode = authMode,
                password = if (authMode == AuthMode.PASSWORD) current.password else "",
                privateKey = if (authMode == AuthMode.PRIVATE_KEY) current.privateKey else "",
            )
        }
    }

    fun addForward() {
        val hostId = editorState.value.selectedHostId ?: return
        val nextIndex = editorState.value.appData.forwards.count { it.hostId == hostId } + 1
        val forward = createForward(hostId, nextIndex)
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
        val updatedForwards = editorState.value.appData.forwards.map { forward ->
            when {
                forward.id == forwardId -> forward.copy(widgetSlot = slot)
                slot != null && forward.widgetSlot == slot -> forward.copy(widgetSlot = null)
                else -> forward
            }
        }

        persist(editorState.value.copy(appData = editorState.value.appData.copy(forwards = updatedForwards)))
    }

    fun toggleForward(forwardId: String) {
        TunnelForegroundService.start(getApplication(), TunnelForegroundService.ACTION_TOGGLE, forwardId)
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
            name = "Host $index",
        )
    }

    private fun createForward(hostId: String, index: Int): PortForwardRule {
        return PortForwardRule(
            id = UUID.randomUUID().toString(),
            hostId = hostId,
            name = "Port $index",
        )
    }
}
