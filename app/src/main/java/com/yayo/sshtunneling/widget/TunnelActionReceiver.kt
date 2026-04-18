package com.yayo.sshtunneling.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.yayo.sshtunneling.service.TunnelForegroundService

class TunnelActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == ACTION_TOGGLE_FORWARD) {
            val forwardId = intent.getStringExtra(TunnelForegroundService.EXTRA_FORWARD_ID) ?: return
            TunnelForegroundService.start(context, TunnelForegroundService.ACTION_TOGGLE, forwardId)
        }
    }

    companion object {
        const val ACTION_TOGGLE_FORWARD = "com.yayo.sshtunneling.widget.TOGGLE_FORWARD"
    }
}
