package com.yayo.sshtunneling.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.yayo.sshtunneling.MainActivity
import com.yayo.sshtunneling.R
import com.yayo.sshtunneling.data.TunnelPreferences
import com.yayo.sshtunneling.model.TunnelConnectionState
import com.yayo.sshtunneling.model.WidgetSlots
import com.yayo.sshtunneling.service.TunnelForegroundService
import com.yayo.sshtunneling.service.TunnelRuntime

class TunnelWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { appWidgetId ->
            appWidgetManager.updateAppWidget(appWidgetId, buildRemoteViews(context))
        }
    }

    companion object {
        private val cellIds = intArrayOf(
            R.id.widget_slot_0,
            R.id.widget_slot_1,
            R.id.widget_slot_2,
            R.id.widget_slot_3,
            R.id.widget_slot_4,
            R.id.widget_slot_5,
        )

        private val titleIds = intArrayOf(
            R.id.widget_slot_title_0,
            R.id.widget_slot_title_1,
            R.id.widget_slot_title_2,
            R.id.widget_slot_title_3,
            R.id.widget_slot_title_4,
            R.id.widget_slot_title_5,
        )

        fun refreshAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, TunnelWidgetProvider::class.java)
            manager.getAppWidgetIds(component).forEach { appWidgetId ->
                manager.updateAppWidget(appWidgetId, buildRemoteViews(context))
            }
        }

        private fun buildRemoteViews(context: Context): RemoteViews {
            val preferences = TunnelPreferences(context)
            val data = preferences.loadAppData()
            val statuses = TunnelRuntime.statuses.value.takeIf { it.isNotEmpty() } ?: preferences.loadStatuses()
            val views = RemoteViews(context.packageName, R.layout.widget_tunnel)

            repeat(WidgetSlots.COUNT) { slot ->
                val forward = data.forwards.firstOrNull { it.widgetSlot == slot }
                val host = data.hosts.firstOrNull { it.id == forward?.hostId }
                val status = forward?.let { statuses[it.id] }

                if (forward == null || host == null) {
                    views.setTextViewText(titleIds[slot], context.getString(R.string.widget_empty_title))
                    views.setInt(cellIds[slot], "setBackgroundResource", R.drawable.widget_slot_idle)
                    views.setOnClickPendingIntent(cellIds[slot], buildOpenAppIntent(context, slot))
                } else {
                    views.setTextViewText(titleIds[slot], forward.name)
                    views.setInt(cellIds[slot], "setBackgroundResource", backgroundFor(status?.state))
                    views.setOnClickPendingIntent(cellIds[slot], buildToggleIntent(context, forward.id, slot))
                }
            }

            return views
        }

        private fun buildToggleIntent(context: Context, forwardId: String, requestCode: Int): PendingIntent {
            val intent = Intent(context, TunnelActionReceiver::class.java)
                .setAction(TunnelActionReceiver.ACTION_TOGGLE_FORWARD)
                .putExtra(TunnelForegroundService.EXTRA_FORWARD_ID, forwardId)

            return PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        private fun buildOpenAppIntent(context: Context, requestCode: Int): PendingIntent {
            val intent = Intent(context, MainActivity::class.java)
            return PendingIntent.getActivity(
                context,
                100 + requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        private fun backgroundFor(state: TunnelConnectionState?): Int {
            return when (state) {
                TunnelConnectionState.CONNECTED -> R.drawable.widget_slot_connected
                TunnelConnectionState.CONNECTING -> R.drawable.widget_slot_connecting
                TunnelConnectionState.ERROR -> R.drawable.widget_slot_error
                TunnelConnectionState.IDLE, null -> R.drawable.widget_slot_idle
            }
        }
    }
}
