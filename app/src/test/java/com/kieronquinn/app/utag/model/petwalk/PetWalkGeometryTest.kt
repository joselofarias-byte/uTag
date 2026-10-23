package com.kieronquinn.app.utag.model.petwalk

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PetWalkGeometryTest {

    @Test
    fun samePointHasNoDistance() {
        val point = point(51.5, -0.12, 0L)
        assertEquals(0.0, PetWalkGeometry.distanceMetres(point, point), 0.001)
    }

    @Test
    fun oneDegreeOfLongitudeAtEquatorIsAbout111Kilometres() {
        val distance = PetWalkGeometry.distanceMetres(
            point(0.0, 0.0, 0L),
            point(0.0, 1.0, 1L)
        )
        assertEquals(111_194.9, distance, 50.0)
    }

    @Test
    fun pathSkipsMissingPointsAndStillJoinsTheFixesThatExist() {
        val distance = PetWalkGeometry.pathDistanceMetres(
            listOf(
                point(0.0, 0.0, 0L),
                null,
                point(0.0, 1.0, 1L)
            )
        )
        assertEquals(111_194.9, distance, 50.0)
    }

    @Test
    fun firstFixIsKeptAndAStationaryRepeatIsDropped() {
        val first = sample(phone = point(10.0, 10.0, 0L), recordedAt = 1_000L)
        assertTrue(PetWalkGeometry.shouldKeepSample(null, first))
        val repeat = first.copy(recordedAt = 20_000L)
        assertFalse(PetWalkGeometry.shouldKeepSample(first, repeat))
    }

    @Test
    fun sampleWithNoPhoneAndNoTagIsDropped() {
        val empty = sample(recordedAt = 1_000L)
        assertFalse(PetWalkGeometry.shouldKeepSample(null, empty))
    }

    @Test
    fun movementStatusChangeOrStaleIntervalKeepsTheSample() {
        val first = sample(
            phone = point(10.0, 10.0, 0L),
            tag = point(10.0, 10.0, 0L),
            recordedAt = 0L
        )
        val movedPhone = first.copy(
            recordedAt = 30_000L,
            phone = point(10.001, 10.0, 30_000L)
        )
        assertTrue(PetWalkGeometry.shouldKeepSample(first, movedPhone))
        val statusChanged = first.copy(
            recordedAt = 30_000L,
            tag = null,
            tagStatus = TagSampleStatus.NO_LOCATION
        )
        assertTrue(PetWalkGeometry.shouldKeepSample(first, statusChanged))
        val stale = first.copy(recordedAt = PetWalkGeometry.MIN_KEEP_INTERVAL_MS)
        assertTrue(PetWalkGeometry.shouldKeepSample(first, stale))
    }

    @Test
    fun summaryUsesPhoneAndTagPathsSeparately() {
        val samples = listOf(
            sample(phone = point(0.0, 0.0, 0L), tag = point(0.0, 0.0, 0L), recordedAt = 0L),
            sample(
                phone = point(0.0, 1.0, 1L),
                tag = null,
                tagStatus = TagSampleStatus.NO_LOCATION,
                recordedAt = 60_000L
            ),
            sample(phone = point(0.0, 2.0, 2L), tag = point(0.0, 1.0, 2L), recordedAt = 120_000L)
        )
        val summary = PetWalkGeometry.summarize(
            startedAt = 0L,
            endedAt = 180_000L,
            samples = samples,
            eventCount = 2,
            now = 999L
        )
        assertEquals(222_389.8, summary.phoneDistanceMetres, 100.0)
        assertEquals(111_194.9, summary.tagDistanceMetres, 50.0)
        assertEquals(3, summary.sampleCount)
        assertEquals(2, summary.locatedTagSamples)
        assertEquals(2, summary.eventCount)
        assertEquals(180_000L, summary.durationMillis)
        assertEquals("3m", PetWalkGeometry.formatDuration(summary.durationMillis))
        assertTrue(PetWalkGeometry.formatDistance(summary.phoneDistanceMetres).endsWith("km"))
    }

    @Test
    fun openWalkDurationUsesTheClockPassedIn() {
        val summary = PetWalkGeometry.summarize(
            startedAt = 1_000L,
            endedAt = null,
            samples = emptyList(),
            eventCount = 0,
            now = 61_000L
        )
        assertEquals(60_000L, summary.durationMillis)
        assertEquals("1m", PetWalkGeometry.formatDuration(summary.durationMillis))
        assertEquals("12 m", PetWalkGeometry.formatDistance(12.4))
        assertEquals("1.50 km", PetWalkGeometry.formatDistance(1500.0))
    }

    private fun point(latitude: Double, longitude: Double, capturedAt: Long): GeoPoint {
        return GeoPoint(latitude, longitude, accuracyMetres = 5.0, capturedAt = capturedAt)
    }

    private fun sample(
        phone: GeoPoint? = null,
        tag: GeoPoint? = null,
        tagStatus: TagSampleStatus = if (tag == null) TagSampleStatus.NO_LOCATION else TagSampleStatus.LOCATED,
        recordedAt: Long
    ): PetWalkSampleDraft {
        return PetWalkSampleDraft(recordedAt, phone, tag, tagStatus)
    }

}
