package com.kieronquinn.app.utag.model.petwalk

import java.util.Locale
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Pure decisions for the local pet-walk recorder. A sample is kept when the phone or the tag
 * has moved, the tag status changed, or the previous kept sample is already stale. Tag distance
 * connects the fixes that actually arrived, so a gap becomes one straight segment.
 */
object PetWalkGeometry {

    const val MIN_MOVE_METRES = 5.0
    const val MIN_KEEP_INTERVAL_MS = 120_000L

    private const val EARTH_RADIUS_METRES = 6_371_000.0

    fun distanceMetres(from: GeoPoint, to: GeoPoint): Double {
        val latitudeDelta = Math.toRadians(to.latitude - from.latitude)
        val longitudeDelta = Math.toRadians(to.longitude - from.longitude)
        val startLatitude = Math.toRadians(from.latitude)
        val endLatitude = Math.toRadians(to.latitude)
        val haversine = sin(latitudeDelta / 2).pow(2) +
            cos(startLatitude) * cos(endLatitude) * sin(longitudeDelta / 2).pow(2)
        return 2 * EARTH_RADIUS_METRES * asin(sqrt(haversine))
    }

    fun pathDistanceMetres(points: List<GeoPoint?>): Double {
        return points.filterNotNull().zipWithNext().sumOf { (from, to) ->
            distanceMetres(from, to)
        }
    }

    fun shouldKeepSample(previous: PetWalkSampleDraft?, next: PetWalkSampleDraft): Boolean {
        if (next.phone == null && next.tag == null) return false
        if (previous == null) return true
        if (moved(previous.phone, next.phone) || moved(previous.tag, next.tag)) return true
        if (previous.tagStatus != next.tagStatus) return true
        return next.recordedAt - previous.recordedAt >= MIN_KEEP_INTERVAL_MS
    }

    fun summarize(
        startedAt: Long,
        endedAt: Long?,
        samples: List<PetWalkSampleDraft>,
        eventCount: Int,
        now: Long
    ): PetWalkSummary {
        val end = endedAt ?: now
        return PetWalkSummary(
            phoneDistanceMetres = pathDistanceMetres(samples.map { it.phone }),
            tagDistanceMetres = pathDistanceMetres(samples.map { it.tag }),
            sampleCount = samples.size,
            locatedTagSamples = samples.count {
                it.tagStatus == TagSampleStatus.LOCATED && it.tag != null
            },
            eventCount = eventCount,
            durationMillis = (end - startedAt).coerceAtLeast(0L)
        )
    }

    fun formatDistance(metres: Double): String {
        return if (metres >= 1000.0) {
            String.format(Locale.US, "%.2f km", metres / 1000.0)
        } else {
            String.format(Locale.US, "%.0f m", metres)
        }
    }

    fun formatDuration(millis: Long): String {
        val totalMinutes = (millis / 60_000L).coerceAtLeast(0L)
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
    }

    private fun moved(previous: GeoPoint?, next: GeoPoint?): Boolean {
        if (next == null) return false
        if (previous == null) return true
        return distanceMetres(previous, next) >= MIN_MOVE_METRES
    }

}
