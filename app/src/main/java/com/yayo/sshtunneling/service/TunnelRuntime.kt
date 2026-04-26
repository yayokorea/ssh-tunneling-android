package com.yayo.sshtunneling.service

import android.content.Context
import com.yayo.sshtunneling.data.TunnelPreferences
import com.yayo.sshtunneling.model.ForwardStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object TunnelRuntime {
    private val _statuses = MutableStateFlow<Map<String, ForwardStatus>>(emptyMap())
    val statuses: StateFlow<Map<String, ForwardStatus>> = _statuses.asStateFlow()

    fun initialize(context: Context) {
        if (_statuses.value.isNotEmpty()) return
        _statuses.value = TunnelPreferences(context).loadStatuses()
    }

    fun replace(context: Context, statuses: Map<String, ForwardStatus>) {
        TunnelPreferences(context).saveStatuses(statuses)
        _statuses.value = statuses
    }

    fun upsert(context: Context, status: ForwardStatus) {
        val updated = _statuses.value.toMutableMap().apply {
            put(status.forwardId, status)
        }
        replace(context, updated)
    }

    fun remove(context: Context, forwardId: String) {
        val updated = _statuses.value.toMutableMap().apply {
            remove(forwardId)
        }
        replace(context, updated)
    }
}
