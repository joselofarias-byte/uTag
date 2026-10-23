package com.kieronquinn.app.utag.model.petwalk

/**
 * A coordinate captured for a pet walk. [capturedAt] is when that fix was produced,
 * which for a tag is the network's last update and can be older than the sample time.
 */
data class GeoPoint(
    val latitude: Double,
    val longitude: Double,
    val accuracyMetres: Double?,
    val capturedAt: Long
)

enum class TagSampleStatus {
    LOCATED,
    NO_LOCATION,
    PIN_REQUIRED,
    NOT_ALLOWED,
    ERROR
}

enum class PetWalkEventType {
    TOILET,
    INTERACTION,
    NOTE
}

data class PetWalkSampleDraft(
    val recordedAt: Long,
    val phone: GeoPoint?,
    val tag: GeoPoint?,
    val tagStatus: TagSampleStatus
)

data class PetWalkSummary(
    val phoneDistanceMetres: Double,
    val tagDistanceMetres: Double,
    val sampleCount: Int,
    val locatedTagSamples: Int,
    val eventCount: Int,
    val durationMillis: Long
)
