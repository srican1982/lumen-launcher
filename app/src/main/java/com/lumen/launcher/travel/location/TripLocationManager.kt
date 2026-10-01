package com.lumen.launcher.travel.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.lumen.launcher.travel.model.ResolvedLocation
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Battery-conscious location for Trip Mode.
 * Only called when Trip Mode is ON (start trip / new photo).
 * Never runs a continuous foreground location service.
 */
class TripLocationManager(
    private val context: Context,
    private val resolver: LocationResolver = LocationResolver(context)
) {
    private val client = LocationServices.getFusedLocationProviderClient(context)
    @Volatile private var cached: ResolvedLocation? = null

    fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    fun isLocationServicesEnabled(): Boolean {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return if (Build.VERSION.SDK_INT >= 28) {
            lm.isLocationEnabled
        } else {
            @Suppress("DEPRECATION")
            lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        }
    }

    /**
     * Primary path for Trip Mode: phone location (not camera EXIF).
     * Cache lasts 90s, but a move of >3 km forces a fresh fix + place resolve
     * so suburb hops still get the right metro when needed.
     */
    @SuppressLint("MissingPermission")
    suspend fun obtainPhoneLocation(forceRefresh: Boolean = false): ResolvedLocation? {
        if (!hasLocationPermission() || !isLocationServicesEnabled()) return null
        val now = System.currentTimeMillis()
        val hit = cached
        val location = currentLocation() ?: recentLastLocation()
        if (location != null && hit != null && !forceRefresh) {
            val moved = if (hit.latitude != null && hit.longitude != null) {
                GeoMath.distanceKm(location.latitude, location.longitude, hit.latitude, hit.longitude)
            } else {
                Double.MAX_VALUE
            }
            if (now - hit.timestamp < 90_000L && moved < 3.0) {
                return hit.copy(
                    latitude = location.latitude,
                    longitude = location.longitude,
                    timestamp = now
                )
            }
        } else if (location == null) {
            return hit?.takeIf { now - it.timestamp in 0..120_000L }
        }

        val loc = location ?: return hit
        val resolved = resolver.resolve(loc.latitude, loc.longitude)
            .copy(
                latitude = loc.latitude,
                longitude = loc.longitude,
                timestamp = now
            )
        cached = resolved
        return resolved
    }

    fun clearCache() {
        cached = null
    }

    @SuppressLint("MissingPermission")
    private suspend fun currentLocation(): Location? = runCatching {
        val token = CancellationTokenSource()
        try {
            withTimeoutOrNull(8_000L) {
                client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, token.token).await()
            }
        } finally {
            token.cancel()
        }
    }.getOrNull()

    @SuppressLint("MissingPermission")
    private suspend fun recentLastLocation(): Location? = runCatching {
        val last = client.lastLocation.await() ?: return null
        val ageMs = if (Build.VERSION.SDK_INT >= 33) {
            SystemClock.elapsedRealtime() - last.elapsedRealtimeNanos / 1_000_000
        } else {
            System.currentTimeMillis() - last.time
        }
        if (ageMs in 0..120_000L) last else null
    }.getOrNull()
}
