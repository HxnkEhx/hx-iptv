package de.hxnk.iptv

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import de.hxnk.iptv.data.AppDb
import de.hxnk.iptv.data.Repository
import de.hxnk.iptv.data.Settings
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient

/** Einfacher Service-Locator, reicht für diese App völlig aus. */
object Graph {
    lateinit var settings: Settings
    lateinit var db: AppDb
    lateinit var http: OkHttpClient
    lateinit var repo: Repository

    /** Läuft unabhängig von Activity/ViewModel, damit Downloads nicht abbrechen. */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Synchronized
    fun init(ctx: Context) {
        if (::repo.isInitialized) return
        val app = ctx.applicationContext
        settings = Settings(app)
        db = Room.databaseBuilder(app, AppDb::class.java, "hxiptv.db")
            .fallbackToDestructiveMigration()
            .build()
        http = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .addInterceptor { chain ->
                val req = chain.request()
                chain.proceed(
                    if (req.header("User-Agent") == null) {
                        req.newBuilder().header("User-Agent", Settings.UA).build()
                    } else req
                )
            }
            .build()
        repo = Repository(app, db.dao(), settings, http)
    }
}

class IptvApp : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        Graph.init(this)

        val request = PeriodicWorkRequestBuilder<SyncWorker>(12, TimeUnit.HOURS)
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            )
            .build()
        WorkManager.getInstance(this)
            .enqueueUniquePeriodicWork("hx-sync", ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /** Logos werden einmal geladen und dauerhaft auf der Platte gecacht. */
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .okHttpClient(Graph.http)
            .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.15).build() }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("logos"))
                    .maxSizeBytes(300L * 1024 * 1024)
                    .build()
            }
            .respectCacheHeaders(false)
            .crossfade(false)
            .build()
}

class SyncWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        Graph.init(applicationContext)
        if (Graph.settings.playlistUrl.isBlank()) return Result.success()
        return if (Graph.repo.sync()) Result.success() else Result.retry()
    }
}
