package de.hxnk.iptv.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.hxnk.iptv.Graph

@Composable
fun SetupScreen(vm: MainViewModel) {
    val s = Graph.settings
    var mode by remember { mutableStateOf(s.mode) }
    var playlist by remember { mutableStateOf(s.playlistUrl) }
    var host by remember { mutableStateOf(s.xtreamHost) }
    var user by remember { mutableStateOf(s.xtreamUser) }
    var pass by remember { mutableStateOf(s.xtreamPass) }
    var format by remember { mutableStateOf(s.streamFormat) }
    var epg by remember { mutableStateOf(s.epgUrl) }

    val configured by vm.configured.collectAsStateWithLifecycle()
    val update by vm.update.collectAsStateWithLifecycle()
    val updateStatus by vm.updateStatus.collectAsStateWithLifecycle()

    val canSave = if (mode == "xtream") {
        host.isNotBlank() && user.isNotBlank() && pass.isNotBlank()
    } else {
        playlist.isNotBlank()
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 120.dp, vertical = 40.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("HX IPTV", fontSize = 40.sp, fontWeight = FontWeight.Bold, color = Accent)
        Spacer(Modifier.height(8.dp))
        Text(
            "Sender, Logos und TV-Programm werden heruntergeladen und lokal gespeichert, damit die App " +
                "danach schnell und auch bei kurzen Netzausfällen zuverlässig läuft.",
            color = TextDim,
            fontSize = 16.sp,
        )
        Spacer(Modifier.height(24.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (mode == "xtream") {
                Button(onClick = { mode = "xtream" }) { Text("Xtream-Login") }
                OutlinedButton(onClick = { mode = "m3u" }) { Text("M3U-URL") }
            } else {
                OutlinedButton(onClick = { mode = "xtream" }) { Text("Xtream-Login") }
                Button(onClick = { mode = "m3u" }) { Text("M3U-URL") }
            }
        }
        Spacer(Modifier.height(16.dp))

        if (mode == "xtream") {
            OutlinedTextField(
                value = host,
                onValueChange = { host = it },
                label = { Text("Server, z. B. http://beispiel.de oder http://beispiel.de:8080") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = user,
                    onValueChange = { user = it },
                    label = { Text("Benutzername") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = pass,
                    onValueChange = { pass = it },
                    label = { Text("Passwort") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedButton(onClick = { format = if (format == "ts") "m3u8" else "ts" }) {
                    Text(if (format == "ts") "Stream-Format: TS (Standard)" else "Stream-Format: HLS (m3u8)")
                }
                Text("Bei Problemen mit einzelnen Sendern das Format wechseln.", color = TextDim, fontSize = 13.sp)
            }
        } else {
            OutlinedTextField(
                value = playlist,
                onValueChange = { playlist = it },
                label = { Text("M3U-Playlist-URL") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = epg,
                onValueChange = { epg = it },
                label = { Text("TV-Programm-URL (XMLTV, optional – sonst aus der Playlist)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Tipp: Die Eingabe geht am bequemsten über die Amazon-Fire-TV-App auf dem Handy (Tastatur-Symbol).",
            color = TextDim,
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(24.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Button(
                onClick = {
                    vm.save(mode, playlist, host, user, pass, format, if (mode == "xtream") "" else epg)
                },
                enabled = canSave,
            ) {
                Text("Speichern & laden")
            }
            if (configured) {
                OutlinedButton(onClick = { vm.closeSettings() }) { Text("Abbrechen") }
            }
        }

        Spacer(Modifier.height(32.dp))
        Text("Version ${vm.versionName}", color = TextDim, fontSize = 13.sp)
        Spacer(Modifier.height(8.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(onClick = { vm.checkUpdate(manual = true) }) { Text("Nach Updates suchen") }
            if (update != null) {
                Button(onClick = { vm.installUpdate() }) { Text("Update installieren") }
            }
            if (updateStatus.isNotEmpty()) {
                Text(updateStatus, color = Accent, fontSize = 14.sp)
            }
        }
    }
}
