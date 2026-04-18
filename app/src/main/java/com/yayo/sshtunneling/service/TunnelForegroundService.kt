package com.yayo.sshtunneling.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.yayo.sshtunneling.MainActivity
import com.yayo.sshtunneling.R
import com.yayo.sshtunneling.data.TunnelPreferences
import com.yayo.sshtunneling.model.ForwardStatus
import com.yayo.sshtunneling.model.TunnelConnectionState
import com.yayo.sshtunneling.widget.TunnelWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class TunnelForegroundService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val tunnelManagers = mutableMapOf<String, SshTunnelManager>()
    private val connectJobs = mutableMapOf<String, Job>()

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        TunnelRuntime.initialize(applicationContext)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CONNECT -> intent.getStringExtra(EXTRA_FORWARD_ID)?.let(::connectTunnel)
            ACTION_DISCONNECT -> intent.getStringExtra(EXTRA_FORWARD_ID)?.let(::disconnectTunnel)
            ACTION_TOGGLE -> intent.getStringExtra(EXTRA_FORWARD_ID)?.let(::toggleTunnel)
            ACTION_DISCONNECT_ALL -> disconnectAllTunnels()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        connectJobs.values.forEach { it.cancel() }
        tunnelManagers.values.forEach { it.disconnect() }
        connectJobs.clear()
        tunnelManagers.clear()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun toggleTunnel(forwardId: String) {
        val state = TunnelRuntime.statuses.value[forwardId]?.state
        if (state == TunnelConnectionState.CONNECTED || state == TunnelConnectionState.CONNECTING) {
            disconnectTunnel(forwardId)
        } else {
            connectTunnel(forwardId)
        }
    }

    private fun connectTunnel(forwardId: String) {
        if (connectJobs[forwardId]?.isActive == true || tunnelManagers[forwardId]?.isConnected() == true) {
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

        startForeground(NOTIFICATION_ID, buildNotification())
        updateStatus(
            ForwardStatus(
                forwardId = forwardId,
                state = TunnelConnectionState.CONNECTING,
                message = getString(R.string.status_connecting_item, forward.name),
            )
        )

        connectJobs[forwardId] = serviceScope.launch {
            val result = runCatching {
                val manager = SshTunnelManager(host, forward)
                val localPort = manager.connect()
                tunnelManagers[forwardId] = manager
                updateStatus(
                    ForwardStatus(
                        forwardId = forwardId,
                        state = TunnelConnectionState.CONNECTED,
                        message = getString(
                            R.string.status_connected_item,
                            forward.name,
                            localPort,
                            forward.remoteHost,
                            forward.remotePort,
                        ),
                    )
                )
                refreshNotification()
            }
            connectJobs.remove(forwardId)
            result.onFailure { error ->
                tunnelManagers.remove(forwardId)?.disconnect()
                updateStatus(
                    ForwardStatus(
                        forwardId = forwardId,
                        state = TunnelConnectionState.ERROR,
                        message = error.message ?: getString(R.string.status_unknown_error),
                    )
                )
                stopIfIdle()
            }
        }
    }

    private fun disconnectTunnel(forwardId: String, updateIdleState: Boolean = true) {
        connectJobs.remove(forwardId)?.cancel()
        tunnelManagers.remove(forwardId)?.disconnect()
        if (updateIdleState) {
            updateStatus(
                ForwardStatus(
                    forwardId = forwardId,
                    state = TunnelConnectionState.IDLE,
                    message = getString(R.string.status_idle_item),
                )
            )
        }
        stopIfIdle()
    }

    private fun disconnectAllTunnels() {
        val forwardIds = (tunnelManagers.keys + connectJobs.keys).toSet()
        forwardIds.forEach { forwardId ->
            disconnectTunnel(forwardId)
        }
    }

    private fun stopIfIdle() {
        if (tunnelManagers.isEmpty() && connectJobs.values.none { it.isActive }) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        } else {
            refreshNotification()
        }
    }

    private fun updateStatus(status: ForwardStatus) {
        TunnelRuntime.upsert(applicationContext, status)
        TunnelWidgetProvider.refreshAll(applicationContext)
    }

    private fun refreshNotification() {
        val manager = ContextCompat.getSystemService(this, NotificationManager::class.java) ?: return
        manager.notify(NOTIFICATION_ID, buildNotification())
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

        const val ACTION_CONNECT = "com.yayo.sshtunneling.action.CONNECT"
        const val ACTION_DISCONNECT = "com.yayo.sshtunneling.action.DISCONNECT"
        const val ACTION_TOGGLE = "com.yayo.sshtunneling.action.TOGGLE"
        const val ACTION_DISCONNECT_ALL = "com.yayo.sshtunneling.action.DISCONNECT_ALL"
        const val EXTRA_FORWARD_ID = "extra_forward_id"

        fun start(context: Context, action: String, forwardId: String? = null) {
            val intent = Intent(context, TunnelForegroundService::class.java).setAction(action)
            if (forwardId != null) {
                intent.putExtra(EXTRA_FORWARD_ID, forwardId)
            }
            if (action == ACTION_DISCONNECT || action == ACTION_DISCONNECT_ALL) {
                context.startService(intent)
            } else {
                ContextCompat.startForegroundService(context, intent)
            }
        }
    }
}
