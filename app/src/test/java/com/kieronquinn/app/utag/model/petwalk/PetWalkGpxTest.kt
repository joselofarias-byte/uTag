package com.kieronquinn.app.utag.model.petwalk

import org.junit.Assert.*
import org.junit.Test
import javax.xml.parsers.DocumentBuilderFactory

class PetWalkGpxTest {
    @Test
    fun separatesSourcesGapsAndEscapesLabels() {
        val phone = GeoPoint(0.0, 0.0, 5.0, 1000)
        val tag = GeoPoint(1.0, 1.0, null, 500)
        val samples = listOf(
            PetWalkSampleDraft(1000, phone, tag, TagSampleStatus.LOCATED),
            PetWalkSampleDraft(2000, null, tag, TagSampleStatus.LOCATED),
            PetWalkSampleDraft(3000, phone.copy(longitude = 0.001, capturedAt = 3000), null, TagSampleStatus.NO_LOCATION)
        )
        val xml = PetWalkGpx.export("Luna <&> 🐈\u0001", samples)
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        val doc = factory.newDocumentBuilder().parse(xml.byteInputStream())
        assertEquals("Luna <&> 🐈", doc.getElementsByTagName("name").item(0).textContent)
        assertEquals(2, doc.getElementsByTagName("trk").length)
        assertEquals(3, doc.getElementsByTagName("trkseg").length)
        assertEquals(3, doc.getElementsByTagName("trkpt").length) // Cached tag fix deduplicated.
        assertTrue(xml.contains("1970-01-01T00:00:00.500Z")) // Actual tag fix time, not query time.
        assertFalse(xml.contains("NaN"))
    }

    @Test
    fun emptyAndInvalidSamplesProduceValidXmlWithoutTracks() {
        val sample = PetWalkSampleDraft(1, GeoPoint(Double.NaN, 0.0, 5.0, 1), null, TagSampleStatus.ERROR)
        val xml = PetWalkGpx.export("empty", listOf(sample))
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(xml.byteInputStream())
        assertEquals(0, doc.getElementsByTagName("trk").length)
    }
}
