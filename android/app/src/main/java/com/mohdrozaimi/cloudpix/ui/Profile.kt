package com.mohdrozaimi.cloudpix.ui

import android.content.ContentValues
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.biometric.BiometricManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mohdrozaimi.cloudpix.Graph
import com.mohdrozaimi.cloudpix.data.Stats
import com.mohdrozaimi.cloudpix.data.bool
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import java.io.File

@Composable
fun ProfileScreen(themeMode: Int, onTheme: (Int) -> Unit, onNavigate: (String) -> Unit, onServerReset: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val refresh by Graph.refresh.collectAsState()
    var stats by remember { mutableStateOf(Stats()) }
    var biometric by remember { mutableStateOf(Graph.prefs.biometricLock) }
    var exporting by remember { mutableStateOf<String?>(null) }
    val email = remember { runCatching { Graph.client.auth.currentUserOrNull()?.email }.getOrNull() ?: "" }
    LaunchedEffect(refresh) { runCatching { stats = Graph.repo.stats() } }

    LazyColumn(contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 120.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Text("Profil", style = MaterialTheme.typography.headlineLarge)
            Text(email, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            GlassCard(Modifier.fillMaxWidth(), onClick = { onNavigate("storage") }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Storage, null, tint = MaterialTheme.colorScheme.primary)
                    Text("  Storan", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text("%.1f%%".format(stats.percent), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                LinearProgressIndicator(progress = { (stats.percent / 100).toFloat().coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                    color = if (stats.percent >= 95) Danger else if (stats.percent >= 80) Warn else MaterialTheme.colorScheme.primary)
                Text("${formatBytes(stats.used, 2)} daripada ${formatBytes(stats.quota, 0)} · baki ${formatBytes(stats.available)}", style = MaterialTheme.typography.labelSmall)
            }
        }
        item {
            GlassCard(Modifier.fillMaxWidth(), padding = 6.dp) {
                NavRow(Icons.Outlined.Backup, "Auto backup", if (Graph.prefs.backupEnabled) (if (Graph.prefs.backupPaused) "Dijeda" else "Aktif") else "Mati") { onNavigate("backup") }
                HorizontalDivider()
                NavRow(Icons.Outlined.FavoriteBorder, "Kegemaran", "${stats.favouriteCount}") { onNavigate("favourites") }
                HorizontalDivider()
                NavRow(Icons.Outlined.DeleteOutline, "Baru Dipadam", "${stats.trashCount} · ${formatBytes(stats.trashBytes)}") { onNavigate("trash") }
            }
        }
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Fingerprint, null)
                    Column(Modifier.padding(start = 12.dp).weight(1f)) {
                        Text("Kunci biometrik", fontWeight = FontWeight.SemiBold)
                        Text("Minta cap jari / wajah setiap kali buka app", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(biometric, onCheckedChange = { on ->
                        val can = BiometricManager.from(ctx).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                        if (on && can != BiometricManager.BIOMETRIC_SUCCESS && BiometricManager.from(ctx).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) != BiometricManager.BIOMETRIC_SUCCESS) {
                            toast(ctx, "Tetapkan cap jari atau kunci skrin di Tetapan telefon dahulu")
                        } else { biometric = on; Graph.prefs.biometricLock = on }
                    })
                }
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Palette, null)
                    Text("  Tema", fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(8.dp))
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    listOf("Sistem", "Cerah", "Gelap").forEachIndexed { i, label ->
                        SegmentedButton(themeMode == i, { onTheme(i) }, SegmentedButtonDefaults.itemShape(i, 3)) { Text(label) }
                    }
                }
            }
        }
        item {
            GlassCard(Modifier.fillMaxWidth(), padding = 6.dp) {
                NavRow(Icons.Outlined.Download, "Eksport data akaun", exporting ?: "JSON ke Downloads") {
                    if (exporting != null) return@NavRow
                    scope.launch {
                        try {
                            val media = mutableListOf<kotlinx.serialization.json.JsonElement>()
                            var first: JsonObject? = null
                            var page = 0
                            while (true) {
                                exporting = "Halaman ${page + 1}…"
                                val r = Graph.api.exportPage(page)
                                if (first == null) first = r
                                (r["media"] as? JsonArray)?.let { media.addAll(it) }
                                if (!r.bool("hasMore")) break
                                page++
                            }
                            val out = JsonObject(first!!.toMutableMap().apply { put("media", JsonArray(media)); remove("page"); remove("hasMore") })
                            val name = "cloudpix-export-${System.currentTimeMillis()}.json"
                            withContext(Dispatchers.IO) { saveToDownloads(ctx, name, out.toString().toByteArray()) }
                            toast(ctx, "Disimpan: Downloads/$name")
                        } catch (e: Exception) {
                            toast(ctx, e.message ?: "Gagal eksport")
                        } finally {
                            exporting = null
                        }
                    }
                }
                HorizontalDivider()
                NavRow(Icons.Outlined.Dns, "Tukar server", Graph.prefs.serverUrl.removePrefix("https://")) {
                    scope.launch {
                        runCatching { Graph.client.auth.signOut() }
                        Graph.prefs.serverUrl = ""
                        Graph.prefs.anonKey = ""
                        Graph.resetServer()
                        onServerReset()
                    }
                }
                HorizontalDivider()
                NavRow(Icons.AutoMirrored.Outlined.Logout, "Log keluar", "") {
                    scope.launch { runCatching { Graph.client.auth.signOut() } }
                }
            }
        }
        item { Text("CloudPix · Android", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(4.dp)) }
    }
}

@Composable
fun NavRow(icon: ImageVector, title: String, value: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null)
        Text(title, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 12.dp).weight(1f))
        Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall, maxLines = 1)
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun saveToDownloads(ctx: android.content.Context, name: String, bytes: ByteArray) {
    if (Build.VERSION.SDK_INT >= 29) {
        val v = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, "application/json")
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        }
        val uri = ctx.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v) ?: error("Tidak dapat simpan fail")
        ctx.contentResolver.openOutputStream(uri)!!.use { it.write(bytes) }
    } else {
        @Suppress("DEPRECATION")
        val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        dir.mkdirs()
        File(dir, name).writeBytes(bytes)
    }
}

@Composable
fun StorageScreen(onBack: () -> Unit, onOpenViewer: () -> Unit) {
    val refresh by Graph.refresh.collectAsState()
    var s by remember { mutableStateOf(Stats()) }
    LaunchedEffect(refresh) { runCatching { s = Graph.repo.stats() } }
    MediaGridScreen(
        base = com.mohdrozaimi.cloudpix.data.MediaFilter(com.mohdrozaimi.cloudpix.data.View.ALL, sort = com.mohdrozaimi.cloudpix.data.Sort.SIZE),
        title = "Storan · ${"%.1f".format(s.percent)}%",
        emptyText = "Belum ada fail.",
        onOpen = onOpenViewer,
        onBack = onBack,
        headerActions = {
            Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(end = 8.dp)) {
                Text("Foto ${formatBytes(s.photoBytes)}", style = MaterialTheme.typography.labelSmall)
                Text("Video ${formatBytes(s.videoBytes)}", style = MaterialTheme.typography.labelSmall)
                Text("Sampah ${formatBytes(s.trashBytes)}", style = MaterialTheme.typography.labelSmall)
            }
        },
    )
}

@Suppress("unused")
private val unusedJson = buildJsonObject { }
