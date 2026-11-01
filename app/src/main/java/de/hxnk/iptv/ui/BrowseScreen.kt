package de.hxnk.iptv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import de.hxnk.iptv.Graph
import de.hxnk.iptv.data.ChannelEntity
import de.hxnk.iptv.data.ProgrammeEntity
import de.hxnk.iptv.data.ProgrammeLite
import de.hxnk.iptv.data.Repository
import kotlinx.coroutines.delay

@Composable
fun BrowseScreen(vm: MainViewModel) {
    val groups by vm.groups.collectAsStateWithLifecycle()
    val selected by vm.selectedGroup.collectAsStateWithLifecycle()
    val channels by vm.visible.collectAsStateWithLifecycle()
    val favs by vm.favorites.collectAsStateWithLifecycle()
    val epg by vm.epg.collectAsStateWithLifecycle()
    val clock by vm.clock.collectAsStateWithLifecycle()
    val sync by vm.syncState.collectAsStateWithLifecycle()
    val ready by vm.ready.collectAsStateWithLifecycle()
    val playing by vm.playing.collectAsStateWithLifecycle()
    val lastPlayed by vm.lastPlayedUrl.collectAsStateWithLifecycle()

    var focusedChannel by remember { mutableStateOf<ChannelEntity?>(null) }
    val restore = remember { FocusRequester() }
    val initial = remember { FocusRequester() }

    // Beim Start Fokus auf die Kategorieliste legen
    LaunchedEffect(Unit) {
        delay(150)
        runCatching { initial.requestFocus() }
    }
    LaunchedEffect(selected) { focusedChannel = null }
    // Nach dem Schließen des Players zurück zum zuletzt gesehenen Sender
    LaunchedEffect(playing) {
        if (playing == null && lastPlayed != null) {
            delay(80)
            runCatching { restore.requestFocus() }
        }
    }

    Row(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 36.dp, vertical = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        // ---- Kategorien ----
        Column(Modifier.width(250.dp).fillMaxHeight()) {
            Text("HX IPTV", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Accent)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallButton("Neu laden") { vm.refresh() }
                SmallButton("Setup") { vm.openSettings() }
            }
            Spacer(Modifier.height(14.dp))
            LazyColumn(Modifier.weight(1f)) {
                itemsIndexed(groups, key = { _, g -> g.key }) { i, g ->
                    GroupRow(
                        g = g,
                        selected = selected == g.key,
                        modifier = if (i == 0) Modifier.focusRequester(initial) else Modifier,
                        onFocus = { vm.selectGroup(g.key) },
                    )
                }
            }
        }

        // ---- Senderliste ----
        Column(Modifier.weight(1.4f).fillMaxHeight()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    groups.firstOrNull { it.key == selected }?.label ?: "",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                SyncBadge(sync, Modifier.widthIn(max = 360.dp))
            }
            Spacer(Modifier.height(10.dp))
            when {
                !ready -> Unit
                channels.isEmpty() -> EmptyHint(selected, sync)
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(channels, key = { it.streamUrl }) { ch ->
                        val (now, _) = nowNext(ch.tvgId?.let { epg[it] }, clock)
                        ChannelRow(
                            ch = ch,
                            fav = ch.streamUrl in favs,
                            now = now,
                            t = clock,
                            modifier = if (ch.streamUrl == lastPlayed) Modifier.focusRequester(restore) else Modifier,
                            onFocus = { focusedChannel = ch },
                            onClick = { vm.play(ch) },
                            onMenu = { vm.toggleFavorite(ch) },
                        )
                    }
                }
            }
        }

        // ---- Programm-Details ----
        DetailPanel(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            ch = focusedChannel ?: channels.firstOrNull(),
            vm = vm,
            clock = clock,
        )
    }
}

@Composable
private fun EmptyHint(selected: String, sync: Repository.SyncState) {
    val text = when {
        sync.running -> "Sender werden geladen …"
        selected == GROUP_FAV ->
            "Noch keine Favoriten.\nDrücke auf einem Sender die Menü-Taste (drei Striche) auf der Fernbedienung."
        sync.error != null -> sync.error ?: ""
        else -> "Keine Sender gefunden."
    }
    Text(text, color = TextDim, fontSize = 16.sp, modifier = Modifier.padding(top = 24.dp))
}

@Composable
private fun SyncBadge(s: Repository.SyncState, modifier: Modifier = Modifier) {
    when {
        s.running -> Row(modifier, verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = Accent)
            Spacer(Modifier.width(8.dp))
            Text(s.message, fontSize = 13.sp, color = Accent, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }

        s.error != null ->
            Text(s.error ?: "", fontSize = 12.sp, color = ErrorRed, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = modifier)

        else -> {
            val last = Graph.settings.lastSync
            if (last > 0) {
                Text("Aktualisiert ${fmtDateTime(last)}", fontSize = 12.sp, color = TextDim, modifier = modifier)
            }
        }
    }
}

@Composable
private fun SmallButton(label: String, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (focused) Accent else Card)
            .onFocusChanged { focused = it.isFocused }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = 13.sp, color = if (focused) Color.Black else TextMain)
    }
}

@Composable
private fun GroupRow(g: GroupItem, selected: Boolean, modifier: Modifier, onFocus: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clip(shape)
            .background(
                when {
                    focused -> Focus
                    selected -> Panel
                    else -> Color.Transparent
                }
            )
            .border(2.dp, if (focused) Accent else Color.Transparent, shape)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocus()
            }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onFocus() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            g.label,
            fontSize = 16.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) Accent else TextMain,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text("${g.count}", fontSize = 13.sp, color = TextDim)
    }
}

@Composable
private fun ChannelRow(
    ch: ChannelEntity,
    fav: Boolean,
    now: ProgrammeLite?,
    t: Long,
    modifier: Modifier,
    onFocus: () -> Unit,
    onClick: () -> Unit,
    onMenu: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(shape)
            .background(if (focused) Focus else Card)
            .border(2.dp, if (focused) Accent else Color.Transparent, shape)
            .onKeyEvent { e ->
                if (e.type == KeyEventType.KeyDown && e.key == Key.Menu) {
                    onMenu()
                    true
                } else false
            }
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocus()
            }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Logo(ch.logoUrl, ch.name, 52.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (fav) Text("★ ", color = Accent, fontSize = 16.sp)
                Text(
                    ch.name,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (now != null) {
                Text(
                    "${fmtTime(now.start)}  ${now.title}",
                    fontSize = 13.sp,
                    color = TextDim,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                ProgressBar(((t - now.start).toFloat() / (now.stop - now.start).toFloat()).coerceIn(0f, 1f))
            }
        }
    }
}

@Composable
fun ProgressBar(p: Float, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .padding(top = 5.dp)
            .height(3.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(Color(0xFF2B3646)),
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(p)
                .background(Accent),
        )
    }
}

private fun initials(name: String): String {
    val s = name.split(' ', '-', '_', '.').filter { it.isNotBlank() }.take(2)
        .joinToString("") { it.take(1).uppercase() }
    return s.ifEmpty { "TV" }
}

/** Zeigt das Senderlogo (aus dem lokalen Cache) oder Initialen als Platzhalter. */
@Composable
fun Logo(url: String?, name: String, size: Dp) {
    var loaded by remember(url) { mutableStateOf(false) }
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(8.dp))
            .background(LogoBg),
        contentAlignment = Alignment.Center,
    ) {
        if (!loaded) {
            Text(
                initials(name),
                fontSize = (size.value * 0.34f).sp,
                fontWeight = FontWeight.Bold,
                color = TextDim,
            )
        }
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().padding(4.dp),
                onSuccess = { loaded = true },
            )
        }
    }
}

@Composable
private fun DetailPanel(modifier: Modifier, ch: ChannelEntity?, vm: MainViewModel, clock: Long) {
    val version by vm.syncVersion.collectAsStateWithLifecycle()
    val tvg = ch?.tvgId
    val upcoming by produceState(initialValue = emptyList<ProgrammeEntity>(), tvg, version) {
        value = emptyList()
        if (tvg != null) {
            delay(200) // kurz warten, damit schnelles Scrollen nicht für jeden Sender abfragt
            value = vm.upcoming(tvg)
        }
    }

    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Panel)
            .padding(20.dp),
    ) {
        if (ch == null) {
            Text("Wähle einen Sender", color = TextDim)
            return@Column
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Logo(ch.logoUrl, ch.name, 72.dp)
            Spacer(Modifier.width(16.dp))
            Column {
                Text(ch.name, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(ch.groupName, fontSize = 13.sp, color = TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.height(18.dp))

        val items = upcoming.filter { it.stop > clock }
        if (items.isEmpty()) {
            Text(
                if (ch.tvgId == null) "Für diesen Sender sind keine Programmdaten zugeordnet." else "Keine Programmdaten verfügbar.",
                color = TextDim,
                fontSize = 14.sp,
            )
            return@Column
        }

        val first = items.first()
        val isNow = first.start <= clock
        Text(if (isNow) "JETZT" else "ALS NÄCHSTES", fontSize = 12.sp, color = Accent, fontWeight = FontWeight.Bold)
        Text(first.title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text("${fmtTime(first.start)} – ${fmtTime(first.stop)}", fontSize = 13.sp, color = TextDim)
        if (isNow) {
            ProgressBar(((clock - first.start).toFloat() / (first.stop - first.start).toFloat()).coerceIn(0f, 1f))
        }
        first.description?.let {
            Text(it, fontSize = 13.sp, color = TextDim, maxLines = 5, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
        }

        val later = items.drop(1).take(7)
        if (later.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Text("DANACH", fontSize = 12.sp, color = Accent, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            for (p in later) {
                Row(Modifier.padding(vertical = 2.dp)) {
                    Text(fmtTime(p.start), fontSize = 13.sp, color = Accent, modifier = Modifier.width(52.dp))
                    Text(p.title, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
