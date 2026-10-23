package com.kieronquinn.app.utag.model.find

import com.kieronquinn.app.utag.networking.model.smartthings.DevicesResponse

/**
 * Splits the existing FMM device list into tags, other FMM devices, shared accounts
 * and members. Location calls stay limited to tags with a SmartThings device id.
 */
object FindDeviceClassifier {

    fun classify(response: DevicesResponse): List<FindCatalogEntry> {
        val seen = HashSet<String>()
        val devices = response.devices.map { device ->
            when {
                device.isTag() -> tag(device, seen)
                !device.fmmDevId.isNullOrBlank() -> fmm(device, seen)
                !device.saGuid.isNullOrBlank() -> account(device, seen)
                else -> unknown(device, seen)
            }
        }
        val people = response.members.orEmpty().map { member ->
            person(member, seen)
        }
        return devices + people
    }

    fun locateRequests(entries: List<FindCatalogEntry>): List<TagLocateRequest> {
        return entries.mapNotNull { entry ->
            val tagId = entry.tagDeviceId ?: return@mapNotNull null
            TagLocateRequest(entry.stableId, tagId)
        }
    }

    private fun tag(device: DevicesResponse.Device, seen: MutableSet<String>): FindCatalogEntry {
        val tagId = device.stDid?.takeIf { it.isNotBlank() }
        return FindCatalogEntry(
            kind = FindCatalogKind.TAG,
            stableId = unique(tagId ?: "tracker", seen),
            title = device.stDevName?.takeIf { it.isNotBlank() },
            locationType = device.locationType,
            tagDeviceId = tagId,
            fmmDeviceId = device.fmmDevId,
            accountId = device.saGuid,
            shareGeolocation = device.shareGeolocation,
            mutualAgreement = device.mutualAgreement
        )
    }

    private fun fmm(device: DevicesResponse.Device, seen: MutableSet<String>): FindCatalogEntry {
        val fmmId = device.fmmDevId.orEmpty()
        return FindCatalogEntry(
            kind = FindCatalogKind.FMM_DEVICE,
            stableId = unique("fmm:$fmmId", seen),
            title = device.stDevName?.takeIf { it.isNotBlank() },
            locationType = device.locationType,
            tagDeviceId = null,
            fmmDeviceId = fmmId,
            accountId = device.saGuid,
            shareGeolocation = device.shareGeolocation,
            mutualAgreement = device.mutualAgreement
        )
    }

    private fun account(device: DevicesResponse.Device, seen: MutableSet<String>): FindCatalogEntry {
        val accountId = device.saGuid.orEmpty()
        return FindCatalogEntry(
            kind = FindCatalogKind.SHARED_ACCOUNT,
            stableId = unique("account:$accountId", seen),
            title = device.stDevName?.takeIf { it.isNotBlank() },
            locationType = device.locationType,
            tagDeviceId = null,
            fmmDeviceId = null,
            accountId = accountId,
            shareGeolocation = device.shareGeolocation,
            mutualAgreement = device.mutualAgreement
        )
    }

    private fun unknown(device: DevicesResponse.Device, seen: MutableSet<String>): FindCatalogEntry {
        return FindCatalogEntry(
            kind = FindCatalogKind.UNKNOWN,
            stableId = unique("unknown:${device.locationType}:${device.stOwnerId}", seen),
            title = device.stDevName?.takeIf { it.isNotBlank() },
            locationType = device.locationType,
            tagDeviceId = null,
            fmmDeviceId = null,
            accountId = device.saGuid,
            shareGeolocation = device.shareGeolocation,
            mutualAgreement = device.mutualAgreement
        )
    }

    private fun person(
        member: DevicesResponse.Member,
        seen: MutableSet<String>
    ): FindCatalogEntry {
        return FindCatalogEntry(
            kind = FindCatalogKind.PERSON,
            stableId = unique("person:${member.saGuid}", seen),
            title = member.name.takeIf { it.isNotBlank() },
            locationType = null,
            tagDeviceId = null,
            fmmDeviceId = null,
            accountId = member.saGuid,
            shareGeolocation = null,
            mutualAgreement = null
        )
    }

    private fun unique(base: String, seen: MutableSet<String>): String {
        var candidate = base
        var suffix = 2
        while (!seen.add(candidate)) {
            candidate = "$base#$suffix"
            suffix++
        }
        return candidate
    }

}
