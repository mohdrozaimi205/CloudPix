package com.mohdrozaimi.cloudpix.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.mohdrozaimi.cloudpix.Graph
import com.mohdrozaimi.cloudpix.data.BackupRun
import com.mohdrozaimi.cloudpix.work.BackupWorker
import com.mohdrozaimi.cloudpix.work.BucketInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val mediaPerms: Array<String> = when {
    Build.VERSION.SDK_INT >= 33 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.POST_NOTIFICATIONS)
    else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
}

@Composable
fun BackupScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val p = Graph.prefs
    val refresh by Graph.refresh.collectAsState()
    var enabled by remember { mutableStateOf(p.backupEnabled) }
    var paused by remember { mutableStateOf(p.backupPaused) }
    var wifiOnly by remember { mutableStateOf(p.wifiOnly) }
    var photos by remember { mutableStateOf(p.backupPhotos) }
    var videos by remember { mutableStateOf(p.backupVideos) }
    var chosen by remember { mutableStateOf(p.backupBuckets) }
    var buckets by remember { mutableStateOf<List<BucketInfo>>(emptyList()) }
    var history by remember { mutableStateOf<List<BackupRun>>(emptyList()) }
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(ctx, mediaPerms[0]) == PackageManager.PERMISSION_GRANTED)
    }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        granted = r[mediaPerms[0]] == true || ContextCompat.checkSelfPermission(ctx, mediaPerms[0]) == PackageManager.PERMISSION_GRANTED
    }
    val partialOnly = Build.VERSION.SDK_INT >= 34 && !granted &&
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) == PackageManager.PERMISSION_GRANTED

    LaunchedEffect(granted) { if (granted) buckets = withContext(Dispatchers.IO) { BackupWorker.buckets(ctx) } }
    LaunchedEffect(refresh) { history = runCatching { Graph.repo.backupHistory() }.getOrDefault(history) }

    val work by remember { WorkManager.getInstance(ctx).getWorkInfosForUniqueWorkFlow("cloudpix-backup-now") }.collectAsState(emptyList())
    val running = work.firstOrNull { it.state == WorkInfo.State.RUNNING }

    fun save() {
        p.backupEnabled = enabled; p.backupPaused = paused; p.wifiOnly = wifiOnly
        p.backupPhotos = photos; p.backupVideos = videos; p.backupBuckets = chosen
        BackupWorker.schedule(ctx)
        if (paused) BackupWorker.stop(ctx)
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kembali") }
            Text("Auto backup", style = MaterialTheme.typography.headlineSmall)
        }
        LazyColumn(contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 120.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item {
                GlassCard(Modifier.fillMaxWidth()) {
                    ToggleRow("Hidupkan auto backup", "Foto & video baru diupload secara automatik", enabled) {
                        if (it && !granted) permLauncher.launch(mediaPerms)
                        enabled = it; save()
                    }
                    if (enabled) {
                        HorizontalDivider(Modifier.padding(vertical = 8.dp))
                        ToggleRow("Wi-Fi sahaja", "Matikan untuk benarkan data mudah alih", wifiOnly) { wifiOnly = it; save() }
                        ToggleRow("Backup foto", null, photos) { photos = it; save() }
                        ToggleRow("Backup video", null, videos) { videos = it; save() }
                        ToggleRow("Jeda backup", "Hentikan sementara tanpa ubah tetapan", paused) { paused = it; save() }
                    }
                }
            }
            if (enabled && !granted) item {
                GlassCard(Modifier.fillMaxWidth()) {
                    Text(
                        if (partialOnly) "Anda hanya benarkan akses kepada sebahagian foto. Auto backup perlukan akses penuh untuk mengesan fail baru."
                        else "CloudPix perlukan kebenaran membaca foto & video untuk backup.",
                    )
                    OutlinedButton(onClick = { permLauncher.launch(mediaPerms) }, modifier = Modifier.padding(top = 10.dp)) { Text("Beri kebenaran") }
                }
            }
            if (enabled && granted) item {
                GlassCard(Modifier.fillMaxWidth()) {
                    Text("Folder untuk backup", fontWeight = FontWeight.Bold)
                    Text(
                        "Kamera, Screenshots dan WhatsApp (Images/Video) dipaparkan jika ada. Media WhatsApp hanya boleh dibaca jika Android membenarkan.",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 6.dp),
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        buckets.forEach { b ->
                            FilterChip(b.id in chosen, {
                                chosen = if (b.id in chosen) chosen - b.id else chosen + b.id
                                save()
                            }, label = { Text("${b.name} (${b.count})") })
                        }
                    }
                    if (buckets.isEmpty()) Text("Tiada folder media dijumpai.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (enabled && granted) item {
                GlassCard(Modifier.fillMaxWidth()) {
                    if (running != null) {
                        val done = running.progress.getInt("done", 0)
                        val total = running.progress.getInt("total", 0)
                        Text("Sedang backup ${if (total > 0) "$done/$total" else "…"}", fontWeight = FontWeight.Bold)
                        Text(running.progress.getString("name") ?: "", style = MaterialTheme.typography.labelSmall)
                        LinearProgressIndicator(progress = { if (total > 0) done.toFloat() / total else 0f }, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
                    } else {
                        val last = p.lastBackupAt
                        Text(if (last > 0) "Backup terakhir: ${java.text.DateFormat.getDateTimeInstance().format(java.util.Date(last))}" else "Belum pernah backup")
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 8.dp)) {
                        OutlinedButton(onClick = {
                            if (chosen.isEmpty()) toast(ctx, "Pilih sekurang-kurangnya satu folder") else { paused = false; save(); BackupWorker.runNow(ctx); toast(ctx, "Backup dimulakan") }
                        }) { Text("Backup sekarang") }
                        if (running != null) OutlinedButton(onClick = { paused = true; save() }) { Text("Jeda") }
                    }
                }
            }
            item { Text("Sejarah backup", style = MaterialTheme.typography.titleMedium) }
            if (history.isEmpty()) item { Text("Tiada sejarah lagi.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(history, key = { it.id }) { h ->
                GlassCard(Modifier.fillMaxWidth(), padding = 14.dp) {
                    Row {
                        Text(formatDateTime(h.startedAt), fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Text(
                            when (h.status) { "completed" -> "Selesai"; "paused" -> "Dijeda"; "failed" -> "Gagal"; else -> "Berjalan" },
                            color = when (h.status) { "completed" -> Ok; "failed" -> Danger; else -> Warn },
                        )
                    }
                    Text(
                        "${h.filesUploaded} diupload (${formatBytes(h.bytesUploaded)}) · ${h.filesSkipped} sudah ada · ${h.filesFailed} gagal · ${h.deviceName}",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    h.message?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = Danger) }
                }
            }
        }
    }
}

@Composable
private fun ToggleRow(title: String, sub: String?, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            sub?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Switch(value, onChange)
    }
}
