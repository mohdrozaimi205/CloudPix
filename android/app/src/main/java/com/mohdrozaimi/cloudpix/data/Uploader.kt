package com.mohdrozaimi.cloudpix.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import java.io.ByteArrayOutputStream
import java.io.FileInputStream
import java.io.InputStream
import java.security.MessageDigest
import java.time.Instant

sealed class UploadResult {
    data class Done(val mediaId: String, val bytes: Long) : UploadResult()
    data object Duplicate : UploadResult()
}

data class LocalFile(val uri: Uri, val name: String, val size: Long, val takenAt: Long?)

/**
 * Enjin upload Android — sama aliran dengan web:
 * hash SHA-256 → upload-init (kuota + duplikat) → PUT terus ke R2 (single / multipart + resume) → sahkan.
 */
class Uploader(private val ctx: Context, private val api: Api) {
    private val resolver get() = ctx.contentResolver

    fun describe(uri: Uri): LocalFile? {
        var name: String? = null
        var size = -1L
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                name = c.getString(0)
                if (!c.isNull(1)) size = c.getLong(1)
            }
        }
        var taken: Long? = null
        runCatching {
            resolver.query(uri, arrayOf(MediaStore.MediaColumns.DATE_TAKEN), null, null, null)?.use { c ->
                if (c.moveToFirst() && !c.isNull(0)) taken = c.getLong(0).takeIf { it > 0 }
            }
        }
        if (name == null || size <= 0) return null
        return LocalFile(uri, name!!, size, taken)
    }

    suspend fun upload(
        f: LocalFile,
        source: String,
        albumId: String? = null,
        onProgress: (sent: Long, total: Long) -> Unit,
    ): UploadResult = withContext(Dispatchers.IO) {
        if (!isSupported(f.name)) throw ApiException(415, "UNSUPPORTED_TYPE", "Jenis fail tidak disokong: ${f.name}")
        val sha = sha256(f.uri)
        val isVideo = videoExt.any { f.name.lowercase().endsWith(it) }
        val meta = readMeta(f.uri, isVideo)

        val init = retrying {
            api.call("upload-init", buildJsonObject {
                put("filename", f.name)
                put("size", f.size)
                put("sha256", sha)
                albumId?.let { put("albumId", it) }
                meta.width?.let { put("width", it) }
                meta.height?.let { put("height", it) }
                meta.durationMs?.let { put("durationMs", it) }
                f.takenAt?.let { put("takenAt", Instant.ofEpochMilli(it).toString()) }
                put("source", source)
            })
        }
        if (init.bool("duplicate")) return@withContext UploadResult.Duplicate
        val mediaId = init.str("mediaId")!!

        meta.thumb?.let { bytes ->
            runCatching { api.put(init.str("thumbUploadUrl")!!, bytes.toRequestBody("image/jpeg".toMediaType())) }
        }

        try {
            if (init.str("mode") == "single") {
                retrying(tries = 4) {
                    api.put(
                        init.str("uploadUrl")!!,
                        StreamBody(init.str("contentType")!!.toMediaType(), f.size, { resolver.openInputStream(f.uri)!! }) {
                            onProgress(it, f.size)
                        },
                    )
                }
                onProgress(f.size, f.size)
                retrying { api.call("upload-complete", buildJsonObject { put("mediaId", mediaId) }) }
            } else {
                multipart(f, mediaId, init.long("partSize")!!, init.long("partCount")!!.toInt(), onProgress)
                retrying { api.call("upload-multipart", buildJsonObject { put("action", "complete"); put("mediaId", mediaId) }) }
            }
        } catch (e: Exception) {
            // Upload single tak boleh disambung → lepaskan tempahan kuota. Multipart disimpan untuk resume (7 hari).
            if (init.str("mode") == "single") {
                runCatching { api.call("upload-multipart", buildJsonObject { put("action", "abort"); put("mediaId", mediaId) }) }
            }
            throw e
        }
        UploadResult.Done(mediaId, f.size)
    }

    private suspend fun multipart(f: LocalFile, mediaId: String, partSize: Long, partCount: Int, onProgress: (Long, Long) -> Unit) {
        val listed = retrying { api.call("upload-multipart", buildJsonObject { put("action", "list"); put("mediaId", mediaId) }) }
        val done = mutableMapOf<Int, Long>()
        (listed["parts"] as? JsonArray)?.forEach {
            val o = it.jsonObject
            done[o.long("partNumber")!!.toInt()] = o.long("size") ?: 0
        }
        onProgress(done.values.sum(), f.size)
        val todo = (1..partCount).filter { it !in done }
        val urls = mutableMapOf<Int, String>()

        resolver.openFileDescriptor(f.uri, "r")!!.use { pfd ->
            FileInputStream(pfd.fileDescriptor).use { fis ->
                val channel = fis.channel
                val buf = ByteArray(partSize.toInt())
                for (n in todo) {
                    if (n !in urls) {
                        val batch = todo.filter { it >= n && it !in urls }.take(20)
                        val r = retrying {
                            api.call("upload-multipart", buildJsonObject {
                                put("action", "sign")
                                put("mediaId", mediaId)
                                put("partNumbers", JsonArray(batch.map { JsonPrimitive(it) }))
                            })
                        }
                        (r["urls"] as? JsonObject)?.forEach { (k, v) -> urls[k.toInt()] = (v as JsonPrimitive).content }
                    }
                    val start = (n - 1) * partSize
                    val len = minOf(partSize, f.size - start).toInt()
                    channel.position(start)
                    var read = 0
                    while (read < len) {
                        val k = fis.read(buf, read, len - read)
                        if (k < 0) break
                        read += k
                    }
                    if (read != len) throw ApiException(422, "READ_ERROR", "Fail berubah semasa upload.")
                    val base = done.values.sum()
                    retrying(tries = 8) {
                        api.put(urls[n]!!, StreamBody(null, len.toLong(), { buf.inputStream(0, len) }) { onProgress(base + it, f.size) })
                    }
                    done[n] = len.toLong()
                    onProgress(done.values.sum(), f.size)
                }
            }
        }
    }

    private fun sha256(uri: Uri): String {
        val md = MessageDigest.getInstance("SHA-256")
        resolver.openInputStream(uri)!!.use { input ->
            val buf = ByteArray(1 shl 16)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    private data class Meta(val width: Int? = null, val height: Int? = null, val durationMs: Long? = null, val thumb: ByteArray? = null)

    private fun readMeta(uri: Uri, isVideo: Boolean): Meta = runCatching {
        var w: Int? = null
        var h: Int? = null
        var dur: Long? = null
        var bmp: Bitmap? = null
        if (isVideo) {
            val r = MediaMetadataRetriever()
            try {
                r.setDataSource(ctx, uri)
                val rot = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
                val vw = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull()
                val vh = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull()
                if (rot == 90 || rot == 270) { w = vh; h = vw } else { w = vw; h = vh }
                dur = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                bmp = r.getFrameAtTime(1_000_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            } finally {
                r.release()
            }
        } else {
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o) }
            if (o.outWidth > 0) { w = o.outWidth; h = o.outHeight }
        }
        if (bmp == null && Build.VERSION.SDK_INT >= 29) {
            bmp = runCatching { resolver.loadThumbnail(uri, Size(512, 512), null) }.getOrNull()
        }
        if (bmp == null && !isVideo && (w ?: 0) > 0) {
            val sample = Integer.highestOneBit(maxOf(1, maxOf(w!!, h!!) / 512))
            bmp = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
        }
        val thumb = bmp?.let { b ->
            val s = minOf(1f, 512f / maxOf(b.width, b.height))
            val scaled = if (s < 1f) Bitmap.createScaledBitmap(b, (b.width * s).toInt(), (b.height * s).toInt(), true) else b
            ByteArrayOutputStream().also { scaled.compress(Bitmap.CompressFormat.JPEG, 82, it) }.toByteArray()
                .takeIf { it.size <= 2 * 1024 * 1024 }
        }
        Meta(w, h, dur, thumb)
    }.getOrDefault(Meta())

    companion object {
        private val imageExt = listOf(".jpg", ".jpeg", ".png", ".webp", ".heic", ".heif", ".gif")
        private val videoExt = listOf(".mp4", ".m4v", ".mov", ".mkv", ".webm")
        fun isSupported(name: String) = (imageExt + videoExt).any { name.lowercase().endsWith(it) }

        suspend fun <T> retrying(tries: Int = 5, block: suspend () -> T): T {
            var last: Exception? = null
            repeat(tries) { i ->
                try {
                    return block()
                } catch (e: ApiException) {
                    if (!e.transient) throw e
                    last = e
                    delay(minOf(30_000L, 1000L shl i))
                }
            }
            throw last!!
        }
    }
}

/** RequestBody yang melaporkan bait sebenar yang dihantar. */
class StreamBody(
    private val type: MediaType?,
    private val length: Long,
    private val open: () -> InputStream,
    private val onBytes: (Long) -> Unit,
) : RequestBody() {
    override fun contentType() = type
    override fun contentLength() = length
    override fun writeTo(sink: BufferedSink) {
        open().use { input ->
            val buf = ByteArray(1 shl 16)
            var total = 0L
            var lastReport = 0L
            while (total < length) {
                val n = input.read(buf, 0, minOf(buf.size.toLong(), length - total).toInt())
                if (n < 0) break
                sink.write(buf, 0, n)
                total += n
                if (total - lastReport > 256 * 1024 || total == length) {
                    onBytes(total)
                    lastReport = total
                }
            }
        }
    }
}
