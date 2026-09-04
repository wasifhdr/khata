package com.wasif.khata.core.place

import java.net.URLDecoder

/**
 * !3d!4d before @lat,lng, and deliberately so: the @ is where the map happened to be
 * centred when the link was made, the !3d!4d is the place. They disagree whenever the
 * user panned before sharing, and the camera is the wrong answer.
 */
private val COORD_PATTERNS = listOf(
    Regex("""!3d(-?[\d.]+)!4d(-?[\d.]+)"""),
    Regex("""@(-?[\d.]+),(-?[\d.]+)"""),
    Regex("""[?&]q=(-?[\d.]+),(-?[\d.]+)"""),
    Regex("""[?&]query=(-?[\d.]+),(-?[\d.]+)"""),
    Regex("""geo:(-?[\d.]+),(-?[\d.]+)"""),
)

private val PLACE_NAME = Regex("""/maps/place/([^/@?]+)""")
private val GEO_LABEL = Regex("""geo:[^?]*\?q=([^&]+)""")
private val URL = Regex("""https?://\S+""")

data class ParsedPlace(
    val name: String?,
    val lat: Double?,
    val lng: Double?,
    val url: String?,
)

/**
 * Null only when the text holds neither a link nor coordinates -- an ordinary
 * message, not a share. A link with no coordinates is a perfectly good place: a short
 * link that never resolved still opens in Maps.
 */
fun parseSharedPlace(text: String): ParsedPlace? {
    val url = URL.find(text)?.value ?: text.lineSequence().map { it.trim() }
        .firstOrNull { it.startsWith("geo:") }

    val coords = COORD_PATTERNS.firstNotNullOfOrNull { pattern ->
        pattern.find(text)?.destructured?.let { (lat, lng) ->
            val parsed = lat.toDoubleOrNull() to lng.toDoubleOrNull()
            if (parsed.first != null && parsed.second != null) parsed else null
        }
    }

    if (url == null && coords == null) return null

    return ParsedPlace(
        name = nameFrom(text),
        lat = coords?.first,
        lng = coords?.second,
        url = url,
    )
}

private fun nameFrom(text: String): String? {
    PLACE_NAME.find(text)?.let { return decode(it.groupValues[1]) }
    GEO_LABEL.find(text)?.let { return decode(it.groupValues[1]) }
    // Maps shares a place as a name line followed by a link line, so a first line
    // that is not itself a link is the name the user saw.
    return text.lineSequence()
        .map { it.trim() }
        .firstOrNull { it.isNotBlank() && !it.startsWith("http") && !it.startsWith("geo:") }
}

/** `+` is a space in a Maps path, which URLDecoder already knows. */
private fun decode(raw: String): String? =
    runCatching { URLDecoder.decode(raw, "UTF-8") }.getOrNull()?.takeIf { it.isNotBlank() }
