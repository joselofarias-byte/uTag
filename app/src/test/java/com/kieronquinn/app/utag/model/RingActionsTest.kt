package com.kieronquinn.app.utag.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RingActionsTest {

    @Test
    fun idleIsNotRingingAndAChannelIs() {
        assertFalse(RingActions.isRinging(null))
        assertTrue(RingActions.isRinging(RingChannel.BLUETOOTH))
        assertTrue(RingActions.isRinging(RingChannel.NETWORK))
    }

    @Test
    fun stopTriesTheChannelThatStartedThenTheOther() {
        assertEquals(
            listOf(RingChannel.BLUETOOTH, RingChannel.NETWORK),
            RingActions.stopOrder(RingChannel.BLUETOOTH)
        )
        assertEquals(
            listOf(RingChannel.NETWORK, RingChannel.BLUETOOTH),
            RingActions.stopOrder(RingChannel.NETWORK)
        )
    }

    @Test
    fun unknownSessionStopsBothWithoutStarting() {
        assertEquals(
            listOf(RingChannel.BLUETOOTH, RingChannel.NETWORK),
            RingActions.stopOrder(null)
        )
    }

}
