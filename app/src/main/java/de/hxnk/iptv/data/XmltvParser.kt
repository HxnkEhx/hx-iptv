package de.hxnk.iptv.data

import android.util.Xml
import java.io.InputStream
import java.util.GregorianCalendar
import java.util.TimeZone
import org.xmlpull.v1.XmlPullParser

object XmltvParser {

    private fun newParser(input: InputStream): XmlPullParser {
        val p = Xml.newPullParser()
        p.setInput(input, null)
        return p
    }

    /**
     * Liest nur den Kanalkopf (id -> Anzeigenamen) und bricht beim ersten <programme> ab.
     * XMLTV legt alle <channel>-Einträge vor die Sendungen.
     */
    fun readChannels(input: InputStream): Map<String, List<String>> {
        val p = newParser(input)
        val map = LinkedHashMap<String, MutableList<String>>()
        var current: String? = null
        var ev = p.eventType
        loop@ while (ev != XmlPullParser.END_DOCUMENT) {
            if (ev == XmlPullParser.START_TAG) {
                when (p.name) {
                    "channel" -> {
                        current = p.getAttributeValue(null, "id")
                        val c = current
                        if (c != null) map.getOrPut(c) { mutableListOf() }
                    }

                    "display-name" -> {
                        val text = p.nextText().trim()
                        val c = current
                        if (c != null && text.isNotEmpty()) map[c]?.add(text)
                    }

                    "programme" -> break@loop
                }
            } else if (ev == XmlPullParser.END_TAG && p.name == "channel") {
                current = null
            }
            ev = p.next()
        }
        return map
    }

    /** Liefert alle Sendungen als Sequenz (streamend, kein komplettes Laden in den Speicher). */
    fun programmes(input: InputStream): Sequence<ProgrammeEntity> = sequence {
        val p = newParser(input)
        var channel: String? = null
        var start = 0L
        var stop = 0L
        var title: String? = null
        var desc: String? = null
        var inProg = false

        var ev = p.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            if (ev == XmlPullParser.START_TAG) {
                when (p.name) {
                    "programme" -> {
                        inProg = true
                        channel = p.getAttributeValue(null, "channel")
                        start = parseTime(p.getAttributeValue(null, "start"))
                        stop = parseTime(p.getAttributeValue(null, "stop"))
                        title = null
                        desc = null
                    }

                    "title" -> if (inProg) {
                        val t = p.nextText().trim()
                        if (title == null) title = t
                    }

                    "desc" -> if (inProg) {
                        val t = p.nextText().trim()
                        if (desc == null && t.isNotEmpty()) desc = t.take(600)
                    }
                }
            } else if (ev == XmlPullParser.END_TAG && p.name == "programme") {
                inProg = false
                val c = channel
                val t = title
                if (c != null && !t.isNullOrEmpty() && start > 0 && stop > start) {
                    yield(ProgrammeEntity(c, start, stop, t, desc))
                }
            }
            ev = p.next()
        }
    }

    private val cal = GregorianCalendar(TimeZone.getTimeZone("UTC"))

    /** Format: 20261101180000 +0100 (Zone optional, sonst UTC). */
    @Synchronized
    private fun parseTime(s: String?): Long {
        if (s == null || s.length < 14) return 0L
        return try {
            val y = s.substring(0, 4).toInt()
            val mo = s.substring(4, 6).toInt()
            val d = s.substring(6, 8).toInt()
            val h = s.substring(8, 10).toInt()
            val mi = s.substring(10, 12).toInt()
            val sec = s.substring(12, 14).toInt()
            cal.clear()
            cal.set(y, mo - 1, d, h, mi, sec)
            var t = cal.timeInMillis
            val rest = s.substring(14).trim()
            if (rest.length >= 5 && (rest[0] == '+' || rest[0] == '-')) {
                val hh = rest.substring(1, 3).toInt()
                val mm = rest.substring(3, 5).toInt()
                val off = (hh * 60L + mm) * 60_000L
                t -= if (rest[0] == '+') off else -off
            }
            t
        } catch (e: Exception) {
            0L
        }
    }
}
