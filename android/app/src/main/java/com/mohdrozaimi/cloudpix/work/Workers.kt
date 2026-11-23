package com.mohdrozaimi.cloudpix.work

import android.app.Notification
import android.content.ContentUris
import android.content.Context
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.mohdrozaimi.cloudpix.CloudPixApp
import com.mohdrozaimi.cloudpix.Graph
import com.mohdrozaimi.cloudpix.R
import com.mohdrozaimi.cloudpix.data.ApiException
import com.mohdrozaimi.cloudpix.data.LocalFile
import com.mohdrozaimi.cloudpix.data.UploadResult
import com.mohdrozaimi.cloudpix.data.Uploader
import java.util.concurrent.TimeUnit

private fun notification(ctx: Context, title: String, text: String, sent: Long = 0, total: Long = 0, ongoing: Boolean = true): Notification =
    NotificationCompat.Builder(ctx, CloudPixApp.CHANNEL_UPLOAD)
        .setSmallIcon(R.drawable.ic_stat_cloud)
        .setContentTitle(title)
        .setContentText(text)
        .setOnlyAlertOnce(true)
        .setOngoing(ongoing)
        .setSilent(true)
        .apply {
            if (total > 0) setProgress(1000, ((sent * 1000) / total).toInt(), false)
            else if (ongoing) setProgress(0, 0, true)
        }
        .build()

private fun foregroundInfo(id: Int, n: Notification) =
    if (Build.VERSION.SDK_INT >= 29) ForegroundInfo(id, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC) else ForegroundInfo(id, n)

private fun canNotify(ctx: Context) = NotificationManagerCompat.from(ctx).areNotificationsEnabled()

/** Upload satu fail yang dipilih pengguna. Kemajuan dilaporkan ke UI melalui WorkInfo. */
class UploadWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    private val notifId = id.hashCode()

    override suspend fun getForegroundInfo() = foregroundInfo(notifId, notification(applicationContext, "CloudPix", "Menyediakan upload…"))

    override suspend fun doWork(): Result {
        if (!Graph.isConfigured) return Result.failure(workDataOf("error" to "Server belum disambung"))
        val uri = Uri.parse(inputData.getString("uri") ?: return Result.failure())
        val albumId = inputData.getString("albumId")
        val up = Uploader(applicationContext, Graph.api)
        val f = up.describe(uri) ?: return Result.failure(workDataOf("error" to "Fail tidak dapat dibaca", "name" to "?"))
        runCatching { setForeground(foregroundInfo(notifId, notification(applicationContext, "Mengupload", f.name))) }
        setProgress(workDataOf("name" to f.name, "sent" to 0L, "total" to f.size))
        var last = 0L
        return try {
            val r = up.upload(f, "android", albumId) { sent, total ->
                val now = System.currentTimeMillis()
                if (now - last > 500 || sent == total) {
                    last = now
                    setProgressAsync(workDataOf("name" to f.name, "sent" to sent, "total" to total))
                    if (canNotify(applicationContext)) {
                        runCatching {
                            NotificationManagerCompat.from(applicationContext)
                                .notify(notifId, notification(applicationContext, "Mengupload", f.name, sent, total))
                        }
                    }
                }
            }
            Graph.bump()
            Graph.prefs.setUploadError(id.toString(), f.name, null)
            Result.success(workDataOf("name" to f.name, "duplicate" to (r is UploadResult.Duplicate)))
        } catch (e: ApiException) {
            Graph.prefs.setUploadError(id.toString(), f.name, e.message)
            if (e.transient && runAttemptCount < 6) Result.retry()
            else Result.failure(workDataOf("name" to f.name, "error" to (e.message ?: "Gagal")))
        } catch (e: SecurityException) {
            Result.failure(workDataOf("name" to f.name, "error" to "Tiada kebenaran membaca fail"))
        } catch (e: Exception) {
            Graph.prefs.setUploadError(id.toString(), f.name, e.message ?: e.javaClass.simpleName)
            if (runAttemptCount < 3) Result.retry() else Result.failure(workDataOf("name" to f.name, "error" to (e.message ?: "Gagal")))
        }
    }

    companion object {
        const val TAG = "cloudpix-upload"
        fun enqueue(ctx: Context, uris: List<Uri>, albumId: String? = null) {
            val wm = WorkManager.getInstance(ctx)
            uris.forEach { uri ->
                val req = OneTimeWorkRequestBuilder<UploadWorker>()
                    .setInputData(workDataOf("uri" to uri.toString(), "albumId" to albumId))
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                    .addTag(TAG)
                    .build()
                wm.enqueueUniqueWork("upload-$uri", ExistingWorkPolicy.KEEP, req)
            }
        }
    }
}

data class BucketInfo(val id: String, val name: String, val count: Int)

/** Auto-backup: imbas folder MediaStore yang dipilih dan upload fail baru. */
class BackupWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun getForegroundInfo() = foregroundInfo(NOTIF_ID, notification(applicationContext, "Backup CloudPix", "Mengimbas…"))

    override suspend fun doWork(): Result {
        val p = Graph.prefs
        if (!Graph.isConfigured || !p.backupEnabled || p.backupPaused || p.backupBuckets.isEmpty()) return Result.success()
        val files = scan(applicationContext).filterNot { p.isBackedUp(it.first) }
        if (files.isEmpty()) {
            p.lastBackupAt = System.currentTimeMillis()
            return Result.success()
        }
        runCatching { setForeground(foregroundInfo(NOTIF_ID, notification(applicationContext, "Backup CloudPix", "${files.size} fail baru"))) }

        val runId = runCatching { Graph.repo.startBackupRun("${Build.MANUFACTURER} ${Build.MODEL}") }.getOrNull()
        val up = Uploader(applicationContext, Graph.api)
        var ok = 0
        var skipped = 0
        var failed = 0
        var bytes = 0L
        var message: String? = null
        var status = "completed"

        for ((i, pair) in files.withIndex()) {
            if (isStopped || Graph.prefs.backupPaused) { status = "paused"; break }
            val (key, f) = pair
            setProgress(workDataOf("done" to i, "total" to files.size, "name" to f.name))
            if (canNotify(applicationContext)) {
                runCatching {
                    NotificationManagerCompat.from(applicationContext).notify(
                        NOTIF_ID,
                        notification(applicationContext, "Backup ${i + 1}/${files.size}", f.name, i.toLong(), files.size.toLong()),
                    )
                }
            }
            try {
                when (val r = up.upload(f, "backup") { _, _ -> }) {
                    is UploadResult.Done -> { ok++; bytes += r.bytes }
                    UploadResult.Duplicate -> skipped++
                }
                p.markBackedUp(key)
            } catch (e: ApiException) {
                failed++
                message = e.message
                if (e.code == "QUOTA_EXCEEDED") { status = "failed"; message = "Storan penuh"; break }
                if (e.code == "UNSUPPORTED_TYPE") p.markBackedUp(key)
                if (e.status == 0) { status = "paused"; message = "Sambungan terputus"; break }
            } catch (e: Exception) {
                failed++
                message = e.message
            }
        }
        if (runId != null) runCatching { Graph.repo.finishBackupRun(runId, status, ok, skipped, failed, bytes, message) }
        p.lastBackupAt = System.currentTimeMillis()
        Graph.bump()
        if (canNotify(applicationContext)) {
            runCatching {
                NotificationManagerCompat.from(applicationContext).notify(
                    NOTIF_ID + 1,
                    notification(applicationContext, "Backup selesai", "$ok diupload · $skipped sudah ada · $failed gagal", ongoing = false),
                )
            }
        }
        return if (status == "paused" && !Graph.prefs.backupPaused) Result.retry() else Result.success()
    }

    companion object {
        private const val NOTIF_ID = 4201
        private const val PERIODIC = "cloudpix-backup"
        private const val NOW = "cloudpix-backup-now"

        private fun constraints() = Constraints.Builder()
            .setRequiredNetworkType(if (Graph.prefs.wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()

        fun schedule(ctx: Context) {
            val wm = WorkManager.getInstance(ctx)
            val p = Graph.prefs
            if (!p.backupEnabled || p.backupPaused) {
                wm.cancelUniqueWork(PERIODIC)
                return
            }
            val req = PeriodicWorkRequestBuilder<BackupWorker>(1, TimeUnit.HOURS).setConstraints(constraints()).build()
            wm.enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.UPDATE, req)
        }

        fun runNow(ctx: Context) {
            val req = OneTimeWorkRequestBuilder<BackupWorker>().setConstraints(constraints()).build()
            WorkManager.getInstance(ctx).enqueueUniqueWork(NOW, ExistingWorkPolicy.KEEP, req)
        }

        fun stop(ctx: Context) {
            WorkManager.getInstance(ctx).cancelUniqueWork(NOW)
        }

        /** Senarai folder (bucket) yang ada foto/video — termasuk Camera, Screenshots, WhatsApp. */
        fun buckets(ctx: Context): List<BucketInfo> {
            val map = linkedMapOf<String, Pair<String, Int>>()
            listOf(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, MediaStore.Video.Media.EXTERNAL_CONTENT_URI).forEach { uri ->
                runCatching {
                    ctx.contentResolver.query(
                        uri,
                        arrayOf(MediaStore.MediaColumns.BUCKET_ID, MediaStore.MediaColumns.BUCKET_DISPLAY_NAME),
                        null, null, null,
                    )?.use { c ->
                        while (c.moveToNext()) {
                            val id = c.getString(0) ?: continue
                            val name = c.getString(1) ?: "(tanpa nama)"
                            map[id] = name to ((map[id]?.second ?: 0) + 1)
                        }
                    }
                }
            }
            return map.map { (id, v) -> BucketInfo(id, v.first, v.second) }.sortedByDescending { it.count }
        }

        fun scan(ctx: Context): List<Pair<String, LocalFile>> {
            val p = Graph.prefs
            val buckets = p.backupBuckets.toList()
            if (buckets.isEmpty()) return emptyList()
            val out = mutableListOf<Pair<String, LocalFile>>()
            val sources = buildList {
                if (p.backupPhotos) add("img" to MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                if (p.backupVideos) add("vid" to MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
            }
            val sel = "${MediaStore.MediaColumns.BUCKET_ID} IN (${buckets.joinToString(",") { "?" }}) AND ${MediaStore.MediaColumns.SIZE} > 0"
            for ((prefix, base) in sources) {
                runCatching {
                    ctx.contentResolver.query(
                        base,
                        arrayOf(
                            MediaStore.MediaColumns._ID,
                            MediaStore.MediaColumns.DISPLAY_NAME,
                            MediaStore.MediaColumns.SIZE,
                            MediaStore.MediaColumns.DATE_TAKEN,
                            MediaStore.MediaColumns.DATE_MODIFIED,
                        ),
                        sel, buckets.toTypedArray(), "${MediaStore.MediaColumns.DATE_ADDED} ASC",
                    )?.use { c ->
                        while (c.moveToNext()) {
                            val id = c.getLong(0)
                            val name = c.getString(1) ?: continue
                            if (!Uploader.isSupported(name)) continue
                            val size = c.getLong(2)
                            val taken = if (!c.isNull(3)) c.getLong(3) else c.getLong(4) * 1000
                            val key = "$prefix:$id:$size"
                            out += key to LocalFile(ContentUris.withAppendedId(base, id), name, size, taken.takeIf { it > 0 })
                        }
                    }
                }
            }
            return out
        }
    }
}
