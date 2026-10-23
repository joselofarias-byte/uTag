package com.kieronquinn.app.utag.model.find

import com.kieronquinn.app.utag.networking.model.smartthings.DevicesResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FindDeviceClassifierTest {

    @Test
    fun trackerWithSmartThingsIdIsTheOnlyRowThatCanBeLocated() {
        val catalog = FindDeviceClassifier.classify(
            response(
                device(
                    locationType = "TRACKER",
                    stDid = "tag-1",
                    stDevName = "Luna",
                    shareGeolocation = true
                ),
                device(
                    locationType = "TRACKER",
                    stDid = null,
                    stDevName = "Broken"
                ),
                device(
                    locationType = "FMM",
                    fmmDevId = "phone-9",
                    stDevName = "Galaxy S24"
                ),
                device(
                    locationType = "WEARABLE",
                    fmmDevId = "watch-2",
                    stDevName = "Galaxy Watch"
                ),
                device(
                    locationType = "BUDS",
                    saGuid = "buds-user",
                    stDevName = "Galaxy Buds3"
                ),
                members = listOf(member("friend", "Ana"))
            )
        )

        val tag = catalog.first { it.title == "Luna" }
        assertEquals(FindCatalogKind.TAG, tag.kind)
        assertEquals("tag-1", tag.tagDeviceId)

        val broken = catalog.first { it.title == "Broken" }
        assertEquals(FindCatalogKind.TAG, broken.kind)
        assertNull(broken.tagDeviceId)

        val phone = catalog.first { it.title == "Galaxy S24" }
        assertEquals(FindCatalogKind.FMM_DEVICE, phone.kind)
        assertEquals("phone-9", phone.fmmDeviceId)
        assertNull(phone.tagDeviceId)

        val watch = catalog.first { it.fmmDeviceId == "watch-2" }
        assertEquals(FindCatalogKind.FMM_DEVICE, watch.kind)
        assertEquals("WEARABLE", watch.locationType)
        assertNull(watch.tagDeviceId)

        val buds = catalog.first { it.title == "Galaxy Buds3" }
        assertEquals(FindCatalogKind.SHARED_ACCOUNT, buds.kind)
        assertNull(buds.tagDeviceId)

        val person = catalog.first { it.title == "Ana" }
        assertEquals(FindCatalogKind.PERSON, person.kind)
        assertEquals("friend", person.accountId)
        assertNull(person.tagDeviceId)
        assertNull(person.locationType)

        val requests = FindDeviceClassifier.locateRequests(catalog)
        assertEquals(listOf("tag-1"), requests.map { it.tagDeviceId })
        assertTrue(requests.all { request ->
            catalog.first { it.stableId == request.stableId }.kind == FindCatalogKind.TAG
        })
    }

    @Test
    fun unknownDeviceWithoutIdsIsListedAndNotLocated() {
        val catalog = FindDeviceClassifier.classify(
            response(
                device(locationType = "SOMETHING_ELSE", stOwnerId = "owner-a")
            )
        )
        assertEquals(1, catalog.size)
        assertEquals(FindCatalogKind.UNKNOWN, catalog.single().kind)
        assertEquals("SOMETHING_ELSE", catalog.single().locationType)
        assertTrue(FindDeviceClassifier.locateRequests(catalog).isEmpty())
    }

    @Test
    fun duplicateIdsStayDistinctSoEachRowCanShowItsOwnResult() {
        val catalog = FindDeviceClassifier.classify(
            response(
                device(locationType = "FMM", fmmDevId = "same", stDevName = "One"),
                device(locationType = "FMM", fmmDevId = "same", stDevName = "Two")
            )
        )
        assertEquals(2, catalog.map { it.stableId }.distinct().size)
        assertTrue(FindDeviceClassifier.locateRequests(catalog).isEmpty())
    }

    private fun response(
        vararg devices: DevicesResponse.Device,
        members: List<DevicesResponse.Member>? = null
    ): DevicesResponse {
        return DevicesResponse(
            ownerId = "owner",
            devices = devices.toList(),
            favourites = emptyList(),
            favouritesTimestamp = "0",
            members = members
        )
    }

    private fun device(
        locationType: String,
        stDid: String? = null,
        fmmDevId: String? = null,
        saGuid: String? = null,
        stDevName: String? = null,
        stOwnerId: String = "owner",
        shareGeolocation: Boolean? = null,
        mutualAgreement: Boolean? = null
    ): DevicesResponse.Device {
        return DevicesResponse.Device(
            saGuid = saGuid,
            fmmDevId = fmmDevId,
            locationType = locationType,
            stDevName = stDevName,
            stOwnerId = stOwnerId,
            stDid = stDid,
            shareGeolocation = shareGeolocation,
            mutualAgreement = mutualAgreement
        )
    }

    private fun member(saGuid: String, name: String): DevicesResponse.Member {
        return DevicesResponse.Member(
            saGuid = saGuid,
            stOwnerId = "owner",
            name = name
        )
    }

}
