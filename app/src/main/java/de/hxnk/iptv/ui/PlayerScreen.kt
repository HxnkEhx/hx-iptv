package de.hxnk.iptv.ui

import android.content.Context
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import de.hxnk.iptv.Graph
import de.hxnk.iptv.data.ChannelEntity
import de.hxnk.iptv.data.ProgrammeLite
import de.hxnk.iptv.data.Settings
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.delay

object PlayerFactory {
    /**
     * ExoPlayer, getrimmt auf Live-Streams:
     *  - großzügiger Puffer (15–60 s), damit kurze Aussetzer nicht auffallen
     *  - viele Wiederholungsversuche bei Ladefehlern (Segmente, Playlists)
     *  - OkHttp als Netzwerk-Stack (HTTP/2, Redirects, Keep-Alive)
     */
    fun create(ctx: Context): ExoPlayer {
        val client = Graph.http.newBuilder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
        val dataSource = OkHttpDataSource.Factory(client).setUserAgent(Settings.UA)
        val mediaSourceFactory = DefaultMediaSourceFactory(ctx)
            .setDataSourceFactory(dataSource)
            .setLoadErrorHandlingPolicy(DefaultLoadErrorHandlingPolicy(20))
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(15_000, 60_000, 2_500, 5_000)
            .build()
        val renderers = DefaultRenderersFactory(ctx)
            .setEnableDecoderFallback(true)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF)

        return ExoPlayer.Builder(ctx, renderers)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
            .also { it.setAudioAttributes(AudioAttributes.DEFAULT, true) }
    }
}

@Composable
fun PlayerScreen(vm: MainViewModel, channel: ChannelEntity) {
    val ctx = LocalContext.current
    val exo = remember { PlayerFactory.create(ctx) }

    var status by remember { mutableStateOf("Verbinde …") }
    var errorCount by remember { mutableIntStateOf(0) }
    var forceHls by remember { mutableStateOf(false) }
    var infoTick by remember { mutableIntStateOf(0) }
    var showInfo by remember { mutableStateOf(true) }
    var wasStopped by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }

    val epg by vm.epg.collectAsStateWithLifecycle()
    val clock by vm.clock.collectAsStateWithLifecycle()
    val favs by vm.favorites.collectAsStateWithLifecycle()
    val list by vm.visible.collectAsStateWithLifecycle()

    fun load() {
        val item = MediaItem.Builder()
            .setUri(channel.streamUrl)
            .apply { if (forceHls) setMimeType(MimeTypes.APPLICATION_M3U8) }
            .build()
        exo.setMediaItem(item)
        exo.prepare()
        exo.playWhenReady = true
    }

    BackHandler { vm.stop() }

    DisposableEffect(exo) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_READY -> {
                        status = ""
                        errorCount = 0
                    }

                    Player.STATE_BUFFERING ->
                        status = if (errorCount > 0) "Verbindung wird wiederhergestellt …" else "Puffert …"

                    else -> Unit
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                // Unbekanntes Format (z. B. HLS ohne .m3u8-Endung): beim nächsten Versuch HLS erzwingen
                if (error.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED) {
                    forceHls = !forceHls
                }
                errorCount += 1
            }
        }
        exo.addListener(listener)
        onDispose {
            exo.removeListener(listener)
            exo.release()
        }
    }

    // Senderwechsel
    LaunchedEffect(channel.streamUrl) {
        errorCount = 0
        forceHls = false
        status = "Verbinde …"
        load()
        infoTick++
    }

    // Auto-Reconnect mit wachsender Wartezeit (1 s, 2 s, … max. 8 s), bis der Stream wieder läuft
    LaunchedEffect(errorCount) {
        if (errorCount > 0) {
            status = "Verbindung unterbrochen – neuer Versuch ($errorCount)"
            delay(minOf(1000L * errorCount, 8000L))
            load()
        }
    }

    // Watchdog: hängt der Player länger als 30 s im Puffern, wird neu verbunden
    LaunchedEffect(channel.streamUrl) {
        var stuck = 0
        while (true) {
            delay(2000)
            stuck = if (exo.playbackState == Player.STATE_BUFFERING && exo.playWhenReady) stuck + 2 else 0
            if (stuck >= 30) {
                stuck = 0
                errorCount += 1
            }
        }
    }

    // Infoleiste nach 5 s ausblenden
    LaunchedEffect(infoTick) {
        showInfo = true
        delay(5000)
        showInfo = false
    }

    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    // Beim Wechsel in den Hintergrund pausieren, beim Zurückkommen frisch verbinden
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        wasStopped = true
        exo.pause()
    }
    LifecycleEventEffect(Lifecycle.Event.ON_START) {
        if (wasStopped) {
            wasStopped = false
            load()
        }
    }

    val index = list.indexOfFirst { it.streamUrl == channel.streamUrl } + 1
    val (now, next) = nowNext(channel.tvgId?.let { epg[it] }, clock)

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focus)
            .focusable()
            .onKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (e.key) {
                    Key.DirectionUp, Key.ChannelUp, Key.PageUp -> {
                        vm.zap(-1)
                        true
                    }

                    Key.DirectionDown, Key.ChannelDown, Key.PageDown -> {
                        vm.zap(1)
                        true
                    }

                    // Links/Rechts verbrauchen, sonst wandert der Fokus in die verdeckte Senderliste
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter,
                    Key.DirectionLeft, Key.DirectionRight -> {
                        infoTick++
                        true
                    }

                    Key.Menu -> {
                        vm.toggleFavorite(channel)
                        infoTick++
                        true
                    }

                    else -> false
                }
            },
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { c ->
                PlayerView(c).apply {
                    useController = false
                    setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    isFocusable = false
                    isFocusableInTouchMode = false
                    descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                    keepScreenOn = true
                    this.player = exo
                }
            },
        )

        AnimatedVisibility(
            visible = status.isNotEmpty(),
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 40.dp),
        ) {
            Row(
                Modifier
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xCC000000))
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Accent)
                Spacer(Modifier.width(12.dp))
                Text(status, fontSize = 16.sp, color = Color.White)
            }
        }

        AnimatedVisibility(
            visible = showInfo,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            InfoBar(channel, index, channel.streamUrl in favs, now, next, clock)
        }
    }
}

@Composable
private fun InfoBar(
    ch: ChannelEntity,
    index: Int,
    fav: Boolean,
    now: ProgrammeLite?,
    next: ProgrammeLite?,
    t: Long,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xEE000000))))
            .padding(horizontal = 56.dp, vertical = 36.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Logo(ch.logoUrl, ch.name, 80.dp)
            Spacer(Modifier.width(20.dp))
            Column(Modifier.weight(1f)) {
                val prefix = if (index > 0) "$index  ·  " else ""
                Text(
                    "$prefix${ch.name}${if (fav) "  ★" else ""}",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (now != null) {
                    Text(
                        "${fmtTime(now.start)} – ${fmtTime(now.stop)}   ${now.title}",
                        fontSize = 18.sp,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    ProgressBar(((t - now.start).toFloat() / (now.stop - now.start).toFloat()).coerceIn(0f, 1f))
                } else {
                    Text("Keine Programminfo", fontSize = 16.sp, color = TextDim)
                }
                if (next != null) {
                    Text(
                        "Danach: ${fmtTime(next.start)}   ${next.title}",
                        fontSize = 15.sp,
                        color = TextDim,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
                Text(
                    "Hoch/Runter: Sender wechseln    Menü-Taste: Favorit    Zurück: Liste",
                    fontSize = 12.sp,
                    color = TextDim,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
        }
    }
}
