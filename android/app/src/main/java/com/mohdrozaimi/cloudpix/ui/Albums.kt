package com.mohdrozaimi.cloudpix.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mohdrozaimi.cloudpix.Graph
import com.mohdrozaimi.cloudpix.data.Album
import com.mohdrozaimi.cloudpix.data.Media
import com.mohdrozaimi.cloudpix.data.MediaFilter
import com.mohdrozaimi.cloudpix.data.View

@Composable
fun AlbumsScreen(onOpen: (String) -> Unit) {
    val refresh by Graph.refresh.collectAsState()
    var albums by remember { mutableStateOf<List<Album>?>(null) }
    var creating by remember { mutableStateOf(false) }
    LaunchedEffect(refresh) { albums = runCatching { Graph.repo.albums() }.getOrDefault(albums ?: emptyList()) }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Album", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.weight(1f))
            IconButton(onClick = { creating = true }) { Icon(Icons.Outlined.Add, "Album baru") }
        }
        val list = albums
        if (list != null && list.isEmpty()) EmptyState(Icons.Outlined.Folder, "Belum ada album", "Tekan + untuk cipta album pertama.")
        LazyVerticalGrid(
            columns = GridCells.Adaptive(150.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 120.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            items(list ?: emptyList(), key = { it.id }) { a ->
                val cover by produceState<Media?>(null, a.id, a.coverMediaId) { value = runCatching { Graph.repo.albumCover(a) }.getOrNull() }
                Column(Modifier.clickable { onOpen(a.id) }) {
                    Box(
                        Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(22.dp)).background(LocalGlass.current.glass),
                        contentAlignment = Alignment.Center,
                    ) {
                        val c = cover
                        if (c != null) MediaThumb(c, Modifier.fillMaxSize())
                        else Icon(Icons.Outlined.Folder, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp))
                    }
                    Text(a.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
                    Text("${a.count} item", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
    if (creating) TextPromptDialog("Album baru", "Nama album", confirm = "Cipta", onDismiss = { creating = false }) {
        Graph.repo.createAlbum(it); Graph.bump()
    }
}

@Composable
fun AlbumDetailScreen(albumId: String, onBack: () -> Unit, onOpenViewer: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val refresh by Graph.refresh.collectAsState()
    val album by produceState<Album?>(null, albumId, refresh) { value = runCatching { Graph.repo.albums().find { it.id == albumId } }.getOrNull() }
    var menu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    val pick = rememberUploadPicker(albumId)

    MediaGridScreen(
        base = MediaFilter(view = View.ALBUM, albumId = albumId),
        title = album?.name ?: "Album",
        emptyText = "Album ini kosong. Upload atau pindahkan foto ke sini.",
        onOpen = onOpenViewer,
        onBack = onBack,
        headerActions = {
            IconButton(onClick = pick) { Icon(Icons.Outlined.CloudUpload, "Upload ke album") }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, "Menu") }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem(text = { Text("Tukar nama") }, onClick = { menu = false; renaming = true })
                    DropdownMenuItem(text = { Text("Padam album", color = Danger) }, onClick = { menu = false; deleting = true })
                }
            }
        },
    )
    if (renaming) TextPromptDialog("Tukar nama album", "Nama album", album?.name ?: "", "Simpan", onDismiss = { renaming = false }) {
        Graph.repo.renameAlbum(albumId, it); Graph.bump()
    }
    if (deleting) ConfirmDialog(
        "Padam album?", "Foto & video di dalamnya TIDAK dipadam — ia kekal dalam Foto/Video.", "Padam album",
        onDismiss = { deleting = false },
    ) {
        Graph.scope.launchCatching(ctx) { Graph.repo.deleteAlbum(albumId); Graph.bump() }
        onBack()
    }
}
