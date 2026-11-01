package de.hxnk.iptv.data

import java.io.BufferedReader

object M3uParser {
    data class Result(val channels: List<ChannelEntity>, val epgUrls: List<String>)

    private val ATTR = Regex("""([A-Za-z0-9_-]+)="([^"]*)"""")

    private fun attrs(line: String): Map<String, String> {
        val map = HashMap<String, String>()
        for (m in ATTR.findAll(line)) map[m.groupValues[1].lowercase()] = m.groupValues[2]
        return map
    }

    fun parse(reader: BufferedReader): Result {
        val list = ArrayList<ChannelEntity>()
        val epg = ArrayList<String>()

        var pending = false
        var name = ""
        var logo: String? = null
        var tvgId: String? = null
        var groupAttr: String? = null
        var extGroup: String? = null
        var index = 0

        while (true) {
            val raw = reader.readLine() ?: break
            val line = raw.trim().removePrefix("﻿")
            if (line.isEmpty()) continue

            when {
                line.startsWith("#EXTM3U", ignoreCase = true) -> {
                    val a = attrs(line)
                    val v = a["url-tvg"] ?: a["x-tvg-url"] ?: a["tvg-url"]
                    if (v != null) {
                        v.split(',').map { it.trim() }.filter { it.startsWith("http", true) }
                            .forEach { epg.add(it) }
                    }
                }

                line.startsWith("#EXTINF", ignoreCase = true) -> {
                    val a = attrs(line)
                    val lastQuote = line.lastIndexOf('"')
                    val comma = line.indexOf(',', if (lastQuote >= 0) lastQuote else 0)
                    val display = if (comma >= 0) line.substring(comma + 1).trim() else ""
                    name = display.ifEmpty { a["tvg-name"] ?: "Unbekannt" }
                    logo = a["tvg-logo"]?.trim()?.takeIf { it.startsWith("http", ignoreCase = true) }
                    tvgId = a["tvg-id"]?.trim()?.takeIf { it.isNotEmpty() }
                    groupAttr = a["group-title"]?.trim()?.takeIf { it.isNotEmpty() }
                    pending = true
                }

                line.startsWith("#EXTGRP:", ignoreCase = true) -> {
                    extGroup = line.substring(8).trim().takeIf { it.isNotEmpty() }
                }

                line.startsWith("#") -> Unit

                else -> {
                    if (pending && line.contains("://")) {
                        val group = groupAttr ?: extGroup ?: "Ohne Gruppe"
                        list.add(ChannelEntity(line, name, group, logo, tvgId, index++))
                        pending = false
                        extGroup = null
                    }
                }
            }
        }
        return Result(list, epg)
    }
}
