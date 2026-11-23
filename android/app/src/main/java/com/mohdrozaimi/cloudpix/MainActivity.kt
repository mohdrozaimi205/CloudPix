package com.mohdrozaimi.cloudpix

import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.mohdrozaimi.cloudpix.data.MediaFilter
import com.mohdrozaimi.cloudpix.data.View
import com.mohdrozaimi.cloudpix.ui.AlbumDetailScreen
import com.mohdrozaimi.cloudpix.ui.AlbumsScreen
import com.mohdrozaimi.cloudpix.ui.AuroraBackground
import com.mohdrozaimi.cloudpix.ui.AuthScreen
import com.mohdrozaimi.cloudpix.ui.BackupScreen
import com.mohdrozaimi.cloudpix.ui.CloudPixTheme
import com.mohdrozaimi.cloudpix.ui.Grad
import com.mohdrozaimi.cloudpix.ui.HomeScreen
import com.mohdrozaimi.cloudpix.ui.LocalGlass
import com.mohdrozaimi.cloudpix.ui.MediaGridScreen
import com.mohdrozaimi.cloudpix.ui.ProfileScreen
import com.mohdrozaimi.cloudpix.ui.SetupScreen
import com.mohdrozaimi.cloudpix.ui.StorageScreen
import com.mohdrozaimi.cloudpix.ui.ViewerScreen
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {
    private val locked = MutableStateFlow(false)
    private var backgroundAt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null && !freshStartHandled) {
            freshStartHandled = true
            locked.value = Graph.prefs.biometricLock
            // "Ingat saya" dimatikan → log keluar setiap kali app dibuka semula
            if (!Graph.prefs.rememberMe && Graph.isConfigured) {
                Graph.scope.launchSignOut()
            }
        }
        lifecycle.addObserver(LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_STOP -> backgroundAt = System.currentTimeMillis()
                Lifecycle.Event.ON_START -> if (Graph.prefs.biometricLock && backgroundAt > 0 && System.currentTimeMillis() - backgroundAt > 30_000) locked.value = true
                Lifecycle.Event.ON_RESUME -> if (locked.value) promptUnlock()
                else -> Unit
            }
        })

        setContent {
            var theme by remember { mutableIntStateOf(Graph.prefs.theme) }
            var configured by remember { mutableStateOf(Graph.isConfigured) }
            val isLocked by locked.collectAsState()
            CloudPixTheme(theme) {
                AuroraBackground {
                    when {
                        isLocked -> LockScreen { promptUnlock() }
                        !configured -> SetupScreen { configured = true }
                        else -> Root(
                            theme = theme,
                            onTheme = { theme = it; Graph.prefs.theme = it },
                            onServerReset = { configured = false },
                        )
                    }
                }
            }
        }
    }

    private fun promptUnlock() {
        val executor = ContextCompat.getMainExecutor(this)
        val prompt = BiometricPrompt(this, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                locked.value = false
            }
        })
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Buka CloudPix")
            .setSubtitle("Sahkan identiti anda")
            .apply {
                if (Build.VERSION.SDK_INT >= 30) setAllowedAuthenticators(BIOMETRIC_WEAK or DEVICE_CREDENTIAL)
                else setAllowedAuthenticators(BIOMETRIC_WEAK).setNegativeButtonText("Batal")
            }
            .build()
        runCatching { prompt.authenticate(info) }
    }

    companion object {
        private var freshStartHandled = false
    }
}

private fun kotlinx.coroutines.CoroutineScope.launchSignOut() {
    launch {
        runCatching {
            Graph.client.auth.awaitReady()
            Graph.client.auth.signOut()
        }
    }
}

private suspend fun io.github.jan.supabase.auth.Auth.awaitReady() {
    kotlinx.coroutines.withTimeoutOrNull(10_000) {
        sessionStatus.first { it !is SessionStatus.Initializing }
    }
}

@Composable
private fun LockScreen(onUnlock: () -> Unit) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(Modifier.size(80.dp).clip(RoundedCornerShape(26.dp)).background(Grad), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.Lock, null, tint = Color.White, modifier = Modifier.size(38.dp))
        }
        Text("CloudPix dikunci", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 18.dp))
        TextButton(onClick = onUnlock) { Text("Buka kunci") }
    }
}

private data class Tab(val route: String, val label: String, val on: ImageVector, val off: ImageVector)
private val tabs = listOf(
    Tab("home", "Utama", Icons.Filled.Home, Icons.Outlined.Home),
    Tab("photos", "Foto", Icons.Filled.Image, Icons.Outlined.Image),
    Tab("albums", "Album", Icons.Filled.Folder, Icons.Outlined.Folder),
    Tab("videos", "Video", Icons.Filled.Movie, Icons.Outlined.Movie),
    Tab("profile", "Profil", Icons.Filled.Person, Icons.Outlined.Person),
)

@Composable
private fun Root(theme: Int, onTheme: (Int) -> Unit, onServerReset: () -> Unit) {
    val status by remember { Graph.client.auth.sessionStatus }.collectAsState()
    when (status) {
        is SessionStatus.Initializing -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        is SessionStatus.Authenticated -> {
            LaunchedEffect(Unit) { Graph.repo.startRealtime() }
            Main(theme, onTheme, onServerReset)
        }
        else -> AuthScreen(onServerChange = {
            Graph.prefs.serverUrl = ""
            Graph.prefs.anonKey = ""
            Graph.resetServer()
            onServerReset()
        })
    }
}

@Composable
private fun Main(theme: Int, onTheme: (Int) -> Unit, onServerReset: () -> Unit) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val showBar = route in tabs.map { it.route }
    val openViewer = { nav.navigate("viewer") }

    Box(Modifier.fillMaxSize()) {
        NavHost(nav, startDestination = "home", modifier = Modifier.fillMaxSize().then(if (route == "viewer") Modifier else Modifier.statusBarsPadding())) {
            composable("home") { HomeScreen(onOpenViewer = openViewer, onNavigate = { nav.navigate(it) }) }
            composable("photos") { MediaGridScreen(MediaFilter(View.PHOTOS), "Foto", "Upload foto pertama anda.", openViewer, showKindFilter = false) }
            composable("videos") { MediaGridScreen(MediaFilter(View.VIDEOS), "Video", "Upload video pertama anda.", openViewer, showKindFilter = false) }
            composable("albums") { AlbumsScreen(onOpen = { nav.navigate("album/$it") }) }
            composable("album/{id}") { AlbumDetailScreen(it.arguments?.getString("id") ?: "", onBack = { nav.popBackStack() }, onOpenViewer = openViewer) }
            composable("profile") { ProfileScreen(theme, onTheme, onNavigate = { nav.navigate(it) }, onServerReset = onServerReset) }
            composable("favourites") {
                MediaGridScreen(MediaFilter(View.FAVOURITES), "Kegemaran", "Tekan ♥ pada foto atau video untuk simpan di sini.", openViewer, onBack = { nav.popBackStack() })
            }
            composable("trash") {
                MediaGridScreen(MediaFilter(View.TRASH), "Baru Dipadam", "Tiada fail dipadam. Fail dipadam kekal selepas 30 hari.", openViewer, onBack = { nav.popBackStack() })
            }
            composable("storage") { StorageScreen(onBack = { nav.popBackStack() }, onOpenViewer = openViewer) }
            composable("backup") { BackupScreen(onBack = { nav.popBackStack() }) }
            composable("viewer") { ViewerScreen(onClose = { nav.popBackStack() }) }
        }

        AnimatedVisibility(
            visible = showBar,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            BottomBar(route) { r ->
                nav.navigate(r) {
                    popUpTo("home") { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            }
        }
    }
}

@Composable
private fun BottomBar(current: String?, onSelect: (String) -> Unit) {
    val g = LocalGlass.current
    Surface(
        modifier = Modifier.navigationBarsPadding().padding(horizontal = 14.dp, vertical = 10.dp).fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        border = BorderStroke(1.dp, g.border),
        shadowElevation = 12.dp,
    ) {
        Row(Modifier.padding(6.dp), horizontalArrangement = Arrangement.SpaceAround) {
            tabs.forEach { t ->
                val sel = current == t.route
                Column(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (sel) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else Color.Transparent)
                        .clickable { onSelect(t.route) }
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(if (sel) t.on else t.off, t.label, tint = if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(t.label, fontSize = 11.sp, fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium,
                        color = if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
