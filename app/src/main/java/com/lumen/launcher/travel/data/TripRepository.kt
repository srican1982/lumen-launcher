package com.lumen.launcher.travel.data

import android.content.Context
import android.net.Uri
import com.lumen.launcher.travel.TripPhotoRules
import com.lumen.launcher.travel.location.LocationResolver
import com.lumen.launcher.travel.location.MajorCities
import com.lumen.launcher.travel.location.TripLocationManager
import com.lumen.launcher.travel.location.TripPlaceOrganizer
import com.lumen.launcher.travel.media.MediaStorePhotoObserver
import com.lumen.launcher.travel.media.MediaStorePhotoRepository
import com.lumen.launcher.travel.model.PlaceSection
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
 *
 * Trip album owns every camera photo while Trip Mode is on.
 * Location is metadata used to infer optional place sections — never whether a photo belongs.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TripRepository private constructor(context: Context) {
    private val app = context.applicationContext
    private val dao = TravelDatabase.get(app).tripDao()
    private val location = TripLocationManager(app)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var observer: MediaStorePhotoObserver? = null
    private val syncMutex = Mutex()
    private val lifecycleMutex = Mutex()

    companion object {
        @Volatile private var instance: TripRepository? = null
        fun get(context: Context): TripRepository =
            instance ?: synchronized(this) {
                instance ?: TripRepository(context.applicationContext).also { instance = it }
            }
    }

    val activeTripFlow: Flow<Trip?> = dao.observeActiveTrip().flatMapLatest { entity ->
        if (entity == null) flowOf(null)
        else dao.observeTripPhotos(entity.id).map { photos ->
            enrichFromPhotos(entity, photos)
        }
    }.distinctUntilChanged()

    val pastTripsFlow: Flow<List<Trip>> = dao.observePastTrips().flatMapLatest { entities ->
        if (entities.isEmpty()) flowOf(emptyList())
        else {
            combine(entities.map { e ->
                dao.observeTripPhotos(e.id).map { photos -> enrichFromPhotos(e, photos) }
            }) { trips -> trips.toList() }
        }
    }

    /** Meaningful destination sections inside a trip (not top-level albums). */
    fun placeSectionsFlow(tripId: Long): Flow<List<PlaceSection>> =
        combine(
            dao.observeTrip(tripId),
            dao.observeTripPhotos(tripId)
        ) { trip, photos ->
            if (trip == null) emptyList()
            else TripPlaceOrganizer.organize(
                photos = photos.map { it.toModel() },
                startLat = trip.startLatitude,
                startLng = trip.startLongitude
            ).sections
        }.distinctUntilChanged()

    /** @deprecated use [placeSectionsFlow] */
    fun cityBucketsFlow(tripId: Long): Flow<List<PlaceSection>> = placeSectionsFlow(tripId)

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
        val photos = dao.getTripPhotos(e.id)
        return enrichFromPhotos(e, photos)
    }

    private fun enrichFromPhotos(e: TripEntity, photos: List<TripPhotoEntity>): Trip {
        val models = photos.map { it.toModel() }
        val organized = TripPlaceOrganizer.organize(
            photos = models,
            startLat = e.startLatitude,
            startLng = e.startLongitude
        )
        val destinations = organized.sections.filterNot { it.isOther }
        return e.toModel(
            photoCount = photos.size,
            coverUri = photos.firstOrNull()?.contentUri,
            previewUris = photos.take(4).map { it.contentUri },
            citiesLabel = destinations.take(3).joinToString(" · ") { it.label }
                .ifBlank { null }
        )
    }

    /**
     * Starts a trip after permissions + location services are already confirmed by UI.
     * Always begins as "Current Trip" — never named from the activation city.
     */
    suspend fun startTrip(): Trip = lifecycleMutex.withLock { withContext(Dispatchers.IO) {
        dao.getActiveTrip()?.let { existing ->
            ensureObserverRunning()
            return@withContext enrichTrip(existing)
        }
        val now = System.currentTimeMillis()
        val place = location.obtainPhoneLocation(forceRefresh = true)
        val id = dao.insertTrip(
            TripEntity(
                startTime = now,
                countryCode = place?.countryCode,
                countryName = place?.countryName,
                primaryCity = null,
                title = "Current Trip",
                isActive = true,
                createdAt = now,
                startLatitude = place?.latitude,
                startLongitude = place?.longitude
            )
        )
        ensureObserverRunning()
        syncNewPhotos()
        enrichTrip(dao.getTrip(id)!!)
    } }

    suspend fun endTrip(): Trip? = lifecycleMutex.withLock { withContext(Dispatchers.IO) {
        val active = dao.getActiveTrip() ?: return@withContext null
        val end = System.currentTimeMillis()
        syncMutex.withLock {
            try {
                syncNewPhotosLocked(end)
            } catch (_: SecurityException) {
                // Revoked photo access must not prevent the user ending a trip.
            }
            refreshTripIdentity(active.id)
            dao.endTrip(active.id, end)
        }
        stopObserver()
        location.clearCache()
        enrichTrip(dao.getTrip(active.id)!!)
    } }

    @Synchronized private fun ensureObserverRunning() {
        if (observer != null) return
        val obs = MediaStorePhotoObserver(app, scope) { syncNewPhotos() }
        observer = obs
        obs.start()
        scope.launch { runCatching { syncNewPhotos() } }
    }

    @Synchronized private fun stopObserver() {
        observer?.stop()
        observer = null
    }

    /** Call on app start if an active trip exists. */
    suspend fun restoreIfNeeded() = lifecycleMutex.withLock {
        if (dao.getActiveTrip() != null) ensureObserverRunning()
        else stopObserver()
    }

    suspend fun syncNewPhotos() = syncMutex.withLock {
        syncNewPhotosLocked(System.currentTimeMillis())
    }

    private suspend fun syncNewPhotosLocked(until: Long) {
        withContext(Dispatchers.IO) {
            val trip = dao.getActiveTrip() ?: return@withContext
            val photos = MediaStorePhotoRepository.queryPhotosSince(app, trip.startTime)
            val fresh = photos.filter { photo ->
                TripPhotoRules.belongsToTrip(photo.dateTaken, trip.startTime, until) &&
                    dao.countByMediaStoreId(photo.mediaStoreId) == 0
            }
            if (fresh.isEmpty()) {
                refreshTripIdentity(trip.id)
                return@withContext
            }

            // Always insert into the trip album first — location never gates membership.
            val inserted = fresh.associate { photo ->
                photo.mediaStoreId to dao.insertTripPhoto(
                    TripPhotoEntity(
                        tripId = trip.id,
                        mediaStoreId = photo.mediaStoreId,
                        contentUri = photo.contentUri.toString(),
                        displayName = photo.displayName,
                        dateTaken = photo.dateTaken,
                        relativePath = photo.relativePath,
                        addedAt = System.currentTimeMillis()
                    )
                )
            }

            val phonePlace = location.obtainPhoneLocation()
            for (photo in fresh) {
                val recentPlace = phonePlace?.takeIf {
                    TripPhotoRules.canUsePhoneLocation(photo.dateTaken, it.timestamp)
                }
                val place = resolvePhotoPlace(photo.contentUri, recentPlace, trip)
                dao.updatePhotoLocation(
                    id = inserted.getValue(photo.mediaStoreId),
                    latitude = place?.latitude,
                    longitude = place?.longitude,
                    countryCode = place?.countryCode ?: trip.countryCode,
                    countryName = place?.countryName ?: trip.countryName,
                    city = place?.city
                )
            }
            refreshTripIdentity(trip.id)
        }
    }

    /**
     * After enough evidence, rename Current Trip from the dominant destination cluster —
     * never from the start-zone city.
     */
    private suspend fun refreshTripIdentity(tripId: Long) {
        val trip = dao.getTrip(tripId) ?: return
        val photos = dao.getTripPhotos(tripId).map { it.toModel() }
        val organized = TripPlaceOrganizer.organize(
            photos = photos,
            startLat = trip.startLatitude,
            startLng = trip.startLongitude
        )
        val title = when {
            organized.suggestedTitle != null && TripPlaceOrganizer.isAutoTitle(trip.title) ->
                organized.suggestedTitle
            else -> trip.title
        }
        val primary = organized.dominantPlace
        val countryCode = organized.countryCode ?: trip.countryCode
        val countryName = organized.countryName ?: trip.countryName
        if (title != trip.title ||
            primary != trip.primaryCity ||
            countryCode != trip.countryCode ||
            countryName != trip.countryName
        ) {
            dao.updateTripLocation(
                tripId = tripId,
                countryCode = countryCode,
                countryName = countryName,
                primaryCity = primary,
                title = title
            )
        }
    }

    suspend fun removePhotosFromTrip(photoIds: List<Long>) = withContext(Dispatchers.IO) {
        if (photoIds.isEmpty()) return@withContext
        dao.deletePhotosByIds(photoIds)
        dao.getActiveTrip()?.id?.let { refreshTripIdentity(it) }
    }

    suspend fun photosByIds(photoIds: List<Long>): List<TripPhoto> = withContext(Dispatchers.IO) {
        dao.getPhotosByIds(photoIds).map { it.toModel() }
    }

    /**
     * Prefer phone location (works with Camera Location Tags OFF).
     * EXIF is only a fallback when phone location is missing.
     * City is metro-scale metadata for later place sections — not an album name.
     */
    private suspend fun resolvePhotoPlace(
        uri: Uri,
        phonePlace: ResolvedLocation?,
        trip: TripEntity
    ): ResolvedLocation? {
        val exif = readExifLocation(uri)
        if (exif != null) {
            return LocationResolver(app).resolve(exif.first, exif.second)
        }
        if (phonePlace != null) return phonePlace
        // Last resort: snap to start coords' metro only as weak metadata (not a place section).
        val lat = trip.startLatitude
        val lng = trip.startLongitude
        if (lat != null && lng != null) {
            val metro = MajorCities.nearest(lat, lng, trip.countryCode)?.name
            return ResolvedLocation(
                latitude = lat,
                longitude = lng,
                countryCode = trip.countryCode,
                countryName = trip.countryName,
                city = metro
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
