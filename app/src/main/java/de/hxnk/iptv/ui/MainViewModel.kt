package de.hxnk.iptv.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.hxnk.iptv.Graph
import de.hxnk.iptv.data.ChannelEntity
import de.hxnk.iptv.data.FavoriteEntity
import de.hxnk.iptv.data.ProgrammeEntity
import de.hxnk.iptv.data.ProgrammeLite
import de.hxnk.iptv.data.Updater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

const val GROUP_ALL = "__all"
const val GROUP_FAV = "__fav"

data class GroupItem(val key: String, val label: String, val count: Int)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = Graph.repo
    private val dao = Graph.db.dao()
    private val settings = Graph.settings

    val configured = MutableStateFlow(settings.isConfigured)
    val showSettings = MutableStateFlow(false)
    val playing = MutableStateFlow<ChannelEntity?>(null)
    val lastPlayedUrl = MutableStateFlow<String?>(null)
    val selectedGroup = MutableStateFlow(GROUP_ALL)

    val syncState = repo.state
    val syncVersion = repo.version

    private val channelsRaw: StateFlow<List<ChannelEntity>?> =
        dao.channels().stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** false, solange die Datenbank noch nicht geantwortet hat (verhindert "Keine Sender"-Flackern). */
    val ready: StateFlow<Boolean> =
        channelsRaw.map { it != null }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val channels: StateFlow<List<ChannelEntity>> =
        channelsRaw.map { it ?: emptyList() }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val favorites: StateFlow<Set<String>> =
        dao.favorites().map<List<String>, Set<String>> { it.toHashSet() }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    val groups: StateFlow<List<GroupItem>> =
        combine(channels, favorites) { ch, fav -> buildGroups(ch, fav) }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val visible: StateFlow<List<ChannelEntity>> =
        combine(channels, favorites, selectedGroup) { ch, fav, g ->
            when (g) {
                GROUP_ALL -> ch
                GROUP_FAV -> ch.filter { it.streamUrl in fav }
                else -> ch.filter { it.groupName == g }
            }
        }.flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val clock: StateFlow<Long> =
        flow {
            while (true) {
                emit(System.currentTimeMillis())
                delay(30_000)
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, System.currentTimeMillis())

    /** Sendungen der nächsten 6 Stunden, gruppiert nach XMLTV-Kanal-ID. */
    val epg: StateFlow<Map<String, List<ProgrammeLite>>> =
        combine(clock, repo.version) { t, _ -> t }
            .map { t -> dao.programmesBetween(t, t + 6 * 3600_000L).groupBy { it.channelId } }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    /** Gefundenes Update (null = nichts Neues bzw. noch nicht geprüft). */
    val update = MutableStateFlow<Updater.Info?>(null)
    val updateStatus = MutableStateFlow("")

    init {
        val stale = System.currentTimeMillis() - settings.lastSync > 12 * 3600_000L
        if (configured.value && stale) refresh()
        checkUpdate()
    }

    val versionName: String get() = Updater.installedVersionName(getApplication())

    fun checkUpdate(manual: Boolean = false) {
        Graph.scope.launch {
            if (manual) updateStatus.value = "Suche nach Updates …"
            try {
                val info = Updater.check(getApplication())
                update.value = info
                if (manual) {
                    updateStatus.value =
                        if (info == null) "Du hast die neueste Version ($versionName)."
                        else "Update ${info.name} verfügbar."
                }
            } catch (e: Exception) {
                if (manual) updateStatus.value = "Update-Suche fehlgeschlagen: ${e.message}"
            }
        }
    }

    fun installUpdate() {
        val info = update.value ?: return
        val ctx = getApplication<Application>()
        if (!Updater.ensureInstallPermission(ctx)) {
            updateStatus.value =
                "Bitte erlaube HX IPTV in den Einstellungen die Installation und tippe danach nochmal auf „Update installieren“."
            return
        }
        Graph.scope.launch {
            try {
                updateStatus.value = "Lade Update …"
                val apk = Updater.download(ctx, info) { pct -> updateStatus.value = "Lade Update … $pct %" }
                updateStatus.value = "Installation wird gestartet …"
                Updater.install(ctx, apk)
            } catch (e: Exception) {
                updateStatus.value = "Update fehlgeschlagen: ${e.message}"
            }
        }
    }

    private fun buildGroups(ch: List<ChannelEntity>, fav: Set<String>): List<GroupItem> {
        val counts = LinkedHashMap<String, Int>()
        for (c in ch) counts[c.groupName] = (counts[c.groupName] ?: 0) + 1
        val favCount = ch.count { it.streamUrl in fav }
        val out = ArrayList<GroupItem>()
        out.add(GroupItem(GROUP_ALL, "Alle Sender", ch.size))
        out.add(GroupItem(GROUP_FAV, "★ Favoriten", favCount))
        for ((k, v) in counts) out.add(GroupItem(k, k, v))
        return out
    }

    fun selectGroup(key: String) {
        selectedGroup.value = key
    }

    fun play(ch: ChannelEntity) {
        lastPlayedUrl.value = ch.streamUrl
        playing.value = ch
    }

    fun stop() {
        playing.value = null
    }

    /** Sender wechseln innerhalb der aktuell angezeigten Liste (delta = +1 / -1). */
    fun zap(delta: Int) {
        val list = visible.value
        val cur = playing.value ?: return
        if (list.isEmpty()) return
        val i = list.indexOfFirst { it.streamUrl == cur.streamUrl }
        val n = if (i < 0) 0 else (i + delta).mod(list.size)
        play(list[n])
    }

    fun toggleFavorite(ch: ChannelEntity) {
        viewModelScope.launch {
            if (ch.streamUrl in favorites.value) dao.removeFavorite(ch.streamUrl)
            else dao.addFavorite(FavoriteEntity(ch.streamUrl))
        }
    }

    fun refresh() {
        Graph.scope.launch { repo.sync() }
    }

    fun openSettings() {
        showSettings.value = true
    }

    fun closeSettings() {
        showSettings.value = false
    }

    fun save(
        mode: String,
        playlist: String,
        host: String,
        user: String,
        pass: String,
        format: String,
        epg: String,
    ) {
        settings.mode = mode
        settings.playlistUrl = if (playlist.isBlank()) "" else normalizeUrl(playlist)
        settings.xtreamHost = if (host.isBlank()) "" else normalizeUrl(host).trimEnd('/')
        settings.xtreamUser = user.trim()
        settings.xtreamPass = pass.trim()
        settings.streamFormat = format
        settings.epgUrl = if (epg.isBlank()) "" else normalizeUrl(epg)
        configured.value = settings.isConfigured
        showSettings.value = false
        selectedGroup.value = GROUP_ALL
        refresh()
    }

    suspend fun upcoming(tvgId: String): List<ProgrammeEntity> =
        dao.upcoming(tvgId, System.currentTimeMillis())

    private fun normalizeUrl(s: String): String {
        val t = s.trim()
        return if (t.contains("://")) t else "http://$t"
    }
}
