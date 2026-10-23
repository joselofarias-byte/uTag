package com.kieronquinn.app.utag.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModVersionPolicyTest {

    @Test
    fun installedCodeMatchingExpectedIsUpToDateEvenWhenJsonDisagrees() {
        assertTrue(ModVersionPolicy.isUpToDate(1019, 1019))
        assertFalse(
            ModVersionPolicy.shouldOfferModUpdate(
                installedVersionCode = 1019,
                expectedVersionCode = 1019,
                remoteVersionCode = 1020,
                wasInstalledOnPlay = false
            )
        )
    }

    @Test
    fun matchingRemoteAndInstalledDoesNotOfferAnUpdate() {
        assertFalse(
            ModVersionPolicy.shouldOfferModUpdate(
                installedVersionCode = 1019,
                expectedVersionCode = 1020,
                remoteVersionCode = 1019,
                wasInstalledOnPlay = false
            )
        )
    }

    @Test
    fun offersUpdateWhenInstalledCodeIsBehindBothExpectedAndRemote() {
        assertFalse(ModVersionPolicy.isUpToDate(1018, 1019))
        assertTrue(
            ModVersionPolicy.shouldOfferModUpdate(
                installedVersionCode = 1018,
                expectedVersionCode = 1019,
                remoteVersionCode = 1019,
                wasInstalledOnPlay = false
            )
        )
    }

    @Test
    fun missingInstalledCodeStillOffersTheDownload() {
        assertTrue(
            ModVersionPolicy.shouldOfferModUpdate(
                installedVersionCode = null,
                expectedVersionCode = 1019,
                remoteVersionCode = 1019,
                wasInstalledOnPlay = false
            )
        )
    }

    @Test
    fun neverOffersADowngrade() {
        assertFalse(ModVersionPolicy.shouldOfferModUpdate(1021, 1020, 1019, false))
    }

    @Test
    fun playInstallAndMissingRemoteNeverOffer() {
        assertFalse(
            ModVersionPolicy.shouldOfferModUpdate(
                installedVersionCode = 1019,
                expectedVersionCode = 1020,
                remoteVersionCode = 1020,
                wasInstalledOnPlay = true
            )
        )
        assertFalse(
            ModVersionPolicy.shouldOfferModUpdate(
                installedVersionCode = 1018,
                expectedVersionCode = 1019,
                remoteVersionCode = null,
                wasInstalledOnPlay = false
            )
        )
    }

}
