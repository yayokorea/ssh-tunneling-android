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

        private val detailIds = intArrayOf(
            R.id.widget_slot_detail_0,
            R.id.widget_slot_detail_1,
            R.id.widget_slot_detail_2,
            R.id.widget_slot_detail_3,
            R.id.widget_slot_detail_4,
            R.id.widget_slot_detail_5,
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
            val statuses = preferences.loadStatuses()
            val views = RemoteViews(context.packageName, R.layout.widget_tunnel)

            views.setTextViewText(R.id.widget_title, context.getString(R.string.widget_title))
            views.setTextViewText(R.id.widget_subtitle, context.getString(R.string.widget_subtitle))

            repeat(WidgetSlots.COUNT) { slot ->
                val forward = data.forwards.firstOrNull { it.widgetSlot == slot }
                val host = data.hosts.firstOrNull { it.id == forward?.hostId }
                val status = forward?.let { statuses[it.id] }

                if (forward == null || host == null) {
                    views.setTextViewText(titleIds[slot], context.getString(R.string.widget_empty_title))
                    views.setTextViewText(detailIds[slot], context.getString(R.string.widget_empty_detail))
                    views.setOnClickPendingIntent(cellIds[slot], buildOpenAppIntent(context, slot))
                } else {
                    val detail = when (status?.state) {
                        TunnelConnectionState.CONNECTED -> context.getString(R.string.widget_slot_connected, forward.localPort)
                        TunnelConnectionState.CONNECTING -> context.getString(R.string.widget_slot_connecting)
                        TunnelConnectionState.ERROR -> context.getString(R.string.widget_slot_error)
                        TunnelConnectionState.IDLE, null -> context.getString(
                            R.string.widget_slot_idle,
                            host.name,
                            forward.localPort,
                        )
                    }
                    views.setTextViewText(titleIds[slot], forward.name)
                    views.setTextViewText(detailIds[slot], detail)
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
    }
}
