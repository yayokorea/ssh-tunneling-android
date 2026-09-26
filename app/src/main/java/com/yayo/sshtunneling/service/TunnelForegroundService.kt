package com.yayo.sshtunneling.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Context.VIBRATOR_MANAGER_SERVICE
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
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
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

class TunnelForegroundService : Service() {
    // Service state and commands live on the main thread; blocking SSH work runs on IO.
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val tunnelManagers = mutableMapOf<String, SshTunnelManager>()
    private val connectJobs = mutableMapOf<String, Job>()
    private val cleanupJobs = mutableMapOf<String, Job>()
    private val adbWatchJobs = mutableMapOf<String, Job>()
    private val activeAdbForwardIds = mutableSetOf<String>()
    private val desiredForwardIds = mutableSetOf<String>()
    private val networkChanges = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private lateinit var adbDiscovery: AdbEndpointDiscovery
    private lateinit var connectivityManager: ConnectivityManager

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) { networkChanges.tryEmit(Unit) }
        override fun onLost(network: Network) { networkChanges.tryEmit(Unit) }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        TunnelRuntime.initialize(applicationContext)
        desiredForwardIds += TunnelPreferences(applicationContext).loadDesiredForwardIds()
        adbDiscovery = AdbEndpointDiscovery(applicationContext, serviceScope)
        connectivityManager = getSystemService(ConnectivityManager::class.java)
        connectivityManager.registerDefaultNetworkCallback(networkCallback)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val triggerHaptic = intent?.getBooleanExtra(EXTRA_TRIGGER_HAPTIC, false) == true
        when (intent?.action) {
            ACTION_CONNECT -> {
                startForeground(NOTIFICATION_ID, buildNotification())
                intent.getStringExtra(EXTRA_FORWARD_ID)?.let { connectTunnel(it, triggerHaptic) }
            }
            ACTION_DISCONNECT -> intent.getStringExtra(EXTRA_FORWARD_ID)?.let { disconnectTunnel(it, triggerHaptic) }
            ACTION_TOGGLE -> {
                startForeground(NOTIFICATION_ID, buildNotification())
                intent.getStringExtra(EXTRA_FORWARD_ID)?.let { toggleTunnel(it, triggerHaptic) }
            }
            ACTION_DISCONNECT_ALL -> disconnectAllTunnels()
            null -> {
                if (desiredForwardIds.isEmpty()) stopSelf(startId)
                else {
                    startForeground(NOTIFICATION_ID, buildNotification())
                    desiredForwardIds.toList().forEach { connectTunnel(it) }
                }
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        connectJobs.values.forEach { it.cancel() }
        adbWatchJobs.values.forEach { it.cancel() }
        runCatching { connectivityManager.unregisterNetworkCallback(networkCallback) }
        adbDiscovery.close()
        val managers = tunnelManagers.values.toList()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            managers.forEach { runCatching { it.disconnect() } }
        }
        TunnelRuntime.replace(applicationContext, TunnelRuntime.statuses.value.mapValues { (_, status) ->
            if (status.state == TunnelConnectionState.CONNECTING || status.state == TunnelConnectionState.CONNECTED) {
                status.copy(state = TunnelConnectionState.IDLE, phase = TunnelPhase.IDLE, message = null)
            } else status
        })
        TunnelWidgetProvider.refreshAll(applicationContext)
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun toggleTunnel(forwardId: String, triggerHaptic: Boolean = false) {
        if (forwardId in desiredForwardIds) disconnectTunnel(forwardId, triggerHaptic = triggerHaptic)
        else connectTunnel(forwardId, triggerHaptic)
    }

    private fun connectTunnel(forwardId: String, triggerHaptic: Boolean = false) {
        if (triggerHaptic) triggerHapticFeedback()
        desiredForwardIds += forwardId
        saveDesiredForwardIds()
        if (connectJobs[forwardId]?.isActive == true) return
        updateStatus(ForwardStatus(
            forwardId = forwardId,
            state = TunnelConnectionState.CONNECTING,
            phase = TunnelPhase.CONNECTING_SSH,
            message = getString(R.string.status_preparing_connection),
        ))
        val job = serviceScope.launch {
            try {
                cleanupJobs[forwardId]?.join()
                val data = withContext(Dispatchers.IO) { TunnelPreferences(applicationContext).loadAppData() }
                val forward = data.forwards.firstOrNull { it.id == forwardId }
                val host = data.hosts.firstOrNull { it.id == forward?.hostId }
                if (forward == null || host == null || !host.isComplete() || !forward.isComplete()) {
                    throw PermanentTunnelException(getString(R.string.status_profile_incomplete))
                }
                if (forward.mode != ForwardMode.LOCAL && Build.VERSION.SDK_INT < 30) {
                    throw PermanentTunnelException(getString(R.string.status_adb_api_unsupported))
                }
                if (forward.mode != ForwardMode.LOCAL) {
                    activeAdbForwardIds += forwardId
                    adbDiscovery.start()
                    adbDiscovery.snapshot.value.errorMessage?.let { throw PermanentTunnelException(it) }
                }
                var retryDelay = TunnelRetryPolicy.INITIAL_DELAY_MILLIS
                while (isActive && forwardId in desiredForwardIds) {
                    val manager = SshTunnelManager(host, forward)
                    try {
                        val endpoint = if (forward.mode == ForwardMode.LOCAL) null else {
                            val kind = forward.mode.toAdbServiceKind()
                            updateStatus(ForwardStatus(forwardId, TunnelConnectionState.CONNECTING,
                                TunnelPhase.DISCOVERING_ADB, getString(R.string.status_adb_discovering, forward.name)))
                            adbDiscovery.awaitEndpoint(kind) ?: throw IOException(
                                getString(if (kind == AdbServiceKind.PAIRING) R.string.status_pairing_not_found else R.string.status_adb_not_found)
                            )
                        }
                        updateStatus(ForwardStatus(forwardId, TunnelConnectionState.CONNECTING,
                            TunnelPhase.CONNECTING_SSH, getString(R.string.status_connecting_item, forward.name)))
                        val boundPort = withContext(Dispatchers.IO) {
                            if (endpoint == null) manager.connect()
                            else {
                                manager.connectSession()
                                manager.addReverseForward(
                                    endpoint.primaryAddress?.hostAddress ?: error("ADB endpoint address is missing"),
                                    endpoint.port,
                                )
                            }
                        }
                        tunnelManagers[forwardId] = manager
                        if (endpoint != null) startAdbWatcher(forwardId, forward, manager, endpoint)
                        updateStatus(ForwardStatus(
                            forwardId = forwardId,
                            state = TunnelConnectionState.CONNECTED,
                            phase = TunnelPhase.CONNECTED,
                            message = if (endpoint == null) getString(R.string.status_connected_item,
                                forward.name, boundPort, forward.remoteHost, forward.remotePort)
                            else getString(R.string.status_connected_adb, forward.name,
                                forward.reverseBindPort, endpoint.primaryAddress?.hostAddress.orEmpty(), endpoint.port),
                        ))
                        refreshNotification()
                        retryDelay = TunnelRetryPolicy.INITIAL_DELAY_MILLIS
                        val intervalMillis = host.keepAliveSeconds.coerceAtLeast(MIN_MONITOR_INTERVAL_SECONDS) * 1_000L
                        while (isActive && forwardId in desiredForwardIds) {
                            delay(intervalMillis)
                            if (!withContext(Dispatchers.IO) { manager.verifyConnected() }) {
                                throw IOException(getString(R.string.status_connection_lost))
                            }
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (failure: Exception) {
                        if (failure.isPermanent()) {
                            desiredForwardIds.remove(forwardId)
                            saveDesiredForwardIds()
                            updateStatus(ForwardStatus(forwardId, TunnelConnectionState.ERROR,
                                TunnelPhase.ERROR, failure.userFacingMessage()))
                            break
                        }
                        updateStatus(ForwardStatus(forwardId, TunnelConnectionState.CONNECTING,
                            TunnelPhase.RECONNECTING, getString(R.string.status_reconnecting, retryDelay / 1_000)))
                    } finally {
                        adbWatchJobs.remove(forwardId)?.cancel()
                        if (tunnelManagers[forwardId] === manager) tunnelManagers.remove(forwardId)
                        withContext(NonCancellable + Dispatchers.IO) { manager.disconnect() }
                    }
                    if (forwardId in desiredForwardIds) {
                        withTimeoutOrNull(retryDelay) { networkChanges.first() }
                        retryDelay = TunnelRetryPolicy.nextDelayMillis(retryDelay)
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                desiredForwardIds.remove(forwardId)
                saveDesiredForwardIds()
                updateStatus(ForwardStatus(forwardId, TunnelConnectionState.ERROR,
                    TunnelPhase.ERROR, failure.userFacingMessage()))
            } finally {
                val ownsForward = currentCoroutineContext()[Job] === connectJobs[forwardId]
                if (ownsForward) {
                    connectJobs.remove(forwardId)
                    activeAdbForwardIds.remove(forwardId)
                }
                if (activeAdbForwardIds.isEmpty()) adbDiscovery.stop()
                if (ownsForward) stopIfIdle()
            }
        }
        connectJobs[forwardId] = job
    }

    private fun disconnectTunnel(forwardId: String, triggerHaptic: Boolean = false) {
        if (triggerHaptic) triggerHapticFeedback()
        desiredForwardIds.remove(forwardId)
        saveDesiredForwardIds()
        val oldJob = connectJobs.remove(forwardId)
        oldJob?.cancel()
        adbWatchJobs.remove(forwardId)?.cancel()
        activeAdbForwardIds.remove(forwardId)
        val orphanManager = if (oldJob == null) tunnelManagers.remove(forwardId) else null
        if (oldJob != null || orphanManager != null) {
            val cleanup = serviceScope.launch {
                oldJob?.join()
                if (orphanManager != null) withContext(Dispatchers.IO) { orphanManager.disconnect() }
            }
            cleanupJobs[forwardId] = cleanup
            cleanup.invokeOnCompletion {
                serviceScope.launch {
                    if (cleanupJobs[forwardId] === cleanup) cleanupJobs.remove(forwardId)
                    stopIfIdle()
                }
            }
        }
        updateStatus(ForwardStatus(forwardId, TunnelConnectionState.IDLE,
            TunnelPhase.IDLE, getString(R.string.status_idle_item)))
        stopIfIdle()
    }

    private fun disconnectAllTunnels() {
        (desiredForwardIds + connectJobs.keys + tunnelManagers.keys).toSet().forEach { disconnectTunnel(it) }
    }

    private fun saveDesiredForwardIds() {
        TunnelPreferences(applicationContext).saveDesiredForwardIds(desiredForwardIds)
    }

    private fun Throwable.isPermanent(): Boolean {
        if (this is PermanentTunnelException) return true
        return TunnelRetryPolicy.isPermanentSshFailure(message.orEmpty())
    }

    private class PermanentTunnelException(message: String) : Exception(message)

    private fun stopIfIdle() {
        if (connectJobs.values.none { it.isActive } && cleanupJobs.values.none { it.isActive } && tunnelManagers.isEmpty()) {
            if (activeAdbForwardIds.isEmpty()) adbDiscovery.stop()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        } else refreshNotification()
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
                    withContext(Dispatchers.IO) { manager.removeReverseForward() }
                    endpointWasMissing = true
                    endpointKey = ""
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
                var targetReady = nextKey == endpointKey
                if (!targetReady) {
                    endpoint.primaryAddress?.hostAddress?.let { address ->
                        try {
                            withContext(Dispatchers.IO) { manager.replaceReverseTarget(address, endpoint.port) }
                            endpointKey = nextKey
                            targetReady = true
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (failure: Exception) {
                            withContext(Dispatchers.IO) { manager.disconnect() }
                        }
                    }
                }
                if (endpointWasMissing && targetReady) {
                    endpointWasMissing = false
                    updateStatus(
                        ForwardStatus(
                            forwardId = forwardId,
                            state = TunnelConnectionState.CONNECTED,
                            phase = TunnelPhase.CONNECTED,
                            message = getString(
                                R.string.status_connected_adb,
                                forward.name,
                                forward.reverseBindPort,
                                endpoint.primaryAddress?.hostAddress.orEmpty(),
                                endpoint.port,
                            ),
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
            this is PermanentTunnelException -> detail
            detail == getString(R.string.status_connection_lost) -> detail
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
