package com.example.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {
    @Query("SELECT * FROM download_jobs ORDER BY addedTime DESC")
    fun getAllDownloads(): Flow<List<DownloadJob>>

    @Query("SELECT * FROM download_jobs WHERE id = :id")
    suspend fun getDownloadById(id: String): DownloadJob?

    @Query("SELECT * FROM download_jobs WHERE id = :id")
    fun getDownloadFlowById(id: String): Flow<DownloadJob?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDownload(job: DownloadJob)

    @Update
    suspend fun updateDownload(job: DownloadJob)

    @Query("UPDATE download_jobs SET downloadedSize = :downloadedSize, status = :status, speedBytesPerSec = :speed, activeThreads = :active, threadProgress = :threadProgress, errorMessage = :errorMessage WHERE id = :id")
    suspend fun updateDownloadProgress(
        id: String,
        downloadedSize: Long,
        status: String,
        speed: Long,
        active: Int,
        threadProgress: String,
        errorMessage: String? = null
    )

    @Query("DELETE FROM download_jobs WHERE id = :id")
    suspend fun deleteDownloadById(id: String)
}

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmarks ORDER BY addedTime DESC")
    fun getAllBookmarks(): Flow<List<Bookmark>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookmark(bookmark: Bookmark)

    @Query("DELETE FROM bookmarks WHERE id = :id")
    suspend fun deleteBookmarkById(id: Long)
}

@Dao
interface ExtensionDao {
    @Query("SELECT * FROM user_extensions")
    fun getAllExtensions(): Flow<List<UserExtension>>

    @Query("SELECT * FROM user_extensions WHERE enabled = 1")
    suspend fun getEnabledExtensionsSync(): List<UserExtension>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExtension(extension: UserExtension)

    @Update
    suspend fun updateExtension(extension: UserExtension)

    @Query("DELETE FROM user_extensions WHERE id = :id")
    suspend fun deleteExtensionById(id: String)
}
