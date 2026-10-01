package com.lumen.launcher.travel.location

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import com.lumen.launcher.travel.model.ResolvedLocation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlin.math.roundToInt

/**
 * Reverse-geocodes GPS into a full admin hierarchy for trip clustering.
 * Cache cells are ~1 km so nearby suburbs share one lookup.
 */
class LocationResolver(private val context: Context) {
    private val cache = ConcurrentHashMap<String, ResolvedLocation>()

    suspend fun resolve(latitude: Double, longitude: Double): ResolvedLocation =
        withContext(Dispatchers.IO) {
            val key = cacheKey(latitude, longitude)
            cache[key]?.let {
                return@withContext it.copy(latitude = latitude, longitude = longitude)
            }
            val resolved = runCatching {
                if (!Geocoder.isPresent()) return@runCatching null
                val a = addresses(latitude, longitude)?.firstOrNull() ?: return@runCatching null
                fromAddress(a, latitude, longitude)
            }.getOrNull() ?: ResolvedLocation(latitude = latitude, longitude = longitude)
            if (resolved.countryName != null || resolved.city != null || resolved.locality != null) {
                cache[key] = resolved
            }
            resolved
        }

    private fun fromAddress(a: Address, latitude: Double, longitude: Double): ResolvedLocation {
        val levels = AdminPlaceLabeler.AdminLevels(
            locality = a.locality?.trim()?.takeIf { it.isNotBlank() }
                ?: a.subLocality?.trim()?.takeIf { it.isNotBlank() },
            subAdminArea = a.subAdminArea?.trim()?.takeIf { it.isNotBlank() },
            adminArea = a.adminArea?.trim()?.takeIf { it.isNotBlank() },
            countryName = a.countryName?.trim()?.takeIf { it.isNotBlank() },
            countryCode = a.countryCode?.trim()?.takeIf { it.isNotBlank() }
        )
        return ResolvedLocation(
            latitude = latitude,
            longitude = longitude,
            countryCode = levels.countryCode,
            countryName = levels.countryName,
            city = AdminPlaceLabeler.displayForPhoto(levels),
            locality = levels.locality,
            subAdminArea = levels.subAdminArea,
            adminArea = levels.adminArea
        )
    }

    private suspend fun addresses(lat: Double, lng: Double): List<Address>? =
        withTimeoutOrNull(4_000L) {
            suspendCancellableCoroutine { continuation ->
                fun finish(result: List<Address>) {
                    if (continuation.isActive) continuation.resume(result)
                }
                val geocoder = Geocoder(context, Locale.getDefault())
                try {
                    if (Build.VERSION.SDK_INT >= 33) {
                        geocoder.getFromLocation(lat, lng, 1, object : Geocoder.GeocodeListener {
                            override fun onGeocode(addresses: MutableList<Address>) = finish(addresses)
                            override fun onError(errorMessage: String?) = finish(emptyList())
                        })
                    } else {
                        legacyExecutor.execute {
                            @Suppress("DEPRECATION")
                            val result = runCatching { geocoder.getFromLocation(lat, lng, 1) }.getOrNull()
                            finish(result.orEmpty())
                        }
                    }
                } catch (_: Exception) {
                    finish(emptyList())
                }
            }
        }

    companion object {
        private val legacyExecutor = java.util.concurrent.ThreadPoolExecutor(
            0, 1, 30L, java.util.concurrent.TimeUnit.SECONDS,
            java.util.concurrent.SynchronousQueue(),
            java.util.concurrent.ThreadFactory { task ->
                Thread(task, "TripGeocoder").apply { isDaemon = true }
            }
        )
    }

    private fun cacheKey(lat: Double, lng: Double): String {
        val a = (lat * 100).roundToInt()
        val b = (lng * 100).roundToInt()
        return "$a,$b"
    }
}
