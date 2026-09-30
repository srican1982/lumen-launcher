package com.lumen.launcher.travel.model

data class CityBucket(
    val city: String,
    val count: Int
)

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
