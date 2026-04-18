package com.yayo.sshtunneling.service

import android.content.Context
import com.yayo.sshtunneling.data.TunnelPreferences
import com.yayo.sshtunneling.model.TunnelStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object TunnelRuntime {
    private val _status = MutableStateFlow(TunnelStatus())
    val status: StateFlow<TunnelStatus> = _status.asStateFlow()

    fun initialize(context: Context) {
        _status.value = TunnelPreferences(context).loadStatus()
    }

    fun update(context: Context, status: TunnelStatus) {
        TunnelPreferences(context).saveStatus(status)
        _status.value = status
    }
}
