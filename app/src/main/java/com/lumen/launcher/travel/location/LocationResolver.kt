package com.lumen.launcher.travel.location

import android.content.Context
import android.location.Geocoder
import com.lumen.launcher.travel.model.ResolvedLocation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt

/**
 * Reverse-geocodes coordinates with a coarse cache so nearby photos
 * (same ~100m cell) do not hit Geocoder repeatedly.
 */
class LocationResolver(private val context: Context) {
    private val cache = ConcurrentHashMap<String, ResolvedLocation>()

    suspend fun resolve(latitude: Double, longitude: Double): ResolvedLocation =
        withContext(Dispatchers.IO) {
            val key = cacheKey(latitude, longitude)
            cache[key]?.let { return@withContext it.copy(latitude = latitude, longitude = longitude) }
            val resolved = runCatching {
                if (!Geocoder.isPresent()) return@runCatching null
                @Suppress("DEPRECATION")
                val list = Geocoder(context, Locale.getDefault()).getFromLocation(latitude, longitude, 1)
                val a = list?.firstOrNull() ?: return@runCatching null
                ResolvedLocation(
                    latitude = latitude,
                    longitude = longitude,
                    countryCode = a.countryCode,
                    countryName = a.countryName,
                    city = a.locality?.takeIf { it.isNotBlank() }
                        ?: a.subAdminArea?.takeIf { it.isNotBlank() }
                        ?: a.adminArea?.takeIf { it.isNotBlank() }
                )
            }.getOrNull() ?: ResolvedLocation(latitude = latitude, longitude = longitude)
            cache[key] = resolved
            resolved
        }

    private fun cacheKey(lat: Double, lng: Double): String {
        // ~0.001 deg ≈ 100m
        val a = (lat * 1000).roundToInt()
        val b = (lng * 1000).roundToInt()
        return "$a,$b"
    }
}
