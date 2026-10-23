package com.kieronquinn.app.utag.model.find

/**
 * One row from the Find My Mobile `GET /devices` response this app already calls.
 * [tagDeviceId] is set only for a SmartTag that can be passed to the existing
 * `/trackers/geolocation` request. Other rows are listed and are not located.
 */
data class FindCatalogEntry(
    val kind: FindCatalogKind,
    val stableId: String,
    val title: String?,
    val locationType: String?,
    val tagDeviceId: String?,
    val fmmDeviceId: String?,
    val accountId: String?,
    val shareGeolocation: Boolean?,
    val mutualAgreement: Boolean?
)

enum class FindCatalogKind {
    TAG,
    FMM_DEVICE,
    SHARED_ACCOUNT,
    PERSON,
    UNKNOWN
}

data class TagLocateRequest(
    val stableId: String,
    val tagDeviceId: String
)
