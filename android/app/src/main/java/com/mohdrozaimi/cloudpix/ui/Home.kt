package com.mohdrozaimi.cloudpix.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.mohdrozaimi.cloudpix.Graph
import com.mohdrozaimi.cloudpix.data.Media
import com.mohdrozaimi.cloudpix.data.Stats
import com.mohdrozaimi.cloudpix.work.UploadWorker
import kotlinx.coroutines.flow.flowOf

/** Pemilih foto/video sistem → upload melalui WorkManager. */
@Composable
fun rememberUploadPicker(albumId: String? = null): () -> Unit {
    val ctx = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(200)) { uris: List<Uri> ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        uris.forEach { runCatching { ctx.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION) } }
        UploadWorker.enqueue(ctx, uris, albumId)
        toast(ctx, "${uris.size} fail ditambah ke barisan upload")
    }
    return { launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) }
}

@Composable
fun HomeScreen(onOpenViewer: () -> Unit, onNavigate: (String) -> Unit) {
    val ctx = LocalContext.current
    val refresh by Graph.refresh.collectAsState()
    var stats by remember { mutableStateOf<Stats?>(null) }
    var recent by remember { mutableStateOf<List<Media>>(emptyList()) }
    var accessed by remember { mutableStateOf<List<Media>>(emptyList()) }
    var favs by remember { mutableStateOf<List<Media>>(emptyList()) }
    var name by remember { mutableStateOf<String?>(null) }
    val pick = rememberUploadPicker()

    LaunchedEffect(refresh) {
        runCatching { stats = Graph.repo.stats() }
        runCatching { recent = Graph.repo.recent("created_at") }
        runCatching { accessed = Graph.repo.recent("last_accessed_at") }
        runCatching { favs = Graph.repo.recent("created_at", favouritesOnly = true) }
        if (name == null) name = runCatching { Graph.repo.displayName() }.getOrNull()
    }

    val wm = remember { WorkManager.getInstance(ctx) }
    val works by remember { runCatching { wm.getWorkInfosByTagFlow(UploadWorker.TAG) }.getOrDefault(flowOf(emptyList())) }.collectAsState(emptyList())

    val s = stats ?: Stats()
    LazyColumn(contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 120.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Column {
                Text(if (name.isNullOrBlank()) "Selamat datang" else "Hai, $name", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("CloudPix", style = MaterialTheme.typography.headlineLarge)
            }
        }
        warningText(s.percent)?.let { w ->
            item {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background((if (s.percent >= 95) Danger else Warn).copy(alpha = .15f)).padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.WarningAmber, null, tint = if (s.percent >= 95) Danger else Warn)
                    Text("  $w", color = if (s.percent >= 95) Danger else Warn, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        item {
            GlassCard(Modifier.fillMaxWidth(), padding = 20.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StorageRing(s.used, s.quota, 128.dp)
                    Column(Modifier.padding(start = 20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Jumlah storan", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                        Text(formatBytes(s.quota, 0), fontWeight = FontWeight.ExtraBold, fontSize = 26.sp)
                        KV("Digunakan", formatBytes(s.used, 2))
                        KV("Baki", formatBytes(s.available, 2))
                    }
                }
                Spacer(Modifier.height(16.dp))
                GradientButton("Upload foto & video", Modifier.fillMaxWidth(), icon = { Icon(Icons.Outlined.CloudUpload, null, tint = Color.White) }, onClick = pick)
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile(Icons.Outlined.Image, "${s.photoCount}", "Foto", Grad, Modifier.weight(1f)) { onNavigate("photos") }
                StatTile(Icons.Outlined.Movie, "${s.videoCount}", "Video", Brush.linearGradient(listOf(Color(0xFFA78BFA), Color(0xFF6D28D9))), Modifier.weight(1f)) { onNavigate("videos") }
                StatTile(Icons.Outlined.FavoriteBorder, "${s.favouriteCount}", "Kegemaran", Brush.linearGradient(listOf(Color(0xFFF9A8D4), Color(0xFFDB2777))), Modifier.weight(1f)) { onNavigate("favourites") }
            }
        }
        val visible = works.filter { it.state != WorkInfo.State.CANCELLED }.sortedBy { it.state.isFinished }
        if (visible.isNotEmpty()) item { UploadQueueCard(visible) { wm.pruneWork() } }
        item { Strip("Upload terkini", recent, onOpenViewer) }
        item { Strip("Baru dibuka", accessed, onOpenViewer) }
        item { Strip("Kegemaran", favs, onOpenViewer) }
    }
}

@Composable
private fun KV(k: String, v: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(k, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
        Text(v, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}

@Composable
private fun StatTile(icon: ImageVector, value: String, label: String, brush: Brush, modifier: Modifier, onClick: () -> Unit) {
    GlassCard(modifier, padding = 14.dp, onClick = onClick) {
        Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(brush), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(20.dp))
        }
        Text(value, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, modifier = Modifier.padding(top = 10.dp))
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
    }
}

@Composable
private fun UploadQueueCard(works: List<WorkInfo>, onClear: () -> Unit) {
    val running = works.count { !it.state.isFinished }
    GlassCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (running > 0) "Mengupload $running fail" else "Upload selesai", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            if (running == 0) TextButton(onClick = onClear) { Text("Kosongkan") }
        }
        works.take(8).forEach { w ->
            val name = w.progress.getString("name") ?: w.outputData.getString("name") ?: "Fail"
            val sent = w.progress.getLong("sent", 0)
            val total = w.progress.getLong("total", 0)
            Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                when (w.state) {
                    WorkInfo.State.SUCCEEDED -> Icon(Icons.Outlined.CheckCircle, null, tint = Ok, modifier = Modifier.size(20.dp))
                    WorkInfo.State.FAILED -> Icon(Icons.Outlined.ErrorOutline, null, tint = Danger, modifier = Modifier.size(20.dp))
                    else -> Icon(Icons.Outlined.CloudUpload, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                }
                Column(Modifier.padding(start = 10.dp).weight(1f)) {
                    Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 14.sp)
                    val status = when (w.state) {
                        WorkInfo.State.SUCCEEDED -> if (w.outputData.getBoolean("duplicate", false)) "Sudah wujud (duplikat)" else "Siap"
                        WorkInfo.State.FAILED -> w.outputData.getString("error") ?: "Gagal"
                        WorkInfo.State.ENQUEUED -> if (w.runAttemptCount > 0) "Menunggu sambungan, akan cuba semula…" else "Menunggu"
                        WorkInfo.State.BLOCKED -> "Menunggu"
                        else -> if (total > 0) "${formatBytes(sent)} / ${formatBytes(total)} · ${(sent * 100 / total)}%" else "Menyemak fail…"
                    }
                    Text(status, fontSize = 12.sp, color = if (w.state == WorkInfo.State.FAILED) Danger else MaterialTheme.colorScheme.onSurfaceVariant)
                    if (w.state == WorkInfo.State.RUNNING && total > 0) {
                        LinearProgressIndicator(progress = { (sent.toFloat() / total).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun Strip(title: String, items: List<Media>, onOpenViewer: () -> Unit) {
    if (items.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(items, key = { it.id }) { m ->
                MediaThumb(
                    m,
                    Modifier.size(112.dp).clip(RoundedCornerShape(16.dp)).clickable {
                        ViewerHolder.items = items; ViewerHolder.index = items.indexOf(m); onOpenViewer()
                    },
                )
            }
        }
    }
}

@Suppress("unused")
@Composable
private fun Placeholder() = Box(Modifier.fillMaxSize())
