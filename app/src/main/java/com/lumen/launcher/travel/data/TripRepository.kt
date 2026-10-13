package com.lumen.launcher.travel.data

import android.content.Context
import android.net.Uri
import com.lumen.launcher.travel.TripPhotoRules
import com.lumen.launcher.travel.location.LocationResolver
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

    suspend fun getLatestEndedTrip(): Trip? = dao.getLatestEndedTrip()?.let { enrichTrip(it) }

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
            coverUri = photos.firstOrNull { it.contentUri.isNotBlank() }?.contentUri,
            previewUris = photos.filter { it.contentUri.isNotBlank() }.take(4).map { it.contentUri },
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
        // A newly created local trip establishes that its MediaStore references belong to this install.
        java.io.File(app.noBackupFilesDir, "trip_photo_links_v1").writeText("validated")
        val id = dao.insertTrip(
            TripEntity(
                startTime = now,
                primaryCity = null,
                title = "Current Trip",
                isActive = true,
                createdAt = now,
            )
        )
        // Commit the trip immediately; GPS/geocoding must never block the switch.
        scope.launch {
            runCatching {
                val place = location.obtainPhoneLocation(forceRefresh = true)
                syncMutex.withLock {
                    if (dao.getActiveTrip()?.id == id) {
                        dao.updateStartCoordinates(id, place?.latitude, place?.longitude)
                        syncNewPhotosLocked(System.currentTimeMillis())
                    }
                }
            }
        }
        ensureObserverRunning(syncImmediately = false)
        enrichTrip(dao.getTrip(id)!!)
    } }

    suspend fun endTrip(): Trip? = lifecycleMutex.withLock { withContext(Dispatchers.IO) {
        val active = dao.getActiveTrip() ?: return@withContext null
        val end = System.currentTimeMillis()
        syncMutex.withLock {
            try {
                // Finishing a trip must not wait for a GPS fix. Photo membership
                // is timestamp-based; retain any place metadata already collected.
                syncNewPhotosLocked(end, resolvePlaces = false)
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

    @Synchronized private fun ensureObserverRunning(syncImmediately: Boolean = true) {
        if (observer != null) return
        val obs = MediaStorePhotoObserver(app, scope) { syncNewPhotos() }
        observer = obs
        obs.start()
        if (syncImmediately) scope.launch { runCatching { syncNewPhotos() } }
    }

    @Synchronized private fun stopObserver() {
        observer?.stop()
        observer = null
    }

    /** Call on app start if an active trip exists. */
    suspend fun restoreIfNeeded() = lifecycleMutex.withLock {
        val marker = java.io.File(app.noBackupFilesDir, "trip_photo_links_v1")
        val permission = if (android.os.Build.VERSION.SDK_INT >= 33) android.Manifest.permission.READ_MEDIA_IMAGES
            else android.Manifest.permission.READ_EXTERNAL_STORAGE
        if (!marker.exists() && androidx.core.content.ContextCompat.checkSelfPermission(app, permission) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            TripBackupManager(app).reconnect()
            // A trip restored by Android is historical; do not silently capture a new phone's photos into it.
            val installedAt = app.packageManager.getPackageInfo(app.packageName, 0).firstInstallTime
            dao.getActiveTrip()?.takeIf { it.createdAt < installedAt }?.let { active ->
                dao.endTrip(active.id, maxOf(active.startTime, dao.lastProcessedTimestamp(active.id) ?: active.startTime))
            }
            withContext(Dispatchers.IO) { marker.writeText("validated") }
        }
        if (dao.getActiveTrip() != null) ensureObserverRunning()
        else stopObserver()
    }

    suspend fun syncNewPhotos() = syncMutex.withLock {
        syncNewPhotosLocked(System.currentTimeMillis())
    }

    private suspend fun syncNewPhotosLocked(until: Long, resolvePlaces: Boolean = true) {
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

            if (!resolvePlaces) {
                refreshTripIdentity(trip.id)
                return@withContext
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
                    city = place?.city,
                    locality = place?.locality,
                    subAdminArea = place?.subAdminArea,
                    adminArea = place?.adminArea
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

    /** Deletes only Lumen's album records. MediaStore originals are never deleted. */
    suspend fun deleteAlbum(tripId: Long) = lifecycleMutex.withLock {
        syncMutex.withLock {
            withContext(Dispatchers.IO) {
                check(dao.deleteEndedTrip(tripId) > 0) { "End this trip before deleting its album." }
            }
        }
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
