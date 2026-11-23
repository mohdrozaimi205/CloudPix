package com.mohdrozaimi.cloudpix.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.time.Instant
import java.util.concurrent.TimeUnit

class ApiException(val status: Int, val code: String, message: String) : Exception(message) {
    /** Ralat sementara (rangkaian / pelayan) → boleh cuba semula */
    val transient get() = status == 0 || status == 429 || status >= 500
}

data class SignedUrl(val url: String?, val expiresAt: Long)

/** Panggil Edge Functions CloudPix. Kunci rahsia tidak pernah ada dalam APK. */
class Api(private val client: SupabaseClient, private val baseUrl: String, private val anonKey: String) {
    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(180, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun token(): String {
        withTimeoutOrNull(15_000) { client.auth.sessionStatus.first { it !is SessionStatus.Initializing } }
        val s = client.auth.currentSessionOrNull() ?: throw ApiException(401, "UNAUTHENTICATED", "Sila log masuk.")
        if (s.expiresAt.toEpochMilliseconds() - System.currentTimeMillis() < 60_000) {
            runCatching { client.auth.refreshCurrentSession() }
        }
        return client.auth.currentAccessTokenOrNull() ?: throw ApiException(401, "UNAUTHENTICATED", "Sila log masuk.")
    }

    suspend fun call(name: String, body: JsonObject): JsonObject {
        val t = token()
        return withContext(Dispatchers.IO) {
            val req = Request.Builder()
                .url("$baseUrl/functions/v1/$name")
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .header("Authorization", "Bearer $t")
                .header("apikey", anonKey)
                .build()
            try {
                http.newCall(req).execute().use { r ->
                    val text = r.body?.string().orEmpty()
                    val obj = runCatching { json.parseToJsonElement(text).jsonObject }.getOrElse { JsonObject(emptyMap()) }
                    if (!r.isSuccessful) {
                        val e = obj["error"] as? JsonObject
                        throw ApiException(
                            r.code,
                            e?.str("code") ?: "HTTP_${r.code}",
                            e?.str("message") ?: "Ralat pelayan (${r.code}).",
                        )
                    }
                    obj
                }
            } catch (e: IOException) {
                throw ApiException(0, "NETWORK", "Tiada sambungan internet.")
            }
        }
    }

    suspend fun mediaUrls(ids: List<String>, variant: String): Map<String, SignedUrl> {
        val r = call("media-url", buildJsonObject {
            put("ids", JsonArray(ids.map { JsonPrimitive(it) }))
            put("variant", variant)
        })
        val urls = r["urls"] as? JsonObject ?: return emptyMap()
        return urls.mapValues { (_, v) ->
            val o = v.jsonObject
            SignedUrl(o.str("url"), o.str("expiresAt")?.let { Instant.parse(it).toEpochMilli() } ?: 0)
        }
    }

    suspend fun delete(ids: List<String>, action: String) {
        ids.chunked(500).forEach { chunk ->
            call("media-delete", buildJsonObject {
                put("ids", JsonArray(chunk.map { JsonPrimitive(it) }))
                put("action", action)
            })
        }
    }

    suspend fun share(id: String, hours: Int): String? =
        call("share-create", buildJsonObject {
            put("mediaId", id)
            put("expiresInHours", hours)
        }).str("url")

    suspend fun exportPage(page: Int): JsonObject = call("export-data", buildJsonObject { put("page", page) })

    /** PUT terus ke R2 menggunakan URL bertandatangan. */
    fun put(url: String, body: RequestBody) {
        val req = Request.Builder().url(url).put(body).build()
        try {
            http.newCall(req).execute().use { r ->
                if (!r.isSuccessful) throw ApiException(if (r.code == 403) 0 else r.code, "R2_${r.code}", "Storan menolak upload (${r.code}).")
            }
        } catch (e: IOException) {
            throw ApiException(0, "NETWORK", "Sambungan terputus semasa upload.")
        }
    }
}

fun JsonObject.str(k: String): String? = (this[k] as? JsonPrimitive)?.contentOrNull
fun JsonObject.long(k: String): Long? = (this[k] as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull()?.toLong()
fun JsonObject.bool(k: String): Boolean = (this[k] as? JsonPrimitive)?.contentOrNull == "true"
fun JsonElement.obj(): JsonObject? = this as? JsonObject
