package com.kieronquinn.app.utag.model

/**
 * Which path actually started the ring. The next tap has to stop that path,
 * and then the other one, so a network ring is not left running after a
 * Bluetooth stop (or the other way around).
 */
enum class RingChannel {
    BLUETOOTH,
    NETWORK
}

object RingActions {

    fun isRinging(active: RingChannel?): Boolean = active != null

    /**
     * Stop the channel we started, then the other one. Unknown state tries
     * Bluetooth first and then the network, and never starts a new ring.
     */
    fun stopOrder(active: RingChannel?): List<RingChannel> {
        return when (active) {
            RingChannel.BLUETOOTH -> listOf(RingChannel.BLUETOOTH, RingChannel.NETWORK)
            RingChannel.NETWORK -> listOf(RingChannel.NETWORK, RingChannel.BLUETOOTH)
            null -> listOf(RingChannel.BLUETOOTH, RingChannel.NETWORK)
        }
    }

}
