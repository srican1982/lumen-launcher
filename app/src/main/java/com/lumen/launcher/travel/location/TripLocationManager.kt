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
     * Reuses a fresh cache (&lt; 2 minutes) when available.
     */
    @SuppressLint("MissingPermission")
    suspend fun obtainPhoneLocation(forceRefresh: Boolean = false): ResolvedLocation? {
        if (!hasLocationPermission() || !isLocationServicesEnabled()) return null
        val now = System.currentTimeMillis()
        val hit = cached
        if (!forceRefresh && hit != null && now - hit.timestamp < 120_000L) return hit

        val location = currentLocation() ?: recentLastLocation()
        if (location == null) return cached
        val resolved = resolver.resolve(location.latitude, location.longitude)
            .copy(
                latitude = location.latitude,
                longitude = location.longitude,
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
        withTimeoutOrNull(8_000L) {
            val token = CancellationTokenSource()
            client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, token.token).await()
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
        if (ageMs in 0..10 * 60_000L) last else null
    }.getOrNull()
}
