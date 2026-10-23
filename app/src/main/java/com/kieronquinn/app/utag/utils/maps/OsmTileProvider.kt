package com.kieronquinn.app.utag.utils.maps

import android.content.Context
import com.google.android.gms.maps.model.Tile
import com.google.android.gms.maps.model.TileProvider
import okhttp3.Cache
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Loads OSM tiles on the Maps background thread. [UrlTileProvider] cannot set a
 * User-Agent, which the OSM tile usage policy requires.
 */
class OsmTileProvider(
    context: Context,
    versionName: String
) : TileProvider {

    private val client = OkHttpClient.Builder()
        .dispatcher(Dispatcher().apply {
            maxRequests = 2
            maxRequestsPerHost = 2
        })
        .cache(Cache(File(context.filesDir, "osm-tiles").apply { mkdirs() }, CACHE_BYTES))
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            chain.proceed(
                chain.request().newBuilder()
                    .header("User-Agent", OsmTiles.userAgent(versionName))
                    .build()
            )
        }
        .build()

    override fun getTile(x: Int, y: Int, zoom: Int): Tile {
        val url = OsmTiles.tileUrl(zoom, x, y) ?: return TileProvider.NO_TILE
        return try {
            client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                val bytes = response.body?.bytes()
                if (!response.isSuccessful || bytes == null || bytes.isEmpty()) {
                    TileProvider.NO_TILE
                } else {
                    Tile(OsmTiles.TILE_SIZE, OsmTiles.TILE_SIZE, bytes)
                }
            }
        } catch (_: Exception) {
            TileProvider.NO_TILE
        }
    }

    private companion object {
        private const val CACHE_BYTES = 20L * 1024L * 1024L
    }

}
