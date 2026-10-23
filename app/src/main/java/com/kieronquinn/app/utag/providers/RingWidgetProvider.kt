package com.kieronquinn.app.utag.providers

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import com.kieronquinn.app.utag.R
import com.kieronquinn.app.utag.model.RingActions
import com.kieronquinn.app.utag.repositories.RingController
import com.kieronquinn.app.utag.repositories.RingWidgetStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class RingWidgetProvider : AppWidgetProvider(), KoinComponent {

    private val store by inject<RingWidgetStore>()
    private val controller by inject<RingController>()

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        appWidgetIds.forEach { render(context, appWidgetManager, it, store) }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        appWidgetIds.forEach { store.removeWidget(it) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_TOGGLE) {
            super.onReceive(context, intent)
            return
        }
        val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val deviceId = store.deviceId(widgetId) ?: return@launch
                controller.toggle(deviceId)
                renderAll(context, store)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_TOGGLE = "com.kieronquinn.app.utag.action.RING_WIDGET_TOGGLE"

        fun renderAll(context: Context, store: RingWidgetStore) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                ComponentName(context, RingWidgetProvider::class.java)
            )
            ids.forEach { render(context, manager, it, store) }
        }

        fun render(
            context: Context,
            manager: AppWidgetManager,
            widgetId: Int,
            store: RingWidgetStore
        ) {
            val deviceId = store.deviceId(widgetId)
            val label = store.label(widgetId)
                ?: context.getString(R.string.ring_widget_unset)
            val ringing = deviceId != null && RingActions.isRinging(store.session(deviceId))
            val action = context.getString(
                if (ringing) R.string.ring_widget_stop else R.string.ring_widget_ring
            )
            val views = RemoteViews(context.packageName, R.layout.widget_ring).apply {
                setTextViewText(R.id.ring_widget_label, label)
                setTextViewText(R.id.ring_widget_action, action)
                setOnClickPendingIntent(R.id.ring_widget_root, clickIntent(context, widgetId))
            }
            manager.updateAppWidget(widgetId, views)
        }

        private fun clickIntent(context: Context, widgetId: Int): PendingIntent {
            val intent = Intent(context, RingWidgetProvider::class.java).apply {
                action = ACTION_TOGGLE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                data = Uri.parse("utag://ring-widget/$widgetId")
            }
            return PendingIntent.getBroadcast(
                context,
                widgetId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }

}
