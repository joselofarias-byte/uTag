package com.kieronquinn.app.utag.repositories

import android.content.Context
import com.kieronquinn.app.utag.components.bluetooth.RemoteTagConnection
import com.kieronquinn.app.utag.components.bluetooth.RemoteTagConnection.RingResult
import com.kieronquinn.app.utag.model.RingActions
import com.kieronquinn.app.utag.model.RingChannel
import com.kieronquinn.app.utag.service.UTagForegroundService
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * One-tap ring / stop for a chosen tag. Uses the same [RemoteTagConnection]
 * path as the in-app ring dialog. A second tap always tries to stop, including
 * the channel we did not start on, and does not start another ring if stop fails.
 */
class RingController(
    private val context: Context,
    private val store: RingWidgetStore
) {

    private val lock = Mutex()

    suspend fun toggle(deviceId: String): RingOutcome = lock.withLock {
        if (deviceId.isBlank()) return@withLock RingOutcome.FAILED
        try {
            UTagForegroundService.startIfNeeded(context)
        } catch (_: Exception) {
            // Binding below still tries. Background starts can be rejected.
        }
        val connection = RemoteTagConnection(deviceId)
        try {
            val active = store.session(deviceId)
            if (RingActions.isRinging(active)) {
                stop(connection, deviceId, active)
            } else {
                start(connection, deviceId)
            }
        } finally {
            connection.close()
        }
    }

    private suspend fun start(connection: RemoteTagConnection, deviceId: String): RingOutcome {
        return when (val result = connection.startRinging()) {
            is RingResult.SuccessBluetooth -> {
                store.setSession(deviceId, RingChannel.BLUETOOTH)
                RingOutcome.RINGING
            }
            is RingResult.SuccessNetwork -> {
                store.setSession(deviceId, RingChannel.NETWORK)
                RingOutcome.RINGING
            }
            else -> RingOutcome.FAILED
        }
    }

    private suspend fun stop(
        connection: RemoteTagConnection,
        deviceId: String,
        active: RingChannel?
    ): RingOutcome {
        var stopped = false
        for (channel in RingActions.stopOrder(active)) {
            val ok = when (channel) {
                RingChannel.BLUETOOTH -> connection.stopRingingBluetooth()
                RingChannel.NETWORK -> connection.stopRingingNetwork()
            }
            if (ok) stopped = true
        }
        return if (stopped) {
            store.clearSession(deviceId)
            RingOutcome.STOPPED
        } else {
            // Keep the session so the next tap tries to stop again.
            RingOutcome.STOP_FAILED
        }
    }

}

enum class RingOutcome {
    RINGING,
    STOPPED,
    STOP_FAILED,
    FAILED
}
