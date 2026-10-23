package com.kieronquinn.app.utag.repositories

import android.content.Context
import com.kieronquinn.app.utag.model.RingChannel

/**
 * Widget id -> tag, plus the channel that last started a ring for that tag.
 * Configuration runs in the main process and the widget click runs in :service,
 * so each read reloads the file.
 */
class RingWidgetStore(context: Context) {

    private val appContext = context.applicationContext

    fun deviceId(widgetId: Int): String? {
        return prefs().getString(keyDevice(widgetId), null)
    }

    fun label(widgetId: Int): String? {
        return prefs().getString(keyLabel(widgetId), null)
    }

    fun setWidget(widgetId: Int, deviceId: String, label: String) {
        prefs().edit()
            .putString(keyDevice(widgetId), deviceId)
            .putString(keyLabel(widgetId), label)
            .apply()
    }

    fun removeWidget(widgetId: Int) {
        prefs().edit()
            .remove(keyDevice(widgetId))
            .remove(keyLabel(widgetId))
            .apply()
    }

    fun session(deviceId: String): RingChannel? {
        val name = prefs().getString(keySession(deviceId), null) ?: return null
        return runCatching { RingChannel.valueOf(name) }.getOrNull()
    }

    fun setSession(deviceId: String, channel: RingChannel) {
        prefs().edit().putString(keySession(deviceId), channel.name).apply()
    }

    fun clearSession(deviceId: String) {
        prefs().edit().remove(keySession(deviceId)).apply()
    }

    @Suppress("DEPRECATION")
    private fun prefs() = appContext.getSharedPreferences(
        PREFS,
        Context.MODE_PRIVATE or Context.MODE_MULTI_PROCESS
    )

    private fun keyDevice(widgetId: Int) = "device_$widgetId"
    private fun keyLabel(widgetId: Int) = "label_$widgetId"
    private fun keySession(deviceId: String) = "session_$deviceId"

    companion object {
        private const val PREFS = "ring_widget"
    }

}
