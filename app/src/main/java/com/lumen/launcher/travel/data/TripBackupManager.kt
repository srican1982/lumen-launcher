package com.lumen.launcher.travel.data

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.lumen.launcher.travel.media.MediaStorePhotoRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class TripBackupManager(context: Context) {
    private val app = context.applicationContext
    private val database = TravelDatabase.get(app)
    private val dao = database.tripDao()
    companion object { private val lock = Mutex() }
    data class Result(val trips: Int, val connected: Int, val missing: Int, val skipped: Int = 0)

    private suspend fun snapshot(tripId: Long?): TripArchiveCodec.Archive = database.withTransaction {
        val trips = dao.allTrips().filter { tripId == null || it.id == tripId }
        require(tripId == null || trips.isNotEmpty()) { "Trip no longer exists" }
        val photos = dao.allPhotos().groupBy { it.tripId }
        TripArchiveCodec.Archive(System.currentTimeMillis(), trips.map { TripArchiveCodec.Album(it, photos[it.id].orEmpty()) })
    }

    suspend fun export(uri: Uri, tripId: Long? = null) = withContext(Dispatchers.IO) {
        val json = TripArchiveCodec.encode(snapshot(tripId))
        require(json.toByteArray().size <= TripArchiveCodec.MAX_BYTES) { "Trip metadata exceeds the backup size limit" }
        requireNotNull(app.contentResolver.openOutputStream(uri, "wt")) { "Cannot open backup destination" }.bufferedWriter().use { it.write(json) }
    }

    suspend fun restore(uri: Uri): Result = withContext(Dispatchers.IO) { lock.withLock {
        val bytes = requireNotNull(app.contentResolver.openInputStream(uri)) { "Cannot read trip backup" }.use {
            it.readBytesBounded(TripArchiveCodec.MAX_BYTES)
        }
        val archive = TripArchiveCodec.decode(bytes.toString(Charsets.UTF_8))
        require(com.lumen.launcher.travel.TripViewModel.hasMediaPermission(app)) { "Allow photo access before restoring trip photos" }
        val media = MediaStorePhotoRepository.queryPhotosSince(app, 0, cameraOnly = false)
        val index = media.groupBy { TripPhotoReconnect.key(it.displayName, it.dateTaken) }
        database.withTransaction {
            val existing = dao.allTrips().map { it.startTime to it.createdAt }.toMutableSet()
            val rows = dao.allPhotos()
            val used = rows.map { it.mediaStoreId }.toMutableSet()
            var placeholder = minOf(-1L, (rows.minOfOrNull { it.mediaStoreId } ?: 0L) - 1)
            var count = 0; var connected = 0; var missing = 0; var skipped = 0
            archive.albums.forEach { album ->
                val identity = album.trip.startTime to album.trip.createdAt
                if (!existing.add(identity)) { skipped++; return@forEach }
                // A backup of an ongoing trip is a snapshot, never an instruction to capture new photos.
                val id = dao.insertTrip(TripArchiveCodec.restoredTrip(album, archive.exportedAt))
                album.photos.forEach { p ->
                    val key = TripPhotoReconnect.key(p.displayName, p.dateTaken)
                    val candidate = TripPhotoReconnect.match(key, index)?.takeIf { it.mediaStoreId !in used }
                    val mediaId = candidate?.mediaStoreId ?: placeholder--
                    check(dao.insertTripPhoto(p.copy(id = 0, tripId = id, mediaStoreId = mediaId,
                        contentUri = candidate?.contentUri?.toString().orEmpty())) > 0) { "Photo reference conflict" }
                    used.add(mediaId)
                    if (!p.removed) { if (candidate == null) missing++ else connected++ }
                }
                count++
            }
            Result(count, connected, missing, skipped)
        }
    } }

    suspend fun reconnect(): Result = withContext(Dispatchers.IO) { lock.withLock {
        require(com.lumen.launcher.travel.TripViewModel.hasMediaPermission(app)) { "Allow photo access before reconnecting trip photos" }
        val media = MediaStorePhotoRepository.queryPhotosSince(app, 0, cameraOnly = false)
        val index = media.groupBy { TripPhotoReconnect.key(it.displayName, it.dateTaken) }
        database.withTransaction {
            val photos = dao.allPhotos()
            var placeholder = minOf(-1L, (photos.minOfOrNull { it.mediaStoreId } ?: 0L) - 1)
            // Clear stale local IDs before assigning new IDs, so recycled IDs cannot point at other photos.
            photos.forEach { dao.reconnectPhoto(it.id, placeholder--, "") }
            val used = mutableSetOf<Long>(); var connected = 0; var missing = 0
            photos.forEach { p ->
                val key = TripPhotoReconnect.key(p.displayName, p.dateTaken)
                val match = TripPhotoReconnect.match(key, index)?.takeIf { used.add(it.mediaStoreId) }
                if (match != null) dao.reconnectPhoto(p.id, match.mediaStoreId, match.contentUri.toString())
                if (!p.removed) { if (match == null) missing++ else connected++ }
            }
            Result(dao.allTrips().size, connected, missing)
        }
    } }

    suspend fun exportPhotos(uri: Uri, tripId: Long): Result = withContext(Dispatchers.IO) {
        val archive = snapshot(tripId)
        var connected = 0; var missing = 0
        ZipOutputStream(requireNotNull(app.contentResolver.openOutputStream(uri, "wt"))).use { zip ->
            zip.putNextEntry(ZipEntry("album.lumentrip")); zip.write(TripArchiveCodec.encode(archive).toByteArray()); zip.closeEntry()
            archive.albums.single().photos.filterNot { it.removed }.forEach { p ->
                val source = if (p.contentUri.isBlank()) null else runCatching { app.contentResolver.openInputStream(Uri.parse(p.contentUri)) }.getOrNull()
                if (source == null) { missing++; return@forEach }
                val name = (p.displayName ?: "photo.jpg").replace(Regex("[^a-zA-Z0-9._-]"), "_").take(150).ifBlank { "photo.jpg" }
                source.use {
                    zip.putNextEntry(ZipEntry("photos/${p.id}/$name")); it.copyTo(zip); zip.closeEntry()
                }
                connected++
            }
            zip.putNextEntry(ZipEntry("README.txt"))
            zip.write(("Lumen Trip Album\nPhotos exported: $connected\nMissing or inaccessible photos: $missing\n" +
                "album.lumentrip contains trip organization and photo references. Photos remain yours.\n" +
                "Copy extracted photos to your new phone and import album.lumentrip in Lumen.\n").toByteArray())
            zip.closeEntry()
        }
        Result(1, connected, missing)
    }

    private fun java.io.InputStream.readBytesBounded(limit: Int): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = read(buffer); if (count < 0) break
            require(output.size() + count <= limit) { "Trip backup is too large" }
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }
}
