package com.kieronquinn.app.utag.model

/**
 * Where the launcher icon goes. The map is the default so Find is one tap away.
 * Setup and an explicit "open settings" from the map stay on the settings screen.
 */
enum class LaunchScreen {
    MAP,
    SETTINGS
}

object LaunchRouting {

    fun shouldOpenMap(
        screen: LaunchScreen,
        forceSettings: Boolean,
        isSignedIn: Boolean
    ): Boolean {
        if (!isSignedIn || forceSettings) return false
        return screen == LaunchScreen.MAP
    }

}
