package com.lumen.launcher.travel.model

/** Meaningful destination section inside a trip album (not a top-level album). */
data class PlaceSection(
    val label: String,
    val count: Int,
    val photoIds: List<Long> = emptyList(),
    val centerLat: Double? = null,
    val centerLng: Double? = null,
    val firstTaken: Long? = null,
    val lastTaken: Long? = null,
    val isOther: Boolean = false
)

/** @deprecated Prefer [PlaceSection] — kept as alias for call sites mid-migration. */
typealias CityBucket = PlaceSection

val PlaceSection.city: String get() = label

data class TripUiState(
    val tripModeEnabled: Boolean = false,
    val activeTrip: Trip? = null,
    val pastTrips: List<Trip> = emptyList(),
    val photoCount: Int = 0,
    val previewUris: List<String> = emptyList(),
    val cityBuckets: List<CityBucket> = emptyList(),
    val locationPermissionGranted: Boolean = false,
    val locationServicesEnabled: Boolean = false,
    val mediaPermissionGranted: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val needsLocationPermission: Boolean = false,
    val needsLocationServices: Boolean = false,
    val needsMediaPermission: Boolean = false,
    val completedTrip: Trip? = null
)
