package de.hxnk.iptv.data

import android.content.Context
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.net.URLEncoder
import java.util.zip.GZIPInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray

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

    private class Loaded(val channels: List<ChannelEntity>, val epgUrl: String?)

    private val _state = MutableStateFlow(SyncState())
    val state: StateFlow<SyncState> = _state

    /** Wird nach jedem erfolgreichen Datenimport hochgezählt, damit die UI neu abfragt. */
    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version

    private val mutex = Mutex()

    /** Lädt Sender und TV-Programm neu. Gibt true zurück, wenn die Senderliste erfolgreich war. */
    suspend fun sync(): Boolean = withContext(Dispatchers.IO) {
        if (!settings.isConfigured) return@withContext false
        if (!mutex.tryLock()) return@withContext false
        try {
            _state.value = SyncState(running = true, message = "Sender werden geladen …")

            var channels: List<ChannelEntity> = emptyList()
            var epgFromProvider: String? = null
            try {
                val loaded = if (settings.mode == "xtream") loadXtream() else loadM3u()
                if (loaded.channels.isEmpty()) throw IOException("Keine Sender gefunden")
                dao.replaceChannels(loaded.channels)
                channels = loaded.channels
                epgFromProvider = loaded.epgUrl
                _version.update { it + 1 }
            } catch (e: Exception) {
                // Alte Daten bleiben erhalten, die App funktioniert weiter offline.
                _state.value = SyncState(error = "Sender konnten nicht geladen werden: ${e.message}")
                return@withContext false
            }

            var epgError: String? = null
            val epgUrl = settings.epgUrl.ifBlank { epgFromProvider ?: "" }
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

    // ---------- Quellen ----------

    private fun loadM3u(): Loaded {
        val file = File(ctx.cacheDir, "playlist.m3u")
        download(settings.playlistUrl, file)
        val res = file.bufferedReader().use { M3uParser.parse(it) }
        return Loaded(res.channels, res.epgUrls.firstOrNull())
    }

    /** Xtream-Codes-API: Kategorien + Live-Sender, TV-Programm über xmltv.php. */
    private fun loadXtream(): Loaded {
        val host = settings.xtreamHost.trim().trimEnd('/')
        val user = settings.xtreamUser.trim()
        val pass = settings.xtreamPass.trim()
        val u = URLEncoder.encode(user, "UTF-8")
        val p = URLEncoder.encode(pass, "UTF-8")
        val base = "$host/player_api.php?username=$u&password=$p"

        val catText = fetchText("$base&action=get_live_categories")
        if (catText.trimStart().startsWith("{")) {
            throw IOException("Anmeldung fehlgeschlagen – Server, Benutzername und Passwort prüfen")
        }
        val names = HashMap<String, String>()
        val cats = JSONArray(catText)
        for (i in 0 until cats.length()) {
            val o = cats.optJSONObject(i) ?: continue
            names[o.optString("category_id")] = o.optString("category_name").trim()
        }

        val streams = JSONArray(fetchText("$base&action=get_live_streams"))
        val ext = if (settings.streamFormat == "m3u8") "m3u8" else "ts"
        val out = ArrayList<ChannelEntity>(streams.length())
        for (i in 0 until streams.length()) {
            val o = streams.optJSONObject(i) ?: continue
            val id = o.optString("stream_id")
            if (id.isBlank()) continue
            val name = o.optString("name").trim().ifEmpty { "Sender $id" }
            val group = names[o.optString("category_id")]?.ifEmpty { null } ?: "Ohne Gruppe"
            val icon = o.optString("stream_icon").trim().takeIf { it.startsWith("http", ignoreCase = true) }
            val epgId = o.optString("epg_channel_id").trim().takeIf { it.isNotEmpty() && it != "null" }
            val url = "$host/live/$user/$pass/$id.$ext"
            out.add(ChannelEntity(url, name, group, icon, epgId, out.size))
        }
        return Loaded(out, "$host/xmltv.php?username=$u&password=$p")
    }

    private fun fetchText(url: String): String {
        val f = File(ctx.cacheDir, "api.json")
        download(url, f)
        return f.readText()
    }

    // ---------- EPG ----------

    private suspend fun importEpg(file: File, channels: List<ChannelEntity>) {
        val now = System.currentTimeMillis()
        val from = now - 2 * 3600_000L
        val until = now + 3 * 24 * 3600_000L

        // 1. Kanalkopf lesen und mit den Sendern abgleichen (erst per ID, dann per Name)
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
