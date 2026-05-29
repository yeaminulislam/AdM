package com.example.ui

import android.app.Application
import android.util.Log
import android.webkit.MimeTypeMap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.database.AppDatabase
import com.example.data.database.Bookmark
import com.example.data.database.DownloadJob
import com.example.data.database.UserExtension
import com.example.data.download.DownloadManager
import com.example.data.repository.BrowserRepository
import com.example.BuildConfig
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URL
import java.util.UUID

enum class AppTheme {
    HIGH_DENSITY,   // Light Material 3 High Density style
    ONYX_MIDNIGHT,  // Amoled black + cyan neon
    COSMIC_INDIGO,   // Deep purple + pink accent
    FOREST_MINT,     // Charcoal green + mint
    CLASSIC_OCEAN    // Navy + blue
}

data class DetectedMedia(
    val id: String,
    val url: String,
    val fileName: String,
    val mimeType: String,
    val description: String = "",
    val quality: String = "",
    val sizeEstimate: Long = -1L,
    val detectedTime: Long = System.currentTimeMillis()
)

data class GeminiAppAction(
    val type: String, // "open_url", "search", "toggle_adblock", "toggle_popups", "set_theme", "add_bookmark", "clear_grabber"
    val argument: String,
    val title: String,
    var executed: Boolean = false
)

data class ChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val proposedActions: List<GeminiAppAction> = emptyList()
)

class BrowserViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application)
    val repository = BrowserRepository(
        database.downloadDao(),
        database.bookmarkDao(),
        database.extensionDao()
    )
    val downloadManager = DownloadManager(application, repository)

    // Browsing States
    val currentUrl = MutableStateFlow("https://www.google.com")
    val searchInput = MutableStateFlow("https://www.google.com")
    val loadUrlEvent = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val currentPageTitle = MutableStateFlow("Google")
    val currentPageDescription = MutableStateFlow("")
    val importSuccessEvent = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val importErrorEvent = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val toastEvent = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val loadingProgress = MutableStateFlow(0)
    val isLoading = MutableStateFlow(false)
    val canGoBack = MutableStateFlow(false)
    val canGoForward = MutableStateFlow(false)

    // Selected App Tab (0 = Browser, 1 = Media Grabber, 2 = Downloads, 3 = Extensions/Settings)
    val currentTab = MutableStateFlow(0)

    // Gemini Chat States
    val chatHistory = MutableStateFlow<List<ChatMessage>>(listOf(
        ChatMessage(
            text = "Welcome to Apex Browser AI Page. Powered by Gemini 3.5 Flash.\n\nI am your companion, specialized in web navigation and system automation. Ask me anything, or instruct me to control the browser!\n\nFounder & Core Developer: Jack (SK Jack). Node: APEX-900456.",
            isUser = false
        )
    ))
    val isChatLoading = MutableStateFlow(false)

    // Theme & Downloader Preferences
    val selectedAppTheme = MutableStateFlow(AppTheme.HIGH_DENSITY)
    val defaultThreadsCount = MutableStateFlow(4)
    val blockAdsEnabled = MutableStateFlow(true)
    val blockPopupsEnabled = MutableStateFlow(true)
    val userAgentType = MutableStateFlow("Mobile") // "Mobile", "Desktop", "Safari"

    // Detected Media List
    private val _detectedMedia = MutableStateFlow<List<DetectedMedia>>(emptyList())
    val detectedMedia: StateFlow<List<DetectedMedia>> = _detectedMedia.asStateFlow()

    // Room Flows
    val allDownloads: StateFlow<List<DownloadJob>> = repository.allDownloads
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allBookmarks: StateFlow<List<Bookmark>> = repository.allBookmarks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allExtensions: StateFlow<List<UserExtension>> = repository.allExtensions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Active speed flows
    val activeSpeeds = downloadManager.activeSpeeds

    init {
        // Pre-configure or verify dependencies
        viewModelScope.launch {
            repository.allBookmarks.first() // Trigger DB compile & seed callback
            overwriteBuiltInExtensions()
        }
    }

    private suspend fun overwriteBuiltInExtensions() {
        val mediaSniffer = UserExtension(
            id = "media-sniffer",
            name = "Apex Video Grabber",
            description = "Extracts dynamically embedded MP4 or MP3 sources from dynamic video players and logs files directly for download.",
            version = "1.1.0",
            enabled = true,
            isBuiltIn = true,
            script = """
                (function() {
                    console.log("Media Sniffer Extension Active!");
                    setInterval(function() {
                        const mediaFiles = [];
                        document.querySelectorAll('video').forEach(el => {
                            var src = el.src || el.currentSrc;
                            if (src) {
                                var q = "";
                                if (el.videoHeight) {
                                    q = el.videoHeight + "p (" + el.videoWidth + "x" + el.videoHeight + ")";
                                }
                                mediaFiles.push({ url: src, quality: q });
                            }
                        });
                        document.querySelectorAll('audio, source').forEach(el => {
                            var src = el.src || el.currentSrc;
                            if (src && !mediaFiles.some(m => m.url === src)) {
                                mediaFiles.push({ url: src, quality: "" });
                            }
                        });
                        if (mediaFiles.length > 0) {
                            if (window.ApexNative && window.ApexNative.onVideosDetectedWithMeta) {
                                window.ApexNative.onVideosDetectedWithMeta(JSON.stringify(mediaFiles));
                            } else if (window.ApexNative) {
                                window.ApexNative.onVideosDetected(JSON.stringify(mediaFiles.map(m => m.url)));
                            }
                        }
                    }, 3000);
                })();
            """.trimIndent()
        )
        repository.insertExtension(mediaSniffer)
    }

    fun loadUrl(url: String) {
        currentUrl.value = url
        searchInput.value = url
        loadUrlEvent.tryEmit(url)
    }

    // Browsing Actions
    fun handleSearch(query: String) {
        val trimmed = query.trim()
        val destinationUrl = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            trimmed
        } else if (trimmed.contains(".") && !trimmed.contains(" ")) {
            "https://$trimmed"
        } else {
            "https://www.google.com/search?q=${java.net.URLEncoder.encode(trimmed, "UTF-8")}"
        }
        loadUrl(destinationUrl)
    }

    // Extension Management
    fun toggleExtension(extensionId: String, enabled: Boolean) {
        viewModelScope.launch {
            val list = repository.allExtensions.first()
            val ext = list.find { it.id == extensionId } ?: return@launch
            repository.updateExtension(ext.copy(enabled = enabled))
        }
    }

    fun addCustomExtension(name: String, desc: String, script: String) {
        if (name.isEmpty() || script.isEmpty()) return
        val ext = UserExtension(
            id = "custom_" + UUID.randomUUID().toString().take(6),
            name = name,
            description = desc,
            version = "1.0.0",
            enabled = true,
            script = script,
            isBuiltIn = false
        )
        viewModelScope.launch {
            repository.insertExtension(ext)
        }
    }

    fun deleteExtension(extensionId: String) {
        viewModelScope.launch {
            repository.deleteExtensionById(extensionId)
        }
    }

    // Bookmarks
    fun addBookmark(title: String, url: String) {
        if (url.isEmpty()) return
        val finalTitle = title.ifEmpty { "Webpage" }
        viewModelScope.launch {
            repository.insertBookmark(Bookmark(title = finalTitle, url = url))
        }
    }

    fun removeBookmark(id: Long) {
        viewModelScope.launch {
            repository.deleteBookmarkById(id)
        }
    }

    fun sniffMediaUrlWithMeta(url: String, detectedQuality: String) {
        sniffMediaUrl(url, forcedQuality = detectedQuality)
    }

    // Media & Document Sniffer Operations
    fun sniffMediaUrl(url: String, forcedQuality: String? = null) {
        val path = url.lowercase()
        // Explicitly filter out local browser blob pointers to prevent File Protocol openConnection failures!
        if (path.startsWith("blob:")) return
        
        val urlExt = MimeTypeMap.getFileExtensionFromUrl(url)?.lowercase() ?: ""
        
        val extensionsList = listOf(
            // Media
            "mp4", "mp3", "m4a", "webm", "mkv", "avi", "mov", "flv", "3gp", "ts", "ogg", "wav", "aac", "m3u8",
            // Archives
            "zip", "rar", "7z", "tar", "gz", "bz2", "xz", "iso", "cab", "dmg", "pkg",
            // Documents
            "pdf", "docx", "xlsx", "pptx", "txt", "rtf", "epub", "mobi", "csv", "xml", "json",
            // Installers & Scripts
            "apk", "exe", "msi", "deb", "rpm", "bin", "sh", "bat",
            // Images
            "png", "jpg", "jpeg", "gif", "webp", "svg", "ico"
        )
        
        val hasMatchingExt = extensionsList.any { ext -> path.contains(".$ext") || urlExt == ext }
        val isMediaKeywords = path.contains("videoplayback") || path.contains("googlevideo") ||
                path.contains("mime=video") || path.contains("mime=audio") || path.contains("download_file")
                
        val isDownloadable = hasMatchingExt || isMediaKeywords

        if (isDownloadable) {
            val titleText = currentPageTitle.value
            val isYouTubeValue = url.contains("videoplayback") || url.contains("googlevideo")
            
            val qualityVal = if (!forcedQuality.isNullOrEmpty()) {
                forcedQuality
            } else if (isYouTubeValue) {
                getYouTubeQuality(url)
            } else {
                getGeneralQuality(url)
            }
            
            val isAudioOnly = url.contains("mime=audio") || path.contains(".mp3") || path.contains(".aac") || qualityVal.contains("Audio-Only") || qualityVal.contains("Audio")
            
            // Extract genuine extension, default to mp4 if video, mp3 if audio
            var targetExt = urlExt
            if (targetExt.isEmpty()) {
                val found = extensionsList.find { ext -> path.contains(".$ext") }
                targetExt = found ?: (if (isAudioOnly) "mp3" else "mp4")
            }
            
            // Clean title for file name
            val cleanTitle = titleText.replace(Regex("[^a-zA-Z0-9_\\-\\s]"), "")
                .trim()
                .replace("\\s+".toRegex(), "_")
                .ifEmpty { "grabbed_file_" + System.currentTimeMillis().toString().takeLast(6) }
                
            val finalName = if (isYouTubeValue) {
                val qTag = if (qualityVal.isNotEmpty()) "_${qualityVal.replace(" ", "_")}" else ""
                "YouTube_${cleanTitle}${qTag}.$targetExt"
            } else {
                val qTag = if (qualityVal.isNotEmpty()) "_${qualityVal.replace(" ", "_")}" else ""
                "${cleanTitle}${qTag}.$targetExt"
            }
            
            val pageMetaDesc = currentPageDescription.value
            val finalDesc = if (pageMetaDesc.isNotEmpty()) {
                pageMetaDesc
            } else {
                "Captured from page: \"$titleText\""
            }
            
            val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(targetExt) ?: when (targetExt) {
                "mp3" -> "audio/mp3"
                "mp4" -> "video/mp4"
                "apk" -> "application/vnd.android.package-archive"
                "zip" -> "application/zip"
                "pdf" -> "application/pdf"
                else -> if (isAudioOnly) "audio/$targetExt" else "video/$targetExt"
            }
            
            addDetectedMedia(url, finalName, mimeType, description = finalDesc, quality = if (qualityVal == "Standard Stream" && targetExt != "mp4" && targetExt != "mp3") targetExt.uppercase() else qualityVal)
        }
    }

    fun getYouTubeQuality(url: String): String {
        try {
            val uri = android.net.Uri.parse(url)
            val itag = uri.getQueryParameter("itag") ?: return "Standard stream"
            return when (itag) {
                "18" -> "360p (MP4)"
                "22" -> "720p HD (MP4)"
                "37" -> "1080p FHD (MP4)"
                "38" -> "4K UHD (MP4)"
                "133" -> "240p (Video)"
                "134" -> "360p (Video)"
                "135" -> "480p (Video)"
                "136" -> "720p HD (Video)"
                "137" -> "1080p FHD (Video)"
                "160" -> "144p (Video)"
                "298" -> "720p60 HD (Video)"
                "299" -> "1080p60 FHD (Video)"
                "264" -> "1440p 2K (Video)"
                "266" -> "2160p 4K (Video)"
                "139" -> "48kbps Low (Audio)"
                "140" -> "128kbps Medium (Audio)"
                "251" -> "160kbps High (Audio)"
                else -> "YouTube Stream (itag $itag)"
            }
        } catch (e: Exception) {
            return "YouTube Stream"
        }
    }
    
    fun getGeneralQuality(url: String): String {
        val path = url.lowercase()
        return when {
            path.contains("1080p") || path.contains("1080") -> "1080p FHD"
            path.contains("720p") || path.contains("720") -> "720p HD"
            path.contains("480p") || path.contains("480") -> "480p SD"
            path.contains("360p") || path.contains("360") -> "360p SD"
            path.contains("2160p") || path.contains("4k") -> "4K UHD"
            path.contains("1440p") || path.contains("2k") -> "2K QHD"
            path.contains("high") || path.contains("hq") -> "High Quality"
            path.contains("low") || path.contains("lq") -> "Low Quality"
            else -> "Standard Stream"
        }
    }

    fun addDetectedMedia(url: String, name: String, mimeType: String, description: String = "", quality: String = "") {
        val currentList = _detectedMedia.value
        if (currentList.any { it.url == url }) return // Stop duplicates
        
        val newMedia = DetectedMedia(
            id = UUID.randomUUID().toString(),
            url = url,
            fileName = name,
            mimeType = mimeType,
            description = description.ifEmpty { "Manually detected source web link" },
            quality = quality.ifEmpty { getGeneralQuality(url) },
            detectedTime = System.currentTimeMillis()
        )
        _detectedMedia.value = (listOf(newMedia) + currentList).take(30) // Limit to 30 items
    }

    fun clearDetectedMedia() {
        _detectedMedia.value = emptyList()
    }

    private fun getFileNameFromUrl(url: String): String {
        try {
            val parsedUrl = URL(url)
            val path = parsedUrl.path
            val lastSegment = path.substring(path.lastIndexOf('/') + 1)
            if (lastSegment.isNotEmpty() && lastSegment.contains(".")) {
                return lastSegment
            }
        } catch (e: Exception) {
            // Fallback
        }
        val extension = MimeTypeMap.getFileExtensionFromUrl(url)
        val extStr = if (extension.isNotEmpty()) ".$extension" else ".mp4"
        return "grabbed_media_" + System.currentTimeMillis().toString().takeLast(6) + extStr
    }

    private fun getMimeTypeFromUrl(url: String): String {
        val ext = MimeTypeMap.getFileExtensionFromUrl(url)
        if (ext.isNotEmpty()) {
            val type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext.lowercase())
            if (type != null) return type
        }
        return if (url.contains(".mp3")) "audio/mp3" else "video/mp4"
    }

    // Import loaded Firefox/Chrome WebExtension ZIP file
    fun importExtensionFromZip(inputStream: java.io.InputStream) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                var manifestJsonStr = ""
                val jsScripts = java.util.HashMap<String, String>()
                val cssStyles = java.util.HashMap<String, String>()

                // Read ZIP contents
                val zipIn = java.util.zip.ZipInputStream(inputStream)
                var entry = zipIn.nextEntry
                while (entry != null) {
                    val name = entry.name
                    if (!entry.isDirectory) {
                        if (name.endsWith("manifest.json", ignoreCase = true)) {
                            manifestJsonStr = readZipEntryText(zipIn)
                        } else if (name.endsWith(".js", ignoreCase = true)) {
                            val content = readZipEntryText(zipIn)
                            jsScripts[name] = content
                        } else if (name.endsWith(".css", ignoreCase = true)) {
                            val content = readZipEntryText(zipIn)
                            cssStyles[name] = content
                        }
                    }
                    zipIn.closeEntry()
                    entry = zipIn.nextEntry
                }
                zipIn.close()

                if (manifestJsonStr.isEmpty()) {
                    throw Exception("Could not find manifest.json inside the extension zip packet.")
                }

                // Parse manifest details
                val manifestJson = org.json.JSONObject(manifestJsonStr)
                val extName = manifestJson.optString("name", "Imported Extension")
                val extDesc = manifestJson.optString("description", "Imported WebExtension browser overlay")
                val extVersion = manifestJson.optString("version", "1.0.0")

                // Re-build standard content-script runner
                val scriptBuilder = java.lang.StringBuilder()
                scriptBuilder.append("/* Imported extension content script: $extName */\n")

                // Handle sub stylesheets (CSS mapping)
                if (cssStyles.isNotEmpty()) {
                    scriptBuilder.append("(function() {\n")
                    scriptBuilder.append("  const style = document.createElement('style');\n")
                    scriptBuilder.append("  style.innerHTML = `")
                    for ((cssPath, cssValue) in cssStyles) {
                        val cleanCss = cssValue.replace("\\", "\\\\").replace("`", "\\`").replace("$", "\\$")
                        scriptBuilder.append("/* Css Source: $cssPath */\n")
                        scriptBuilder.append(cleanCss)
                        scriptBuilder.append("\n")
                    }
                    scriptBuilder.append("`;\n")
                    scriptBuilder.append("  document.head.appendChild(style);\n")
                    scriptBuilder.append("})();\n")
                }

                // Parse JS scripts inside extension content scripts rules
                val contentScriptsArray = manifestJson.optJSONArray("content_scripts")
                val addedScripts = java.util.HashSet<String>()
                if (contentScriptsArray != null) {
                    for (i in 0 until contentScriptsArray.length()) {
                        val scriptObj = contentScriptsArray.getJSONObject(i)
                        val jsArray = scriptObj.optJSONArray("js")
                        if (jsArray != null) {
                            for (j in 0 until jsArray.length()) {
                                val jsPath = jsArray.getString(j)
                                val normalizedPath = jsPath.removePrefix("./").removePrefix("/")
                                addedScripts.add(normalizedPath)
                                val fileContent = jsScripts[normalizedPath] ?: jsScripts.entries.firstOrNull { it.key.endsWith(normalizedPath) }?.value
                                if (fileContent != null) {
                                    scriptBuilder.append("(function() {\n")
                                    scriptBuilder.append("/* Injected Script: $jsPath */\n")
                                    scriptBuilder.append(fileContent)
                                    scriptBuilder.append("\n})();\n")
                                }
                            }
                        }
                    }
                }

                // Fallback: If no files registered explicitly as content scripts, inject all JS modules in zip root for safety
                if (addedScripts.isEmpty()) {
                    for ((jsPath, jsValue) in jsScripts) {
                        if (!jsPath.contains("/") || jsPath.lowercase().contains("content")) {
                            scriptBuilder.append("(function() {\n")
                            scriptBuilder.append("/* File Injected: $jsPath */\n")
                            scriptBuilder.append(jsValue)
                            scriptBuilder.append("\n})();\n")
                        }
                    }
                }

                val finalScript = scriptBuilder.toString()
                val finalId = "imported-" + UUID.randomUUID().toString().take(8)

                repository.insertExtension(
                    UserExtension(
                        id = finalId,
                        name = extName,
                        description = extDesc,
                        version = extVersion,
                        enabled = true,
                        script = finalScript,
                        manifestJson = manifestJsonStr,
                        isBuiltIn = false
                    )
                )

                importSuccessEvent.emit("Successfully imported and overlayed: $extName v$extVersion")
            } catch (e: Exception) {
                e.printStackTrace()
                importErrorEvent.emit("Import Failed: " + (e.localizedMessage ?: "Unknown zip structure"))
            }
        }
    }

    // Initiates download immediately
    fun startGrabbedDownload(url: String, name: String, threads: Int? = null) {
        val threadCount = threads ?: defaultThreadsCount.value
        downloadManager.startDownload(url, name, threadCount)
    }

    // Checking if an ad URL should be blocked
    fun shouldBlockUrl(url: String): Boolean {
        if (!blockAdsEnabled.value) return false
        val domain = try {
            URL(url).host?.lowercase() ?: ""
        } catch (e: Exception) {
            url.lowercase()
        }
        
        val adKeywords = listOf(
            "doubleclick", "googlead", "googlesyndication", "adservice", "popads",
            "adsystem", "fbcdn.net/pagead", "scorecardresearch", "adnxs", "optimizely",
            "analytics", "craigspop", "yandex.ru/clck", "adcolony", "unityads", "mopub"
        )
        for (kw in adKeywords) {
            if (domain.contains(kw)) {
                Log.d("AdBlocker", "Natively blocked ad request: $url")
                return true
            }
        }
        return false
    }

    fun getDeviceUserAgent(): String {
        return when (userAgentType.value) {
            "Desktop" -> "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
            "Safari" -> "Mozilla/5.0 (iPhone; CPU iPhone OS 17_1 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.1 Mobile/15E148 Safari/604.1"
            else -> "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/116.0.0.0 Mobile Safari/537.36"
        }
    }

    private fun readZipEntryText(zipIn: java.util.zip.ZipInputStream): String {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(4096)
        var length = zipIn.read(buffer)
        while (length != -1) {
            output.write(buffer, 0, length)
            length = zipIn.read(buffer)
        }
        return output.toString("UTF-8")
    }

    fun saveBlobDownload(fileName: String, base64Data: String, url: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val id = UUID.randomUUID().toString()
            val downloadsDir = java.io.File(getApplication<Application>().filesDir, "Downloads")
            if (!downloadsDir.exists()) downloadsDir.mkdirs()
            
            val uniqueName = getUniqueFileName(downloadsDir, fileName)
            val file = java.io.File(downloadsDir, uniqueName)
            
            try {
                val bytes = android.util.Base64.decode(base64Data, android.util.Base64.DEFAULT)
                file.writeBytes(bytes)
                
                val job = DownloadJob(
                    id = id,
                    url = url,
                    fileName = uniqueName,
                    filePath = file.absolutePath,
                    totalSize = bytes.size.toLong(),
                    downloadedSize = bytes.size.toLong(),
                    status = "COMPLETED",
                    numThreads = 1,
                    addedTime = System.currentTimeMillis()
                )
                repository.insertDownload(job)
                toastEvent.tryEmit("Download completed: $uniqueName")
            } catch (e: Exception) {
                Log.e("BrowserViewModel", "Blob decode/save failed", e)
                val job = DownloadJob(
                    id = id,
                    url = url,
                    fileName = uniqueName,
                    filePath = file.absolutePath,
                    totalSize = -1,
                    downloadedSize = 0,
                    status = "FAILED",
                    numThreads = 1,
                    addedTime = System.currentTimeMillis(),
                    errorMessage = e.localizedMessage ?: "Conversion error"
                )
                repository.insertDownload(job)
                toastEvent.tryEmit("Failed to decode blob: ${e.localizedMessage}")
            }
        }
    }
    
    fun reportBlobFailed(fileName: String, errorMsg: String, url: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val id = UUID.randomUUID().toString()
            val downloadsDir = java.io.File(getApplication<Application>().filesDir, "Downloads")
            val file = java.io.File(downloadsDir, fileName)
            val job = DownloadJob(
                id = id,
                url = url,
                fileName = fileName,
                filePath = file.absolutePath,
                totalSize = -1,
                downloadedSize = 0,
                status = "FAILED",
                numThreads = 1,
                addedTime = System.currentTimeMillis(),
                errorMessage = errorMsg
            )
            repository.insertDownload(job)
            toastEvent.tryEmit("Blob download failed: $errorMsg")
        }
    }

    private fun getUniqueFileName(dir: java.io.File, name: String): String {
        val file = java.io.File(dir, name)
        if (!file.exists()) return name
        
        val dotIndex = name.lastIndexOf('.')
        val baseName = if (dotIndex != -1) name.substring(0, dotIndex) else name
        val extension = if (dotIndex != -1) name.substring(dotIndex) else ""
        
        var count = 1
        var newName = "$baseName-$count$extension"
        while (java.io.File(dir, newName).exists()) {
            count++
            newName = "$baseName-$count$extension"
        }
        return newName
    }

    private val client = okhttp3.OkHttpClient.Builder()
        .connectTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    fun sendChatMessage(prompt: String) {
        if (prompt.trim().isEmpty()) return
        
        // Add user message
        val userMsg = ChatMessage(text = prompt, isUser = true)
        chatHistory.value = chatHistory.value + userMsg
        isChatLoading.value = true
        
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val apiKey = BuildConfig.GEMINI_API_KEY
                if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
                    // Fallback simulated helpful Gemini in case user hasn't added API Key
                    kotlinx.coroutines.delay(1200)
                    val mockResponse = "Hello Jack, I noticed your Gemini API Key is not set in your Secrets panel yet. To activate active real-time AI capabilities, please configure the `GEMINI_API_KEY` in the AI Studio Secrets panel.\n\nBut as your local browser assistant, I can still assist you! Here are some proposed actions based on your message: [ACTION:open_url:https://google.com] or you can change theme to Onyx Midnight: [ACTION:set_theme:ONYX_MIDNIGHT]"
                    handleGeminiResponse(mockResponse)
                    return@launch
                }
                
                val sysPrompt = """
                    You are "Apex AI", the native AI Assistant built into Apex Browser, founded by Jack (SK Jack).
                    The developer info: Full Name: Jack, known as SK Jack (founder), system level node: APEX-900456.

                    You have the power to help the user navigate, search, configure their browser, and automate actions.
                    If the user asks to open a website, search, toggle adblock, change theme, or clear grabber, you can suggest clickable in-app actions!
                    Provide the suggestion inside the text using these formats (you can include multiple):
                    [ACTION:open_url:someurl] e.g. [ACTION:open_url:https://www.youtube.com]
                    [ACTION:search:somequery] e.g. [ACTION:search:avatar trailer]
                    [ACTION:toggle_adblock:true] or [ACTION:toggle_adblock:false]
                    [ACTION:toggle_popups:true] or [ACTION:toggle_popups:false]
                    [ACTION:set_theme:ONYX_MIDNIGHT] (Themes: HIGH_DENSITY, ONYX_MIDNIGHT, COSMIC_INDIGO, FOREST_MINT, CLASSIC_OCEAN)
                    [ACTION:add_bookmark:some_title]
                    [ACTION:clear_grabber]

                    If the user asks/suggests "can we add a apt or pkg option on here like termux", you MUST answer:
                    "Due to Android's secure application sandboxing, standard apps compiled without root level privileges cannot package native system packages, or execute standard 'apt'/'pkg' package managers directly inside their sandbox filesystem without containing a full virtualized PRoot environment like Termux does. However, Apex Browser allows loading virtual sandbox environments, customized extensions, or integration with terminal utilities in future update nodes designed by SK Jack."
                    
                    Be professional, cyber-themed, concise, and helpful! Always refer to the user as Jack or SK Jack with high esteem.
                """.trimIndent()
                
                val reqObj = org.json.JSONObject()
                val contentsArray = org.json.JSONArray()
                
                // Add recent history to context
                val currentHistory = chatHistory.value.takeLast(10)
                for (hMsg in currentHistory) {
                    val hObj = org.json.JSONObject()
                    hObj.put("role", if (hMsg.isUser) "user" else "model")
                    val hParts = org.json.JSONArray()
                    val hPart = org.json.JSONObject()
                    // Strip actions out of history prompts to keep context clean
                    val cleanText = hMsg.text.replace(Regex("\\[ACTION:.*?]"), "")
                    hPart.put("text", cleanText)
                    hParts.put(hPart)
                    hObj.put("parts", hParts)
                    contentsArray.put(hObj)
                }
                
                reqObj.put("contents", contentsArray)
                
                val sysObj = org.json.JSONObject()
                val sysParts = org.json.JSONArray()
                val sysPart = org.json.JSONObject()
                sysPart.put("text", sysPrompt)
                sysParts.put(sysPart)
                sysObj.put("parts", sysParts)
                reqObj.put("systemInstruction", sysObj)
                
                val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
                val requestBody = reqObj.toString().toRequestBody(mediaType)
                
                val request = okhttp3.Request.Builder()
                    .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=$apiKey")
                    .post(requestBody)
                    .build()
                
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val responseBody = response.body?.string() ?: ""
                    val jsonResponse = org.json.JSONObject(responseBody)
                    val candidates = jsonResponse.optJSONArray("candidates")
                    val firstCandidate = candidates?.optJSONObject(0)
                    val content = firstCandidate?.optJSONObject("content")
                    val parts = content?.optJSONArray("parts")
                    val firstPart = parts?.optJSONObject(0)
                    val text = firstPart?.optString("text", "No response text received.") ?: "No response text."
                    handleGeminiResponse(text)
                } else {
                    val errCode = response.code
                    val errMsg = response.message
                    handleGeminiResponse("API Request returned error code $errCode: $errMsg. Please check your Gemini API key inside the AI Studio Secrets panel.")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                handleGeminiResponse("Network connection error: ${e.localizedMessage}. Please verify your internet connection.")
            } finally {
                isChatLoading.value = false
            }
        }
    }

    private fun handleGeminiResponse(rawText: String) {
        val actions = mutableListOf<GeminiAppAction>()
        var cleanText = rawText
        
        // Find action patterns: [ACTION:type:arg] or [ACTION:type]
        val pattern = java.util.regex.Pattern.compile("\\[ACTION:([a-zA-Z_]+)(?::([^]]+))?]")
        val matcher = pattern.matcher(rawText)
        
        while (matcher.find()) {
            val type = matcher.group(1) ?: ""
            val argument = matcher.group(2) ?: ""
            
            val action = when (type) {
                "open_url" -> GeminiAppAction("open_url", argument, "Open Web URL")
                "search" -> GeminiAppAction("search", argument, "Search Browser")
                "toggle_adblock" -> GeminiAppAction("toggle_adblock", argument, "Toggle Ad Blocker")
                "toggle_popups" -> GeminiAppAction("toggle_popups", argument, "Toggle Popups")
                "set_theme" -> GeminiAppAction("set_theme", argument, "Apply App Theme")
                "add_bookmark" -> GeminiAppAction("add_bookmark", argument, "Bookmark Current Page")
                "clear_grabber" -> GeminiAppAction("clear_grabber", argument, "Clear Grabber List")
                else -> null
            }
            if (action != null) {
                actions.add(action)
            }
            // Remove the action block from text
            cleanText = cleanText.replace(matcher.group(0), "")
        }
        
        // Trim double spaces or empty actions line
        cleanText = cleanText.trim()
        
        val aiMsg = ChatMessage(text = cleanText, isUser = false, proposedActions = actions)
        chatHistory.value = chatHistory.value + aiMsg
    }

    fun executeProposedAction(action: GeminiAppAction) {
        when (action.type) {
            "open_url" -> {
                loadUrl(action.argument)
                currentTab.value = 0 // Switch to Browser tab
                toastEvent.tryEmit("AI Automation: Opening ${action.argument}")
            }
            "search" -> {
                handleSearch(action.argument)
                currentTab.value = 0 // Switch to Browser tab
                toastEvent.tryEmit("AI Automation: Searching for '${action.argument}'")
            }
            "toggle_adblock" -> {
                val value = action.argument.lowercase().toBoolean()
                blockAdsEnabled.value = value
                toastEvent.tryEmit("AI Automation: Set Ad Blocker to $value")
            }
            "toggle_popups" -> {
                val value = action.argument.lowercase().toBoolean()
                blockPopupsEnabled.value = value
                toastEvent.tryEmit("AI Automation: Set Popups Blocker to $value")
            }
            "set_theme" -> {
                try {
                    val enumVal = AppTheme.valueOf(action.argument.uppercase())
                    selectedAppTheme.value = enumVal
                    toastEvent.tryEmit("AI Automation: Updated theme to ${action.argument}")
                } catch (e: Exception) {
                    toastEvent.tryEmit("AI Automation: Invalid theme requested")
                }
            }
            "add_bookmark" -> {
                addBookmark(action.argument, currentUrl.value)
                toastEvent.tryEmit("AI Automation: Bookmarked '${action.argument}'")
            }
            "clear_grabber" -> {
                _detectedMedia.value = emptyList()
                toastEvent.tryEmit("AI Automation: Grabbed list cleared.")
            }
        }
    }
}
