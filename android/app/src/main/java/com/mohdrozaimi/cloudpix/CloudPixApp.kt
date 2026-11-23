package com.mohdrozaimi.cloudpix

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.mohdrozaimi.cloudpix.data.Api
import com.mohdrozaimi.cloudpix.data.Prefs
import com.mohdrozaimi.cloudpix.data.Repo
import com.mohdrozaimi.cloudpix.work.BackupWorker
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.serializer.KotlinXSerializer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.Json

class CloudPixApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Graph.init(this)
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_UPLOAD, "Upload & backup", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Status upload dan auto-backup CloudPix"
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_UPDATE, "Kemas kini app", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Notifikasi bila versi baru CloudPix tersedia"
            },
        )
        BackupWorker.schedule(this)
        com.mohdrozaimi.cloudpix.work.Updates.schedule(this)
    }

    companion object {
        const val CHANNEL_UPLOAD = "upload"
        const val CHANNEL_UPDATE = "update"
    }
}

/** Bekas objek dikongsi (service locator ringkas). */
object Graph {
    lateinit var app: Application
        private set
    lateinit var prefs: Prefs
        private set
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Dinaikkan setiap kali data berubah (realtime / selepas tindakan) supaya skrin muat semula. */
    val refresh = MutableStateFlow(0)
    fun bump() {
        refresh.value++
    }

    @Volatile private var _client: SupabaseClient? = null
    @Volatile private var _api: Api? = null
    @Volatile private var _repo: Repo? = null

    fun init(app: Application) {
        this.app = app
        prefs = Prefs(app)
    }

    val isConfigured: Boolean get() = prefs.serverUrl.isNotBlank() && prefs.anonKey.isNotBlank()

    val client: SupabaseClient
        get() = _client ?: synchronized(this) {
            _client ?: createSupabaseClient(prefs.serverUrl, prefs.anonKey) {
                defaultSerializer = KotlinXSerializer(
                    Json {
                        ignoreUnknownKeys = true
                        explicitNulls = false
                        encodeDefaults = true
                    },
                )
                install(Auth)
                install(Postgrest)
                install(Realtime)
            }.also { _client = it }
        }

    val api: Api
        get() = _api ?: synchronized(this) { _api ?: Api(client, prefs.serverUrl, prefs.anonKey).also { _api = it } }

    val repo: Repo
        get() = _repo ?: synchronized(this) { _repo ?: Repo(client).also { _repo = it } }

    fun resetServer() {
        synchronized(this) {
            _client = null
            _api = null
            _repo = null
        }
    }
}
