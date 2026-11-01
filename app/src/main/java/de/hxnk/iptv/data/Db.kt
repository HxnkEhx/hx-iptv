package de.hxnk.iptv.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "channels", indices = [Index("groupName")])
data class ChannelEntity(
    @PrimaryKey val streamUrl: String,
    val name: String,
    val groupName: String,
    val logoUrl: String?,
    val tvgId: String?,
    val sortIndex: Int,
)

@Entity(tableName = "programmes", primaryKeys = ["channelId", "start"])
data class ProgrammeEntity(
    val channelId: String,
    val start: Long,
    val stop: Long,
    val title: String,
    val description: String?,
)

/** Schlanke Variante ohne Beschreibung für die Listenansicht. */
data class ProgrammeLite(
    val channelId: String,
    val start: Long,
    val stop: Long,
    val title: String,
)

@Entity(tableName = "favorites")
data class FavoriteEntity(@PrimaryKey val streamUrl: String)

@Dao
abstract class IptvDao {
    @Query("SELECT * FROM channels ORDER BY sortIndex")
    abstract fun channels(): Flow<List<ChannelEntity>>

    @Query("SELECT streamUrl FROM favorites")
    abstract fun favorites(): Flow<List<String>>

    @Query("SELECT channelId, start, stop, title FROM programmes WHERE stop > :from AND start < :until ORDER BY start")
    abstract suspend fun programmesBetween(from: Long, until: Long): List<ProgrammeLite>

    @Query("SELECT * FROM programmes WHERE channelId = :id AND stop > :from ORDER BY start LIMIT 40")
    abstract suspend fun upcoming(id: String, from: Long): List<ProgrammeEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertChannels(list: List<ChannelEntity>)

    @Query("DELETE FROM channels")
    abstract suspend fun clearChannels()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertProgrammes(list: List<ProgrammeEntity>)

    @Query("DELETE FROM programmes")
    abstract suspend fun clearProgrammes()

    @Query("UPDATE channels SET tvgId = :tvgId WHERE streamUrl = :url")
    abstract suspend fun setTvgId(url: String, tvgId: String)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun addFavorite(f: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE streamUrl = :url")
    abstract suspend fun removeFavorite(url: String)

    @Transaction
    open suspend fun replaceChannels(list: List<ChannelEntity>) {
        clearChannels()
        insertChannels(list)
    }

    @Transaction
    open suspend fun setTvgIds(map: Map<String, String>) {
        for ((url, id) in map) setTvgId(url, id)
    }
}

@Database(
    entities = [ChannelEntity::class, ProgrammeEntity::class, FavoriteEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDb : RoomDatabase() {
    abstract fun dao(): IptvDao
}
