package com.example.data.repository

import com.example.data.database.Bookmark
import com.example.data.database.BookmarkDao
import com.example.data.database.DownloadDao
import com.example.data.database.DownloadJob
import com.example.data.database.ExtensionDao
import com.example.data.database.UserExtension
import kotlinx.coroutines.flow.Flow

class BrowserRepository(
    private val downloadDao: DownloadDao,
    private val bookmarkDao: BookmarkDao,
    private val extensionDao: ExtensionDao
) {
    // Downloads
    val allDownloads: Flow<List<DownloadJob>> = downloadDao.getAllDownloads()
    
    fun getDownloadFlowById(id: String): Flow<DownloadJob?> = downloadDao.getDownloadFlowById(id)
    
    suspend fun getDownloadById(id: String): DownloadJob? = downloadDao.getDownloadById(id)
    
    suspend fun insertDownload(job: DownloadJob) = downloadDao.insertDownload(job)
    
    suspend fun updateDownload(job: DownloadJob) = downloadDao.updateDownload(job)
    
    suspend fun updateDownloadProgress(
        id: String,
        downloadedSize: Long,
        status: String,
        speed: Long,
        active: Int,
        threadProgress: String,
        errorMessage: String? = null
    ) = downloadDao.updateDownloadProgress(id, downloadedSize, status, speed, active, threadProgress, errorMessage)

    suspend fun deleteDownloadById(id: String) = downloadDao.deleteDownloadById(id)

    // Bookmarks
    val allBookmarks: Flow<List<Bookmark>> = bookmarkDao.getAllBookmarks()
    
    suspend fun insertBookmark(bookmark: Bookmark) = bookmarkDao.insertBookmark(bookmark)
    
    suspend fun deleteBookmarkById(id: Long) = bookmarkDao.deleteBookmarkById(id)

    // Extensions
    val allExtensions: Flow<List<UserExtension>> = extensionDao.getAllExtensions()
    
    suspend fun getEnabledExtensionsSync(): List<UserExtension> = extensionDao.getEnabledExtensionsSync()
    
    suspend fun insertExtension(extension: UserExtension) = extensionDao.insertExtension(extension)
    
    suspend fun updateExtension(extension: UserExtension) = extensionDao.updateExtension(extension)
    
    suspend fun deleteExtensionById(id: String) = extensionDao.deleteExtensionById(id)
}
