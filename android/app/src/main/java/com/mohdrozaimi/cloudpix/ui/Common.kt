package com.mohdrozaimi.cloudpix.ui

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.mohdrozaimi.cloudpix.Graph
import com.mohdrozaimi.cloudpix.data.Album
import com.mohdrozaimi.cloudpix.data.Media
import com.mohdrozaimi.cloudpix.data.UrlCache
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

// ---------------- format ----------------
fun formatBytes(n: Long, digits: Int = 1): String {
    if (n <= 0) return "0 B"
    val u = listOf("B", "KB", "MB", "GB", "TB")
    var i = 0
    var v = n.toDouble()
    while (v >= 1024 && i < u.size - 1) { v /= 1024; i++ }
    return if (i == 0 || v >= 100) "${v.toLong()} ${u[i]}" else "%.${digits}f %s".format(Locale.US, v, u[i])
}

fun formatDuration(ms: Long?): String {
    if (ms == null || ms <= 0) return ""
    val s = ms / 1000
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}

private val dateFmt = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale("ms", "MY"))
fun formatDateTime(iso: String): String = runCatching { dateFmt.format(Instant.parse(iso).atZone(ZoneId.systemDefault())) }.getOrDefault(iso)

fun groupLabel(iso: String): String {
    val d = runCatching { Instant.parse(iso).atZone(ZoneId.systemDefault()).toLocalDate() }.getOrNull() ?: return ""
    val today = LocalDate.now()
    val days = ChronoUnit.DAYS.between(d, today)
    return when {
        days == 0L -> "Hari ini"
        days == 1L -> "Semalam"
        !d.isBefore(today.minusDays((today.dayOfWeek.value - 1).toLong())) -> "Minggu ini"
        d.year == today.year && d.month == today.month -> "Bulan ini"
        else -> d.year.toString()
    }
}

fun warningText(pct: Double): String? = when {
    pct >= 100 -> "Storan penuh. Upload disekat sehingga anda kosongkan ruang."
    pct >= 95 -> "Storan hampir penuh (95%). Kosongkan ruang segera."
    pct >= 90 -> "Storan sudah 90% digunakan."
    pct >= 80 -> "Storan sudah 80% digunakan."
    else -> null
}

fun toast(ctx: Context, msg: String) = Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()

/** Muat turun kualiti asal ke Downloads/CloudPix melalui DownloadManager. */
suspend fun downloadToDevice(ctx: Context, items: List<Media>) {
    val urls = UrlCache.downloads(items.map { it.id })
    val dm = ctx.getSystemService(DownloadManager::class.java)
    items.forEach { m ->
        val url = urls[m.id] ?: return@forEach
        val req = DownloadManager.Request(Uri.parse(url))
            .setTitle(m.filename)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "CloudPix/${m.filename.replace('/', '_')}")
        dm.enqueue(req)
    }
}

// ---------------- komponen ----------------
@Composable
fun GlassCard(modifier: Modifier = Modifier, padding: Dp = 18.dp, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val g = LocalGlass.current
    Surface(
        modifier = modifier.then(if (onClick != null) Modifier.clip(MaterialTheme.shapes.large).clickable { onClick() } else Modifier),
        shape = MaterialTheme.shapes.large,
        color = g.glass,
        border = BorderStroke(1.dp, g.border),
    ) {
        Column(Modifier.padding(padding), content = content)
    }
}

@Composable
fun GradientButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, icon: (@Composable () -> Unit)? = null, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        contentPadding = PaddingValues(0.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, disabledContainerColor = Color.Transparent),
    ) {
        Row(
            Modifier
                .background(if (enabled) Grad else Brush.linearGradient(listOf(Color.Gray, Color.Gray)), RoundedCornerShape(16.dp))
                .padding(horizontal = 20.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        ) {
            icon?.invoke()
            Text(text, color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun StorageRing(used: Long, quota: Long, size: Dp = 150.dp) {
    val pct = if (quota > 0) (used.toDouble() / quota).coerceIn(0.0, 1.0) else 0.0
    val track = MaterialTheme.colorScheme.surfaceVariant
    val color: Brush = when {
        pct >= 0.95 -> Brush.linearGradient(listOf(Danger, Danger))
        pct >= 0.8 -> Brush.linearGradient(listOf(Warn, Warn))
        else -> Grad
    }
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val sw = this.size.minDimension * 0.09f
            val s = Size(this.size.width - sw, this.size.height - sw)
            val o = Offset(sw / 2, sw / 2)
            drawArc(track, 0f, 360f, false, o, s, style = Stroke(sw))
            drawArc(color, -90f, (pct * 360).toFloat().coerceAtLeast(if (used > 0) 2f else 0f), false, o, s, style = Stroke(sw, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val p = pct * 100
            Text(if (p > 0 && p < 0.1) "<0.1%" else "%.${if (p < 10) 1 else 0}f%%".format(Locale.US, p), fontWeight = FontWeight.ExtraBold, fontSize = 24.sp)
            Text(formatBytes(used), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun MediaThumb(m: Media, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val url by produceState<String?>(null, m.id, m.thumbKey) {
        value = runCatching {
            when {
                m.thumbKey != null -> UrlCache.thumb(m.id)
                m.kind == "image" && m.sizeBytes < 15L * 1024 * 1024 -> UrlCache.original(m.id)
                else -> null
            }
        }.getOrNull()
    }
    Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
        Icon(if (m.isVideo) Icons.Outlined.Movie else Icons.Outlined.Image, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .5f))
        if (url != null) {
            val key = if (m.thumbKey != null) "t:${m.id}" else "o:${m.id}"
            AsyncImage(
                model = ImageRequest.Builder(ctx).data(url).memoryCacheKey(key).diskCacheKey(key).crossfade(true).build(),
                contentDescription = m.filename,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
fun EmptyState(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, text: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 60.dp, horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(72.dp).clip(RoundedCornerShape(22.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
        }
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 14.dp))
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
fun TextPromptDialog(title: String, label: String, initial: String = "", confirm: String, onDismiss: () -> Unit, onConfirm: suspend (String) -> Unit) {
    var v by remember { mutableStateOf(initial) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(v, { v = it.take(255) }, label = { Text(label) }, singleLine = true) },
        confirmButton = {
            TextButton(enabled = v.isNotBlank() && !busy, onClick = {
                busy = true
                scope.launch {
                    runCatching { onConfirm(v.trim()) }.onFailure { toast(ctx, it.message ?: "Ralat") }
                    busy = false
                    onDismiss()
                }
            }) { Text(confirm) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
    )
}

@Composable
fun ConfirmDialog(title: String, text: String, confirm: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = { onConfirm(); onDismiss() }) { Text(confirm, color = Danger) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
    )
}

@Composable
fun AlbumPickerDialog(onDismiss: () -> Unit, onPick: suspend (String?) -> Unit) {
    val albums by produceState<List<Album>>(emptyList()) { value = runCatching { Graph.repo.albums() }.getOrDefault(emptyList()) }
    var creating by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    if (creating) {
        TextPromptDialog("Album baru", "Nama album", confirm = "Cipta & pindah", onDismiss = onDismiss) { name ->
            val a = Graph.repo.createAlbum(name)
            onPick(a.id)
        }
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pindah ke album") },
        text = {
            LazyColumn(Modifier.height(360.dp)) {
                item {
                    Row(Modifier.fillMaxWidth().clickable { creating = true }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.CreateNewFolder, null, tint = MaterialTheme.colorScheme.primary)
                        Text("  Album baru…", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                    }
                }
                items(albums, key = { it.id }) { a ->
                    Row(
                        Modifier.fillMaxWidth().clickable { scope.launch { onPick(a.id); onDismiss() } }.padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(a.name, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Text("${a.count}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                item {
                    Text(
                        "Buang dari album",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().clickable { scope.launch { onPick(null); onDismiss() } }.padding(14.dp),
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Tutup") } },
    )
}

@Composable
fun Pill(text: String, modifier: Modifier = Modifier) {
    Box(modifier.clip(CircleShape).background(Color(0x99000000)).padding(horizontal = 7.dp, vertical = 3.dp)) {
        Text(text, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun LaunchOnce(block: suspend () -> Unit) {
    LaunchedEffect(Unit) { block() }
}

fun kotlinx.coroutines.CoroutineScope.launchCatching(ctx: Context, block: suspend () -> Unit) =
    launch(kotlinx.coroutines.Dispatchers.Main) { runCatching { block() }.onFailure { toast(ctx, friendlyError(it)) } }

/** Mesej ralat ringkas untuk pengguna — jangan papar URL/header/token mentah. */
fun friendlyError(e: Throwable): String {
    val t = e.message ?: return "Ralat tidak diketahui"
    return when {
        t.contains("schema cache", true) || t.contains("does not exist", true) -> "Database belum dipasang. Ikut panduan pemasangan (SQL Editor)."
        t.contains("JWT", true) || t.contains("401") -> "Sesi tamat. Sila log keluar dan log masuk semula."
        t.contains("Unable to resolve host", true) || t.contains("timeout", true) || t.contains("failed to connect", true) -> "Tiada sambungan internet."
        else -> t.lineSequence().first().take(160)
    }
}
