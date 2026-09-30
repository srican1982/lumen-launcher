package com.lumen.launcher.travel

import android.Manifest
import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lumen.launcher.travel.data.TripRepository
import com.lumen.launcher.travel.model.CityBucket
import com.lumen.launcher.travel.model.Trip
import com.lumen.launcher.travel.model.TripPhoto
import com.lumen.launcher.travel.model.TripUiState
import com.lumen.launcher.travel.share.TripShareManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class TripViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = TripRepository.get(app)

    private val flags = MutableStateFlow(PermissionFlags())
    private val loading = MutableStateFlow(false)
    private val error = MutableStateFlow<String?>(null)
    private val completed = MutableStateFlow<Trip?>(null)
    private val promptLocationPermission = MutableStateFlow(false)
    private val promptLocationServices = MutableStateFlow(false)
    private val promptMediaPermission = MutableStateFlow(false)

    private data class PermissionFlags(
        val location: Boolean = false,
        val locationServices: Boolean = false,
        val media: Boolean = false
    )

    private data class CoreUi(
        val trip: Trip?,
        val past: List<Trip>,
        val perms: PermissionFlags,
        val load: Boolean,
        val err: String?,
        val done: Trip?
    )

    val uiState: StateFlow<TripUiState> = combine(
        combine(repo.activeTripFlow, repo.pastTripsFlow, flags, loading) { trip, past, perms, load ->
            Triple(trip, past, perms to load)
        },
        error,
        completed,
        promptLocationPermission,
        promptLocationServices
    ) { pack, err, done, needLoc, needSvc ->
        val (trip, past, permsLoad) = pack
        val (perms, load) = permsLoad
        CoreUi(trip, past, perms, load, err, done) to (needLoc to needSvc)
    }.combine(promptMediaPermission) { pair, needMedia ->
        val (core, prompts) = pair
        val (needLoc, needSvc) = prompts
        TripUiState(
            tripModeEnabled = core.trip != null,
            activeTrip = core.trip,
            pastTrips = core.past,
            photoCount = core.trip?.photoCount ?: 0,
            previewUris = core.trip?.previewUris.orEmpty(),
            locationPermissionGranted = core.perms.location,
            locationServicesEnabled = core.perms.locationServices,
            mediaPermissionGranted = core.perms.media,
            isLoading = core.load,
            errorMessage = core.err,
            needsLocationPermission = needLoc,
            needsLocationServices = needSvc,
            needsMediaPermission = needMedia,
            completedTrip = core.done
        )
    }.distinctUntilChanged().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TripUiState())

    fun cityBuckets(tripId: Long): StateFlow<List<CityBucket>> =
        repo.cityBucketsFlow(tripId)
            .distinctUntilChanged()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun photos(tripId: Long): StateFlow<List<TripPhoto>> =
        repo.photosFlow(tripId)
            .distinctUntilChanged()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        refreshPermissions()
        viewModelScope.launch {
            repo.restoreIfNeeded()
            refreshPermissions()
        }
    }

    fun refreshPermissions() {
        val ctx = getApplication<Application>()
        val next = PermissionFlags(
            location = repo.hasLocationPermission(),
            locationServices = repo.isLocationServicesEnabled(),
            media = hasMediaPermission(ctx)
        )
        if (flags.value != next) flags.value = next
    }

    fun clearError() = error.update { null }
    fun dismissCompleted() = completed.update { null }
    fun dismissLocationPermissionPrompt() = promptLocationPermission.update { false }
    fun dismissLocationServicesPrompt() = promptLocationServices.update { false }
    fun dismissMediaPermissionPrompt() = promptMediaPermission.update { false }

    /**
     * Trip Mode toggle. Flow:
     * OFF → ask location explanation → permission → location services → media → start trip.
     * Phone GPS is used for photo places (Camera Location Tags not required).
     */
    fun onTripModeToggled(enabled: Boolean) {
        if (!enabled) {
            viewModelScope.launch {
                loading.value = true
                runCatching { repo.endTrip() }
                    .onSuccess { completed.value = it }
                    .onFailure { error.value = it.message }
                loading.value = false
                refreshPermissions()
            }
            return
        }
        refreshPermissions()
        val p = flags.value
        when {
            !p.location -> promptLocationPermission.value = true
            !p.locationServices -> promptLocationServices.value = true
            !p.media -> promptMediaPermission.value = true
            else -> beginTrip()
        }
    }

    fun onLocationPermissionResult(granted: Boolean) {
        promptLocationPermission.value = false
        refreshPermissions()
        if (!granted) {
            error.value = "Location permission is needed for Trip Mode."
            return
        }
        if (!flags.value.locationServices) {
            promptLocationServices.value = true
        } else if (!flags.value.media) {
            promptMediaPermission.value = true
        } else {
            beginTrip()
        }
    }

    fun onReturnedFromLocationSettings() {
        promptLocationServices.value = false
        refreshPermissions()
        if (!flags.value.locationServices) {
            error.value = "Location is still off. Turn it on to use Trip Mode."
            return
        }
        if (!flags.value.media) promptMediaPermission.value = true
        else beginTrip()
    }

    fun onMediaPermissionResult(granted: Boolean) {
        promptMediaPermission.value = false
        refreshPermissions()
        if (!granted) {
            error.value = "Photo access is needed so Lumen can organize new camera photos."
            return
        }
        beginTrip()
    }

    fun openLocationSettings() {
        val ctx = getApplication<Application>()
        val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(intent)
    }

    private fun beginTrip() {
        viewModelScope.launch {
            loading.value = true
            runCatching { repo.startTrip() }
                .onFailure { error.value = it.message ?: "Could not start Trip Mode." }
            loading.value = false
            refreshPermissions()
        }
    }

    fun endTripConfirmed() {
        onTripModeToggled(false)
    }

    fun removeFromTrip(photoIds: List<Long>) {
        viewModelScope.launch {
            runCatching { repo.removePhotosFromTrip(photoIds) }
                .onFailure { error.value = it.message }
        }
    }

    fun share(uris: List<Uri>, instagramOnly: Boolean = false) {
        val ctx = getApplication<Application>()
        if (instagramOnly) TripShareManager.shareToInstagram(ctx, uris)
        else TripShareManager.shareImages(ctx, uris)
    }

    fun instagramInstalled(): Boolean =
        TripShareManager.isInstagramInstalled(getApplication())

    companion object {
        fun hasMediaPermission(context: android.content.Context): Boolean {
            return if (Build.VERSION.SDK_INT >= 33) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) ==
                    PackageManager.PERMISSION_GRANTED
            } else {
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) ==
                    PackageManager.PERMISSION_GRANTED
            }
        }

        fun mediaPermission(): String =
            if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES
            else Manifest.permission.READ_EXTERNAL_STORAGE

        val locationPermissions = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    }
}
