package de.hxnk.iptv.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import de.hxnk.iptv.data.ProgrammeLite
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

val Bg = Color(0xFF0B0F14)
val Panel = Color(0xFF121821)
val Card = Color(0xFF18202B)
val Focus = Color(0xFF263649)
val Accent = Color(0xFF38BDF8)
val TextMain = Color(0xFFEAF0F7)
val TextDim = Color(0xFF8A99AD)
val ErrorRed = Color(0xFFF87171)
val LogoBg = Color(0xFF232D3B)

@Composable
fun HxTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Accent,
            onPrimary = Color.Black,
            background = Bg,
            surface = Panel,
            onSurface = TextMain,
            onBackground = TextMain,
        ),
        content = content,
    )
}

private val timeFmt = SimpleDateFormat("HH:mm", Locale.GERMANY)
private val dateTimeFmt = SimpleDateFormat("dd.MM. HH:mm", Locale.GERMANY)

fun fmtTime(ms: Long): String = synchronized(timeFmt) { timeFmt.format(Date(ms)) }
fun fmtDateTime(ms: Long): String = synchronized(dateTimeFmt) { dateTimeFmt.format(Date(ms)) }

/** Liefert (läuft gerade, kommt danach) aus einer nach Startzeit sortierten Liste. */
fun nowNext(list: List<ProgrammeLite>?, t: Long): Pair<ProgrammeLite?, ProgrammeLite?> {
    if (list.isNullOrEmpty()) return null to null
    val now = list.firstOrNull { it.start <= t && t < it.stop }
    val next = if (now != null) list.firstOrNull { it.start >= now.stop } else list.firstOrNull { it.start > t }
    return now to next
}
