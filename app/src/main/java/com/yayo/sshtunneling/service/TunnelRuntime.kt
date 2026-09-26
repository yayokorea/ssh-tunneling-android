package com.yayo.sshtunneling.service

import android.content.Context
import com.yayo.sshtunneling.data.TunnelPreferences
import com.yayo.sshtunneling.model.ForwardStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

object TunnelRuntime {
    private val _statuses = MutableStateFlow<Map<String, ForwardStatus>>(emptyMap())
    val statuses: StateFlow<Map<String, ForwardStatus>> = _statuses.asStateFlow()
    private val persistenceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val pendingSave = Channel<Pair<Context, Map<String, ForwardStatus>>>(Channel.CONFLATED)

    init {
        persistenceScope.launch {
            for ((context, snapshot) in pendingSave) {
                runCatching { TunnelPreferences(context).saveStatuses(snapshot) }
            }
        }
    }

    @Synchronized
    fun initialize(context: Context) {
        if (_statuses.value.isNotEmpty()) return
        _statuses.value = TunnelPreferences(context).loadStatuses()
    }

    @Synchronized
    fun replace(context: Context, statuses: Map<String, ForwardStatus>) {
        _statuses.value = statuses
        pendingSave.trySend(context.applicationContext to statuses)
    }

    @Synchronized
    fun upsert(context: Context, status: ForwardStatus) {
        _statuses.update { it + (status.forwardId to status) }
        pendingSave.trySend(context.applicationContext to _statuses.value)
    }

    @Synchronized
    fun remove(context: Context, forwardId: String) {
        _statuses.update { it - forwardId }
        pendingSave.trySend(context.applicationContext to _statuses.value)
    }
}
