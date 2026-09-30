package com.lumen.launcher.travel.data

import android.content.Context
import android.net.Uri
import com.lumen.launcher.travel.location.LocationResolver
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Trip Mode orchestration.
 * Phone location is the primary place tag for new photos (Camera Location Tags not required).
 * Only camera-folder captures are linked into the trip.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TripRepository private constructor(context: Context) {
    private val app = context.applicationContext
    private val dao = TravelDatabase.get(app).tripDao()
    private val location = TripLocationManager(app)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var observer: MediaStorePhotoObserver? = null
    private val syncMutex = Mutex()

    companion object {
        @Volatile private var instance: TripRepository? = null
        fun get(context: Context): TripRepository =
            instance ?: synchronized(this) {
                instance ?: TripRepository(context.applicationContext).also { instance = it }
            }
    }

    val activeTripFlow: Flow<Trip?> = dao.observeActiveTrip().flatMapLatest { entity ->
        if (entity == null) flowOf(null)
        else combine(
            dao.observeTripPhotoCount(entity.id),
            dao.observeLatestPhotoUris(entity.id, 4),
            dao.observeCityCounts(entity.id)
        ) { count, uris, cities ->
            entity.toModel(
                photoCount = count,
                coverUri = uris.firstOrNull(),
                previewUris = uris,
                citiesLabel = citiesLabel(cities)
            )
        }
    }.distinctUntilChanged()

    val pastTripsFlow: Flow<List<Trip>> = dao.observePastTrips().flatMapLatest { entities ->
        if (entities.isEmpty()) flowOf(emptyList())
        else {
            // Enrich each past trip once per emission of the trip list.
            kotlinx.coroutines.flow.flow {
                emit(entities.map { e -> enrichTrip(e) })
            }
        }
    }

    fun cityBucketsFlow(tripId: Long): Flow<List<CityBucket>> =
        dao.observeCityCounts(tripId).map { rows ->
            rows.map { CityBucket(city = it.city?.takeIf { c -> c.isNotBlank() } ?: "Other", count = it.count) }
                .sortedWith(compareByDescending<CityBucket> { it.count }.thenBy { it.city })
        }.distinctUntilChanged()

    fun photosFlow(tripId: Long): Flow<List<TripPhoto>> =
        dao.observeTripPhotos(tripId).map { list -> list.map { it.toModel() } }.distinctUntilChanged()

    fun hasLocationPermission() = location.hasLocationPermission()
    fun isLocationServicesEnabled() = location.isLocationServicesEnabled()

    suspend fun getActiveTrip(): Trip? {
        val e = dao.getActiveTrip() ?: return null
        return enrichTrip(e)
    }

    suspend fun getTrip(id: Long): Trip? {
        val e = dao.getTrip(id) ?: return null
        return enrichTrip(e)
    }

    private suspend fun enrichTrip(e: TripEntity): Trip {
        val uris = dao.latestPhotoUris(e.id, 4)
        val cities = dao.getCityCounts(e.id)
        return e.toModel(
            photoCount = dao.getTripPhotoCount(e.id),
            coverUri = uris.firstOrNull(),
            previewUris = uris,
            citiesLabel = citiesLabel(cities)
        )
    }

    private fun citiesLabel(rows: List<CityCountRow>): String? {
        val names = rows.mapNotNull { it.city?.takeIf { c -> c.isNotBlank() } }.take(4)
        return names.takeIf { it.isNotEmpty() }?.joinToString(" · ")
    }

    /**
     * Starts a trip after permissions + location services are already confirmed by UI.
     */
    suspend fun startTrip(): Trip = withContext(Dispatchers.IO) {
        dao.getActiveTrip()?.let { existing ->
            ensureObserverRunning()
            return@withContext enrichTrip(existing)
        }
        val now = System.currentTimeMillis()
        val place = location.obtainPhoneLocation(forceRefresh = true)
        val title = place?.countryName?.takeIf { it.isNotBlank() } ?: "Current Trip"
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
        syncNewPhotos()
        enrichTrip(dao.getTrip(id)!!)
    }

    suspend fun endTrip(): Trip? = withContext(Dispatchers.IO) {
        val active = dao.getActiveTrip() ?: return@withContext null
        val end = System.currentTimeMillis()
        dao.endTrip(active.id, end)
        stopObserver()
        location.clearCache()
        enrichTrip(active.copy(endTime = end, isActive = false))
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

    suspend fun syncNewPhotos() = syncMutex.withLock {
        withContext(Dispatchers.IO) {
            val trip = dao.getActiveTrip() ?: return@withContext
            val last = dao.lastProcessedTimestamp(trip.id) ?: 0L
            val since = maxOf(trip.startTime - 2_000L, last)
            val photos = MediaStorePhotoRepository.queryPhotosSince(app, since)
            val fresh = photos.filter { photo ->
                photo.dateTaken >= trip.startTime - 5_000L &&
                    dao.countByMediaStoreId(photo.mediaStoreId) == 0
            }
            if (fresh.isEmpty()) return@withContext

            // Phone location once per burst that actually has new camera photos.
            val phonePlace = location.obtainPhoneLocation()
            var current = trip
            for (photo in fresh) {
                val place = resolvePhotoPlace(photo.contentUri, phonePlace, current)
                dao.insertTripPhoto(
                    TripPhotoEntity(
                        tripId = current.id,
                        mediaStoreId = photo.mediaStoreId,
                        contentUri = photo.contentUri.toString(),
                        displayName = photo.displayName,
                        dateTaken = photo.dateTaken,
                        latitude = place?.latitude,
                        longitude = place?.longitude,
                        countryCode = place?.countryCode ?: current.countryCode,
                        countryName = place?.countryName ?: current.countryName,
                        city = place?.city,
                        relativePath = photo.relativePath,
                        addedAt = System.currentTimeMillis()
                    )
                )
                if ((current.countryName.isNullOrBlank() || current.title == "Current Trip") &&
                    !place?.countryName.isNullOrBlank()
                ) {
                    val title = place!!.countryName!!
                    dao.updateTripLocation(
                        tripId = current.id,
                        countryCode = place.countryCode,
                        countryName = place.countryName,
                        primaryCity = place.city ?: current.primaryCity,
                        title = title
                    )
                    current = current.copy(
                        countryCode = place.countryCode,
                        countryName = place.countryName,
                        primaryCity = place.city ?: current.primaryCity,
                        title = title
                    )
                } else if (current.primaryCity.isNullOrBlank() && !place?.city.isNullOrBlank()) {
                    dao.updateTripLocation(
                        tripId = current.id,
                        countryCode = current.countryCode ?: place?.countryCode,
                        countryName = current.countryName ?: place?.countryName,
                        primaryCity = place!!.city,
                        title = current.title
                    )
                    current = current.copy(primaryCity = place.city)
                }
            }
        }
    }

    suspend fun removePhotosFromTrip(photoIds: List<Long>) = withContext(Dispatchers.IO) {
        if (photoIds.isEmpty()) return@withContext
        dao.deletePhotosByIds(photoIds)
    }

    suspend fun photosByIds(photoIds: List<Long>): List<TripPhoto> = withContext(Dispatchers.IO) {
        dao.getPhotosByIds(photoIds).map { it.toModel() }
    }

    /**
     * Prefer phone location (works with Camera Location Tags OFF).
     * EXIF is only a fallback when phone location is missing.
     */
    private suspend fun resolvePhotoPlace(
        uri: Uri,
        phonePlace: ResolvedLocation?,
        trip: TripEntity
    ): ResolvedLocation? {
        if (phonePlace != null) return phonePlace
        val exif = readExifLocation(uri)
        if (exif != null) {
            return LocationResolver(app).resolve(exif.first, exif.second)
        }
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
