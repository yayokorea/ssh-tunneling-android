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
import com.yayo.sshtunneling.model.TunnelConnectionState
import com.yayo.sshtunneling.model.TunnelStatus
import com.yayo.sshtunneling.widget.TunnelWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class TunnelForegroundService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var tunnelManager: SshTunnelManager? = null
    private var connectJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        TunnelRuntime.initialize(applicationContext)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CONNECT -> connectTunnel()
            ACTION_DISCONNECT -> disconnectTunnel()
            ACTION_TOGGLE -> toggleTunnel()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        tunnelManager?.disconnect()
        tunnelManager = null
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun toggleTunnel() {
        val currentStatus = TunnelRuntime.status.value.state
        if (currentStatus == TunnelConnectionState.CONNECTED || currentStatus == TunnelConnectionState.CONNECTING) {
            disconnectTunnel()
        } else {
            connectTunnel()
        }
    }

    private fun connectTunnel() {
        if (connectJob?.isActive == true || tunnelManager?.isConnected() == true) {
            return
        }

        val profile = TunnelPreferences(applicationContext).loadProfile()
        if (!profile.isComplete()) {
            updateStatus(TunnelStatus(TunnelConnectionState.ERROR, getString(R.string.status_profile_incomplete)))
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }

        startForeground(NOTIFICATION_ID, buildNotification(getString(R.string.status_connecting)))
        updateStatus(TunnelStatus(TunnelConnectionState.CONNECTING, getString(R.string.status_connecting_message)))

        connectJob = serviceScope.launch {
            runCatching {
                val manager = SshTunnelManager(profile)
                val localPort = manager.connect()
                tunnelManager = manager
                val message = getString(R.string.status_connected_message, localPort, profile.remoteHost, profile.remotePort)
                updateStatus(TunnelStatus(TunnelConnectionState.CONNECTED, message))
                refreshNotification(message)
            }.onFailure { error ->
                tunnelManager?.disconnect()
                tunnelManager = null
                val message = error.message ?: getString(R.string.status_unknown_error)
                updateStatus(TunnelStatus(TunnelConnectionState.ERROR, message))
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    private fun disconnectTunnel(updateStatus: Boolean = true) {
        connectJob?.cancel()
        connectJob = null
        tunnelManager?.disconnect()
        tunnelManager = null
        if (updateStatus) {
            updateStatus(TunnelStatus(TunnelConnectionState.IDLE, getString(R.string.status_idle_message)))
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun updateStatus(status: TunnelStatus) {
        TunnelRuntime.update(applicationContext, status)
        TunnelWidgetProvider.refreshAll(applicationContext)
    }

    private fun refreshNotification(message: String) {
        val manager = ContextCompat.getSystemService(this, NotificationManager::class.java) ?: return
        manager.notify(NOTIFICATION_ID, buildNotification(message))
    }

    private fun buildNotification(message: String): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, TunnelForegroundService::class.java).setAction(ACTION_DISCONNECT),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_tunnel)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(message)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .addAction(R.drawable.ic_stop, getString(R.string.disconnect), stopIntent)
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

        fun start(context: Context, action: String) {
            val intent = Intent(context, TunnelForegroundService::class.java).setAction(action)
            if (action == ACTION_DISCONNECT) {
                context.startService(intent)
            } else {
                ContextCompat.startForegroundService(context, intent)
            }
        }
    }
}
