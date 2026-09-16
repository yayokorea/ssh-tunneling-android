package com.yayo.sshtunneling.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Context.VIBRATOR_MANAGER_SERVICE
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.yayo.sshtunneling.MainActivity
import com.yayo.sshtunneling.R
import com.yayo.sshtunneling.adb.AdbEndpointDiscovery
import com.yayo.sshtunneling.adb.AdbEndpointSelector
import com.yayo.sshtunneling.adb.AdbServiceKind
import com.yayo.sshtunneling.data.TunnelPreferences
import com.yayo.sshtunneling.model.ForwardMode
import com.yayo.sshtunneling.model.ForwardStatus
import com.yayo.sshtunneling.model.PortForwardRule
import com.yayo.sshtunneling.model.TunnelConnectionState
import com.yayo.sshtunneling.model.TunnelPhase
import com.yayo.sshtunneling.widget.TunnelWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class TunnelForegroundService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val tunnelManagers = mutableMapOf<String, SshTunnelManager>()
    private val connectJobs = mutableMapOf<String, Job>()
    private val monitorJobs = mutableMapOf<String, Job>()
    private val adbWatchJobs = mutableMapOf<String, Job>()
    private val activeAdbForwardIds = mutableSetOf<String>()
    private lateinit var adbDiscovery: AdbEndpointDiscovery

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        TunnelRuntime.initialize(applicationContext)
        adbDiscovery = AdbEndpointDiscovery(applicationContext, serviceScope)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val triggerHaptic = intent?.getBooleanExtra(EXTRA_TRIGGER_HAPTIC, false) == true
        when (intent?.action) {
            ACTION_CONNECT -> intent.getStringExtra(EXTRA_FORWARD_ID)?.let { connectTunnel(it, triggerHaptic) }
            ACTION_DISCONNECT -> intent.getStringExtra(EXTRA_FORWARD_ID)?.let { disconnectTunnel(it, triggerHaptic = triggerHaptic) }
            ACTION_TOGGLE -> intent.getStringExtra(EXTRA_FORWARD_ID)?.let { toggleTunnel(it, triggerHaptic) }
            ACTION_DISCONNECT_ALL -> disconnectAllTunnels()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        connectJobs.values.forEach { it.cancel() }
        monitorJobs.values.forEach { it.cancel() }
        adbWatchJobs.values.forEach { it.cancel() }
        adbDiscovery.close()
        tunnelManagers.toMap().forEach { (forwardId, manager) ->
            manager.disconnect()
            updateStatus(
                ForwardStatus(
                    forwardId = forwardId,
                    state = TunnelConnectionState.IDLE,
                    message = getString(R.string.status_idle_item),
                )
            )
        }
        connectJobs.clear()
        monitorJobs.clear()
        adbWatchJobs.clear()
        activeAdbForwardIds.clear()
        tunnelManagers.clear()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun toggleTunnel(forwardId: String, triggerHaptic: Boolean = false) {
        val state = TunnelRuntime.statuses.value[forwardId]?.state
        if (state == TunnelConnectionState.CONNECTED || state == TunnelConnectionState.CONNECTING) {
            disconnectTunnel(forwardId, triggerHaptic = triggerHaptic)
        } else {
            connectTunnel(forwardId, triggerHaptic)
        }
    }

    private fun connectTunnel(forwardId: String, triggerHaptic: Boolean = false) {
        if (connectJobs[forwardId]?.isActive == true || tunnelManagers[forwardId]?.isConnected() == true) {
            if (triggerHaptic) triggerHapticFeedback()
            return
        }

        val data = TunnelPreferences(applicationContext).loadAppData()
        val forward = data.forwards.firstOrNull { it.id == forwardId }
        val host = data.hosts.firstOrNull { it.id == forward?.hostId }

        if (forward == null || host == null || !host.isComplete() || !forward.isComplete()) {
            updateStatus(
                ForwardStatus(
                    forwardId = forwardId,
                    state = TunnelConnectionState.ERROR,
                    message = getString(R.string.status_profile_incomplete),
                )
            )
            stopIfIdle()
            return
        }

        if (forward.mode != ForwardMode.LOCAL && Build.VERSION.SDK_INT < 30) {
            updateStatus(
                ForwardStatus(
                    forwardId = forwardId,
                    state = TunnelConnectionState.ERROR,
                    phase = TunnelPhase.ERROR,
                    message = getString(R.string.status_adb_api_unsupported),
                )
            )
            return
        }

        startForeground(NOTIFICATION_ID, buildNotification())
        if (triggerHaptic) triggerHapticFeedback()
        updateStatus(
            ForwardStatus(
                forwardId = forwardId,
                state = TunnelConnectionState.CONNECTING,
                phase = if (forward.mode == ForwardMode.LOCAL) TunnelPhase.CONNECTING_SSH else TunnelPhase.DISCOVERING_ADB,
                message = if (forward.mode == ForwardMode.LOCAL) {
                    getString(R.string.status_connecting_item, forward.name)
                } else {
                    getString(R.string.status_adb_discovering, forward.name)
                },
            )
        )

        if (forward.mode != ForwardMode.LOCAL) {
            activeAdbForwardIds += forwardId
            adbDiscovery.start()
        }
        connectJobs[forwardId] = serviceScope.launch {
            val result = runCatching {
                val manager = SshTunnelManager(host, forward)
                val endpoint = if (forward.mode == ForwardMode.LOCAL) {
                    null
                } else {
                    val kind = forward.mode.toAdbServiceKind()
                    updateStatus(
                        ForwardStatus(
                            forwardId = forwardId,
                            state = TunnelConnectionState.CONNECTING,
                            phase = TunnelPhase.PROBING_ADB,
                            message = getString(R.string.status_adb_probing, forward.name),
                        )
                    )
                    adbDiscovery.snapshot.value.errorMessage?.let { error(it) }
                    adbDiscovery.awaitEndpoint(kind)
                        ?: error(getString(if (kind == AdbServiceKind.PAIRING) R.string.status_pairing_not_found else R.string.status_adb_not_found))
                }
                val boundPort = if (endpoint == null) {
                    manager.connect()
                } else {
                    updateStatus(
                        ForwardStatus(
                            forwardId = forwardId,
                            state = TunnelConnectionState.CONNECTING,
                            phase = TunnelPhase.CONNECTING_SSH,
                            message = getString(R.string.status_connecting_item, forward.name),
                        )
                    )
                    manager.connectSession()
                    manager.addReverseForward(
                        endpoint.primaryAddress?.hostAddress ?: error("ADB endpoint address is missing"),
                        endpoint.port,
                    )
                }
                tunnelManagers[forwardId] = manager
                startMonitor(forwardId, manager, host.keepAliveSeconds)
                if (endpoint != null) {
                    startAdbWatcher(forwardId, forward, manager, endpoint)
                }
                updateStatus(
                    ForwardStatus(
                        forwardId = forwardId,
                        state = TunnelConnectionState.CONNECTED,
                        phase = TunnelPhase.CONNECTED,
                        message = if (endpoint == null) {
                            getString(
                                R.string.status_connected_item,
                                forward.name,
                                boundPort,
                                forward.remoteHost,
                                forward.remotePort,
                            )
                        } else {
                            getString(
                                R.string.status_connected_adb,
                                forward.name,
                                forward.reverseBindPort,
                            )
                        },
                    )
                )
                refreshNotification()
            }
            connectJobs.remove(forwardId)
            activeAdbForwardIds.remove(forwardId)
            result.onFailure { error ->
                tunnelManagers.remove(forwardId)?.disconnect()
                monitorJobs.remove(forwardId)?.cancel()
                updateStatus(
                    ForwardStatus(
                        forwardId = forwardId,
                        state = TunnelConnectionState.ERROR,
                        phase = TunnelPhase.ERROR,
                        message = error.userFacingMessage(),
                    )
                )
                stopIfIdle()
            }
        }
    }

    private fun disconnectTunnel(
        forwardId: String,
        updateIdleState: Boolean = true,
        triggerHaptic: Boolean = false,
    ) {
        if (triggerHaptic) triggerHapticFeedback()
        connectJobs.remove(forwardId)?.cancel()
        monitorJobs.remove(forwardId)?.cancel()
        adbWatchJobs.remove(forwardId)?.cancel()
        activeAdbForwardIds.remove(forwardId)
        tunnelManagers.remove(forwardId)?.disconnect()
        if (updateIdleState) {
            updateStatus(
                ForwardStatus(
                    forwardId = forwardId,
                    state = TunnelConnectionState.IDLE,
                    phase = TunnelPhase.IDLE,
                    message = getString(R.string.status_idle_item),
                )
            )
        }
        stopIfIdle()
    }

    private fun disconnectAllTunnels() {
        val forwardIds = (tunnelManagers.keys + connectJobs.keys + monitorJobs.keys).toSet()
        forwardIds.forEach { forwardId ->
            disconnectTunnel(forwardId)
        }
    }

    private fun startMonitor(forwardId: String, manager: SshTunnelManager, keepAliveSeconds: Int) {
        monitorJobs.remove(forwardId)?.cancel()
        val intervalMillis = keepAliveSeconds.coerceAtLeast(MIN_MONITOR_INTERVAL_SECONDS) * 1_000L
        monitorJobs[forwardId] = serviceScope.launch {
            while (isActive) {
                delay(intervalMillis)
                if (!manager.verifyConnected()) {
                    tunnelManagers.remove(forwardId)?.disconnect()
                    monitorJobs.remove(forwardId)
                    updateStatus(
                        ForwardStatus(
                            forwardId = forwardId,
                            state = TunnelConnectionState.ERROR,
                            phase = TunnelPhase.ERROR,
                            message = getString(R.string.status_connection_lost),
                        )
                    )
                    stopIfIdle()
                    break
                }
            }
        }
    }

    private fun stopIfIdle() {
        if (tunnelManagers.isEmpty() && connectJobs.values.none { it.isActive }) {
            if (activeAdbForwardIds.isEmpty()) adbDiscovery.stop()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        } else {
            refreshNotification()
        }
    }

    private fun startAdbWatcher(
        forwardId: String,
        forward: PortForwardRule,
        manager: SshTunnelManager,
        initialEndpoint: com.yayo.sshtunneling.adb.AdbEndpoint,
    ) {
        adbWatchJobs.remove(forwardId)?.cancel()
        val kind = forward.mode.toAdbServiceKind()
        var endpointKey = endpointKey(initialEndpoint)
        var endpointWasMissing = false
        adbWatchJobs[forwardId] = serviceScope.launch {
            adbDiscovery.snapshot.collect { snapshot ->
                val endpoint = AdbEndpointSelector.select(snapshot.endpoints, kind)
                if (endpoint == null) {
                    manager.removeReverseForward()
                    endpointWasMissing = true
                    updateStatus(
                        ForwardStatus(
                            forwardId = forwardId,
                            state = TunnelConnectionState.CONNECTING,
                            phase = if (kind == AdbServiceKind.PAIRING) TunnelPhase.WAITING_FOR_PAIRING else TunnelPhase.WAITING_FOR_WIFI,
                            message = getString(if (kind == AdbServiceKind.PAIRING) R.string.status_pairing_waiting else R.string.status_adb_waiting_for_network),
                        )
                    )
                    return@collect
                }
                val nextKey = endpointKey(endpoint)
                if (nextKey != endpointKey) {
                    endpoint.primaryAddress?.hostAddress?.let { address ->
                        runCatching {
                            manager.replaceReverseTarget(address, endpoint.port)
                        }.onSuccess {
                            endpointKey = nextKey
                        }.onFailure {
                            updateStatus(
                                ForwardStatus(
                                    forwardId = forwardId,
                                    state = TunnelConnectionState.ERROR,
                                    phase = TunnelPhase.ERROR,
                                    message = it.userFacingMessage(),
                                )
                            )
                        }
                    }
                }
                if (endpointWasMissing) {
                    endpointWasMissing = false
                    updateStatus(
                        ForwardStatus(
                            forwardId = forwardId,
                            state = TunnelConnectionState.CONNECTED,
                            phase = TunnelPhase.CONNECTED,
                            message = getString(R.string.status_connected_adb, forward.name, forward.reverseBindPort),
                        )
                    )
                }
            }
        }
    }

    private fun endpointKey(endpoint: com.yayo.sshtunneling.adb.AdbEndpoint): String =
        "${endpoint.instanceName}:${endpoint.primaryAddress?.hostAddress}:${endpoint.port}"

    private fun Throwable.userFacingMessage(): String {
        val detail = message.orEmpty()
        return when {
            detail.contains("HostKey has been changed", ignoreCase = true) ||
                detail.contains("reject HostKey", ignoreCase = true) -> getString(R.string.status_host_key_changed)
            detail.contains("Auth fail", ignoreCase = true) -> getString(R.string.status_auth_failed)
            detail.contains("refused", ignoreCase = true) -> getString(R.string.status_connection_refused)
            detail.contains("timeout", ignoreCase = true) -> getString(R.string.status_connection_timeout)
            detail.contains("remote port forwarding failed", ignoreCase = true) ||
                detail.contains("port forwarding failed", ignoreCase = true) -> getString(R.string.status_reverse_forward_failed)
            else -> getString(R.string.status_unknown_error)
        }
    }

    private fun ForwardMode.toAdbServiceKind(): AdbServiceKind = when (this) {
        ForwardMode.ADB_CONNECT -> AdbServiceKind.CONNECT
        ForwardMode.ADB_PAIRING -> AdbServiceKind.PAIRING
        ForwardMode.LOCAL -> error("Local forwarding has no ADB service kind")
    }

    private fun updateStatus(status: ForwardStatus) {
        TunnelRuntime.upsert(applicationContext, status)
        TunnelWidgetProvider.refreshAll(applicationContext)
    }

    private fun refreshNotification() {
        val manager = ContextCompat.getSystemService(this, NotificationManager::class.java) ?: return
        manager.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun triggerHapticFeedback() {
        runCatching {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val manager = getSystemService(VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                manager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }

            if (vibrator?.hasVibrator() == true) {
                val effect = VibrationEffect.createOneShot(18L, 90)
                vibrator.vibrate(effect)
            }
        }
    }

    private fun buildNotification(): Notification {
        val statuses = TunnelRuntime.statuses.value.values
        val connectedCount = statuses.count { it.state == TunnelConnectionState.CONNECTED }
        val connectingCount = statuses.count { it.state == TunnelConnectionState.CONNECTING }
        val contentText = when {
            connectedCount > 0 && connectingCount > 0 -> getString(R.string.notification_summary_mixed, connectedCount, connectingCount)
            connectedCount > 0 -> getString(R.string.notification_summary_connected, connectedCount)
            connectingCount > 0 -> getString(R.string.notification_summary_connecting, connectingCount)
            else -> getString(R.string.notification_summary_idle)
        }

        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, TunnelForegroundService::class.java).setAction(ACTION_DISCONNECT_ALL),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_tunnel)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .addAction(R.drawable.ic_stop, getString(R.string.disconnect_all), stopIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val manager = ContextCompat.getSystemService(this, NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        )
        manager.createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "ssh_tunnel_channel"
        private const val NOTIFICATION_ID = 1001
        private const val MIN_MONITOR_INTERVAL_SECONDS = 5

        const val ACTION_CONNECT = "com.yayo.sshtunneling.action.CONNECT"
        const val ACTION_DISCONNECT = "com.yayo.sshtunneling.action.DISCONNECT"
        const val ACTION_TOGGLE = "com.yayo.sshtunneling.action.TOGGLE"
        const val ACTION_DISCONNECT_ALL = "com.yayo.sshtunneling.action.DISCONNECT_ALL"
        const val EXTRA_FORWARD_ID = "extra_forward_id"
        const val EXTRA_TRIGGER_HAPTIC = "extra_trigger_haptic"

        fun start(
            context: Context,
            action: String,
            forwardId: String? = null,
            triggerHaptic: Boolean = false,
        ): Result<Unit> = runCatching {
            val intent = Intent(context, TunnelForegroundService::class.java).setAction(action)
            if (forwardId != null) {
                intent.putExtra(EXTRA_FORWARD_ID, forwardId)
            }
            if (triggerHaptic) {
                intent.putExtra(EXTRA_TRIGGER_HAPTIC, true)
            }
            if (action == ACTION_DISCONNECT || action == ACTION_DISCONNECT_ALL) {
                context.startService(intent)
            } else {
                ContextCompat.startForegroundService(context, intent)
            }
        }
    }
}
