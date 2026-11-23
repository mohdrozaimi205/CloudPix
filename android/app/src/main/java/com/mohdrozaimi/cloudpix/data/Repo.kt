package com.mohdrozaimi.cloudpix.data

import com.mohdrozaimi.cloudpix.Graph
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant

const val PAGE_SIZE = 60

class Repo(private val client: SupabaseClient) {

    suspend fun media(f: MediaFilter, page: Int): List<Media> =
        client.from("media").select(Columns.ALL) {
            filter {
                eq("status", "ready")
                if (f.view == View.TRASH) filterNot("deleted_at", FilterOperator.IS, "null") else exact("deleted_at", null)
                when (f.view) {
                    View.PHOTOS -> eq("kind", "image")
                    View.VIDEOS -> eq("kind", "video")
                    View.FAVOURITES -> eq("is_favourite", true)
                    View.ALBUM -> f.albumId?.let { eq("album_id", it) }
                    else -> Unit
                }
                f.kind?.let { eq("kind", it) }
                if (f.search.isNotBlank()) ilike("filename", "%${f.search.trim().replace("%", "\\%").replace("_", "\\_")}%")
                f.fromMillis?.let { gte("created_at", Instant.ofEpochMilli(it).toString()) }
                f.toMillis?.let { lt("created_at", Instant.ofEpochMilli(it + 86_400_000).toString()) }
            }
            when (f.sort) {
                Sort.OLDEST -> { order("created_at", Order.ASCENDING); order("id", Order.ASCENDING) }
                Sort.SIZE -> { order("size_bytes", Order.DESCENDING); order("id", Order.ASCENDING) }
                Sort.NAME -> { order("filename", Order.ASCENDING); order("id", Order.ASCENDING) }
                Sort.NEWEST -> { order(if (f.view == View.TRASH) "deleted_at" else "created_at", Order.DESCENDING); order("id", Order.DESCENDING) }
            }
            range((page * PAGE_SIZE).toLong(), (page * PAGE_SIZE + PAGE_SIZE - 1).toLong())
        }.decodeList()

    suspend fun recent(orderBy: String, favouritesOnly: Boolean = false, limit: Long = 12): List<Media> =
        client.from("media").select(Columns.ALL) {
            filter {
                eq("status", "ready")
                exact("deleted_at", null)
                if (favouritesOnly) eq("is_favourite", true)
                if (orderBy == "last_accessed_at") filterNot("last_accessed_at", FilterOperator.IS, "null")
            }
            order(orderBy, Order.DESCENDING)
            limit(limit)
        }.decodeList()

    suspend fun stats(): Stats = client.postgrest.rpc("get_storage_stats").decodeList<Stats>().firstOrNull() ?: Stats()

    suspend fun albums(): List<Album> =
        client.from("albums").select(Columns.raw("id, name, cover_media_id, created_at, media!media_album_id_fkey(count)")) {
            order("created_at", Order.DESCENDING)
        }.decodeList()

    suspend fun albumCover(a: Album): Media? =
        client.from("media").select(Columns.ALL) {
            filter {
                eq("status", "ready")
                exact("deleted_at", null)
                if (a.coverMediaId != null) eq("id", a.coverMediaId) else eq("album_id", a.id)
            }
            order("created_at", Order.DESCENDING)
            limit(1)
        }.decodeList<Media>().firstOrNull()

    suspend fun createAlbum(name: String): Album =
        client.from("albums").insert(buildJsonObject { put("name", name) }) { select(Columns.raw("id, name, cover_media_id, created_at")) }
            .decodeSingle()

    suspend fun renameAlbum(id: String, name: String) {
        client.from("albums").update(buildJsonObject { put("name", name) }) { filter { eq("id", id) } }
    }

    suspend fun deleteAlbum(id: String) {
        client.from("albums").delete { filter { eq("id", id) } }
    }

    suspend fun updateMedia(ids: List<String>, values: JsonObject) {
        ids.chunked(200).forEach { chunk ->
            client.from("media").update(values) { filter { isIn("id", chunk) } }
        }
        Graph.bump()
    }

    suspend fun setFavourite(ids: List<String>, v: Boolean) = updateMedia(ids, buildJsonObject { put("is_favourite", v) })
    suspend fun rename(id: String, name: String) = updateMedia(listOf(id), buildJsonObject { put("filename", name) })
    suspend fun moveToAlbum(ids: List<String>, albumId: String?) =
        updateMedia(ids, JsonObject(mapOf("album_id" to (albumId?.let { JsonPrimitive(it) } ?: JsonNull))))

    suspend fun displayName(): String? =
        client.from("profiles").select(Columns.raw("display_name")).decodeList<JsonObject>().firstOrNull()?.str("display_name")

    suspend fun backupHistory(): List<BackupRun> =
        client.from("backup_history").select(Columns.ALL) {
            order("started_at", Order.DESCENDING)
            limit(30)
        }.decodeList()

    suspend fun startBackupRun(device: String): String =
        client.from("backup_history").insert(buildJsonObject { put("device_name", device.take(100)); put("status", "running") }) {
            select(Columns.raw("id"))
        }.decodeSingle<JsonObject>().str("id")!!

    suspend fun finishBackupRun(id: String, status: String, up: Int, skipped: Int, failed: Int, bytes: Long, message: String?) {
        client.from("backup_history").update(buildJsonObject {
            put("status", status)
            put("files_uploaded", up)
            put("files_skipped", skipped)
            put("files_failed", failed)
            put("bytes_uploaded", bytes)
            put("message", message?.take(500))
            put("finished_at", Instant.now().toString())
        }) { filter { eq("id", id) } }
    }

    // ---------- Realtime: perubahan dari Web / peranti lain ----------
    private var rtJob: Job? = null
    fun startRealtime() {
        if (rtJob?.isActive == true) return
        rtJob = Graph.scope.launch {
            runCatching {
                val ch = client.channel("cloudpix-sync")
                val a = ch.postgresChangeFlow<PostgresAction>(schema = "public") { table = "media" }
                val b = ch.postgresChangeFlow<PostgresAction>(schema = "public") { table = "albums" }
                val c = ch.postgresChangeFlow<PostgresAction>(schema = "public") { table = "profiles" }
                var pending: Job? = null
                merge(a, b, c).onEach {
                    pending?.cancel()
                    pending = Graph.scope.launch { delay(400); Graph.bump() }
                }.launchIn(this)
                ch.subscribe()
            }
        }
    }
}
