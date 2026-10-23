package com.kieronquinn.app.utag.model.petwalk

import java.util.Locale
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Pure decisions for the local pet-walk recorder. A sample is kept when the phone or the tag
 * has moved, the tag status changed, or the previous kept sample is already stale. Missing, invalid or stale fixes split the route instead of inventing a straight segment.
 */
object PetWalkGeometry {

    const val MAX_SEGMENT_GAP_MS = 180_000L
    const val MAX_PHONE_AGE_MS = 30_000L

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
        return 2 * EARTH_RADIUS_METRES * asin(sqrt(haversine.coerceIn(0.0, 1.0)))
    }

    fun isValid(point: GeoPoint): Boolean {
        return point.latitude.isFinite() && point.longitude.isFinite() &&
            point.latitude in -90.0..90.0 && point.longitude in -180.0..180.0 &&
            (point.accuracyMetres == null ||
                (point.accuracyMetres.isFinite() && point.accuracyMetres >= 0.0))
    }

    fun isFreshPhone(point: GeoPoint, now: Long): Boolean {
        val accuracy = point.accuracyMetres ?: return false
        return isValid(point) && accuracy in 0.0..50.0 &&
            point.capturedAt <= now && now - point.capturedAt <= MAX_PHONE_AGE_MS
    }

    /** Used by distance, the map and GPX so they agree about discontinuities. */
    fun segments(points: List<GeoPoint?>): List<List<GeoPoint>> {
        val segments = mutableListOf<MutableList<GeoPoint>>()
        var current: MutableList<GeoPoint>? = null
        for (point in points) {
            if (point == null || !isValid(point)) {
                current = null
                continue
            }
            val previous = current?.lastOrNull()
            if (previous != null && point.capturedAt == previous.capturedAt &&
                point.latitude == previous.latitude && point.longitude == previous.longitude) continue
            if (previous != null && (point.capturedAt <= previous.capturedAt ||
                        point.capturedAt - previous.capturedAt > MAX_SEGMENT_GAP_MS)) current = null
            if (current == null) {
                current = mutableListOf()
                segments.add(current)
            }
            current.add(point)
        }
        return segments
    }

    fun pathDistanceMetres(points: List<GeoPoint?>): Double {
        return segments(points).sumOf { segment ->
            segment.zipWithNext().sumOf { (from, to) -> distanceMetres(from, to) }
        }
    }

    fun shouldKeepSample(previous: PetWalkSampleDraft?, next: PetWalkSampleDraft): Boolean {
        if (previous != null && next.recordedAt <= previous.recordedAt) return false
        if (next.phone == null && next.tag == null) {
            // Preserve a loss of fixes as a break in both routes.
            return previous != null && (previous.phone != null || previous.tag != null ||
                previous.tagStatus != next.tagStatus)
        }
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
