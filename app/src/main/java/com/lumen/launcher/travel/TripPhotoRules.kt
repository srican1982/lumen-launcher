package com.lumen.launcher.travel

internal object TripPhotoRules {
    fun belongsToTrip(capturedAt: Long, startedAt: Long, endedAt: Long): Boolean =
        capturedAt in startedAt..endedAt

    fun canUsePhoneLocation(capturedAt: Long, locationAt: Long): Boolean =
        kotlin.math.abs(locationAt - capturedAt) <= 120_000L
}
