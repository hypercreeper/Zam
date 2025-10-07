package com.moqayed.zam

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.moqayed.zam.MainActivity.Companion.AppDatabase
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

@Entity(tableName = "queue_items")
data class QueueItemEntity(
    @PrimaryKey val uri: String,
    val title: String?,
    val artist: String?,
    val artwork: ByteArray?,
    val enabled: Boolean
)

@Entity(tableName = "queue_state")
data class QueueStateEntity(
    @PrimaryKey val key: Int = 0,
    val index: Int,
    val positionMs: Long,
    val playlistName: String?,
)

// Type converters for Room
class Converters {
    private val gson = Gson()

    @TypeConverter
    fun fromList(list: List<String>): String = gson.toJson(list)

    @TypeConverter
    fun toList(data: String): List<String> {
        val type = object : TypeToken<List<String>>() {}.type
        return gson.fromJson(data, type)
    }
}

@Dao
interface QueueDao {
    @Query("SELECT * FROM queue_items")
    suspend fun getAll(): List<QueueItemEntity>


    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<QueueItemEntity>)


    @Query("DELETE FROM queue_items")
    suspend fun clearItems()


    @Query("SELECT * FROM queue_state WHERE `key` = 0")
    suspend fun getState(): QueueStateEntity?


    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertState(state: QueueStateEntity)
}

@Database(entities = [QueueStateEntity::class, QueueItemEntity::class], version = 2)
@TypeConverters(Converters::class)
abstract class QueueDatabase : RoomDatabase() {
    abstract fun queueDao(): QueueDao

    companion object {
        @Volatile private var INSTANCE: QueueDatabase? = null
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Add the new column with a default value
                database.execSQL(
                    "ALTER TABLE queue_items ADD COLUMN enabled INTEGER NOT NULL DEFAULT 1"
                )
                database.execSQL(
                    "ALTER TABLE queue_state ADD COLUMN playlistName TEXT NULL"
                )

            }
        }
        fun getDatabase(context: Context): QueueDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    QueueDatabase::class.java,
                    "last_played_database"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

// Small return object for quick reads
data class QueueState(
    val playlist: Playlist?,
    val index: Int,
    val positionMs: Long
)

/**
 * Room-backed QueueManager.
 * - in-memory cache for instant reads
 * - bulk metadata persistence when setting the queue
 * - limited parallel metadata extraction using your MainActivity.getAudioMetadataFFmpeg
 * - fast non-suspend getters that return cached data
 */
object QueueManager {
    private var currentPlaylist: Playlist? = null
    private var lastIndex: Int = 0
    private var lastPositionMs: Long = 0


    fun setQueue(context: Context, playlist: Playlist) {
// Step 1: Set memory queue immediately for ExoPlayer
        currentPlaylist = Playlist().apply {
            setName(playlist.getName().toString())
            mediaItems = playlist.mediaItems?.toMutableList()
        }


// Step 2: Persist in background thread
        CoroutineScope(Dispatchers.IO).launch {
            val db = QueueDatabase.getDatabase(context)
            val dao = db.queueDao()
            dao.clearItems()
            val items = currentPlaylist?.mediaItems?.map {
                QueueItemEntity(
                    uri = it.localConfiguration?.uri.toString(),
                    title = it.mediaMetadata.title?.toString(),
                    artist = it.mediaMetadata.artist?.toString(),
                    artwork = it.mediaMetadata.artworkData,
                    enabled = it.mediaMetadata.isPlayable?:true
                )
            } ?: emptyList()
            dao.insertAll(items)
            dao.insertState(QueueStateEntity(index = lastIndex, positionMs = lastPositionMs, playlistName = currentPlaylist?.getName()))
        }
    }


    fun getMemoryQueue(): Playlist? {
        return currentPlaylist
    }


    @OptIn(UnstableApi::class)
    suspend fun getPersistentQueue(context: Context): Triple<Playlist?, Int, Long> {
        val dao = QueueDatabase.getDatabase(context).queueDao()
        val items = dao.getAll()
        val state = dao.getState()


        val playlist = if (items.isNotEmpty()) {
            Playlist().apply {
                mediaItems = items.map {
                    MediaItem.Builder()
                        .setUri(Uri.parse(it.uri))
                        .setMediaMetadata(
                            MediaMetadata.Builder()
                                .setTitle(it.title)
                                .setArtist(it.artist)
                                .setArtworkData(it.artwork)
                                .setIsPlayable(it.enabled)
                                .build()
                        )
                        .build()
                }.toMutableList()
                setName(state?.playlistName?:"Unknown")
            }
        } else null


        return Triple(playlist, state?.index ?: 0, state?.positionMs ?: 0L)
    }


    fun savePlaybackState(context: Context, index: Int, positionMs: Long) {
        lastIndex = index
        lastPositionMs = positionMs


// Persist asynchronously
        CoroutineScope(Dispatchers.IO).launch {
            val dao = QueueDatabase.getDatabase(context).queueDao()
            dao.insertState(QueueStateEntity(index = lastIndex, positionMs = lastPositionMs, playlistName = currentPlaylist?.getName()))
        }
    }
}