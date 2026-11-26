package com.jc.player.data.db

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** Where the user stopped in each video, plus per-video choices. */
@Entity(tableName = "playback_state")
data class PlaybackStateEntity(
    @PrimaryKey val uri: String,
    val positionMs: Long,
    val durationMs: Long,
    val lastPlayed: Long,
    val title: String = "",
    val speed: Float = 1f,
    val audioTrack: String? = null,
    val subtitleTrack: String? = null,
    val externalSubtitles: String? = null, // newline separated uris
)

@Entity(tableName = "playlist")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val created: Long = System.currentTimeMillis(),
)

@Entity(tableName = "playlist_item")
data class PlaylistItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val playlistId: Long,
    val uri: String,
    val title: String,
    val sortOrder: Int,
)

/** A starred video or folder. [key] is the video uri or the folder path. */
@Entity(tableName = "favorite")
data class FavoriteEntity(
    @PrimaryKey val key: String,
    val type: String, // "video" or "folder"
    val title: String,
    val added: Long = System.currentTimeMillis(),
)

data class PlaylistWithCount(val id: Long, val name: String, val count: Int)

@Dao
interface PlaybackStateDao {
    @Query("SELECT * FROM playback_state WHERE uri = :uri")
    suspend fun get(uri: String): PlaybackStateEntity?

    @Query("SELECT * FROM playback_state")
    fun observeAll(): Flow<List<PlaybackStateEntity>>

    @Query("SELECT * FROM playback_state ORDER BY lastPlayed DESC LIMIT 1")
    suspend fun lastPlayed(): PlaybackStateEntity?

    @Upsert
    suspend fun upsert(state: PlaybackStateEntity)

    @Query("DELETE FROM playback_state WHERE uri = :uri")
    suspend fun delete(uri: String)

    @Query("DELETE FROM playback_state")
    suspend fun clear()
}

@Dao
interface PlaylistDao {
    @Query("SELECT p.id AS id, p.name AS name, (SELECT COUNT(*) FROM playlist_item i WHERE i.playlistId = p.id) AS count FROM playlist p ORDER BY p.created DESC")
    fun observePlaylists(): Flow<List<PlaylistWithCount>>

    @Query("SELECT * FROM playlist WHERE id = :id")
    suspend fun getPlaylist(id: Long): PlaylistEntity?

    @Query("SELECT * FROM playlist_item WHERE playlistId = :id ORDER BY sortOrder")
    fun observeItems(id: Long): Flow<List<PlaylistItemEntity>>

    @Query("SELECT * FROM playlist_item WHERE playlistId = :id ORDER BY sortOrder")
    suspend fun items(id: Long): List<PlaylistItemEntity>

    @Insert
    suspend fun insertPlaylist(p: PlaylistEntity): Long

    @Query("UPDATE playlist SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<PlaylistItemEntity>)

    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM playlist_item WHERE playlistId = :id")
    suspend fun maxOrder(id: Long): Int

    @Query("DELETE FROM playlist_item WHERE id = :itemId")
    suspend fun deleteItem(itemId: Long)

    @Query("DELETE FROM playlist_item WHERE playlistId = :id")
    suspend fun clearItems(id: Long)

    @Query("DELETE FROM playlist WHERE id = :id")
    suspend fun deletePlaylistRow(id: Long)

    suspend fun deletePlaylist(id: Long) {
        clearItems(id)
        deletePlaylistRow(id)
    }

    suspend fun addToPlaylist(id: Long, entries: List<Pair<String, String>>) {
        var order = maxOrder(id) + 1
        insertItems(entries.map { (uri, title) ->
            PlaylistItemEntity(playlistId = id, uri = uri, title = title, sortOrder = order++)
        })
    }
}

@Dao
interface FavoriteDao {
    @Query("SELECT * FROM favorite ORDER BY added DESC")
    fun observeAll(): Flow<List<FavoriteEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun add(f: FavoriteEntity)

    @Query("DELETE FROM favorite WHERE `key` = :key")
    suspend fun remove(key: String)

    @Query("SELECT COUNT(*) FROM favorite WHERE `key` = :key")
    suspend fun count(key: String): Int

    suspend fun toggle(key: String, type: String, title: String): Boolean =
        if (count(key) > 0) { remove(key); false } else { add(FavoriteEntity(key, type, title)); true }
}

@Database(
    entities = [PlaybackStateEntity::class, PlaylistEntity::class, PlaylistItemEntity::class, FavoriteEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun playbackState(): PlaybackStateDao
    abstract fun playlists(): PlaylistDao
    abstract fun favorites(): FavoriteDao

    companion object {
        /** v2 adds favourites; keeps existing watch history and playlists. */
        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `favorite` (`key` TEXT NOT NULL, `type` TEXT NOT NULL, `title` TEXT NOT NULL, `added` INTEGER NOT NULL, PRIMARY KEY(`key`))")
            }
        }

        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "jc.db")
                .addMigrations(MIGRATION_1_2)
                .fallbackToDestructiveMigration(true)
                .build()
    }
}
