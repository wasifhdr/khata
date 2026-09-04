package com.wasif.khata.core.place

import com.wasif.khata.core.data.dao.PlaceDao
import com.wasif.khata.core.data.entity.PlaceEntity
import com.wasif.khata.core.time.KhataClock
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val SHORT_LINK_HOST = "maps.app.goo.gl"

@Singleton
class PlaceRepository @Inject constructor(
    private val dao: PlaceDao,
    private val clock: KhataClock,
) {
    /**
     * A short link carries no coordinates until something follows it, so one redirect
     * is spent on it here. Failing to resolve is normal, not an error: offline, the
     * URL is stored anyway and the place stays openable in Maps.
     */
    suspend fun save(parsed: ParsedPlace): Long = withContext(Dispatchers.IO) {
        val resolved = if (parsed.lat == null && parsed.url?.contains(SHORT_LINK_HOST) == true) {
            resolve(parsed.url)?.let { parseSharedPlace(it) }
        } else {
            null
        }

        val now = clock.now()
        dao.upsert(
            PlaceEntity(
                uuid = UUID.randomUUID().toString(),
                name = parsed.name ?: resolved?.name ?: "Unnamed place",
                lat = parsed.lat ?: resolved?.lat,
                lng = parsed.lng ?: resolved?.lng,
                mapsUrl = parsed.url,
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    // One redirect, read from the Location header. The same HttpURLConnection pattern
    // as GeminiClient and DriveClient -- the house approach, and no reason for a
    // second one.
    private fun resolve(shortUrl: String): String? = runCatching {
        (URL(shortUrl).openConnection() as HttpURLConnection).run {
            instanceFollowRedirects = false
            connectTimeout = 10_000
            readTimeout = 10_000
            try {
                getHeaderField("Location")
            } finally {
                disconnect()
            }
        }
    }.getOrNull()
}
