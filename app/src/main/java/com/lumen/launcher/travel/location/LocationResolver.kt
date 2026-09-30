package com.lumen.launcher.travel.location

import android.content.Context
import android.location.Geocoder
import android.location.Address
import android.os.Build
import com.lumen.launcher.travel.model.ResolvedLocation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
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
                val list = addresses(latitude, longitude)
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
            if (resolved.countryName != null || resolved.city != null) cache[key] = resolved
            resolved
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
        // Do not queue unlimited lookups if an older device's geocoder stalls.
        private val legacyExecutor = java.util.concurrent.ThreadPoolExecutor(
            0, 1, 30L, java.util.concurrent.TimeUnit.SECONDS,
            java.util.concurrent.SynchronousQueue<Runnable>(),
            java.util.concurrent.ThreadFactory { task -> Thread(task, "TripGeocoder").apply { isDaemon = true } }
        )
    }

    private fun cacheKey(lat: Double, lng: Double): String {
        // ~0.001 deg ≈ 100m
        val a = (lat * 1000).roundToInt()
        val b = (lng * 1000).roundToInt()
        return "$a,$b"
    }
}
