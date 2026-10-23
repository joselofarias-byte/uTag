package com.kieronquinn.app.utag.utils.maps

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.text.Html
import android.text.method.LinkMovementMethod
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.TileOverlay
import com.google.android.gms.maps.model.TileOverlayOptions
import com.google.android.gms.maps.model.TileProvider
import com.kieronquinn.app.utag.R
import com.kieronquinn.app.utag.repositories.SettingsRepository.MapStyle

/**
 * OSM is a [TileOverlay] on an otherwise empty Google map. Other styles stay
 * native Google map types and drop the overlay.
 */
fun GoogleMap.applyUTagMapStyle(
    style: MapStyle,
    current: TileOverlay?,
    tiles: TileProvider
): TileOverlay? {
    return if (style == MapStyle.OSM) {
        setMapStyle(null)
        mapType = GoogleMap.MAP_TYPE_NONE
        current ?: addTileOverlay(
            TileOverlayOptions().tileProvider(tiles).fadeIn(false)
        )
    } else {
        current?.remove()
        mapType = style.style
        null
    }
}

/** Linked credit, shown only while the OSM layer is selected. */
fun TextView.showOsmAttribution(visible: Boolean) {
    isVisible = visible
    if (!visible) return
    text = Html.fromHtml(
        context.getString(R.string.map_osm_attribution),
        Html.FROM_HTML_MODE_COMPACT
    )
    movementMethod = LinkMovementMethod.getInstance()
}

/** Keeps the attribution above a bottom sheet and clear of a side card. */
fun View.offsetOsmAttribution(leftInset: Int, bottomInset: Int) {
    val extra = resources.getDimensionPixelSize(R.dimen.margin_8)
    updateLayoutParams<ViewGroup.MarginLayoutParams> {
        marginStart = leftInset + extra
        bottomMargin = bottomInset + extra
    }
}

/** Static widget snapshots cannot host a link, so the credit is drawn on. */
fun Bitmap.withOsmAttribution(): Bitmap {
    val mutable = if (isMutable && config == Bitmap.Config.ARGB_8888) {
        this
    } else {
        copy(Bitmap.Config.ARGB_8888, true) ?: return this
    }
    val canvas = Canvas(mutable)
    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(0x33, 0x33, 0x33)
        textSize = (mutable.height.coerceAtMost(mutable.width) * 0.045f).coerceAtLeast(18f)
    }
    val pad = textPaint.textSize * 0.45f
    val textWidth = textPaint.measureText(OsmTiles.ATTRIBUTION)
    val background = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(0xCC, 0xFF, 0xFF, 0xFF)
    }
    val top = mutable.height - textPaint.textSize - pad * 2
    canvas.drawRect(0f, top, textWidth + pad * 2, mutable.height.toFloat(), background)
    canvas.drawText(OsmTiles.ATTRIBUTION, pad, mutable.height - pad, textPaint)
    if (mutable != this) recycle()
    return mutable
}
