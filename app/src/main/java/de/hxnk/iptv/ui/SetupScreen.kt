package de.hxnk.iptv.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
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
    var playlist by remember { mutableStateOf(Graph.settings.playlistUrl) }
    var epg by remember { mutableStateOf(Graph.settings.epgUrl) }
    val configured by vm.configured.collectAsStateWithLifecycle()
    val update by vm.update.collectAsStateWithLifecycle()
    val updateStatus by vm.updateStatus.collectAsStateWithLifecycle()

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 120.dp, vertical = 48.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("HX IPTV", fontSize = 40.sp, fontWeight = FontWeight.Bold, color = Accent)
        Spacer(Modifier.height(8.dp))
        Text(
            "Trage die Adresse deiner M3U-Playlist ein. Sender, Logos und TV-Programm werden " +
                "heruntergeladen und lokal gespeichert, damit die App danach schnell und auch bei " +
                "kurzen Netzausfällen zuverlässig läuft.",
            color = TextDim,
            fontSize = 16.sp,
        )
        Spacer(Modifier.height(28.dp))

        OutlinedTextField(
            value = playlist,
            onValueChange = { playlist = it },
            label = { Text("M3U-Playlist-URL") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = epg,
            onValueChange = { epg = it },
            label = { Text("TV-Programm-URL (XMLTV, optional – sonst aus der Playlist)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Tipp: Die URL lässt sich bequem über die Amazon-Fire-TV-App auf dem Handy eintippen oder einfügen.",
            color = TextDim,
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(28.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Button(onClick = { vm.save(playlist, epg) }, enabled = playlist.isNotBlank()) {
                Text("Speichern & laden")
            }
            if (configured) {
                OutlinedButton(onClick = { vm.closeSettings() }) { Text("Abbrechen") }
            }
        }

        Spacer(Modifier.height(36.dp))
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
