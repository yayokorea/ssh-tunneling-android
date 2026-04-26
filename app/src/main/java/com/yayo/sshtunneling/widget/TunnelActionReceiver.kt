package com.yayo.sshtunneling.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.yayo.sshtunneling.R
import com.yayo.sshtunneling.model.ForwardStatus
import com.yayo.sshtunneling.model.TunnelConnectionState
import com.yayo.sshtunneling.service.TunnelForegroundService
import com.yayo.sshtunneling.service.TunnelRuntime

class TunnelActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == ACTION_TOGGLE_FORWARD) {
            val forwardId = intent.getStringExtra(TunnelForegroundService.EXTRA_FORWARD_ID) ?: return
            TunnelForegroundService.start(
                context,
                TunnelForegroundService.ACTION_TOGGLE,
                forwardId,
                triggerHaptic = true,
            )
                .onFailure { error ->
                    TunnelRuntime.upsert(
                        context,
                        ForwardStatus(
                            forwardId = forwardId,
                            state = TunnelConnectionState.ERROR,
                            message = error.message ?: context.getString(R.string.status_service_start_failed),
                        ),
                    )
                }
        }
    }

    companion object {
        const val ACTION_TOGGLE_FORWARD = "com.yayo.sshtunneling.widget.TOGGLE_FORWARD"
    }
}
