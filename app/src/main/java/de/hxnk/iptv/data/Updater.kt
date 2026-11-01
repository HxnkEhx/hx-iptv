package de.hxnk.iptv.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import de.hxnk.iptv.Graph
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject

/**
 * Prüft das neueste GitHub-Release, lädt die APK und startet die Installation.
 * Release-Tag = "v<Build-Nummer>", diese Nummer ist auch der versionCode der APK.
 */
object Updater {
    private const val REPO = "HxnkEhx/hx-iptv"
    private const val API = "https://api.github.com/repos/$REPO/releases/latest"

    data class Info(val versionCode: Long, val name: String, val apkUrl: String, val sizeBytes: Long)

    fun installedVersionCode(ctx: Context): Long {
        val info = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
        return PackageInfoCompat.getLongVersionCode(info)
    }

    fun installedVersionName(ctx: Context): String =
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "?"

    /** Gibt Info zurück, wenn ein neueres Release existiert, sonst null. Wirft bei Netzwerkfehlern. */
    suspend fun check(ctx: Context): Info? = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url(API)
            .header("Accept", "application/vnd.github+json")
            .build()
        Graph.http.newCall(req).execute().use { r ->
            if (r.code == 404) return@withContext null // noch kein Release vorhanden
            if (!r.isSuccessful) throw IOException("GitHub antwortet mit HTTP ${r.code}")
            val json = JSONObject(r.body!!.string())
            val tag = json.optString("tag_name")
            val remote = tag.removePrefix("v").toLongOrNull() ?: return@withContext null
            if (remote <= installedVersionCode(ctx)) return@withContext null

            val assets = json.optJSONArray("assets") ?: return@withContext null
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (a.optString("name").endsWith(".apk")) {
                    return@withContext Info(
                        remote,
                        json.optString("name", tag),
                        a.getString("browser_download_url"),
                        a.optLong("size", 0L),
                    )
                }
            }
            null
        }
    }

    suspend fun download(ctx: Context, info: Info, onProgress: (Int) -> Unit): File =
        withContext(Dispatchers.IO) {
            val dir = File(ctx.cacheDir, "updates").apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() }
            val out = File(dir, "hx-iptv.apk")
            val req = Request.Builder().url(info.apkUrl).build()
            Graph.http.newCall(req).execute().use { r ->
                if (!r.isSuccessful) throw IOException("Download: HTTP ${r.code}")
                val body = r.body!!
                val total = if (body.contentLength() > 0) body.contentLength() else info.sizeBytes
                var done = 0L
                var lastPct = -1
                body.byteStream().use { input ->
                    out.outputStream().use { output ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            output.write(buf, 0, n)
                            done += n
                            if (total > 0) {
                                val pct = (done * 100 / total).toInt().coerceIn(0, 100)
                                if (pct != lastPct) {
                                    lastPct = pct
                                    onProgress(pct)
                                }
                            }
                        }
                    }
                }
            }
            out
        }

    /** true, wenn die App Installationen starten darf; sonst wird der Einstellungs-Dialog geöffnet. */
    fun ensureInstallPermission(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return true
        if (ctx.packageManager.canRequestPackageInstalls()) return true
        val intent = Intent(
            AndroidSettings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${ctx.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(intent)
        return false
    }

    fun install(ctx: Context, apk: File) {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", apk)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(intent)
    }
}
