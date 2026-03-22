package com.maxvale.dzvinaplayer.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Delete
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "favorite_locations")
data class FavoriteLocation(
    @PrimaryKey val path: String,
    val name: String,
    val addedAt: Long = System.currentTimeMillis()
)

@Dao
interface FavoriteDao {
    @Query("SELECT * FROM favorite_locations ORDER BY addedAt DESC")
    fun getAllFavorites(): Flow<List<FavoriteLocation>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavorite(favoriteLocation: FavoriteLocation)

    @Delete
    suspend fun deleteFavorite(favoriteLocation: FavoriteLocation)
    
    @Query("SELECT EXISTS(SELECT * FROM favorite_locations WHERE path = :path)")
    fun isFavorite(path: String): Flow<Boolean>
}

@Entity(tableName = "recent_videos")
data class RecentVideo(
    @PrimaryKey val path: String,
    val name: String,
    val lastPositionMs: Long,
    val durationMs: Long,
    val audioTrackIndex: Int = -1,
    val subtitleTrackIndex: Int = -1,
    val audioOffsetMs: Long = 0,
    val subtitleOffsetMs: Long = 0,
    val lastWatchedAt: Long = System.currentTimeMillis()
)

@Dao
interface RecentVideoDao {
    @Query("SELECT * FROM recent_videos ORDER BY lastWatchedAt DESC")
    fun getAllRecents(): Flow<List<RecentVideo>>

    @Query("SELECT * FROM recent_videos WHERE path = :path LIMIT 1")
    suspend fun getRecent(path: String): RecentVideo?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecent(recentVideo: RecentVideo)

    @Delete
    suspend fun deleteRecent(recentVideo: RecentVideo)
}

@Entity(tableName = "ftp_servers")
data class FtpServer(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val host: String,
    val port: Int = 21,
    val user: String = "anonymous",
    val pass: String = ""
)

@Dao
interface FtpServerDao {
    @Query("SELECT * FROM ftp_servers")
    fun getAllServers(): Flow<List<FtpServer>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertServer(server: FtpServer)

    @Delete
    suspend fun deleteServer(server: FtpServer)
}
