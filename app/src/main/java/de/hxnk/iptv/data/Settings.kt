package de.hxnk.iptv.data

import android.content.Context

class Settings(ctx: Context) {
    private val sp = ctx.getSharedPreferences("hx_iptv", Context.MODE_PRIVATE)

    var playlistUrl: String
        get() = sp.getString("playlist", "") ?: ""
        set(v) = sp.edit().putString("playlist", v).apply()

    var epgUrl: String
        get() = sp.getString("epg", "") ?: ""
        set(v) = sp.edit().putString("epg", v).apply()

    var lastSync: Long
        get() = sp.getLong("last_sync", 0L)
        set(v) = sp.edit().putLong("last_sync", v).apply()

    companion object {
        /** Viele Anbieter sperren unbekannte User-Agents, VLC wird fast immer akzeptiert. */
        const val UA = "VLC/3.0.20 LibVLC/3.0.20"
    }
}
