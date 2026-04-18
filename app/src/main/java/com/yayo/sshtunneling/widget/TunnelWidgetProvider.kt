package com.yayo.sshtunneling.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.yayo.sshtunneling.R
import com.yayo.sshtunneling.data.TunnelPreferences
import com.yayo.sshtunneling.model.TunnelConnectionState

class TunnelWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { appWidgetId ->
            appWidgetManager.updateAppWidget(appWidgetId, buildRemoteViews(context))
        }
    }

    companion object {
        fun refreshAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, TunnelWidgetProvider::class.java)
            val ids = manager.getAppWidgetIds(component)
            if (ids.isNotEmpty()) {
                ids.forEach { appWidgetId ->
                    manager.updateAppWidget(appWidgetId, buildRemoteViews(context))
                }
            }
        }

        private fun buildRemoteViews(context: Context): RemoteViews {
            val status = TunnelPreferences(context).loadStatus()
            val views = RemoteViews(context.packageName, R.layout.widget_tunnel)
            val label = when (status.state) {
                TunnelConnectionState.CONNECTED -> context.getString(R.string.widget_button_disconnect)
                TunnelConnectionState.CONNECTING -> context.getString(R.string.widget_button_connecting)
                TunnelConnectionState.ERROR -> context.getString(R.string.widget_button_retry)
                TunnelConnectionState.IDLE -> context.getString(R.string.widget_button_connect)
            }

            views.setTextViewText(R.id.widget_status, when (status.state) {
                TunnelConnectionState.CONNECTED -> context.getString(R.string.widget_status_connected)
                TunnelConnectionState.CONNECTING -> context.getString(R.string.widget_status_connecting)
                TunnelConnectionState.ERROR -> context.getString(R.string.widget_status_error)
                TunnelConnectionState.IDLE -> context.getString(R.string.widget_status_idle)
            })
            views.setTextViewText(R.id.widget_message, status.message ?: context.getString(R.string.widget_message_default))
            views.setTextViewText(R.id.widget_button, label)
            views.setOnClickPendingIntent(R.id.widget_button, buildPendingIntent(context))
            return views
        }

        private fun buildPendingIntent(context: Context): PendingIntent {
            val intent = Intent(context, TunnelActionReceiver::class.java).setAction(TunnelActionReceiver.ACTION_TOGGLE_TUNNEL)
            return PendingIntent.getBroadcast(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
    }
}
