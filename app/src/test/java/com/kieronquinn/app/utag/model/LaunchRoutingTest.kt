package com.kieronquinn.app.utag.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LaunchRoutingTest {

    @Test
    fun signedInMapOpensTheMap() {
        assertTrue(LaunchRouting.shouldOpenMap(LaunchScreen.MAP, forceSettings = false, isSignedIn = true))
    }

    @Test
    fun settingsPreferenceAndForcedSettingsStayOnSettings() {
        assertFalse(LaunchRouting.shouldOpenMap(LaunchScreen.SETTINGS, forceSettings = false, isSignedIn = true))
        assertFalse(LaunchRouting.shouldOpenMap(LaunchScreen.MAP, forceSettings = true, isSignedIn = true))
    }

    @Test
    fun signedOutStaysOnSetup() {
        assertFalse(LaunchRouting.shouldOpenMap(LaunchScreen.MAP, forceSettings = false, isSignedIn = false))
    }

}
