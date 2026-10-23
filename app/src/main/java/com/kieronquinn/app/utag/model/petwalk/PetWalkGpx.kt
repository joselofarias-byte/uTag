package com.kieronquinn.app.utag.model.petwalk

import java.time.Instant

/** Portable export with separate phone and network-tag tracks and original fix times. */
object PetWalkGpx {
    fun export(label: String, samples: List<PetWalkSampleDraft>): String = buildString {
        append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        append("<gpx version=\"1.1\" creator=\"uTag\" xmlns=\"http://www.topografix.com/GPX/1/1\">\n")
        append("<metadata><name>").append(escape(label)).append("</name>")
        append("<desc>Phone route and separate SmartTag network fixes. Gaps are not interpolated.</desc></metadata>\n")
        track("Phone", samples.map { it.phone })
        track("SmartTag network", samples.map { it.tag })
        append("</gpx>\n")
    }

    private fun StringBuilder.track(name: String, points: List<GeoPoint?>) {
        val segments = PetWalkGeometry.segments(points)
        if (segments.isEmpty()) return
        append("<trk><name>").append(name).append("</name>\n")
        segments.forEach { segment ->
            append("<trkseg>\n")
            segment.forEach { point ->
                append("<trkpt lat=\"").append(point.latitude).append("\" lon=\"")
                    .append(point.longitude).append("\"><time>")
                    .append(Instant.ofEpochMilli(point.capturedAt))
                    .append("</time></trkpt>\n")
            }
            append("</trkseg>\n")
        }
        append("</trk>\n")
    }

    private fun escape(value: String): String = buildString {
        // XML 1.0 permits tabs, line endings and these Unicode scalar ranges.
        value.codePoints().forEach { code ->
            when (code) {
                38 -> append("&amp;")
                60 -> append("&lt;")
                62 -> append("&gt;")
                34 -> append("&quot;")
                39 -> append("&apos;")
                9, 10, 13 -> appendCodePoint(code)
                in 0x20..0xD7FF, in 0xE000..0xFFFD, in 0x10000..0x10FFFF -> appendCodePoint(code)
            }
        }
    }
}
