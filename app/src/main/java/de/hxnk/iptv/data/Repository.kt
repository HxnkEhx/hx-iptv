package de.hxnk.iptv.data

import android.content.Context
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.util.zip.GZIPInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

class Repository(
    private val ctx: Context,
    val dao: IptvDao,
    private val settings: Settings,
    private val http: OkHttpClient,
) {
    data class SyncState(
        val running: Boolean = false,
        val message: String = "",
        val error: String? = null,
    )

    private val _state = MutableStateFlow(SyncState())
    val state: StateFlow<SyncState> = _state

    /** Wird nach jedem erfolgreichen Datenimport hochgezählt, damit die UI neu abfragt. */
    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version

    private val mutex = Mutex()

    /** Lädt Playlist und TV-Programm neu. Gibt true zurück, wenn die Playlist erfolgreich war. */
    suspend fun sync(): Boolean = withContext(Dispatchers.IO) {
        if (settings.playlistUrl.isBlank()) return@withContext false
        if (!mutex.tryLock()) return@withContext false
        try {
            _state.value = SyncState(running = true, message = "Playlist wird geladen …")

            var channels: List<ChannelEntity> = emptyList()
            var epgFromPlaylist: String? = null
            try {
                val file = File(ctx.cacheDir, "playlist.m3u")
                download(settings.playlistUrl, file)
                val res = file.bufferedReader().use { M3uParser.parse(it) }
                if (res.channels.isEmpty()) throw IOException("Die Playlist enthält keine Sender")
                dao.replaceChannels(res.channels)
                channels = res.channels
                epgFromPlaylist = res.epgUrls.firstOrNull()
                _version.update { it + 1 }
            } catch (e: Exception) {
                // Alte Daten bleiben erhalten, die App funktioniert weiter offline.
                _state.value = SyncState(error = "Playlist konnte nicht geladen werden: ${e.message}")
                return@withContext false
            }

            var epgError: String? = null
            val epgUrl = settings.epgUrl.ifBlank { epgFromPlaylist ?: "" }
            if (epgUrl.isNotBlank()) {
                try {
                    _state.value = SyncState(running = true, message = "Programmdaten werden geladen …")
                    val f = File(ctx.cacheDir, "epg.xml")
                    download(epgUrl, f)
                    _state.value = SyncState(running = true, message = "Programmdaten werden verarbeitet …")
                    importEpg(f, channels)
                    _version.update { it + 1 }
                } catch (e: Exception) {
                    epgError = "TV-Programm konnte nicht geladen werden: ${e.message}"
                }
            }

            settings.lastSync = System.currentTimeMillis()
            _state.value = SyncState(error = epgError)
            true
        } finally {
            mutex.unlock()
        }
    }

    private suspend fun importEpg(file: File, channels: List<ChannelEntity>) {
        val now = System.currentTimeMillis()
        val from = now - 2 * 3600_000L
        val until = now + 3 * 24 * 3600_000L

        // 1. Kanalkopf lesen und mit der Playlist abgleichen (erst per tvg-id, dann per Name)
        val xmlChannels = openMaybeGz(file).use { XmltvParser.readChannels(it) }
        val xmlIds = xmlChannels.keys
        val lowerIds = HashMap<String, String>()
        xmlIds.forEach { lowerIds.putIfAbsent(it.lowercase(), it) }
        val nameToId = HashMap<String, String>()
        for ((id, names) in xmlChannels) {
            for (n in names) nameToId.putIfAbsent(normalize(n), id)
        }

        val accepted = HashSet<String>()
        val newIds = HashMap<String, String>()
        for (ch in channels) {
            val tid = ch.tvgId
            if (tid != null && tid in xmlIds) {
                accepted.add(tid)
                continue
            }
            val byLower = if (tid != null) lowerIds[tid.lowercase()] else null
            val matched = byLower ?: nameToId[normalize(ch.name)]
            if (matched != null) {
                accepted.add(matched)
                newIds[ch.streamUrl] = matched
            }
        }
        if (newIds.isNotEmpty()) dao.setTvgIds(newIds)

        // 2. Sendungen streamend einlesen und in Blöcken speichern
        dao.clearProgrammes()
        val batch = ArrayList<ProgrammeEntity>(2000)
        openMaybeGz(file).use { input ->
            for (p in XmltvParser.programmes(input)) {
                if (p.channelId in accepted && p.stop > from && p.start < until) {
                    batch.add(p)
                    if (batch.size >= 2000) {
                        dao.insertProgrammes(batch.toList())
                        batch.clear()
                    }
                }
            }
        }
        if (batch.isNotEmpty()) dao.insertProgrammes(batch.toList())
    }

    private fun normalize(s: String): String {
        var n = s.lowercase().replace(Regex("[^\\p{L}\\p{N}]"), "")
        for (suffix in listOf("uhd", "fhd", "hd")) n = n.removeSuffix(suffix)
        return n
    }

    private fun openMaybeGz(f: File): InputStream {
        val b = BufferedInputStream(FileInputStream(f), 64 * 1024)
        b.mark(2)
        val b0 = b.read()
        val b1 = b.read()
        b.reset()
        return if (b0 == 0x1f && b1 == 0x8b) GZIPInputStream(b, 64 * 1024) else b
    }

    private fun download(url: String, dest: File) {
        var last: Exception? = null
        repeat(3) { attempt ->
            try {
                val req = Request.Builder().url(url).build()
                http.newCall(req).execute().use { r ->
                    if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
                    val tmp = File(dest.path + ".tmp")
                    r.body!!.byteStream().use { i -> tmp.outputStream().use { o -> i.copyTo(o) } }
                    if (!tmp.renameTo(dest)) {
                        tmp.copyTo(dest, overwrite = true)
                        tmp.delete()
                    }
                }
                return
            } catch (e: Exception) {
                last = e
                Thread.sleep(1500L * (attempt + 1))
            }
        }
        throw last ?: IOException("Download fehlgeschlagen")
    }
}
