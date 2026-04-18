package com.yayo.sshtunneling.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yayo.sshtunneling.data.TunnelPreferences
import com.yayo.sshtunneling.model.AuthMode
import com.yayo.sshtunneling.model.TunnelProfile
import com.yayo.sshtunneling.model.TunnelStatus
import com.yayo.sshtunneling.service.TunnelForegroundService
import com.yayo.sshtunneling.service.TunnelRuntime
import com.yayo.sshtunneling.widget.TunnelWidgetProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class TunnelUiState(
    val profile: TunnelProfile = TunnelProfile(),
    val status: TunnelStatus = TunnelStatus(),
    val hasSavedProfile: Boolean = false,
)

class TunnelViewModel(application: Application) : AndroidViewModel(application) {
    private val preferences = TunnelPreferences(application)
    private val profileDraft = MutableStateFlow(preferences.loadProfile())

    val uiState: StateFlow<TunnelUiState>

    init {
        TunnelRuntime.initialize(application)
        uiState = combine(profileDraft, TunnelRuntime.status) { profile, status ->
            TunnelUiState(
                profile = profile,
                status = status,
                hasSavedProfile = preferences.loadProfile().isComplete(),
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = TunnelUiState(
                profile = profileDraft.value,
                status = TunnelRuntime.status.value,
                hasSavedProfile = preferences.loadProfile().isComplete(),
            ),
        )
    }

    fun updateProfile(transform: (TunnelProfile) -> TunnelProfile) {
        profileDraft.value = transform(profileDraft.value)
    }

    fun saveProfile() {
        preferences.saveProfile(profileDraft.value)
        TunnelWidgetProvider.refreshAll(getApplication())
    }

    fun toggleTunnel() {
        TunnelForegroundService.start(getApplication(), TunnelForegroundService.ACTION_TOGGLE)
    }

    fun disconnectTunnel() {
        TunnelForegroundService.start(getApplication(), TunnelForegroundService.ACTION_DISCONNECT)
    }

    fun setAuthMode(authMode: AuthMode) {
        updateProfile { current ->
            current.copy(
                authMode = authMode,
                password = if (authMode == AuthMode.PASSWORD) current.password else "",
                privateKey = if (authMode == AuthMode.PRIVATE_KEY) current.privateKey else "",
            )
        }
    }
}
