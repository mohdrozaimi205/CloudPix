package com.mohdrozaimi.cloudpix.work

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.mohdrozaimi.cloudpix.BuildConfig
import com.mohdrozaimi.cloudpix.CloudPixApp
import com.mohdrozaimi.cloudpix.Graph
import com.mohdrozaimi.cloudpix.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

data class UpdateInfo(val version: String, val notes: String, val apkUrl: String, val pageUrl: String)

/** Semak GitHub Releases untuk versi APK yang lebih baru. */
object Updates {
    private const val API = "https://api.github.com/repos/mohdrozaimi205/CloudPix/releases/latest"
    private val http = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS).build()

    val currentVersion: String get() = BuildConfig.VERSION_NAME

    /** "1.0.12" → [1,0,12] untuk perbandingan nombor. */
    private fun parts(v: String) = v.trim().removePrefix("v").split('.').map { it.toIntOrNull() ?: 0 }

    fun isNewer(remote: String, local: String): Boolean {
        val a = parts(remote)
        val b = parts(local)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    /** Pulangkan maklumat kemas kini jika ada versi lebih baru, atau null. */
    suspend fun check(): UpdateInfo? = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(API).header("Accept", "application/vnd.github+json").build()
        http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) return@withContext null
            val o = Json.parseToJsonElement(r.body?.string().orEmpty()).jsonObject
            val tag = o["tag_name"]?.jsonPrimitive?.content ?: return@withContext null
            if (!isNewer(tag, currentVersion)) return@withContext null
            val apk = (o["assets"] as? JsonArray)
                ?.map { it.jsonObject }
                ?.firstOrNull { it["name"]?.jsonPrimitive?.content?.endsWith(".apk") == true }
                ?.get("browser_download_url")?.jsonPrimitive?.content
            val page = o["html_url"]?.jsonPrimitive?.content ?: "https://github.com/mohdrozaimi205/CloudPix/releases/latest"
            val notes = (o["body"] as? kotlinx.serialization.json.JsonPrimitive)?.content.orEmpty().take(600)
            UpdateInfo(tag.removePrefix("v"), notes, apk ?: page, page)
        }
    }

    /** Buka pautan muat turun APK; pelayar akan muat turun & Android akan tanya untuk pasang. */
    fun open(ctx: Context, info: UpdateInfo) {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(info.apkUrl)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun schedule(ctx: Context) {
        val req = PeriodicWorkRequestBuilder<UpdateWorker>(12, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork("cloudpix-update-check", ExistingPeriodicWorkPolicy.KEEP, req)
    }

    @Suppress("unused")
    private fun unused(o: JsonObject) = o
}

/** Semak kemas kini di latar belakang setiap 12 jam dan hantar notifikasi. */
class UpdateWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val info = runCatching { Updates.check() }.getOrNull() ?: return Result.success()
        val p = Graph.prefs
        if (p.notifiedVersion == info.version) return Result.success() // jangan ulang notifikasi yang sama
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(info.apkUrl))
        val pi = PendingIntent.getActivity(applicationContext, 7, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(applicationContext, CloudPixApp.CHANNEL_UPDATE)
            .setSmallIcon(R.drawable.ic_stat_cloud)
            .setContentTitle("Kemas kini CloudPix v${info.version}")
            .setContentText("Versi baru tersedia. Tekan untuk muat turun.")
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        runCatching {
            if (NotificationManagerCompat.from(applicationContext).areNotificationsEnabled()) {
                NotificationManagerCompat.from(applicationContext).notify(7101, n)
                p.notifiedVersion = info.version
            }
        }
        return Result.success()
    }
}
