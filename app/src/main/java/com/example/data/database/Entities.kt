package com.example.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "download_jobs")
data class DownloadJob(
    @PrimaryKey val id: String, // Typically a UUID or URL hash
    val url: String,
    val fileName: String,
    val filePath: String,
    val totalSize: Long,
    val downloadedSize: Long,
    val status: String, // "PENDING", "DOWNLOADING", "PAUSED", "COMPLETED", "FAILED"
    val numThreads: Int = 4,
    val addedTime: Long = System.currentTimeMillis(),
    val errorMessage: String? = null,
    val speedBytesPerSec: Long = 0,
    val activeThreads: Int = 0,
    // Serialized progress of each thread (e.g. "100,500,200") to allow resume
    val threadProgress: String = ""
)

@Entity(tableName = "bookmarks")
data class Bookmark(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val url: String,
    val addedTime: Long = System.currentTimeMillis()
)

@Entity(tableName = "user_extensions")
data class UserExtension(
    @PrimaryKey val id: String,
    val name: String,
    val description: String,
    val version: String,
    val enabled: Boolean = true,
    val script: String, // The Javascript content script
    val manifestJson: String = "{}",
    val isBuiltIn: Boolean = false
)
