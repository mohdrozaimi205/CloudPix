package com.mohdrozaimi.cloudpix.ui

import android.app.Activity
import android.content.Intent
import android.content.pm.ActivityInfo
import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.mohdrozaimi.cloudpix.Graph
import com.mohdrozaimi.cloudpix.data.Media
import com.mohdrozaimi.cloudpix.data.UrlCache
import kotlinx.coroutines.launch

@Composable
fun ViewerScreen(onClose: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val items = remember { mutableStateListOf<Media>().apply { addAll(ViewerHolder.items) } }
    if (items.isEmpty()) { LaunchedEffect(Unit) { onClose() }; return }
    val pager = rememberPagerState(initialPage = ViewerHolder.index.coerceIn(0, items.size - 1)) { items.size }
    var menu by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var moving by remember { mutableStateOf(false) }
    var purging by remember { mutableStateOf(false) }
    var shareUrl by remember { mutableStateOf<String?>(null) }
    var chrome by remember { mutableStateOf(true) }
    val m = items.getOrNull(pager.currentPage) ?: return
    val inTrash = m.deletedAt != null

    fun replace(n: Media) { val i = items.indexOfFirst { it.id == n.id }; if (i >= 0) items[i] = n }
    fun removeCurrent() { if (items.size <= 1) onClose() else items.removeAt(pager.currentPage) }
    fun act(block: suspend () -> Unit, ok: String? = null, after: () -> Unit = {}) = scope.launch {
        runCatching { block() }.onSuccess { ok?.let { toast(ctx, it) }; after(); Graph.bump() }.onFailure { toast(ctx, it.message ?: "Ralat") }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(pager, Modifier.fillMaxSize(), key = { items[it].id }, beyondViewportPageCount = 1) { page ->
            val item = items[page]
            if (item.isVideo) VideoPage(item, active = page == pager.currentPage)
            else ImagePage(item) { chrome = !chrome }
        }

        if (chrome) Row(
            Modifier.fillMaxWidth().background(Color(0x88000000)).statusBarsPadding().padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kembali", tint = Color.White) }
            Column(Modifier.weight(1f)) {
                Text(m.filename, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(formatDateTime(m.takenAt ?: m.createdAt), color = Color.White.copy(alpha = .65f), style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
            }
            if (!inTrash) {
                IconButton(onClick = { act({ Graph.repo.setFavourite(listOf(m.id), !m.isFavourite) }, after = { replace(m.copy(isFavourite = !m.isFavourite)) }) }) {
                    Icon(if (m.isFavourite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder, "Kegemaran", tint = if (m.isFavourite) Rose else Color.White)
                }
                IconButton(onClick = { act({ shareUrl = Graph.api.share(m.id, 24) ?: throw IllegalStateException("PUBLIC_WEB_URL belum diset di server") }) }) {
                    Icon(Icons.Outlined.Share, "Kongsi", tint = Color.White)
                }
                IconButton(onClick = { act({ Graph.api.delete(listOf(m.id), "trash") }, "Dipindah ke Baru Dipadam") { removeCurrent() } }) {
                    Icon(Icons.Outlined.DeleteOutline, "Padam", tint = Color.White)
                }
            } else {
                IconButton(onClick = { act({ Graph.api.delete(listOf(m.id), "restore") }, "Dipulihkan") { removeCurrent() } }) {
                    Icon(Icons.Outlined.Restore, "Pulihkan", tint = Color.White)
                }
                IconButton(onClick = { purging = true }) { Icon(Icons.Outlined.DeleteForever, "Padam kekal", tint = Color.White) }
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, "Lagi", tint = Color.White) }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem(text = { Text("Muat turun asal") }, leadingIcon = { Icon(Icons.Outlined.Download, null) }, onClick = {
                        menu = false; act({ downloadToDevice(ctx, listOf(m)) }, "Memuat turun ke Downloads/CloudPix")
                    })
                    if (!inTrash) {
                        DropdownMenuItem(text = { Text("Tukar nama") }, leadingIcon = { Icon(Icons.Outlined.Edit, null) }, onClick = { menu = false; renaming = true })
                        DropdownMenuItem(text = { Text("Pindah ke album") }, leadingIcon = { Icon(Icons.Outlined.DriveFileMove, null) }, onClick = { menu = false; moving = true })
                    }
                    DropdownMenuItem(text = { Text("Maklumat") }, leadingIcon = { Icon(Icons.Outlined.Info, null) }, onClick = { menu = false; info = true })
                }
            }
        }
    }

    if (info) ModalBottomSheet(onDismissRequest = { info = false }) {
        Column(Modifier.padding(horizontal = 24.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Maklumat", style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
            InfoRow("Nama", m.filename)
            InfoRow("Saiz", formatBytes(m.sizeBytes, 2))
            if (m.width != null && m.height != null) InfoRow("Resolusi", "${m.width} × ${m.height}")
            if ((m.durationMs ?: 0) > 0) InfoRow("Durasi", formatDuration(m.durationMs))
            InfoRow("Jenis", m.mimeType)
            InfoRow("Diupload", formatDateTime(m.createdAt))
            m.takenAt?.let { InfoRow("Tarikh fail", formatDateTime(it)) }
            InfoRow("Sumber", m.source)
            Box(Modifier.padding(bottom = 24.dp))
        }
    }
    if (renaming) TextPromptDialog("Tukar nama", "Nama fail", m.filename, "Simpan", onDismiss = { renaming = false }) { name ->
        val ext = m.filename.substringAfterLast('.', "")
        val final = if (name.contains('.') || ext.isEmpty()) name else "$name.$ext"
        Graph.repo.rename(m.id, final)
        replace(m.copy(filename = final))
    }
    if (moving) AlbumPickerDialog(onDismiss = { moving = false }) { albumId ->
        runCatching { Graph.repo.moveToAlbum(listOf(m.id), albumId) }.onSuccess { toast(ctx, "Album dikemas kini"); replace(m.copy(albumId = albumId)) }
    }
    if (purging) ConfirmDialog("Padam kekal?", "Fail ini akan dipadam selama-lamanya.", "Padam kekal", { purging = false }) {
        act({ Graph.api.delete(listOf(m.id), "purge") }, "Dipadam kekal") { removeCurrent() }
    }
    shareUrl?.let { url ->
        AlertDialog(
            onDismissRequest = { shareUrl = null },
            title = { Text("Pautan kongsi (24 jam)") },
            text = { Text(url) },
            confirmButton = {
                TextButton(onClick = {
                    ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, url), "Kongsi pautan"))
                    shareUrl = null
                }) { Text("Kongsi") }
            },
            dismissButton = { TextButton(onClick = { shareUrl = null }) { Text("Tutup") } },
        )
    }
}

@Composable
private fun InfoRow(k: String, v: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(k, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(0.35f))
        Text(v, modifier = Modifier.weight(0.65f))
    }
}

@Composable
private fun ImagePage(m: Media, onTap: () -> Unit) {
    val ctx = LocalContext.current
    val original by produceState<String?>(null, m.id) { value = runCatching { UrlCache.original(m.id) }.getOrNull() }
    val thumb by produceState<String?>(null, m.id) { value = if (m.thumbKey != null) runCatching { UrlCache.thumb(m.id) }.getOrNull() else null }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(m.id) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = { if (scale > 1f) { scale = 1f; offset = Offset.Zero } else scale = 2.5f },
                )
            }
            .pointerInput(m.id) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, 6f)
                    offset = if (scale > 1f) offset + pan else Offset.Zero
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        val mod = Modifier.fillMaxSize().graphicsLayer { scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y }
        if (thumb != null) AsyncImage(
            model = ImageRequest.Builder(ctx).data(thumb).memoryCacheKey("t:${m.id}").diskCacheKey("t:${m.id}").build(),
            contentDescription = null, contentScale = ContentScale.Fit, modifier = mod,
        )
        if (original != null) AsyncImage(
            model = ImageRequest.Builder(ctx).data(original).memoryCacheKey("o:${m.id}").diskCacheKey("o:${m.id}").build(),
            contentDescription = m.filename, contentScale = ContentScale.Fit, modifier = mod,
        ) else if (thumb == null) CircularProgressIndicator(color = Color.White)
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun VideoPage(m: Media, active: Boolean) {
    val ctx = LocalContext.current
    val url by produceState<String?>(null, m.id) { value = runCatching { UrlCache.original(m.id) }.getOrNull() }
    if (!active || url == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            MediaThumb(m, Modifier.fillMaxWidth())
            if (active) CircularProgressIndicator(color = Color.White)
        }
        return
    }
    val player = remember(m.id) {
        ExoPlayer.Builder(ctx).build().apply {
            setMediaItem(MediaItem.fromUri(url!!))
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(m.id) {
        onDispose {
            player.release()
            (ctx as? Activity)?.let { setFullscreen(it, false) }
        }
    }
    AndroidView(
        factory = {
            PlayerView(it).apply {
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                this.player = player
                useController = true
                setFullscreenButtonClickListener { full -> (ctx as? Activity)?.let { a -> setFullscreen(a, full) } }
            }
        },
        modifier = Modifier.fillMaxSize(),
    )
}

private fun setFullscreen(a: Activity, full: Boolean) {
    val c = WindowInsetsControllerCompat(a.window, a.window.decorView)
    if (full) {
        a.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        c.hide(WindowInsetsCompat.Type.systemBars())
    } else {
        a.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        c.show(WindowInsetsCompat.Type.systemBars())
    }
    WindowCompat.setDecorFitsSystemWindows(a.window, false)
}
