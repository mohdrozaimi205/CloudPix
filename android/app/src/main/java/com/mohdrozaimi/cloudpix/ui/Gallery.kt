package com.mohdrozaimi.cloudpix.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.ImageNotSupported
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Sort
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mohdrozaimi.cloudpix.Graph
import com.mohdrozaimi.cloudpix.data.Media
import com.mohdrozaimi.cloudpix.data.MediaFilter
import com.mohdrozaimi.cloudpix.data.PAGE_SIZE
import com.mohdrozaimi.cloudpix.data.Sort
import com.mohdrozaimi.cloudpix.data.View
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Simpanan item untuk viewer (elak hantar senarai besar melalui navigasi). */
object ViewerHolder {
    var items: List<Media> = emptyList()
    var index: Int = 0
}

@Composable
fun MediaGridScreen(
    base: MediaFilter,
    title: String,
    emptyText: String,
    onOpen: () -> Unit,
    onBack: (() -> Unit)? = null,
    showKindFilter: Boolean = true,
    headerActions: @Composable () -> Unit = {},
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val refresh by Graph.refresh.collectAsState()
    var search by remember { mutableStateOf("") }
    var debounced by remember { mutableStateOf("") }
    var sort by remember { mutableStateOf(base.sort) }
    var kind by remember { mutableStateOf<String?>(null) }
    var range by remember { mutableStateOf<Pair<Long?, Long?>>(null to null) }
    var showRange by remember { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }

    val items = remember { mutableStateListOf<Media>() }
    var pages by remember { mutableIntStateOf(0) }
    var end by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val selected = remember { mutableStateListOf<String>() }
    var moving by remember { mutableStateOf(false) }
    var purging by remember { mutableStateOf(false) }

    LaunchedEffect(search) { delay(300); debounced = search }
    val filter = base.copy(search = debounced, sort = sort, kind = kind, fromMillis = range.first, toMillis = range.second)
    val currentFilter by rememberUpdatedState(filter)

    suspend fun load(reset: Boolean) {
        if (!reset && (end || loading)) return
        loading = true
        error = null
        try {
            if (reset) {
                // muat semula semua halaman yang sudah dibuka (kekalkan kedudukan skrol)
                val keep = maxOf(1, pages)
                val fresh = mutableListOf<Media>()
                var reachedEnd = false
                for (p in 0 until keep) {
                    val page = Graph.repo.media(currentFilter, p)
                    fresh += page
                    if (page.size < PAGE_SIZE) { reachedEnd = true; break }
                }
                items.clear(); items.addAll(fresh)
                pages = maxOf(1, (fresh.size + PAGE_SIZE - 1) / PAGE_SIZE)
                end = reachedEnd
            } else {
                val page = Graph.repo.media(currentFilter, pages)
                items.addAll(page)
                pages++
                end = page.size < PAGE_SIZE
            }
        } catch (e: Exception) {
            error = friendlyError(e)
        } finally {
            loading = false
        }
    }

    LaunchedEffect(filter) { pages = 0; end = false; items.clear(); selected.clear(); load(true) }
    LaunchedEffect(refresh) { if (refresh > 0) load(true) }

    val grid = rememberLazyGridState()
    LaunchedEffect(grid) {
        snapshotFlow { grid.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }.collect { last ->
            if (last > grid.layoutInfo.totalItemsCount - 24) load(false)
        }
    }

    val isTrash = base.view == View.TRASH
    val grouped = sort == Sort.NEWEST || sort == Sort.OLDEST
    val groups = remember(items.toList(), grouped) {
        if (!grouped) listOf("" to items.toList())
        else items.groupBy { groupLabel(if (isTrash) it.deletedAt ?: it.createdAt else it.createdAt) }.toList()
    }
    val sel = items.filter { it.id in selected }

    fun act(block: suspend () -> Unit, ok: String? = null) = scope.launch {
        runCatching { block() }.onSuccess { ok?.let { toast(ctx, it) }; selected.clear(); Graph.bump() }
            .onFailure { toast(ctx, friendlyError(it)) }
    }

    Column(Modifier.fillMaxSize()) {
        // ---------- kepala ----------
        if (sel.isEmpty()) {
            Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kembali") }
                Text(title, style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(start = if (onBack == null) 12.dp else 0.dp).weight(1f))
                headerActions()
            }
        } else {
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { selected.clear() }) { Icon(Icons.Outlined.Close, "Batal") }
                Text("${sel.size} dipilih", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                TextButton(onClick = { selected.clear(); selected.addAll(items.map { it.id }) }) { Text("Semua") }
                if (!isTrash) {
                    IconButton(onClick = { val v = !sel.all { it.isFavourite }; act({ Graph.repo.setFavourite(sel.map { it.id }, v) }) }) {
                        Icon(Icons.Outlined.FavoriteBorder, "Kegemaran")
                    }
                    IconButton(onClick = { act({ downloadToDevice(ctx, sel) }, "Memuat turun ${sel.size} fail ke Downloads/CloudPix") }) {
                        Icon(Icons.Outlined.Download, "Muat turun")
                    }
                    IconButton(onClick = { moving = true }) { Icon(Icons.Outlined.DriveFileMove, "Pindah") }
                    IconButton(onClick = { act({ Graph.api.delete(sel.map { it.id }, "trash") }, "Dipindah ke Baru Dipadam") }) {
                        Icon(Icons.Outlined.DeleteOutline, "Padam")
                    }
                } else {
                    IconButton(onClick = { act({ Graph.api.delete(sel.map { it.id }, "restore") }, "Dipulihkan") }) { Icon(Icons.Outlined.Restore, "Pulihkan") }
                    IconButton(onClick = { purging = true }) { Icon(Icons.Outlined.DeleteForever, "Padam kekal", tint = Danger) }
                }
            }
        }

        // ---------- carian & tapisan ----------
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                placeholder = { Text("Cari nama fail…") },
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                trailingIcon = { if (search.isNotEmpty()) IconButton(onClick = { search = "" }) { Icon(Icons.Outlined.Close, "Kosongkan") } },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = LocalGlass.current.glass, focusedContainerColor = LocalGlass.current.glass),
                modifier = Modifier.weight(1f),
            )
            Box {
                IconButton(onClick = { sortMenu = true }) { Icon(Icons.Outlined.Sort, "Susun") }
                DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                    Sort.entries.forEach { s ->
                        DropdownMenuItem(text = { Text(s.label, fontWeight = if (s == sort) FontWeight.Bold else null) }, onClick = { sort = s; sortMenu = false })
                    }
                }
            }
        }
        Row(Modifier.padding(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (showKindFilter) {
                FilterChip(kind == "image", { kind = if (kind == "image") null else "image" }, label = { Text("Foto") })
                FilterChip(kind == "video", { kind = if (kind == "video") null else "video" }, label = { Text("Video") })
            }
            val fmt = DateTimeFormatter.ofPattern("d/M/yy")
            val label = if (range.first != null) {
                val a = Instant.ofEpochMilli(range.first!!).atZone(ZoneId.of("UTC")).format(fmt)
                val b = range.second?.let { Instant.ofEpochMilli(it).atZone(ZoneId.of("UTC")).format(fmt) } ?: "…"
                "$a – $b"
            } else "Tarikh"
            FilterChip(range.first != null, { if (range.first != null) range = null to null else showRange = true }, label = { Text(label) },
                leadingIcon = { Icon(Icons.Outlined.DateRange, null, Modifier.size(18.dp)) })
        }

        // ---------- grid ----------
        Box(Modifier.weight(1f)) {
            when {
                items.isEmpty() && loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                items.isEmpty() && error != null -> Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(error ?: "", color = Danger)
                    TextButton(onClick = { scope.launch { load(true) } }) { Text("Cuba lagi") }
                }
                items.isEmpty() -> EmptyState(Icons.Outlined.ImageNotSupported, if (debounced.isNotBlank() || kind != null || range.first != null) "Tiada hasil" else "Kosong", emptyText)
                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(108.dp),
                    state = grid,
                    contentPadding = PaddingValues(start = 10.dp, end = 10.dp, top = 8.dp, bottom = 120.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    groups.forEach { (label, list) ->
                        if (label.isNotEmpty()) item(span = { GridItemSpan(maxLineSpan) }, key = "h-$label") {
                            Text(label, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp, top = 14.dp, bottom = 6.dp))
                        }
                        items(list, key = { it.id }) { m ->
                            val isSel = m.id in selected
                            Box(
                                Modifier
                                    .aspectRatio(1f)
                                    .scale(if (isSel) 0.92f else 1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .then(if (isSel) Modifier.border(3.dp, Sky, RoundedCornerShape(10.dp)) else Modifier)
                                    .combinedClickable(
                                        onClick = {
                                            if (selected.isNotEmpty()) { if (isSel) selected.remove(m.id) else selected.add(m.id) }
                                            else { ViewerHolder.items = items.toList(); ViewerHolder.index = items.indexOf(m); onOpen() }
                                        },
                                        onLongClick = { if (isSel) selected.remove(m.id) else selected.add(m.id) },
                                    ),
                            ) {
                                MediaThumb(m, Modifier.fillMaxSize())
                                if (m.isVideo) Pill("▶ ${formatDuration(m.durationMs)}", Modifier.align(Alignment.BottomEnd).padding(5.dp))
                                if (sort == Sort.SIZE) Pill(formatBytes(m.sizeBytes), Modifier.align(Alignment.BottomStart).padding(5.dp))
                                else if (m.isFavourite) Icon(Icons.Filled.Favorite, null, tint = Rose, modifier = Modifier.align(Alignment.BottomStart).padding(6.dp).size(16.dp))
                                if (selected.isNotEmpty()) Icon(
                                    Icons.Filled.CheckCircle, null,
                                    tint = if (isSel) Sky else Color.White.copy(alpha = .6f),
                                    modifier = Modifier.align(Alignment.TopStart).padding(5.dp).size(22.dp),
                                )
                            }
                        }
                    }
                    if (loading) item(span = { GridItemSpan(maxLineSpan) }) {
                        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(24.dp)) }
                    }
                }
            }
        }
    }

    if (moving) AlbumPickerDialog(onDismiss = { moving = false }) { albumId ->
        runCatching { Graph.repo.moveToAlbum(sel.map { it.id }, albumId) }
            .onSuccess { toast(ctx, if (albumId != null) "Dipindah ke album" else "Dibuang dari album"); selected.clear() }
            .onFailure { toast(ctx, friendlyError(it)) }
    }
    if (purging) ConfirmDialog(
        "Padam kekal?", "${sel.size} fail akan dipadam selama-lamanya dan ruang storan dibebaskan.", "Padam kekal",
        onDismiss = { purging = false },
    ) { act({ Graph.api.delete(sel.map { it.id }, "purge") }, "Dipadam kekal") }
    if (showRange) {
        val st = rememberDateRangePickerState()
        DatePickerDialog(
            onDismissRequest = { showRange = false },
            confirmButton = {
                TextButton(onClick = { range = st.selectedStartDateMillis to (st.selectedEndDateMillis ?: st.selectedStartDateMillis); showRange = false }) { Text("Guna") }
            },
            dismissButton = { TextButton(onClick = { showRange = false }) { Text("Batal") } },
        ) { DateRangePicker(st, Modifier.weight(1f)) }
    }
}
