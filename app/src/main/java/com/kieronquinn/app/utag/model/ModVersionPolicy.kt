package com.kieronquinn.app.utag.model

/**
 * Decides whether the SmartThings mod is current for this uTag build.
 *
 * The number baked into the patched APK ([installedVersionCode], from the capsule
 * provider) is the only version the module can report. This app build expects
 * [expectedVersionCode] (`XPOSED_CODE` from xposed-core). If those two match, the
 * mod is up to date even when SmartThings.json advertises a different code.
 *
 * That mismatch is what 1.0.20 shipped: the app and the JSON said 1020 while the
 * published APK still embeds 1019. Treating "installed == expected" as current
 * stops the update loop without rebuilding that APK.
 *
 * A real mod update for this fork means bumping xposed-core (so the app expects
 * the new code), SmartThings.json, and a newly patched APK together. Until the
 * app's expected code moves, a higher JSON version is ignored on purpose.
 */
object ModVersionPolicy {

    fun isUpToDate(installedVersionCode: Int?, expectedVersionCode: Int): Boolean {
        return installedVersionCode != null && installedVersionCode == expectedVersionCode
    }

    /**
     * Whether the updates UI and the update worker should offer [remoteVersionCode].
     *
     * Play-installed SmartThings cannot be replaced by the mod APK. A missing
     * installed code still offers the download (first install / module not
     * reporting yet). Once a code is reported, it is current when it matches
     * what this build expects, or when it already matches the remote JSON.
     */
    fun shouldOfferModUpdate(
        installedVersionCode: Int?,
        expectedVersionCode: Int,
        remoteVersionCode: Int?,
        wasInstalledOnPlay: Boolean
    ): Boolean {
        if (wasInstalledOnPlay) return false
        if (remoteVersionCode == null) return false
        if (installedVersionCode == null) return true
        if (isUpToDate(installedVersionCode, expectedVersionCode)) return false
        return remoteVersionCode > installedVersionCode
    }

}
