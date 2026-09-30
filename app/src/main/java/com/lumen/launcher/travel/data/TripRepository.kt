package com.lumen.launcher.travel.data

import android.content.Context
import android.net.Uri
import com.lumen.launcher.travel.location.TripLocationManager
import com.lumen.launcher.travel.media.MediaStorePhotoObserver
import com.lumen.launcher.travel.media.MediaStorePhotoRepository
import com.lumen.launcher.travel.model.CityBucket
import com.lumen.launcher.travel.model.ResolvedLocation
import com.lumen.launcher.travel.model.Trip
import com.lumen.launcher.travel.model.TripPhoto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Trip Mode orchestration.
 * Phone location is the primary place tag for new photos (Camera Location Tags not required).
 * EXIF GPS is only a secondary fallback when phone location is unavailable.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TripRepository private constructor(context: Context) {
    private val app = context.applicationContext
    private val dao = TravelDatabase.get(app).tripDao()
    private val location = TripLocationManager(app)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var observer: MediaStorePhotoObserver? = null

    companion object {
        @Volatile private var instance: TripRepository? = null
        fun get(context: Context): TripRepository =
            instance ?: synchronized(this) {
                instance ?: TripRepository(context.applicationContext).also { instance = it }
            }
    }

    val activeTripFlow: Flow<Trip?> = dao.observeActiveTrip().flatMapLatest { entity ->
        if (entity == null) flowOf(null)
        else dao.observeTripPhotoCount(entity.id).map { count -> entity.toModel(count) }
    }

    fun cityBucketsFlow(tripId: Long): Flow<List<CityBucket>> =
        dao.observeCityCounts(tripId).map { rows ->
            rows.map { CityBucket(city = it.city?.takeIf { c -> c.isNotBlank() } ?: "Other", count = it.count) }
                .sortedWith(compareByDescending<CityBucket> { it.count }.thenBy { it.city })
        }

    fun photosFlow(tripId: Long): Flow<List<TripPhoto>> =
        dao.observeTripPhotos(tripId).map { list -> list.map { it.toModel() } }

    fun hasLocationPermission() = location.hasLocationPermission()
    fun isLocationServicesEnabled() = location.isLocationServicesEnabled()

    suspend fun getActiveTrip(): Trip? {
        val e = dao.getActiveTrip() ?: return null
        return e.toModel(dao.getTripPhotoCount(e.id))
    }

    suspend fun getTrip(id: Long): Trip? {
        val e = dao.getTrip(id) ?: return null
        return e.toModel(dao.getTripPhotoCount(e.id))
    }

    /**
     * Starts a trip after permissions + location services are already confirmed by UI.
     */
    suspend fun startTrip(): Trip = withContext(Dispatchers.IO) {
        dao.getActiveTrip()?.let { existing ->
            ensureObserverRunning()
            return@withContext existing.toModel(dao.getTripPhotoCount(existing.id))
        }
        val now = System.currentTimeMillis()
        val place = location.obtainPhoneLocation(forceRefresh = true)
        val title = place?.countryName?.let { "$it Trip" } ?: "Current Trip"
        val id = dao.insertTrip(
            TripEntity(
                startTime = now,
                countryCode = place?.countryCode,
                countryName = place?.countryName,
                primaryCity = place?.city,
                title = title,
                isActive = true,
                createdAt = now
            )
        )
        ensureObserverRunning()
        // Catch any photos taken in the first moments after enabling.
        syncNewPhotos()
        dao.getTrip(id)!!.toModel(0)
    }

    suspend fun endTrip(): Trip? = withContext(Dispatchers.IO) {
        val active = dao.getActiveTrip() ?: return@withContext null
        val end = System.currentTimeMillis()
        dao.endTrip(active.id, end)
        stopObserver()
        location.clearCache()
        active.copy(endTime = end, isActive = false).toModel(dao.getTripPhotoCount(active.id))
    }

    fun ensureObserverRunning() {
        if (observer != null) return
        val obs = MediaStorePhotoObserver(app, scope) { syncNewPhotos() }
        observer = obs
        obs.start()
        scope.launch { syncNewPhotos() }
    }

    fun stopObserver() {
        observer?.stop()
        observer = null
    }

    /** Call on app start if an active trip exists. */
    suspend fun restoreIfNeeded() {
        if (dao.getActiveTrip() != null) ensureObserverRunning()
        else stopObserver()
    }

    suspend fun syncNewPhotos() = withContext(Dispatchers.IO) {
        val trip = dao.getActiveTrip() ?: return@withContext
        val last = dao.lastProcessedTimestamp(trip.id) ?: 0L
        val since = maxOf(trip.startTime - 2_000L, last)
        val photos = MediaStorePhotoRepository.queryPhotosSince(app, since)
        // Phone location once per sync burst — not continuous GPS.
        val phonePlace = location.obtainPhoneLocation()
        for (photo in photos) {
            if (dao.countByMediaStoreId(photo.mediaStoreId) > 0) continue
            if (photo.dateTaken < trip.startTime - 5_000L) continue
            val place = resolvePhotoPlace(photo.contentUri, phonePlace, trip)
            dao.insertTripPhoto(
                TripPhotoEntity(
                    tripId = trip.id,
                    mediaStoreId = photo.mediaStoreId,
                    contentUri = photo.contentUri.toString(),
                    displayName = photo.displayName,
                    dateTaken = photo.dateTaken,
                    latitude = place?.latitude,
                    longitude = place?.longitude,
                    countryCode = place?.countryCode ?: trip.countryCode,
                    countryName = place?.countryName ?: trip.countryName,
                    city = place?.city,
                    relativePath = photo.relativePath,
                    addedAt = System.currentTimeMillis()
                )
            )
            // Refresh trip title/country once we know where we are.
            if ((trip.countryName.isNullOrBlank() || trip.title == "Current Trip") &&
                !place?.countryName.isNullOrBlank()
            ) {
                val title = "${place!!.countryName} Trip"
                dao.updateTripLocation(
                    tripId = trip.id,
                    countryCode = place.countryCode,
                    countryName = place.countryName,
                    primaryCity = place.city ?: trip.primaryCity,
                    title = title
                )
            } else if (trip.primaryCity.isNullOrBlank() && !place?.city.isNullOrBlank()) {
                dao.updateTripLocation(
                    tripId = trip.id,
                    countryCode = trip.countryCode ?: place?.countryCode,
                    countryName = trip.countryName ?: place?.countryName,
                    primaryCity = place!!.city,
                    title = trip.title
                )
            }
        }
    }

    /**
     * Prefer phone location (works with Camera Location Tags OFF).
     * EXIF is only a fallback when phone location is missing.
     */
    private suspend fun resolvePhotoPlace(
        uri: android.net.Uri,
        phonePlace: ResolvedLocation?,
        trip: TripEntity
    ): ResolvedLocation? {
        if (phonePlace != null) return phonePlace
        val exif = readExifLocation(uri)
        if (exif != null) return location.let {
            // Reuse resolver through a fresh resolve via manager path
            com.lumen.launcher.travel.location.LocationResolver(app).resolve(exif.first, exif.second)
        }
        // Still associate with trip using trip's known country.
        if (!trip.countryName.isNullOrBlank() || !trip.primaryCity.isNullOrBlank()) {
            return ResolvedLocation(
                countryCode = trip.countryCode,
                countryName = trip.countryName,
                city = null
            )
        }
        return null
    }

    private fun readExifLocation(uri: Uri): Pair<Double, Double>? = runCatching {
        app.contentResolver.openInputStream(uri)?.use { stream ->
            val exif = android.media.ExifInterface(stream)
            val latLong = FloatArray(2)
            if (exif.getLatLong(latLong)) latLong[0].toDouble() to latLong[1].toDouble() else null
        }
    }.getOrNull()
}
