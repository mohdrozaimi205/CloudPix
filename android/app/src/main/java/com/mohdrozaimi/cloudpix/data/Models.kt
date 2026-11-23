package com.mohdrozaimi.cloudpix.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Media(
    val id: String,
    @SerialName("album_id") val albumId: String? = null,
    val kind: String,
    val status: String = "ready",
    val filename: String,
    @SerialName("mime_type") val mimeType: String,
    @SerialName("size_bytes") val sizeBytes: Long,
    @SerialName("thumb_key") val thumbKey: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    @SerialName("duration_ms") val durationMs: Long? = null,
    @SerialName("is_favourite") val isFavourite: Boolean = false,
    @SerialName("taken_at") val takenAt: String? = null,
    @SerialName("last_accessed_at") val lastAccessedAt: String? = null,
    @SerialName("deleted_at") val deletedAt: String? = null,
    val source: String = "web",
    @SerialName("created_at") val createdAt: String,
) {
    val isVideo get() = kind == "video"
}

@Serializable
data class CountRow(val count: Long = 0)

@Serializable
data class Album(
    val id: String,
    val name: String,
    @SerialName("cover_media_id") val coverMediaId: String? = null,
    @SerialName("created_at") val createdAt: String,
    val media: List<CountRow> = emptyList(),
) {
    val count get() = media.firstOrNull()?.count ?: 0
}

@Serializable
data class Stats(
    @SerialName("quota_bytes") val quota: Long = 1L shl 40,
    @SerialName("used_bytes") val used: Long = 0,
    @SerialName("available_bytes") val available: Long = 1L shl 40,
    @SerialName("used_percent") val percent: Double = 0.0,
    @SerialName("photo_count") val photoCount: Long = 0,
    @SerialName("video_count") val videoCount: Long = 0,
    @SerialName("photo_bytes") val photoBytes: Long = 0,
    @SerialName("video_bytes") val videoBytes: Long = 0,
    @SerialName("trash_count") val trashCount: Long = 0,
    @SerialName("trash_bytes") val trashBytes: Long = 0,
    @SerialName("favourite_count") val favouriteCount: Long = 0,
)

@Serializable
data class BackupRun(
    val id: String,
    @SerialName("device_name") val deviceName: String,
    val status: String,
    @SerialName("files_uploaded") val filesUploaded: Int = 0,
    @SerialName("files_skipped") val filesSkipped: Int = 0,
    @SerialName("files_failed") val filesFailed: Int = 0,
    @SerialName("bytes_uploaded") val bytesUploaded: Long = 0,
    val message: String? = null,
    @SerialName("started_at") val startedAt: String,
    @SerialName("finished_at") val finishedAt: String? = null,
)

enum class View { ALL, PHOTOS, VIDEOS, FAVOURITES, TRASH, ALBUM }
enum class Sort(val label: String) { NEWEST("Terbaru"), OLDEST("Terlama"), SIZE("Saiz terbesar"), NAME("Nama A–Z") }

data class MediaFilter(
    val view: View,
    val albumId: String? = null,
    val search: String = "",
    val kind: String? = null,
    val fromMillis: Long? = null,
    val toMillis: Long? = null,
    val sort: Sort = Sort.NEWEST,
)
