package com.example.data.download

import android.content.Context
import android.os.Environment
import android.util.Log
import com.example.data.database.DownloadJob
import com.example.data.repository.BrowserRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class DownloadManager(
    private val context: Context,
    private val repository: BrowserRepository
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val activeJobs = ConcurrentHashMap<String, Job>()
    private val jobProgressSpeed = ConcurrentHashMap<String, Long>() // Track speed bytes per sec
    
    // Tracks active real-time stats (speed and active thread counts)
    val activeSpeeds = MutableStateFlow<Map<String, Long>>(emptyMap())

    init {
        // Periodically calculate and reset download speed
        scope.launch {
            while (isActive) {
                delay(1000)
                val currentSpeeds = mutableMapOf<String, Long>()
                for ((id, bytesWrittenSinceLastSec) in jobProgressSpeed) {
                    currentSpeeds[id] = bytesWrittenSinceLastSec
                    jobProgressSpeed[id] = 0L // Reset
                }
                activeSpeeds.value = currentSpeeds
            }
        }
    }

    fun startDownload(url: String, fileName: String, threads: Int = 4) {
        val id = UUID.randomUUID().toString()
        val downloadsDir = File(context.filesDir, "Downloads")
        if (!downloadsDir.exists()) downloadsDir.mkdirs()
        
        val uniqueName = getUniqueFileName(downloadsDir, fileName)
        val file = File(downloadsDir, uniqueName)

        val job = DownloadJob(
            id = id,
            url = url,
            fileName = uniqueName,
            filePath = file.absolutePath,
            totalSize = -1,
            downloadedSize = 0,
            status = "PENDING",
            numThreads = threads,
            addedTime = System.currentTimeMillis()
        )

        scope.launch {
            repository.insertDownload(job)
            enqueueDownload(id)
        }
    }

    private fun getUniqueFileName(dir: File, name: String): String {
        val file = File(dir, name)
        if (!file.exists()) return name
        
        val dotIndex = name.lastIndexOf('.')
        val baseName = if (dotIndex != -1) name.substring(0, dotIndex) else name
        val extension = if (dotIndex != -1) name.substring(dotIndex) else ""
        
        var count = 1
        var newName = "$baseName-$count$extension"
        while (File(dir, newName).exists()) {
            count++
            newName = "$baseName-$count$extension"
        }
        return newName
    }

    fun resumeDownload(id: String) {
        scope.launch {
            val job = repository.getDownloadById(id) ?: return@launch
            if (job.status == "DOWNLOADING" || job.status == "COMPLETED") return@launch
            
            repository.updateDownload(job.copy(status = "PENDING", errorMessage = null))
            enqueueDownload(id)
        }
    }

    fun pauseDownload(id: String) {
        activeJobs[id]?.cancel()
        activeJobs.remove(id)
        jobProgressSpeed.remove(id)
        scope.launch {
            val job = repository.getDownloadById(id) ?: return@launch
            if (job.status == "DOWNLOADING" || job.status == "PENDING") {
                repository.updateDownload(job.copy(status = "PAUSED", speedBytesPerSec = 0, activeThreads = 0))
            }
        }
    }

    fun cancelDeleteDownload(id: String) {
        pauseDownload(id)
        scope.launch {
            val job = repository.getDownloadById(id) ?: return@launch
            try {
                val file = File(job.filePath)
                if (file.exists()) {
                    file.delete()
                }
            } catch (e: Exception) {
                Log.e("DownloadManager", "Error deleting file: ${e.message}")
            }
            repository.deleteDownloadById(id)
        }
    }

    private fun enqueueDownload(id: String) {
        val downloadCoroutine = scope.launch {
            try {
                executeDownload(id)
            } catch (e: CancellationException) {
                Log.e("DownloadManager", "Download canceled/paused: $id")
            } catch (e: Exception) {
                Log.e("DownloadManager", "Download failed for $id", e)
                val job = repository.getDownloadById(id)
                if (job != null) {
                    repository.updateDownload(
                        job.copy(
                            status = "FAILED",
                            errorMessage = e.localizedMessage ?: "Unknown network error",
                            activeThreads = 0,
                            speedBytesPerSec = 0
                        )
                    )
                }
            } finally {
                activeJobs.remove(id)
                jobProgressSpeed.remove(id)
            }
        }
        activeJobs[id] = downloadCoroutine
    }

    private suspend fun executeDownload(id: String) = withContext(Dispatchers.IO) {
        var job = repository.getDownloadById(id) ?: return@withContext
        repository.updateDownload(job.copy(status = "DOWNLOADING", activeThreads = job.numThreads))

        val urlConnection = URL(job.url).openConnection() as HttpURLConnection
        urlConnection.requestMethod = "GET"
        urlConnection.setRequestProperty("User-Agent", "Mozilla/5.0 (Android; Mobile)")
        urlConnection.connectTimeout = 10000
        urlConnection.readTimeout = 10000

        // Perform basic connect to intercept response code & size
        val responseCode = urlConnection.responseCode
        if (responseCode !in 200..299) {
            throw Exception("Server returned HTTP $responseCode")
        }

        val totalLength = urlConnection.contentLengthLong
        var acceptRanges = urlConnection.getHeaderField("Accept-Ranges")
        val isRangeSupported = acceptRanges != null && acceptRanges.lowercase().contains("bytes")

        urlConnection.disconnect()

        val updatedJob = job.copy(totalSize = totalLength)
        repository.updateDownload(updatedJob)
        job = updatedJob

        val targetFile = File(job.filePath)
        if (totalLength > 0) {
            val raf = RandomAccessFile(targetFile, "rw")
            raf.setLength(totalLength)
            raf.close()
        }

        if (isRangeSupported && totalLength > 0 && job.numThreads > 1) {
            // MULTI-THREADED DOWNLOAD
            Log.d("DownloadManager", "Starting Multi-Threaded Download: $id with ${job.numThreads} threads")
            runMultiThreadDownload(id, job, totalLength)
        } else {
            // SINGLE THREAD FALLBACK
            Log.d("DownloadManager", "Server does not support range requests or size unknown. Running Single Thread.")
            runSingleThreadDownload(id, job)
        }
    }

    private suspend fun runMultiThreadDownload(id: String, job: DownloadJob, totalSize: Long) = coroutineScope {
        val numThreads = job.numThreads
        val chunkSize = totalSize / numThreads
        
        // Parse parsed existing progress
        val savedProgress = try {
            if (job.threadProgress.isNotEmpty()) {
                job.threadProgress.split(",").map { it.toLong() }.toMutableList()
            } else {
                MutableList(numThreads) { 0L }
            }
        } catch (e: Exception) {
            MutableList(numThreads) { 0L }
        }

        if (savedProgress.size != numThreads) {
            savedProgress.clear()
            savedProgress.addAll(List(numThreads) { 0L })
        }

        // Shared total tracker updated on progress
        val totalProgressTracker = java.util.concurrent.atomic.AtomicLong(savedProgress.sum())

        val deferreds = (0 until numThreads).map { index ->
            async(Dispatchers.IO) {
                var chunkStart = index * chunkSize + savedProgress[index]
                val chunkEnd = if (index == numThreads - 1) totalSize - 1 else (index + 1) * chunkSize - 1
                
                if (chunkStart >= chunkEnd) {
                    return@async // This segment is fully done
                }

                var connection: HttpURLConnection? = null
                try {
                    val url = URL(job.url)
                    connection = url.openConnection() as HttpURLConnection
                    connection.connectTimeout = 8000
                    connection.readTimeout = 8000
                    connection.setRequestProperty("Range", "bytes=$chunkStart-$chunkEnd")
                    connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Android; Mobile)")
                    
                    val responseCode = connection.responseCode
                    if (responseCode != 206) {
                        // Segment failed or server doesn't honor chunk
                        throw Exception("Segment HTTP response is $responseCode, expected 206")
                    }

                    connection.inputStream.use { input ->
                        val fileAccess = RandomAccessFile(File(job.filePath), "rw")
                        fileAccess.seek(chunkStart)

                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        while (true) {
                            ensureActive() // Interrupted check (Pause)
                            
                            bytesRead = input.read(buffer)
                            if (bytesRead == -1) break
                            
                            fileAccess.write(buffer, 0, bytesRead)
                            chunkStart += bytesRead
                            
                            // Save chunk local tracker
                            savedProgress[index] += bytesRead
                            val updatedTotal = totalProgressTracker.addAndGet(bytesRead.toLong())
                            jobProgressSpeed[id] = (jobProgressSpeed[id] ?: 0L) + bytesRead

                            // Periodically flush progress (approx. every 256KB)
                            if (updatedTotal % (256 * 1024) < bytesRead) {
                                val speedSnapshot = activeSpeeds.value[id] ?: 0L
                                repository.updateDownloadProgress(
                                    id = id,
                                    downloadedSize = updatedTotal,
                                    status = "DOWNLOADING",
                                    speed = speedSnapshot,
                                    active = numThreads,
                                    threadProgress = savedProgress.joinToString(",")
                                )
                            }
                        }
                        fileAccess.close()
                    }
                } finally {
                    connection?.disconnect()
                }
            }
        }

        // Wait for all to finish
        try {
            deferreds.awaitAll()
            
            // Re-fetch database info to make sure we persist complete state
            val latestDownloaded = totalProgressTracker.get()
            val speedSnapshot = activeSpeeds.value[id] ?: 0L
            repository.updateDownloadProgress(
                id = id,
                downloadedSize = latestDownloaded,
                status = "COMPLETED",
                speed = 0,
                active = 0,
                threadProgress = savedProgress.joinToString(",")
            )
        } catch (e: Exception) {
            // Save state so we can resume
            if (e is CancellationException) {
                val latestDownloaded = totalProgressTracker.get()
                repository.updateDownloadProgress(
                    id = id,
                    downloadedSize = latestDownloaded,
                    status = "PAUSED",
                    speed = 0,
                    active = 0,
                    threadProgress = savedProgress.joinToString(",")
                )
                throw e
            } else {
                throw e
            }
        }
    }

    private suspend fun runSingleThreadDownload(id: String, job: DownloadJob) = withContext(Dispatchers.IO) {
        val targetFile = File(job.filePath)
        
        // Single thread always starting from current size for resume, if size is known and append is supported
        var downloaded = 0L
        val configConnection = URL(job.url).openConnection() as HttpURLConnection
        configConnection.connectTimeout = 8000
        configConnection.readTimeout = 8000
        configConnection.setRequestProperty("User-Agent", "Mozilla/5.0 (Android; Mobile)")

        if (targetFile.exists() && targetFile.length() > 0) {
            downloaded = targetFile.length()
            configConnection.setRequestProperty("Range", "bytes=$downloaded-")
        }

        val code = configConnection.responseCode
        val isAppend = code == 206
        
        configConnection.disconnect()

        val appendFile = isAppend && downloaded > 0
        var currentDownloaded = if (appendFile) downloaded else 0L

        val mainConnection = URL(job.url).openConnection() as HttpURLConnection
        mainConnection.connectTimeout = 10000
        mainConnection.readTimeout = 10000
        mainConnection.setRequestProperty("User-Agent", "Mozilla/5.0 (Android; Mobile)")
        
        if (appendFile) {
            mainConnection.setRequestProperty("Range", "bytes=$currentDownloaded-")
        }

        val connCode = mainConnection.responseCode
        if (connCode !in 200..299) {
            throw Exception("Sequential connection failed: $connCode")
        }

        mainConnection.inputStream.use { input ->
            val raf = RandomAccessFile(targetFile, "rw")
            if (appendFile) {
                raf.seek(currentDownloaded)
            } else {
                raf.seek(0)
            }

            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (true) {
                ensureActive() // Paused/Canceled check
                
                bytesRead = input.read(buffer)
                if (bytesRead == -1) break
                
                raf.write(buffer, 0, bytesRead)
                currentDownloaded += bytesRead
                jobProgressSpeed[id] = (jobProgressSpeed[id] ?: 0L) + bytesRead

                if (currentDownloaded % (128 * 1024) < bytesRead) {
                    val speedSnapshot = activeSpeeds.value[id] ?: 0L
                    repository.updateDownloadProgress(
                        id = id,
                        downloadedSize = currentDownloaded,
                        status = "DOWNLOADING",
                        speed = speedSnapshot,
                        active = 1,
                        threadProgress = "$currentDownloaded"
                    )
                }
            }
            raf.close()
        }
        mainConnection.disconnect()

        repository.updateDownloadProgress(
            id = id,
            downloadedSize = currentDownloaded,
            status = "COMPLETED",
            speed = 0,
            active = 0,
            threadProgress = "$currentDownloaded"
        )
    }
}
