package com.mohdrozaimi.cloudpix.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.mohdrozaimi.cloudpix.Graph
import com.mohdrozaimi.cloudpix.work.UpdateInfo
import com.mohdrozaimi.cloudpix.work.Updates

/**
 * Pop-up kemas kini.
 * auto = true  → disemak sekali bila app dibuka; senyap jika tiada / sudah ditolak "Nanti".
 * auto = false → dipanggil dari butang "Semak kemas kini"; sentiasa beri jawapan.
 */
@Composable
fun UpdatePrompt(auto: Boolean, onDone: () -> Unit = {}) {
    val ctx = LocalContext.current
    var info by remember { mutableStateOf<UpdateInfo?>(null) }
    var checking by remember { mutableStateOf(!auto) }
    var upToDate by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val r = runCatching { Updates.check() }
        val i = r.getOrNull()
        checking = false
        when {
            i != null && (!auto || Graph.prefs.skippedVersion != i.version) -> info = i
            !auto && r.isFailure -> { toast(ctx, "Tidak dapat semak kemas kini. Periksa internet."); onDone() }
            !auto -> upToDate = true
            else -> onDone()
        }
    }

    if (checking) {
        AlertDialog(onDismissRequest = {}, title = { Text("Menyemak kemas kini…") }, text = { Text("Sila tunggu sebentar.") }, confirmButton = {})
    }
    if (upToDate) {
        AlertDialog(
            onDismissRequest = { upToDate = false; onDone() },
            icon = { Icon(Icons.Outlined.SystemUpdate, null) },
            title = { Text("Sudah versi terkini") },
            text = { Text("Anda menggunakan CloudPix v${Updates.currentVersion}.") },
            confirmButton = { TextButton(onClick = { upToDate = false; onDone() }) { Text("OK") } },
        )
    }
    info?.let { i ->
        AlertDialog(
            onDismissRequest = { info = null; onDone() },
            icon = { Icon(Icons.Outlined.SystemUpdate, null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Kemas kini tersedia: v${i.version}") },
            text = {
                Column(Modifier.heightIn(max = 260.dp).verticalScroll(rememberScrollState())) {
                    Text("Versi anda: v${Updates.currentVersion}")
                    Text(
                        "Tekan Kemas kini untuk muat turun. Selepas siap, buka fail APK dan tekan Install — data & log masuk anda kekal.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (i.notes.isNotBlank()) Text(i.notes, style = MaterialTheme.typography.labelSmall)
                }
            },
            confirmButton = {
                TextButton(onClick = { Updates.open(ctx, i); info = null; onDone() }) { Text("Kemas kini") }
            },
            dismissButton = {
                TextButton(onClick = { Graph.prefs.skippedVersion = i.version; info = null; onDone() }) { Text("Nanti") }
            },
        )
    }
}

/** Android 13+: minta izin notifikasi sekali (untuk notifikasi upload, backup & kemas kini). */
@Composable
fun NotificationPermission() {
    if (Build.VERSION.SDK_INT < 33) return
    val ctx = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
