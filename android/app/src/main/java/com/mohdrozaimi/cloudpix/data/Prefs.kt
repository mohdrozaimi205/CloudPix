package com.mohdrozaimi.cloudpix.data

import android.content.Context
import androidx.core.content.edit
import com.mohdrozaimi.cloudpix.BuildConfig

class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("cloudpix", Context.MODE_PRIVATE)
    private val done = ctx.getSharedPreferences("cloudpix_backup_done", Context.MODE_PRIVATE)

    var serverUrl: String
        get() = sp.getString("server_url", null) ?: BuildConfig.SUPABASE_URL
        set(v) = sp.edit { putString("server_url", v.trim().trimEnd('/')) }
    var anonKey: String
        get() = sp.getString("anon_key", null) ?: BuildConfig.SUPABASE_ANON_KEY
        set(v) = sp.edit { putString("anon_key", v.trim()) }

    var rememberMe: Boolean
        get() = sp.getBoolean("remember", true)
        set(v) = sp.edit { putBoolean("remember", v) }
    var biometricLock: Boolean
        get() = sp.getBoolean("biometric", false)
        set(v) = sp.edit { putBoolean("biometric", v) }
    /** 0 = ikut sistem, 1 = cerah, 2 = gelap */
    var theme: Int
        get() = sp.getInt("theme", 0)
        set(v) = sp.edit { putInt("theme", v) }

    // ---------- Auto backup ----------
    var backupEnabled: Boolean
        get() = sp.getBoolean("backup_enabled", false)
        set(v) = sp.edit { putBoolean("backup_enabled", v) }
    var backupPaused: Boolean
        get() = sp.getBoolean("backup_paused", false)
        set(v) = sp.edit { putBoolean("backup_paused", v) }
    var wifiOnly: Boolean
        get() = sp.getBoolean("wifi_only", true)
        set(v) = sp.edit { putBoolean("wifi_only", v) }
    var backupPhotos: Boolean
        get() = sp.getBoolean("backup_photos", true)
        set(v) = sp.edit { putBoolean("backup_photos", v) }
    var backupVideos: Boolean
        get() = sp.getBoolean("backup_videos", true)
        set(v) = sp.edit { putBoolean("backup_videos", v) }
    /** BUCKET_ID MediaStore folder yang dipilih */
    var backupBuckets: Set<String>
        get() = sp.getStringSet("backup_buckets", emptySet())!!.toSet()
        set(v) = sp.edit { putStringSet("backup_buckets", v) }
    var lastBackupAt: Long
        get() = sp.getLong("last_backup", 0)
        set(v) = sp.edit { putLong("last_backup", v) }

    fun isBackedUp(key: String) = done.contains(key)
    fun markBackedUp(key: String) = done.edit { putBoolean(key, true) }
}
