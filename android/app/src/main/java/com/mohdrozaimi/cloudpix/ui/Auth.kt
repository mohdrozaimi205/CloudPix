package com.mohdrozaimi.cloudpix.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mohdrozaimi.cloudpix.Graph
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@Composable
private fun Brand(title: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Box(Modifier.size(64.dp).clip(RoundedCornerShape(20.dp)).background(Grad), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.Cloud, null, tint = Color.White, modifier = Modifier.size(34.dp))
        }
        Text(title, fontWeight = FontWeight.ExtraBold, fontSize = 28.sp, modifier = Modifier.padding(top = 12.dp))
    }
}

@Composable
fun SetupScreen(onDone: () -> Unit) {
    var url by remember { mutableStateOf("") }
    var key by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Brand("Sambung Server")
        GlassCard(Modifier.fillMaxWidth()) {
            Text(
                "Masukkan Project URL dan Publishable (anon) key Supabase anda. Kedua-duanya selamat diletak dalam app — kunci rahsia tidak diperlukan.",
                color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp,
            )
            OutlinedTextField(url, { url = it.trim() }, label = { Text("Project URL") }, placeholder = { Text("https://xxxx.supabase.co") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
            OutlinedTextField(key, { key = it.trim() }, label = { Text("Publishable / anon key") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
            err?.let { Text(it, color = Danger, modifier = Modifier.padding(top = 8.dp)) }
            GradientButton("Sambung", Modifier.fillMaxWidth().padding(top = 14.dp), enabled = url.isNotBlank() && key.isNotBlank()) {
                if (!url.startsWith("https://")) { err = "URL mesti bermula dengan https://"; return@GradientButton }
                Graph.prefs.serverUrl = url
                Graph.prefs.anonKey = key
                Graph.resetServer()
                onDone()
            }
        }
    }
}

@Composable
fun AuthScreen(onServerChange: () -> Unit) {
    val scope = rememberCoroutineScope()
    var mode by remember { mutableStateOf(0) } // 0 login, 1 daftar, 2 lupa
    var emailText by remember { mutableStateOf("") }
    var pwText by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var rememberMe by remember { mutableStateOf(Graph.prefs.rememberMe) }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<Pair<Boolean, String>?>(null) }

    fun submit() {
        busy = true
        msg = null
        scope.launch {
            try {
                val auth = Graph.client.auth
                when (mode) {
                    0 -> {
                        Graph.prefs.rememberMe = rememberMe
                        auth.signInWith(Email) { this.email = emailText.trim(); this.password = pwText }
                    }
                    1 -> {
                        if (pwText.length < 8) error("Kata laluan mesti sekurang-kurangnya 8 aksara.")
                        auth.signUpWith(Email) {
                            this.email = emailText.trim(); this.password = pwText
                            data = buildJsonObject { put("display_name", name.trim()) }
                        }
                        if (auth.currentSessionOrNull() == null) msg = true to "Akaun dicipta. Semak e-mel anda dan klik pautan pengesahan, kemudian log masuk."
                    }
                    else -> {
                        auth.resetPasswordForEmail(emailText.trim())
                        msg = true to "Jika e-mel ini berdaftar, pautan tetapan semula telah dihantar. Tetapkan kata laluan baru melalui pautan itu."
                    }
                }
            } catch (e: Exception) {
                val t = e.message ?: "Ralat"
                msg = false to when {
                    t.contains("Invalid login", true) -> "E-mel atau kata laluan salah."
                    t.contains("not confirmed", true) -> "E-mel belum disahkan. Semak peti masuk anda."
                    t.contains("Unable to resolve host", true) || t.contains("timeout", true) -> "Tiada sambungan internet."
                    else -> t.lineSequence().first().take(200)
                }
            } finally {
                busy = false
            }
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterVertically),
    ) {
        Brand("CloudPix")
        Text("Storan awan peribadi 1TB untuk foto & video anda.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        GlassCard(Modifier.fillMaxWidth(), padding = 20.dp) {
            if (mode != 2) SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(mode == 0, { mode = 0 }, SegmentedButtonDefaults.itemShape(0, 2)) { Text("Log masuk") }
                SegmentedButton(mode == 1, { mode = 1 }, SegmentedButtonDefaults.itemShape(1, 2)) { Text("Daftar") }
            } else Text("Lupa kata laluan", style = MaterialTheme.typography.titleMedium)
            if (mode == 1) OutlinedTextField(name, { name = it.take(80) }, label = { Text("Nama") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
            OutlinedTextField(
                emailText, { emailText = it }, label = { Text("E-mel") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            )
            if (mode != 2) OutlinedTextField(
                pwText, { pwText = it }, label = { Text("Kata laluan") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            if (mode == 0) Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Checkbox(rememberMe, { rememberMe = it })
                Text("Ingat saya", modifier = Modifier.weight(1f))
                TextButton(onClick = { mode = 2 }) { Text("Lupa kata laluan?") }
            }
            msg?.let { (ok, t) -> Text(t, color = if (ok) Ok else Danger, modifier = Modifier.padding(top = 8.dp)) }
            GradientButton(
                if (busy) "Sila tunggu…" else when (mode) { 0 -> "Log masuk"; 1 -> "Cipta akaun"; else -> "Hantar pautan" },
                Modifier.fillMaxWidth().padding(top = 14.dp),
                enabled = !busy && emailText.isNotBlank() && (mode == 2 || pwText.isNotBlank()),
            ) { submit() }
            if (mode == 2) TextButton(onClick = { mode = 0 }, modifier = Modifier.fillMaxWidth()) { Text("Kembali ke log masuk") }
        }
        TextButton(onClick = onServerChange, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Outlined.Dns, null, Modifier.size(16.dp))
            Text("  Tukar server", fontSize = 13.sp)
        }
    }
}
