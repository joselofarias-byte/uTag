package com.kieronquinn.app.utag.model

/** Local file name for a tag's custom icon. Device ids are not used raw as paths. */
object TagIconFiles {

    fun fileName(deviceId: String): String {
        val safe = buildString {
            deviceId.forEach { ch ->
                append(
                    if (ch.isLetterOrDigit() || ch == '.' || ch == '_' || ch == '-') ch else '_'
                )
            }
        }.ifEmpty { "tag" }
        return "$safe.png"
    }

}
