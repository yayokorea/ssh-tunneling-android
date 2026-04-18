package com.yayo.sshtunneling.widget

import android.content.Context.VIBRATOR_MANAGER_SERVICE
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.yayo.sshtunneling.R
import com.yayo.sshtunneling.model.ForwardStatus
import com.yayo.sshtunneling.model.TunnelConnectionState
import com.yayo.sshtunneling.service.TunnelForegroundService
import com.yayo.sshtunneling.service.TunnelRuntime

class TunnelActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == ACTION_TOGGLE_FORWARD) {
            val forwardId = intent.getStringExtra(TunnelForegroundService.EXTRA_FORWARD_ID) ?: return
            triggerHapticFeedback(context)
            TunnelForegroundService.start(context, TunnelForegroundService.ACTION_TOGGLE, forwardId)
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

        private fun triggerHapticFeedback(context: Context) {
            runCatching {
                val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val manager = context.getSystemService(VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                    manager?.defaultVibrator
                } else {
                    @Suppress("DEPRECATION")
                    context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                }

                if (vibrator?.hasVibrator() == true) {
                    val effect = VibrationEffect.createOneShot(18L, VibrationEffect.DEFAULT_AMPLITUDE)
                    vibrator.vibrate(effect)
                }
            }
        }
    }
}
