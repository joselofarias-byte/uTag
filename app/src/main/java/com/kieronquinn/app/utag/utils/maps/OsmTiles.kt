package com.kieronquinn.app.utag.utils.maps

/**
 * OpenStreetMap standard tile layer. Public tile servers are for light use:
 * this client caches tiles and keeps concurrency low. A widely distributed
 * build should point [tileUrl] at a dedicated tile server.
 */
object OsmTiles {

    const val MAX_ZOOM = 19
    const val TILE_SIZE = 256
    const val ATTRIBUTION = "© OpenStreetMap contributors"
    const val COPYRIGHT_URL = "https://www.openstreetmap.org/copyright"

    fun tileUrl(zoom: Int, x: Int, y: Int): String? {
        if (zoom !in 0..MAX_ZOOM) return null
        val maxIndex = 1 shl zoom
        if (x !in 0 until maxIndex || y !in 0 until maxIndex) return null
        return "https://tile.openstreetmap.org/$zoom/$x/$y.png"
    }

    fun userAgent(versionName: String): String {
        return "uTag/$versionName (https://github.com/joselofarias-byte/uTag)"
    }

}
