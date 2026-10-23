package com.kieronquinn.app.utag.utils.maps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OsmTilesTest {

    @Test
    fun tileUrlUsesTheStandardServerInsideThePolicyZoom() {
        assertEquals(
            "https://tile.openstreetmap.org/3/2/1.png",
            OsmTiles.tileUrl(3, 2, 1)
        )
    }

    @Test
    fun tileUrlRejectsZoomAndCoordinatesOutsideTheTileGrid() {
        assertNull(OsmTiles.tileUrl(20, 0, 0))
        assertNull(OsmTiles.tileUrl(-1, 0, 0))
        assertNull(OsmTiles.tileUrl(2, 4, 0))
        assertNull(OsmTiles.tileUrl(2, 0, -1))
    }

    @Test
    fun userAgentIdentifiesTheFork() {
        val agent = OsmTiles.userAgent("1.0.20")
        assertTrue(agent.contains("uTag/1.0.20"))
        assertTrue(agent.contains("https://github.com/joselofarias-byte/uTag"))
    }

}
