package com.yayo.sshtunneling.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.yayo.sshtunneling.service.TunnelForegroundService

class TunnelActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == ACTION_TOGGLE_TUNNEL) {
            TunnelForegroundService.start(context, TunnelForegroundService.ACTION_TOGGLE)
        }
    }

    companion object {
        const val ACTION_TOGGLE_TUNNEL = "com.yayo.sshtunneling.widget.TOGGLE_TUNNEL"
    }
}
