package de.hxnk.iptv.data

import android.content.Context

class Settings(ctx: Context) {
    private val sp = ctx.getSharedPreferences("hx_iptv", Context.MODE_PRIVATE)

    private fun str(key: String) = sp.getString(key, "") ?: ""
    private fun put(key: String, v: String) = sp.edit().putString(key, v).apply()

    /** "xtream" (Server + Login) oder "m3u" (komplette Playlist-URL). */
    var mode: String
        get() = sp.getString("mode", null) ?: "xtream"
        set(v) = put("mode", v)

    var playlistUrl: String
        get() = str("playlist")
        set(v) = put("playlist", v)

    var xtreamHost: String
        get() = str("xt_host")
        set(v) = put("xt_host", v)

    var xtreamUser: String
        get() = str("xt_user")
        set(v) = put("xt_user", v)

    var xtreamPass: String
        get() = str("xt_pass")
        set(v) = put("xt_pass", v)

    /** Stream-Endung bei Xtream: "ts" oder "m3u8" (HLS). */
    var streamFormat: String
        get() = sp.getString("xt_format", null) ?: "ts"
        set(v) = put("xt_format", v)

    var epgUrl: String
        get() = str("epg")
        set(v) = put("epg", v)

    var lastSync: Long
        get() = sp.getLong("last_sync", 0L)
        set(v) = sp.edit().putLong("last_sync", v).apply()

    val isConfigured: Boolean
        get() = if (mode == "xtream") {
            xtreamHost.isNotBlank() && xtreamUser.isNotBlank() && xtreamPass.isNotBlank()
        } else {
            playlistUrl.isNotBlank()
        }

    companion object {
        /** Viele Anbieter sperren unbekannte User-Agents, VLC wird fast immer akzeptiert. */
        const val UA = "VLC/3.0.20 LibVLC/3.0.20"
    }
}
