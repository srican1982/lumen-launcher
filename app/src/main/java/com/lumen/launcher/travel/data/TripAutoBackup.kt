package com.lumen.launcher.travel.data

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** User-selected document permission is device-local and is never restored onto another phone. */
object TripAutoBackup {
    private const val JOB_ID = 7149
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private var observer: Job? = null
    fun preferences(context: Context) = context.getSharedPreferences("trip_auto_backup", Context.MODE_PRIVATE)
    fun enabled(context: Context) = preferences(context).getString("destination", null) != null

    suspend fun enable(context: Context, uri: Uri) {
        context.contentResolver.takePersistableUriPermission(uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        // Validate the destination before turning on recurring writes.
        TripBackupManager(context).export(uri)
        preferences(context).edit().putString("destination", uri.toString())
            .putLong("success", System.currentTimeMillis()).remove("error").apply()
        start(context)
    }

    fun disable(context: Context) {
        preferences(context).edit().remove("destination").apply()
        context.getSystemService(JobScheduler::class.java).cancel(JOB_ID)
        observer?.cancel(); observer = null
    }

    @Synchronized fun start(context: Context) {
        val app = context.applicationContext
        if (!enabled(app)) return
        val job = JobInfo.Builder(JOB_ID, ComponentName(app, TripAutoBackupJob::class.java))
            .setPeriodic(6 * 60 * 60 * 1000L).setPersisted(true)
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).build()
        if (app.getSystemService(JobScheduler::class.java).schedule(job) == JobScheduler.RESULT_FAILURE) {
            preferences(app).edit().putString("error", "Background scheduling failed. Re-enable automatic backup.").apply()
        }
        if (observer?.isActive == true) return
        observer = scope.launch {
            val dao = TravelDatabase.get(app).tripDao()
            combine(dao.observeAllTrips(), dao.observeAllPhotos()) { trips, photos -> trips to photos }
                .collectLatest {
                    delay(15_000)
                    backup(app)
                }
        }
    }

    suspend fun backup(context: Context): Boolean = mutex.withLock {
        val prefs = preferences(context)
        val destination = prefs.getString("destination", null) ?: return@withLock true
        try {
            TripBackupManager(context).export(Uri.parse(destination))
            // Do not report success for a destination switched off while the write was running.
            if (prefs.getString("destination", null) == destination)
                prefs.edit().putLong("success", System.currentTimeMillis()).remove("error").apply()
            true
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) {
            prefs.edit().putString("error", "Backup needs attention: check the connection and file access, or choose the destination again.").apply()
            false
        }
    }
}

class TripAutoBackupJob : JobService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var running: Job? = null
    override fun onStartJob(params: JobParameters): Boolean {
        running = scope.launch { jobFinished(params, !TripAutoBackup.backup(applicationContext)) }
        return true
    }
    override fun onStopJob(params: JobParameters): Boolean { running?.cancel(); return true }
    override fun onDestroy() { scope.cancel(); super.onDestroy() }
}
