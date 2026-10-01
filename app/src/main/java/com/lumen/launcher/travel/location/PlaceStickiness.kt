package com.lumen.launcher.travel.location

import com.lumen.launcher.travel.model.ResolvedLocation

/**
 * Keeps place folders stable while the user moves between nearby suburbs.
 * A new major-city folder opens only after leaving the sticky metro bubble.
 */
object PlaceStickiness {
    /** Stay on the same place folder inside this radius (km). */
    const val STICK_KM = 28.0

    fun assign(
        incoming: ResolvedLocation?,
        stickyCity: String?,
        stickyLat: Double?,
        stickyLng: Double?
    ): ResolvedLocation? {
        if (incoming == null) {
            if (stickyCity.isNullOrBlank()) return null
            return ResolvedLocation(city = stickyCity)
        }
        val city = incoming.city?.takeIf { it.isNotBlank() }
        val lat = incoming.latitude
        val lng = incoming.longitude

        if (!stickyCity.isNullOrBlank()) {
            if (city != null && city.equals(stickyCity, ignoreCase = true)) {
                return incoming.copy(city = stickyCity)
            }
            if (lat != null && lng != null && stickyLat != null && stickyLng != null) {
                val d = GeoMath.distanceKm(lat, lng, stickyLat, stickyLng)
                if (d <= STICK_KM) {
                    // Still in the same metro bubble (e.g. Cerritos → Norwalk).
                    return incoming.copy(city = stickyCity)
                }
            }
            // Geocode failed but we still have a sticky place and coords are close-ish unknown:
            if (city == null && stickyLat != null && stickyLng != null && lat != null && lng != null) {
                val d = GeoMath.distanceKm(lat, lng, stickyLat, stickyLng)
                if (d <= STICK_KM) return incoming.copy(city = stickyCity)
            }
        }
        return incoming
    }
}
