package com.example.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.util.Log
import android.view.ViewGroup
import android.webkit.*
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.database.Bookmark
import com.example.data.database.DownloadJob
import com.example.data.database.UserExtension
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.File
import kotlin.math.log10

@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserApp(viewModel: BrowserViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    val zipLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
        uri?.let {
            try {
                val inputStream = context.contentResolver.openInputStream(it)
                if (inputStream != null) {
                    viewModel.importExtensionFromZip(inputStream)
                } else {
                    android.widget.Toast.makeText(context, "Could not open selected file", android.widget.Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                android.widget.Toast.makeText(context, "Failed to load extension: " + e.localizedMessage, android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.importSuccessEvent.collect { msg ->
            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_LONG).show()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.importErrorEvent.collect { err ->
            android.widget.Toast.makeText(context, err, android.widget.Toast.LENGTH_LONG).show()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.toastEvent.collect { msg ->
            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_LONG).show()
        }
    }

    // Bind States
    val currentUrl by viewModel.currentUrl.collectAsStateWithLifecycle()
    val searchInput by viewModel.searchInput.collectAsStateWithLifecycle()
    val loadingProgress by viewModel.loadingProgress.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val canGoBack by viewModel.canGoBack.collectAsStateWithLifecycle()
    val canGoForward by viewModel.canGoForward.collectAsStateWithLifecycle()

    val activeTab by viewModel.currentTab.collectAsStateWithLifecycle()
    val activeTheme by viewModel.selectedAppTheme.collectAsStateWithLifecycle()
    
    val detectedMediaList by viewModel.detectedMedia.collectAsStateWithLifecycle()
    val dbDownloads by viewModel.allDownloads.collectAsStateWithLifecycle()
    val dbBookmarks by viewModel.allBookmarks.collectAsStateWithLifecycle()
    val dbExtensions by viewModel.allExtensions.collectAsStateWithLifecycle()
    val activeSpeeds by viewModel.activeSpeeds.collectAsStateWithLifecycle()

    // Configurable choices
    val blockAds by viewModel.blockAdsEnabled.collectAsStateWithLifecycle()
    val blockPopups by viewModel.blockPopupsEnabled.collectAsStateWithLifecycle()
    val userAgentType by viewModel.userAgentType.collectAsStateWithLifecycle()
    val threadCountPreference by viewModel.defaultThreadsCount.collectAsStateWithLifecycle()

    // Map theme colors dynamically
    val (themeBg, themeCard, themePrimary, themeAccent, isDarkTheme) = remember(activeTheme) {
        when (activeTheme) {
            AppTheme.HIGH_DENSITY -> listOf(Color(0xFFFEF7FF), Color(0xFFFFFFFF), Color(0xFF6750A4), Color(0xFFEADDFF), false)
            AppTheme.ONYX_MIDNIGHT -> listOf(Color(0xFF030303), Color(0xFF101010), Color(0xFF00E5FF), Color(0xFF1DE9B6), true)
            AppTheme.COSMIC_INDIGO -> listOf(Color(0xFF0C091A), Color(0xFF17132B), Color(0xFFB388FF), Color(0xFFFF4081), true)
            AppTheme.FOREST_MINT -> listOf(Color(0xFF09120F), Color(0xFF11221D), Color(0xFF2ECC71), Color(0xFF10B981), true)
            AppTheme.CLASSIC_OCEAN -> listOf(Color(0xFF07111F), Color(0xFF102137), Color(0xFF5DADE2), Color(0xFF3498DB), true)
        }
    }

    val customColors = if (isDarkTheme as Boolean) {
        darkColorScheme(
            primary = themePrimary as Color,
            secondary = themeAccent as Color,
            background = themeBg as Color,
            surface = themeCard as Color,
            surfaceVariant = (themeCard as Color).copy(alpha = 0.5f),
            onPrimary = Color.Black,
            onSecondary = Color.White,
            onBackground = Color(0xFFEDEDED),
            onSurface = Color(0xFFEDEDED)
        )
    } else {
        lightColorScheme(
            primary = themePrimary as Color,
            secondary = themeAccent as Color,
            background = themeBg as Color,
            surface = themeCard as Color,
            surfaceVariant = Color(0xFFF3EDF7),
            onPrimary = Color.White,
            onSecondary = Color(0xFF21005D),
            onBackground = Color(0xFF1D1B20),
            onSurface = Color(0xFF1D1B20)
        )
    }

    val textColor = if (isDarkTheme) Color.White else Color(0xFF1D1B20)
    val textMutedColor = if (isDarkTheme) Color.Gray else Color(0xFF49454F)
    val cardBorderColor = if (isDarkTheme) Color.Transparent else Color(0xFFCAC4D0)

    // Manage WebView Instance (Held persistent across recompositions!)
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }

    LaunchedEffect(webViewInstance) {
        viewModel.loadUrlEvent.collect { url ->
            webViewInstance?.loadUrl(url)
        }
    }

    // Intercept BackPresses for Browser back-navigation
    BackHandler(enabled = activeTab == 0 && canGoBack) {
        webViewInstance?.goBack()
    }

    MaterialTheme(
        colorScheme = customColors,
        typography = MaterialTheme.typography
    ) {
        Scaffold(
            bottomBar = {
                NavigationBar(
                    containerColor = if (activeTheme == AppTheme.HIGH_DENSITY) Color(0xFFF3EDF7) else themeBg as Color,
                    tonalElevation = 8.dp,
                    modifier = Modifier.navigationBarsPadding().then(
                        if (activeTheme == AppTheme.HIGH_DENSITY) {
                            Modifier.drawBehind {
                                drawLine(
                                    color = Color(0xFFCAC4D0),
                                    start = Offset(0f, 0f),
                                    end = Offset(size.width, 0f),
                                    strokeWidth = 1.dp.toPx()
                                )
                            }
                        } else Modifier
                    )
                ) {
                    NavigationBarItem(
                        selected = activeTab == 0,
                        onClick = { viewModel.currentTab.value = 0 },
                        icon = { Icon(Icons.Default.Language, contentDescription = "Browser") },
                        label = { Text("Browser", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = if (activeTheme == AppTheme.HIGH_DENSITY) Color(0xFF21005D) else themePrimary,
                            selectedTextColor = if (activeTheme == AppTheme.HIGH_DENSITY) Color(0xFF1D1B20) else themePrimary,
                            indicatorColor = if (activeTheme == AppTheme.HIGH_DENSITY) Color(0xFFEADDFF) else (themePrimary).copy(alpha = 0.15f),
                            unselectedIconColor = if (activeTheme == AppTheme.HIGH_DENSITY) Color(0xFF49454F) else Color.Gray,
                            unselectedTextColor = if (activeTheme == AppTheme.HIGH_DENSITY) Color(0xFF49454F) else Color.Gray
                        )
                    )
                    NavigationBarItem(
                        selected = activeTab == 1,
                        onClick = { viewModel.currentTab.value = 1 },
                        icon = { 
                            BadgedBox(
                                badge = {
                                    if (detectedMediaList.isNotEmpty()) {
                                        Badge(
                                            containerColor = if (activeTheme == AppTheme.HIGH_DENSITY) Color(0xFF6750A4) else themeAccent as Color,
                                            contentColor = if (activeTheme == AppTheme.HIGH_DENSITY) Color.White else Color.Black
                                        ) {
                                            Text(detectedMediaList.size.toString(), fontWeight = FontWeight.Black)
                                        }
                                    }
                                }
                            ) {
                                Icon(Icons.Default.FolderOpen, contentDescription = "Media Grabber")
                            }
                        },
                        label = { Text("Grabber", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = if (activeTheme == AppTheme.HIGH_DENSITY) Color(0xFF21005D) else themePrimary,
                            selectedTextColor = if (activeTheme == AppTheme.HIGH_DENSITY) Color(0xFF1D1B20) else themePrimary,
                            indicatorColor = if (activeTheme == AppTheme.HIGH_DENSITY) Color(0xFFEADDFF) else (themePrimary).copy(alpha = 0.15f),
                            unselectedIconColor = if (activeTheme == AppTheme.HIGH_DENSITY) Color(0xFF49454F) else Color.Gray,
                            unselectedTextColor = if (activeTheme == AppTheme.HIGH_DENSITY) Color(0xFF49454F) else Color.Gray
                        )
                    )
                    NavigationBarItem(
                        selected = activeTab == 2,
                        onClick = { viewModel.currentTab.value = 2 },
                        icon = { 
                            val activeCount = dbDownloads.count { it.status == "DOWNLOADING" }
                            BadgedBox(
                                badge = {
                                    if (activeCount > 0) {
                                        Badge(
                                            containerColor = if (activeTheme == AppTheme.HIGH_DENSITY) Color(0xFF6750A4) else themePrimary
                                        ) {
                                            Text(activeCount.toString(), fontWeight = FontWeight.Black)
                                        }
                                    }
                                }
                            ) {
                                Icon(Icons.Default.Download, contentDescription = "Downloads")
                            }
                        },
                        label = { Text("Downloads", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = if (activeTheme == AppTheme.HIGH_DENSITY) Color(0xFF21005D) else themePrimary,
                            selectedTextColor = if (activeTheme == AppTheme.HIGH_DENSITY) Color(0xFF1D1B20) else themePrimary,
                            indicatorColor = if (activeTheme == AppTheme.HIGH_DENSITY) Color(0xFFEADDFF) else (themePrimary).copy(alpha = 0.15f),
                            unselectedIconColor = if (activeTheme == AppTheme.HIGH_DENSITY) Color(0xFF49454F) else Color.Gray,
                            unselectedTextColor = if (activeTheme == AppTheme.HIGH_DENSITY) Color(0xFF49454F) else Color.Gray
                        )
                    )
                    NavigationBarItem(
                        selected = activeTab == 4,
                        onClick = { viewModel.currentTab.value = 4 },
                        icon = { Icon(Icons.Default.Chat, contentDescription = "Gemini AI") },
                        label = { Text("Apex AI", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = if (activeTheme == AppTheme.HIGH_DENSITY) Color(0xFF21005D) else themePrimary,
                            selectedTextColor = if (activeTheme == AppTheme.HIGH_DENSITY) Color(0xFF1D1B20) else themePrimary,
                            indicatorColor = if (activeTheme == AppTheme.HIGH_DENSITY) Color(0xFFEADDFF) else (themePrimary).copy(alpha = 0.15f),
                            unselectedIconColor = if (activeTheme == AppTheme.HIGH_DENSITY) Color(0xFF49454F) else Color.Gray,
                            unselectedTextColor = if (activeTheme == AppTheme.HIGH_DENSITY) Color(0xFF49454F) else Color.Gray
                        )
                    )
                    NavigationBarItem(
                        selected = activeTab == 3,
                        onClick = { viewModel.currentTab.value = 3 },
                        icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                        label = { Text("Settings", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = if (activeTheme == AppTheme.HIGH_DENSITY) Color(0xFF21005D) else themePrimary,
                            selectedTextColor = if (activeTheme == AppTheme.HIGH_DENSITY) Color(0xFF1D1B20) else themePrimary,
                            indicatorColor = if (activeTheme == AppTheme.HIGH_DENSITY) Color(0xFFEADDFF) else (themePrimary).copy(alpha = 0.15f),
                            unselectedIconColor = if (activeTheme == AppTheme.HIGH_DENSITY) Color(0xFF49454F) else Color.Gray,
                            unselectedTextColor = if (activeTheme == AppTheme.HIGH_DENSITY) Color(0xFF49454F) else Color.Gray
                        )
                    )
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .background(themeBg)
                .statusBarsPadding()
        ) { innerPadding ->

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = innerPadding.calculateBottomPadding())
                    .background(themeBg)
            ) {
                // RENDER WEBVIEW PERSISTENTLY (Kept in composition inside an invisible box when other tabs are active!)
                Box(
                    modifier = if (activeTab == 0) Modifier.fillMaxSize() else Modifier.size(0.dp)
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // TOP BROWSER URL SEARCH BAR
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = themeBg,
                            tonalElevation = 2.dp
                        ) {
                            Column {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconButton(
                                        onClick = { webViewInstance?.goBack() },
                                        enabled = canGoBack,
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.ArrowBack,
                                            contentDescription = "Back",
                                            tint = if (canGoBack) themePrimary else Color.DarkGray
                                        )
                                    }
                                    
                                    Spacer(modifier = Modifier.width(2.dp))
                                    
                                    // URL address field
                                    TextField(
                                        value = searchInput,
                                        onValueChange = { viewModel.searchInput.value = it },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp)
                                            .testTag("url_address_input"),
                                        placeholder = { Text("Search or type URL", color = if (activeTheme == AppTheme.HIGH_DENSITY) Color(0xFF44474E).copy(alpha = 0.5f) else Color.Gray, fontSize = 13.sp) },
                                        singleLine = true,
                                        textStyle = LocalTextStyle.current.copy(
                                            fontSize = 13.sp,
                                            color = if (activeTheme == AppTheme.HIGH_DENSITY) Color(0xFF44474E) else textColor
                                        ),
                                        colors = TextFieldDefaults.colors(
                                            focusedContainerColor = if (activeTheme == AppTheme.HIGH_DENSITY) Color(0xFFEDF1F9) else themeCard,
                                            unfocusedContainerColor = if (activeTheme == AppTheme.HIGH_DENSITY) Color(0xFFEDF1F9) else themeCard,
                                            disabledContainerColor = if (activeTheme == AppTheme.HIGH_DENSITY) Color(0xFFEDF1F9) else themeCard,
                                            focusedIndicatorColor = Color.Transparent,
                                            unfocusedIndicatorColor = Color.Transparent,
                                            focusedTextColor = if (activeTheme == AppTheme.HIGH_DENSITY) Color(0xFF44474E) else textColor,
                                            unfocusedTextColor = if (activeTheme == AppTheme.HIGH_DENSITY) Color(0xFF44474E) else textColor
                                        ),
                                        leadingIcon = {
                                            Icon(
                                                Icons.Default.Lock,
                                                contentDescription = "Secure",
                                                tint = if (currentUrl.startsWith("https")) themePrimary else Color.Gray,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        },
                                        trailingIcon = {
                                            if (searchInput.isNotEmpty()) {
                                                IconButton(onClick = { viewModel.searchInput.value = "" }) {
                                                    Icon(Icons.Default.Close, contentDescription = "Clear", tint = if (activeTheme == AppTheme.HIGH_DENSITY) Color(0xFF44474E) else Color.Gray, modifier = Modifier.size(16.dp))
                                                }
                                            }
                                        },
                                        keyboardOptions = KeyboardOptions(
                                            keyboardType = KeyboardType.Uri,
                                            imeAction = ImeAction.Go
                                        ),
                                        keyboardActions = KeyboardActions(
                                            onGo = {
                                                viewModel.handleSearch(searchInput)
                                                focusManager.clearFocus()
                                            }
                                        ),
                                        shape = RoundedCornerShape(24.dp)
                                    )

                                    Spacer(modifier = Modifier.width(4.dp))

                                    IconButton(
                                        onClick = {
                                            if (isLoading) {
                                                webViewInstance?.stopLoading()
                                            } else {
                                                webViewInstance?.reload()
                                            }
                                        },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            if (isLoading) Icons.Default.Close else Icons.Default.Refresh,
                                            contentDescription = "Action",
                                            tint = themePrimary
                                        )
                                    }

                                    // Add Quick Bookmark addition icon
                                    IconButton(
                                        onClick = {
                                            viewModel.addBookmark(
                                                title = webViewInstance?.title ?: "Web Page",
                                                url = currentUrl
                                            )
                                        },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        val isBookmarked = dbBookmarks.any { it.url == currentUrl }
                                        Icon(
                                            if (isBookmarked) Icons.Default.Star else Icons.Default.StarBorder,
                                            contentDescription = "Bookmark Page",
                                            tint = if (isBookmarked) themeAccent else Color.Gray
                                        )
                                    }
                                }
                                
                                // Precise loading linear progress bar
                                if (isLoading) {
                                    LinearProgressIndicator(
                                        progress = { loadingProgress / 100f },
                                        modifier = Modifier.fillMaxWidth().height(2.5.dp),
                                        color = themePrimary,
                                        trackColor = Color.Transparent
                                    )
                                } else {
                                    Spacer(modifier = Modifier.height(2.5.dp))
                                }
                            }
                        }

                        // THE MAIN ANDROID WEBVIEW COMPONENT
                        AndroidView(
                            factory = { ctx ->
                                WebView(ctx).apply {
                                    webViewInstance = this
                                    layoutParams = ViewGroup.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        ViewGroup.LayoutParams.MATCH_PARENT
                                    )

                                    settings.apply {
                                        javaScriptEnabled = true
                                        domStorageEnabled = true
                                        databaseEnabled = true
                                        useWideViewPort = true
                                        loadWithOverviewMode = true
                                        builtInZoomControls = true
                                        displayZoomControls = false
                                        mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                                        userAgentString = viewModel.getDeviceUserAgent()
                                    }

                                    // Support standard web downloads triggering our multi-thread downloader!
                                    setDownloadListener { url, userAgent, contentDisposition, mimetype, contentLength ->
                                        val guessName = android.webkit.URLUtil.guessFileName(url, contentDisposition, mimetype) ?: "download_file"
                                        viewModel.startGrabbedDownload(url, guessName)
                                        viewModel.currentTab.value = 2 // Auto route user to downloads
                                    }

                                    webViewClient = object : WebViewClient() {
                                        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                            super.onPageStarted(view, url, favicon)
                                            viewModel.isLoading.value = true
                                            viewModel.loadingProgress.value = 10
                                            url?.let {
                                                viewModel.currentUrl.value = it
                                                viewModel.searchInput.value = it
                                            }
                                        }

                                        override fun onPageFinished(view: WebView?, url: String?) {
                                            super.onPageFinished(view, url)
                                            viewModel.isLoading.value = false
                                            viewModel.loadingProgress.value = 100
                                            viewModel.canGoBack.value = view?.canGoBack() ?: false
                                            viewModel.canGoForward.value = view?.canGoForward() ?: false

                                            // Retrieve meta description of page for better video descriptions
                                            view?.evaluateJavascript(
                                                "document.querySelector('meta[name=\"description\"]')?.getAttribute('content') || document.querySelector('meta[property=\"og:description\"]')?.getAttribute('content') || ''",
                                                { desc ->
                                                    val cleanDesc = desc?.trim()?.removePrefix("\"")?.removeSuffix("\"") ?: ""
                                                    if (cleanDesc.isNotEmpty() && cleanDesc != "null") {
                                                        viewModel.currentPageDescription.value = cleanDesc
                                                    } else {
                                                        viewModel.currentPageDescription.value = ""
                                                    }
                                                }
                                            )

                                            // Injected content scripts (Extensions loop)
                                            scope.launch(Dispatchers.IO) {
                                                val enabledExtensions = viewModel.repository.getEnabledExtensionsSync()
                                                withContext(Dispatchers.Main) {
                                                     for (ext in enabledExtensions) {
                                                         view?.evaluateJavascript(ext.script, null)
                                                     }
                                                }
                                            }
                                        }

                                        override fun onLoadResource(view: WebView?, url: String?) {
                                            super.onLoadResource(view, url)
                                            url?.let { viewModel.sniffMediaUrl(it) }
                                        }

                                        override fun shouldInterceptRequest(
                                            view: WebView?,
                                            request: WebResourceRequest?
                                        ): WebResourceResponse? {
                                            val reqUrl = request?.url?.toString() ?: return null
                                            
                                            // Handle Native Host blocking (Ad-blocker WebExtensions rule)
                                            if (viewModel.shouldBlockUrl(reqUrl)) {
                                                return WebResourceResponse(
                                                    "text/plain",
                                                    "utf-8",
                                                    ByteArrayInputStream("".toByteArray())
                                                )
                                            }
                                            
                                            // Also feed background URLs into media sniffer asynchronously
                                            viewModel.sniffMediaUrl(reqUrl)
                                            
                                            return super.shouldInterceptRequest(view, request)
                                        }
                                    }

                                    webChromeClient = object : WebChromeClient() {
                                        override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                            super.onProgressChanged(view, newProgress)
                                            viewModel.loadingProgress.value = newProgress
                                        }

                                        override fun onReceivedTitle(view: android.webkit.WebView?, title: String?) {
                                            super.onReceivedTitle(view, title)
                                            if (!title.isNullOrEmpty()) {
                                                viewModel.currentPageTitle.value = title
                                            }
                                        }
                                    }

                                    // Create Native JS Interaction bridge for Extension APIs
                                    addJavascriptInterface(object {
                                        @JavascriptInterface
                                        fun onVideosDetected(jsonArrayStr: String) {
                                            try {
                                                val array = org.json.JSONArray(jsonArrayStr)
                                                for (i in 0 until array.length()) {
                                                    val url = array.getString(i)
                                                    if (!url.startsWith("blob:")) {
                                                        viewModel.sniffMediaUrl(url)
                                                    }
                                                }
                                            } catch (e: Exception) {
                                                Log.e("ApexNative", "Error parsing video lists: ${e.message}")
                                            }
                                        }

                                        @JavascriptInterface
                                        fun onVideosDetectedWithMeta(jsonArrayStr: String) {
                                            try {
                                                val array = org.json.JSONArray(jsonArrayStr)
                                                for (i in 0 until array.length()) {
                                                    val obj = array.getJSONObject(i)
                                                    val url = obj.getString("url")
                                                    val quality = obj.optString("quality", "")
                                                    viewModel.sniffMediaUrlWithMeta(url, quality)
                                                }
                                            } catch (e: Exception) {
                                                Log.e("ApexNative", "Error parsing video lists with meta: ${e.message}")
                                            }
                                        }

                                        @JavascriptInterface
                                        fun onBlobDownloaded(fileName: String, base64Data: String, url: String) {
                                            Log.d("ApexNative", "Base64 blob size received for: $fileName, url: $url")
                                            viewModel.saveBlobDownload(fileName, base64Data, url)
                                        }

                                        @JavascriptInterface
                                        fun onBlobFailed(fileName: String, errorMsg: String, url: String) {
                                            Log.e("ApexNative", "Blob download failed: $errorMsg for url $url")
                                            viewModel.reportBlobFailed(fileName, errorMsg, url)
                                        }
                                    }, "ApexNative")

                                    loadUrl(currentUrl)
                                }
                            },
                            update = { webView ->
                                // Trigger dynamic user-agent toggles & URL shifts
                                val desiredUserAgent = viewModel.getDeviceUserAgent()
                                if (webView.settings.userAgentString != desiredUserAgent) {
                                    webView.settings.userAgentString = desiredUserAgent
                                    webView.reload()
                                }
                                
                            },
                            modifier = Modifier.weight(1f)
                        )

                        // BOOKMARK SHORTCUT PANEL ON EMPTY HOME VIEW (if google loaded initially or user goes home)
                        if (currentUrl.contains("google.com") && searchInput.endsWith("google.com")) {
                            Text(
                                "Quick Access Bookmarks",
                                color = themePrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp)
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(64.dp)
                                    .background(themeBg)
                            ) {
                                LazyColumn(
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    // Custom row for shortcuts List
                                    item {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 12.dp)
                                        ) {
                                            dbBookmarks.take(4).forEach { bmark ->
                                                Card(
                                                    modifier = Modifier
                                                        .padding(4.dp)
                                                        .height(44.dp)
                                                        .weight(1f)
                                                        .clickable {
                                                            viewModel.loadUrl(bmark.url)
                                                        },
                                                    colors = CardDefaults.cardColors(containerColor = themeCard),
                                                    border = if (activeTheme == AppTheme.HIGH_DENSITY) BorderStroke(1.dp, cardBorderColor) else null
                                                ) {
                                                    Box(
                                                        modifier = Modifier.fillMaxSize(),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Text(
                                                            bmark.title,
                                                            color = textColor,
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // TAB 1: MEDIA GRABBER UI PANEL
                if (activeTab == 1) {
                    MediaGrabberPanel(
                        detectedList = detectedMediaList,
                        themeCard = themeCard,
                        themePrimary = themePrimary,
                        themeAccent = themeAccent,
                        threadCountPref = threadCountPreference,
                        activeTheme = activeTheme,
                        onClear = { viewModel.clearDetectedMedia() },
                        onDownload = { grabbed ->
                            if (grabbed.url.startsWith("blob:")) {
                                val js = """
                                    (async function() {
                                        try {
                                             const response = await fetch('${grabbed.url}');
                                             const blob = await response.blob();
                                             const reader = new FileReader();
                                             reader.onloadend = function() {
                                                 const base64data = reader.result.split(',')[1];
                                                 window.ApexNative.onBlobDownloaded('${grabbed.fileName}', base64data, '${grabbed.url}');
                                             };
                                             reader.readAsDataURL(blob);
                                         } catch(err) {
                                             window.ApexNative.onBlobFailed('${grabbed.fileName}', err.toString(), '${grabbed.url}');
                                         }
                                    })();
                                """.trimIndent()
                                webViewInstance?.evaluateJavascript(js, null)
                                viewModel.currentTab.value = 2 // Transition user to download list
                            } else {
                                viewModel.startGrabbedDownload(grabbed.url, grabbed.fileName)
                                viewModel.currentTab.value = 2 // Auto slide user to downloads to view stats!
                            }
                        }
                    )
                }

                // TAB 2: MULTI-THREADED DOWNLOADS VISUALS
                if (activeTab == 2) {
                    DownloadsPanel(
                        downloads = dbDownloads,
                        activeSpeeds = activeSpeeds,
                        themeCard = themeCard,
                        themePrimary = themePrimary,
                        themeAccent = themeAccent,
                        activeTheme = activeTheme,
                        onResume = { id -> viewModel.downloadManager.resumeDownload(id) },
                        onPause = { id -> viewModel.downloadManager.pauseDownload(id) },
                        onDelete = { id -> viewModel.downloadManager.cancelDeleteDownload(id) }
                    )
                }

                // TAB 3: EXTENSIONS & COMPREHENSIVE SETTINGS
                if (activeTab == 3) {
                    SettingsPanel(
                        extensions = dbExtensions,
                        activeTheme = activeTheme,
                        blockAds = blockAds,
                        blockPopups = blockPopups,
                        userAgent = userAgentType,
                        threadCount = threadCountPreference,
                        themeBg = themeBg,
                        themeCard = themeCard,
                        themePrimary = themePrimary,
                        themeAccent = themeAccent,
                        onExtToggle = { extId, checked -> viewModel.toggleExtension(extId, checked) },
                        onAddCustomExt = { n, d, s -> viewModel.addCustomExtension(n, d, s) },
                        onDeleteExt = { extId -> viewModel.deleteExtension(extId) },
                        onThemeSelect = { theme -> viewModel.selectedAppTheme.value = theme },
                        onAdsToggle = { enabled -> viewModel.blockAdsEnabled.value = enabled },
                        onPopupsToggle = { enabled -> viewModel.blockPopupsEnabled.value = enabled },
                        onUserAgentChange = { bType -> viewModel.userAgentType.value = bType },
                        onThreadChange = { count -> viewModel.defaultThreadsCount.value = count },
                        onImportExtension = { zipLauncher.launch("*/*") }
                    )
                }

                // TAB 4: GEMINI AI CHAT & ASSISTANT PANEL
                if (activeTab == 4) {
                    GeminiChatPanel(
                        viewModel = viewModel,
                        themeBg = themeBg as Color,
                        themeCard = themeCard as Color,
                        themePrimary = themePrimary as Color,
                        themeAccent = themeAccent as Color,
                        textColor = textColor,
                        textMutedColor = textMutedColor,
                        isDarkTheme = isDarkTheme
                    )
                }
            }
        }
    }
}

// COMPOSABLE 1: MEDIA GRABBER SHEET
@Composable
fun MediaGrabberPanel(
    detectedList: List<DetectedMedia>,
    themeCard: Color,
    themePrimary: Color,
    themeAccent: Color,
    threadCountPref: Int,
    activeTheme: AppTheme,
    onClear: () -> Unit,
    onDownload: (DetectedMedia) -> Unit
) {
    val isDarkTheme = activeTheme != AppTheme.HIGH_DENSITY
    val textColor = if (isDarkTheme) Color.White else Color(0xFF1D1B20)
    val textMutedColor = if (isDarkTheme) Color.Gray else Color(0xFF49454F)
    val cardBorderColor = if (isDarkTheme) Color.Transparent else Color(0xFFCAC4D0)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    "Media Sniffer",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    color = textColor
                )
                Text(
                    "Real-time page media capture",
                    fontSize = 12.sp,
                    color = textMutedColor
                )
            }
            if (rawValid(detectedList)) {
                Button(
                    onClick = onClear,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isDarkTheme) Color.DarkGray else Color(0xFFEADDFF),
                        contentColor = if (isDarkTheme) Color.White else Color(0xFF21005D)
                    )
                ) {
                    Text("Clear", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // High Density Media Grabber alert banner matching HTML template
        if (rawValid(detectedList)) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (activeTheme == AppTheme.HIGH_DENSITY) Color(0xFFD0E4FF) else themeAccent.copy(alpha = 0.2f)
                ),
                shape = RoundedCornerShape(16.dp),
                border = if (activeTheme == AppTheme.HIGH_DENSITY) null else BorderStroke(1.dp, cardBorderColor)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(Color.White, RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.PlayArrow,
                                contentDescription = "Media Grab",
                                tint = if (activeTheme == AppTheme.HIGH_DENSITY) Color(0xFF004A77) else themePrimary
                            )
                        }
                        Column {
                            Text(
                                "${detectedList.size} Stream Links Found",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = if (activeTheme == AppTheme.HIGH_DENSITY) Color(0xFF001D35) else textColor
                            )
                            Text(
                                "1080p, 720p, 480p detected",
                                fontSize = 11.sp,
                                color = if (activeTheme == AppTheme.HIGH_DENSITY) Color(0xFF001D35).copy(alpha = 0.7f) else textMutedColor
                            )
                        }
                    }
                    Button(
                        onClick = {
                            if (detectedList.isNotEmpty()) {
                                onDownload(detectedList.first())
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (activeTheme == AppTheme.HIGH_DENSITY) Color(0xFF004A77) else themePrimary,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(20.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            "Grab All",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        if (detectedList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Icon(
                        Icons.Default.PlayCircleOutline,
                        contentDescription = "Empty",
                        tint = themeAccent.copy(alpha = 0.5f),
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "No dynamic media assets sniffed yet",
                        color = textColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Browse to video sites, streaming networks, or dynamic servers, and links will auto-extract below.",
                        color = textMutedColor,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(detectedList) { grabbed ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("grabbed_item_${grabbed.id}"),
                        colors = CardDefaults.cardColors(containerColor = themeCard),
                        shape = RoundedCornerShape(12.dp),
                        border = if (activeTheme == AppTheme.HIGH_DENSITY) BorderStroke(1.dp, cardBorderColor) else null
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                             ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(themePrimary.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        if (grabbed.mimeType.contains("audio")) Icons.Default.MusicNote else Icons.Default.PlayArrow,
                                        contentDescription = "Type Indicator",
                                        tint = themePrimary
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        grabbed.fileName,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = textColor,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        grabbed.mimeType,
                                        fontSize = 11.sp,
                                        color = textMutedColor
                                    )
                                    if (grabbed.description.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(3.dp))
                                        Text(
                                            text = grabbed.description,
                                            fontSize = 11.sp,
                                            color = textMutedColor,
                                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            // Fully expanded direct trigger buttons
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Dynamic size estimate tags
                                Text(
                                    "M-Thread: True" + if (grabbed.quality.isNotEmpty()) " • " + grabbed.quality else "",
                                    color = if (isDarkTheme) themeAccent else Color(0xFF6750A4),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .background(
                                            if (isDarkTheme) themeAccent.copy(alpha = 0.1f) else themeAccent,
                                            RoundedCornerShape(4.dp)
                                        )
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                                
                                Button(
                                    onClick = { onDownload(grabbed) },
                                    modifier = Modifier.height(32.dp).testTag("download_grabbed_btn"),
                                    contentPadding = PaddingValues(horizontal = 12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = themePrimary,
                                        contentColor = if (isDarkTheme) Color.Black else Color.White
                                    )
                                ) {
                                    Icon(Icons.Default.Download, contentDescription = "Grab", modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Download Manager", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// COMPOSABLE 2: DOWNLOADS LAYOUT
@Composable
fun DownloadsPanel(
    downloads: List<DownloadJob>,
    activeSpeeds: Map<String, Long>,
    themeCard: Color,
    themePrimary: Color,
    themeAccent: Color,
    activeTheme: AppTheme,
    onResume: (String) -> Unit,
    onPause: (String) -> Unit,
    onDelete: (String) -> Unit
) {
    val isDarkTheme = activeTheme != AppTheme.HIGH_DENSITY
    val textColor = if (isDarkTheme) Color.White else Color(0xFF1D1B20)
    val textMutedColor = if (isDarkTheme) Color.Gray else Color(0xFF49454F)
    val cardBorderColor = if (isDarkTheme) Color.Transparent else Color(0xFFCAC4D0)
    val trackBgColor = if (isDarkTheme) Color.Black else Color(0xFFE6E1E5)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        Column {
            Text(
                "Multi-Thread Engine",
                fontSize = 20.sp,
                fontWeight = FontWeight.Black,
                color = textColor
            )
            Text(
                "Parallel chunk segment streams",
                fontSize = 12.sp,
                color = textMutedColor
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (downloads.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Icon(
                        Icons.Outlined.CloudDownload,
                        contentDescription = "Empty Downloads",
                        tint = themePrimary.copy(alpha = 0.5f),
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "No ongoing download processes",
                        color = textColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Your downloaded links, compressed zip caches, and video sniffer streams will register here.",
                        color = textMutedColor,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(downloads, key = { it.id }) { job ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("download_card_${job.id}"),
                        colors = CardDefaults.cardColors(containerColor = themeCard),
                        shape = RoundedCornerShape(12.dp),
                        border = if (activeTheme == AppTheme.HIGH_DENSITY) BorderStroke(1.dp, cardBorderColor) else null
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            // File title & cancel action
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        job.fileName,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = textColor,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        "Source: " + try { java.net.URL(job.url).host } catch (e: Exception) { "Web Server" },
                                        fontSize = 10.sp,
                                        color = textMutedColor,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                IconButton(
                                    onClick = { onDelete(job.id) },
                                    modifier = Modifier.size(24.dp).testTag("delete_download_btn_${job.id}")
                                ) {
                                    Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = textMutedColor, modifier = Modifier.size(18.dp))
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Custom segmented worker progress bar if DOWNLOADING and has multiple threads
                            val progressFloat = remember(job.downloadedSize, job.totalSize) {
                                if (job.totalSize > 0) job.downloadedSize.toFloat() / job.totalSize else 0f
                            }

                            val threadProgressList = remember(job.threadProgress) {
                                if (job.threadProgress.isNotEmpty()) {
                                    job.threadProgress.split(",").mapNotNull { it.toLongOrNull() }
                                } else emptyList()
                            }

                            if (job.status == "DOWNLOADING" && threadProgressList.size > 1) {
                                // Draw actual active multi-threaded segment lanes! Premium visualization
                                Text(
                                    "Active parallel lanes: ${threadProgressList.size} stream channels",
                                    fontSize = 10.sp,
                                    color = if (isDarkTheme) themeAccent else Color(0xFF6750A4),
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(bottom = 4.dp)
                                )
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(10.dp)
                                        .clip(RoundedCornerShape(5.dp))
                                        .background(trackBgColor),
                                    horizontalArrangement = Arrangement.spacedBy(1.dp)
                                ) {
                                    val segmentCount = threadProgressList.size
                                    val eachExpectedSize = job.totalSize / segmentCount
                                    
                                    threadProgressList.forEach { writtenInThisSegment ->
                                        val segmentFraction = if (eachExpectedSize > 0) {
                                            (writtenInThisSegment.toFloat() / eachExpectedSize).coerceIn(0f, 1f)
                                        } else 0f
                                        
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .fillMaxHeight()
                                                .background(if (isDarkTheme) Color.DarkGray.copy(alpha = 0.5f) else Color(0xFFE0E0E0))
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth(segmentFraction)
                                                    .fillMaxHeight()
                                                    .background(
                                                        Brush.horizontalGradient(
                                                            listOf(themePrimary, themeAccent)
                                                        )
                                                    )
                                            )
                                        }
                                    }
                                }
                            } else {
                                // Dynamic single progress indicator line
                                LinearProgressIndicator(
                                    progress = { progressFloat },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(4.dp)),
                                    color = themePrimary,
                                    trackColor = trackBgColor
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Bottom row stats: values, status, pause action trigger
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    // Formatted download ratio
                                    val totalFormatted = remember(job.totalSize) { getFormattedSize(job.totalSize) }
                                    val currentFormatted = remember(job.downloadedSize) { getFormattedSize(job.downloadedSize) }
                                    Text(
                                        "$currentFormatted / $totalFormatted",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = textColor
                                    )

                                    // Dynamic Live speed reading
                                    val activeSpeedVal = activeSpeeds[job.id] ?: job.speedBytesPerSec
                                    if (job.status == "DOWNLOADING") {
                                        Text(
                                            "Speed: ${getFormattedSize(activeSpeedVal)}/s",
                                            fontSize = 10.sp,
                                            color = if (isDarkTheme) themeAccent else Color(0xFF6750A4),
                                            fontWeight = FontWeight.Bold
                                        )
                                    } else {
                                        Text(
                                            "Status: " + getStatusTag(job),
                                            fontSize = 10.sp,
                                            color = getStatusColor(job),
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (job.status == "DOWNLOADING" || job.status == "PENDING") {
                                        Button(
                                            onClick = { onPause(job.id) },
                                            modifier = Modifier.height(28.dp).testTag("pause_download_btn_${job.id}"),
                                            contentPadding = PaddingValues(horizontal = 10.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = if (isDarkTheme) Color.DarkGray else Color(0xFFEADDFF),
                                                contentColor = if (isDarkTheme) Color.White else Color(0xFF21005D)
                                            )
                                        ) {
                                            Icon(Icons.Default.Pause, contentDescription = "Pause", modifier = Modifier.size(12.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Pause", fontSize = 10.sp)
                                        }
                                    } else if (job.status != "COMPLETED") {
                                        Button(
                                            onClick = { onResume(job.id) },
                                            modifier = Modifier.height(28.dp).testTag("resume_download_btn_${job.id}"),
                                            contentPadding = PaddingValues(horizontal = 10.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = themePrimary,
                                                contentColor = if (isDarkTheme) Color.Black else Color.White
                                            )
                                        ) {
                                            Icon(Icons.Default.PlayArrow, contentDescription = "Resume", modifier = Modifier.size(12.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Resume", fontSize = 10.sp)
                                        }
                                    } else {
                                        Button(
                                            onClick = { /* No-op open file */ },
                                            modifier = Modifier.height(28.dp),
                                            enabled = false,
                                            contentPadding = PaddingValues(horizontal = 10.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                disabledContainerColor = if (isDarkTheme) Color(0xFF2E7D32).copy(alpha = 0.2f) else Color(0xFFE8F5E9),
                                                disabledContentColor = Color(0xFF4CAF50)
                                            )
                                        ) {
                                            Icon(Icons.Default.Check, contentDescription = "Done", modifier = Modifier.size(12.dp), tint = Color(0xFF4CAF50))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Saved", fontSize = 10.sp, color = Color(0xFF4CAF50))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// COMPOSABLE 3: EXTENSIONS & COMPREHENSIVE SETTINGS SHEET
@Composable
fun SettingsPanel(
    extensions: List<UserExtension>,
    activeTheme: AppTheme,
    blockAds: Boolean,
    blockPopups: Boolean,
    userAgent: String,
    threadCount: Int,
    themeBg: Color,
    themeCard: Color,
    themePrimary: Color,
    themeAccent: Color,
    onExtToggle: (String, Boolean) -> Unit,
    onAddCustomExt: (String, String, String) -> Unit,
    onDeleteExt: (String) -> Unit,
    onThemeSelect: (AppTheme) -> Unit,
    onAdsToggle: (Boolean) -> Unit,
    onPopupsToggle: (Boolean) -> Unit,
    onUserAgentChange: (String) -> Unit,
    onThreadChange: (Int) -> Unit,
    onImportExtension: () -> Unit
) {
    // Custom manual extension script adding state modal
    var showCustomModal by remember { mutableStateOf(false) }

    val isDarkTheme = activeTheme != AppTheme.HIGH_DENSITY
    val textColor = if (isDarkTheme) Color.White else Color(0xFF1D1B20)
    val textMutedColor = if (isDarkTheme) Color.Gray else Color(0xFF49454F)
    val cardBorderColor = if (isDarkTheme) Color.Transparent else Color(0xFFCAC4D0)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Theme Customization Presets Title
        item {
            Column {
                Text(
                    "Settings",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    color = textColor
                )
                Text(
                    "Customize theme settings, rules, and add extensions",
                    fontSize = 12.sp,
                    color = textMutedColor
                )
            }
        }

        // Section A: Theme Customizer Buttons
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = themeCard),
                shape = RoundedCornerShape(12.dp),
                border = if (activeTheme == AppTheme.HIGH_DENSITY) BorderStroke(1.dp, cardBorderColor) else null
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Select Interface Theme", color = textColor, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(10.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            AppTheme.HIGH_DENSITY,
                            AppTheme.ONYX_MIDNIGHT,
                            AppTheme.COSMIC_INDIGO,
                            AppTheme.FOREST_MINT,
                            AppTheme.CLASSIC_OCEAN
                        ).forEach { tPreset ->
                            val isSelected = activeTheme == tPreset
                            val presetColor = when (tPreset) {
                                AppTheme.HIGH_DENSITY -> Color(0xFF6750A4)
                                AppTheme.ONYX_MIDNIGHT -> Color(0xFF00E5FF)
                                AppTheme.COSMIC_INDIGO -> Color(0xFFB388FF)
                                AppTheme.FOREST_MINT -> Color(0xFF2ECC71)
                                AppTheme.CLASSIC_OCEAN -> Color(0xFF5DADE2)
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(34.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (isSelected) presetColor else {
                                            if (isDarkTheme) Color.DarkGray else Color(0xFFF3EDF7)
                                        }
                                    )
                                    .clickable { onThemeSelect(tPreset) }
                                    .testTag("theme_btn_${tPreset.name}"),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    tPreset.name.split("_")[1].lowercase().capitalize(),
                                    color = if (isSelected) {
                                        if (tPreset != AppTheme.HIGH_DENSITY) Color.Black else Color.White
                                    } else textColor,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Black
                                )
                            }
                        }
                    }
                }
            }
        }

        // Section B: Custom downloader Preferences & Global blocks
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = themeCard),
                shape = RoundedCornerShape(12.dp),
                border = if (activeTheme == AppTheme.HIGH_DENSITY) BorderStroke(1.dp, cardBorderColor) else null
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Firefox features & down-caps", color = textColor, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(12.dp))

                    // Parallel Threads allocation
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Max Download Threads", fontSize = 12.sp, color = textColor, fontWeight = FontWeight.Bold)
                            Text("Split source into multiple threads concurrently", fontSize = 10.sp, color = textMutedColor)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            listOf(2, 4, 8, 16).forEach { threads ->
                                val activeCount = threadCount
                                val sel = activeCount == threads
                                Box(
                                    modifier = Modifier
                                        .padding(horizontal = 3.dp)
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (sel) themePrimary else {
                                                if (isDarkTheme) Color.DarkGray else Color(0xFFF3EDF7)
                                            }
                                        )
                                        .clickable { onThreadChange(threads) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        threads.toString(),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Black,
                                        color = if (sel) {
                                            if (activeTheme == AppTheme.HIGH_DENSITY) Color.White else Color.Black
                                        } else textColor
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Ad-Blocker rule Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Adblock (Network filtering)", fontSize = 12.sp, color = textColor, fontWeight = FontWeight.Bold)
                            Text("Natively blocks known ad & tracking servers", fontSize = 10.sp, color = textMutedColor)
                        }
                        Switch(
                            checked = blockAds,
                            onCheckedChange = { onAdsToggle(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = themePrimary,
                                checkedTrackColor = themePrimary.copy(alpha = 0.3f),
                                uncheckedThumbColor = Color.Gray,
                                uncheckedTrackColor = if (isDarkTheme) Color.DarkGray else Color(0xFFEADDFF).copy(alpha = 0.4f)
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Popup window blocks toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Block Web Popups", fontSize = 12.sp, color = textColor, fontWeight = FontWeight.Bold)
                            Text("Prevent custom windows from redirecting", fontSize = 10.sp, color = textMutedColor)
                        }
                        Switch(
                            checked = blockPopups,
                            onCheckedChange = { onPopupsToggle(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = themePrimary,
                                checkedTrackColor = themePrimary.copy(alpha = 0.3f),
                                uncheckedThumbColor = Color.Gray,
                                uncheckedTrackColor = if (isDarkTheme) Color.DarkGray else Color(0xFFEADDFF).copy(alpha = 0.4f)
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Desktop vs Mobile Switcher
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val activeUA = userAgent
                        Column {
                            Text("Custom User Agent Profile", fontSize = 12.sp, color = textColor, fontWeight = FontWeight.Bold)
                            Text("Currently: $activeUA Profile", fontSize = 10.sp, color = textMutedColor)
                        }
                        Row {
                            listOf("Mobile", "Desktop", "Safari").forEach { agent ->
                                val sel = activeUA == agent
                                Box(
                                    modifier = Modifier
                                        .padding(horizontal = 3.dp)
                                        .height(26.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(
                                            if (sel) themeAccent else {
                                                if (isDarkTheme) Color.DarkGray else Color(0xFFF3EDF7)
                                            }
                                        )
                                        .clickable { onUserAgentChange(agent) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        agent,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (sel) {
                                            if (activeTheme == AppTheme.HIGH_DENSITY) Color(0xFF21005D) else Color.Black
                                        } else textColor,
                                        modifier = Modifier.padding(horizontal = 8.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Section C: SIMULATED WEBEXTENSIONS
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Simulated App Extensions (Firefox)",
                    color = if (isDarkTheme) themeAccent else Color(0xFF6750A4),
                    fontWeight = FontWeight.Black,
                    fontSize = 14.sp
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Button(
                        onClick = onImportExtension,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = themeAccent,
                            contentColor = if (isDarkTheme) Color.Black else Color.White
                        ),
                        modifier = Modifier.height(28.dp).testTag("import_zip_btn"),
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Icon(
                            Icons.Default.FolderOpen,
                            contentDescription = "Import",
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text("Import ZIP/XPI", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = { showCustomModal = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = themePrimary,
                            contentColor = if (isDarkTheme) Color.Black else Color.White
                        ),
                        modifier = Modifier.height(28.dp).testTag("install_ext_btn"),
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "Add",
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text("Add Script", fontSize = 10.sp, fontWeight = FontWeight.Black)
                    }
                }
            }
        }

        items(extensions) { ext ->
            Card(
                colors = CardDefaults.cardColors(containerColor = themeCard),
                shape = RoundedCornerShape(10.dp),
                border = if (activeTheme == AppTheme.HIGH_DENSITY) BorderStroke(1.dp, cardBorderColor) else null
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    ext.name,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = textColor
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    "v" + ext.version,
                                    fontSize = 9.sp,
                                    color = if (isDarkTheme) themeAccent else Color(0xFF6750A4),
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .background(
                                            if (isDarkTheme) themeAccent.copy(alpha = 0.15f) else Color(0xFFEADDFF),
                                            RoundedCornerShape(4.dp)
                                        )
                                        .padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                            Text(
                                ext.description,
                                fontSize = 11.sp,
                                color = textMutedColor,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                        
                        Switch(
                            checked = ext.enabled,
                            onCheckedChange = { checked -> onExtToggle(ext.id, checked) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = themePrimary,
                                checkedTrackColor = themePrimary.copy(alpha = 0.3f),
                                uncheckedThumbColor = Color.Gray,
                                uncheckedTrackColor = if (isDarkTheme) Color.DarkGray else Color(0xFFEADDFF).copy(alpha = 0.4f)
                            )
                        )
                    }
                    
                    if (!ext.isBuiltIn) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Text(
                                "Uninstall",
                                color = Color.Red,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .clickable { onDeleteExt(ext.id) }
                                    .padding(vertical = 4.dp, horizontal = 8.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    // Modal Sheet mock for custom Extension inject configuration
    if (showCustomModal) {
        AlertDialog(
            onDismissRequest = { showCustomModal = false },
            title = { Text("Install JavaScript Script", color = textColor, fontWeight = FontWeight.Bold, fontSize = 15.sp) },
            text = {
                var newName by remember { mutableStateOf("") }
                var newDesc by remember { mutableStateOf("") }
                var newScript by remember { mutableStateOf("") }
                
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Manually code active javascript content scripts to run on page completions.", fontSize = 11.sp, color = textMutedColor)
                    
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("Script Name", fontSize = 11.sp, color = textMutedColor) },
                        singleLine = true,
                        textStyle = LocalTextStyle.current.copy(fontSize = 12.sp, color = textColor),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = textColor,
                            unfocusedTextColor = textColor,
                            focusedBorderColor = themePrimary,
                            unfocusedBorderColor = cardBorderColor
                        )
                    )
                    OutlinedTextField(
                        value = newDesc,
                        onValueChange = { newDesc = it },
                        label = { Text("Description", fontSize = 11.sp, color = textMutedColor) },
                        singleLine = true,
                        textStyle = LocalTextStyle.current.copy(fontSize = 12.sp, color = textColor),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = textColor,
                            unfocusedTextColor = textColor,
                            focusedBorderColor = themePrimary,
                            unfocusedBorderColor = cardBorderColor
                        )
                    )
                    OutlinedTextField(
                        value = newScript,
                        onValueChange = { newScript = it },
                        label = { Text("JavaScript Action", fontSize = 11.sp, color = textMutedColor) },
                        modifier = Modifier.height(110.dp),
                        textStyle = FontFamily.Monospace.let { LocalTextStyle.current.copy(fontSize = 10.sp, fontFamily = it, color = textColor) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = textColor,
                            unfocusedTextColor = textColor,
                            focusedBorderColor = themePrimary,
                            unfocusedBorderColor = cardBorderColor
                        )
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showCustomModal = false }) {
                            Text("Cancel", color = textMutedColor)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (newName.isNotEmpty() && newScript.isNotEmpty()) {
                                    onAddCustomExt(newName, newDesc.ifEmpty { "Custom inject actions" }, newScript)
                                    showCustomModal = false
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = themePrimary,
                                contentColor = if (isDarkTheme) Color.Black else Color.White
                            )
                        ) {
                            Text("Install", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            },
            confirmButton = {},
            containerColor = themeCard
        )
    }
}

// FORMATTING FILE SIZE IN DESIGN SPECIFIERS
fun getFormattedSize(bytes: Long): String {
    if (bytes <= 0) return "0.0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (log10(bytes.toDouble()) / log10(1024.0)).toInt()
    return String.format("%.2f %s", bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}

fun getStatusTag(job: DownloadJob): String {
    return when (job.status) {
        "PENDING" -> "Queued..."
        "DOWNLOADING" -> "Downloading (${job.activeThreads} streams)"
        "PAUSED" -> "Paused"
        "COMPLETED" -> "Completed"
        "FAILED" -> "Failed: " + (job.errorMessage ?: "")
        else -> job.status
    }
}

fun getStatusColor(job: DownloadJob): Color {
    return when (job.status) {
        "PENDING" -> Color.Gray
        "DOWNLOADING" -> Color(0xFF00E5FF)
        "PAUSED" -> Color.Yellow
        "COMPLETED" -> Color(0xFF4CAF50)
        "FAILED" -> Color.Red
        else -> Color.White
    }
}

fun rawValid(list: Any?): Boolean {
    return list != null && (list is List<*>) && list.isNotEmpty()
}

@Composable
fun DeveloperIdentityPanel(
    themeCard: Color,
    themePrimary: Color,
    themeAccent: Color,
    textColor: Color,
    textMutedColor: Color,
    isDarkTheme: Boolean
) {
    var showTerminal by remember { mutableStateOf(false) }
    val context = LocalContext.current
    
    Card(
        colors = CardDefaults.cardColors(containerColor = themeCard),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .testTag("sk_jack_profile_card")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Neon Green cyber title line
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF00FF66)) // Glowing neon green dot
                )
                Text(
                    "CORE SYSTEM DEVELOPER PORTFOLIO",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFF00FF66), // Cyber green
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                    letterSpacing = 1.sp
                )
            }
            
            Spacer(modifier = Modifier.height(10.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Interactive Hacker Avatar purely drawn in Compose Canvas!
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF0C0F0C))
                        .border(1.5.dp, Color(0xFF00FF66), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val w = size.width
                        val h = size.height
                        
                        // Cyber Grid background Lines
                        for (i in 1..4) {
                            val x = (w / 5) * i
                            val y = (h / 5) * i
                            drawLine(
                                color = Color(0xFF00290F),
                                start = Offset(x, 0f),
                                end = Offset(x, h),
                                strokeWidth = 1.5f
                            )
                            drawLine(
                                color = Color(0xFF00290F),
                                start = Offset(0f, y),
                                end = Offset(w, y),
                                strokeWidth = 1.5f
                            )
                        }
                        
                        // Hacker hood outline in Compose Canvas (like the uploaded green/dark images!)
                        val path = androidx.compose.ui.graphics.Path().apply {
                            moveTo(w * 0.15f, h * 0.9f)
                            cubicTo(w * 0.15f, h * 0.45f, w * 0.5f, h * 0.12f, w * 0.5f, h * 0.12f)
                            cubicTo(w * 0.5f, h * 0.12f, w * 0.85f, h * 0.45f, w * 0.85f, h * 0.9f)
                            lineTo(w * 0.75f, h * 0.95f)
                            lineTo(w * 0.25f, h * 0.95f)
                            close()
                        }
                        drawPath(
                            path = path,
                            color = Color(0xFF102115),
                            style = androidx.compose.ui.graphics.drawscope.Fill
                        )
                        drawPath(
                            path = path,
                            color = Color(0xFF00FF66),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.5f)
                        )
                        
                        // Shadow inside hood for mystery coder
                        val innerShadow = androidx.compose.ui.graphics.Path().apply {
                            moveTo(w * 0.3f, h * 0.8f)
                            cubicTo(w * 0.3f, h * 0.52f, w * 0.5f, h * 0.35f, w * 0.5f, h * 0.35f)
                            cubicTo(w * 0.5f, h * 0.35f, w * 0.7f, h * 0.52f, w * 0.7f, h * 0.8f)
                            lineTo(w * 0.65f, h * 0.85f)
                            lineTo(w * 0.35f, h * 0.85f)
                            close()
                        }
                        drawPath(
                            path = innerShadow,
                            color = Color(0xFF050906),
                            style = androidx.compose.ui.graphics.drawscope.Fill
                        )
                    }
                    
                    // Name label on hoodie chest overlay
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 6.dp)
                            .background(Color.Black.copy(alpha = 0.85f), RoundedCornerShape(4.dp))
                            .border(0.5.dp, Color(0xFF00FF66), RoundedCornerShape(4.dp))
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "SK JACK",
                            fontSize = 8.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF00FF66),
                            letterSpacing = 0.5.sp
                        )
                    }
                }
                
                // SK Jack Credits text
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "SK Jack",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Black,
                            color = textColor
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .background(Color(0xFF00FF66).copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        ) {
                            Text(
                                "FOUNDER",
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFF00FF66)
                            )
                        }
                    }
                    Text(
                        "Jack (Full Name: SK Jack)",
                        fontSize = 11.sp,
                        color = Color(0xFF00FF66),
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Authorization Node: APEX-900456",
                        fontSize = 9.sp,
                        color = textMutedColor
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(10.dp))
            
            // Slogan & details inspired by the green and dark hooded hack concept files!
            Text(
                "Creator of the core multi-threaded high-efficiency media extraction subsystem, adblock network layers, local sandboxed extension engine, and secure download protocols for Apex Browser.",
                fontSize = 10.sp,
                color = textMutedColor,
                lineHeight = 14.sp
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Buttons block
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { showTerminal = !showTerminal },
                    modifier = Modifier.weight(1f).height(32.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (showTerminal) Color(0xFF003311) else (if (isDarkTheme) Color.DarkGray else Color(0xFFE0E0E0)),
                        contentColor = if (showTerminal) Color(0xFF00FF66) else textColor
                    ),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = "Terminal OS",
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (showTerminal) "Close Logs" else "Kernel Logs", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
                
                Button(
                    onClick = {
                        android.widget.Toast.makeText(
                            context,
                            "⚡ Verified System Developer: SK Jack (Jack). Secure connection authorization: GRANTED.",
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                    },
                    modifier = Modifier.weight(1.3f).height(32.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF00FF66),
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = "Verified Identity",
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Auth Developer", fontSize = 10.sp, fontWeight = FontWeight.Black)
                }
            }
            
            // Terminal Expandable Container
            if (showTerminal) {
                Spacer(modifier = Modifier.height(10.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF060906), RoundedCornerShape(6.dp))
                        .border(1.dp, Color(0xFF009944), RoundedCornerShape(6.dp))
                        .padding(8.dp)
                ) {
                    val terminalLogs = listOf(
                        "SYSTEM: Shell connection established with node SK_JACK...",
                        "ROOT_ACCESS: PORT-8090 active [SECURE_ENCRYPTED]",
                        "COMPILE: Integrating layout engines with multi-threading...",
                        "ENGINE_LOG: Core Sniffer active [ALL FILE EXTENSIONS GRABBER: ACTIVE]",
                        "FOUNDER: Jack (SK Jack), Core Platform Developer of Apex Engine.",
                        "BUILD: Safe and optimized compilation: COMPILED SUCCESSFULLY."
                    )
                    terminalLogs.forEach { log ->
                        Text(
                            text = log,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 8.sp,
                            color = Color(0xFF00FF66),
                            modifier = Modifier.padding(bottom = 3.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun GeminiChatPanel(
    viewModel: BrowserViewModel,
    themeBg: Color,
    themeCard: Color,
    themePrimary: Color,
    themeAccent: Color,
    textColor: Color,
    textMutedColor: Color,
    isDarkTheme: Boolean
) {
    val chatHistory by viewModel.chatHistory.collectAsStateWithLifecycle()
    val isLoading by viewModel.isChatLoading.collectAsStateWithLifecycle()
    var userPrompt by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current
    var isTerminalOpen by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(themeBg)
            .padding(14.dp)
    ) {
        // Futuristic cyber-neon banner header for Gemini/Apex AI
        Card(
            colors = CardDefaults.cardColors(containerColor = if (isDarkTheme) Color(0xFF0C100D) else themeCard),
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    1.dp,
                    if (isDarkTheme) Color(0xFF00FF66) else themePrimary,
                    RoundedCornerShape(8.dp)
                ),
            shape = RoundedCornerShape(8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF00FF66))
                        )
                        Text(
                            "APEX CENTRAL CYBER-INTELLIGENCE",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            color = if (isDarkTheme) Color(0xFF00FF66) else themePrimary,
                            letterSpacing = 0.5.sp
                        )
                    }
                    Text(
                        "Ask Gemini to control settings, change theme, search, or automate operations.",
                        fontSize = 9.sp,
                        color = textMutedColor
                    )
                }

                // Interactive Termux sandbox toggle button
                Button(
                    onClick = { isTerminalOpen = !isTerminalOpen },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isTerminalOpen) Color(0xFF990000) else Color(0xFF003311),
                        contentColor = Color(0xFF00FF66)
                    ),
                    shape = RoundedCornerShape(4.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier.height(28.dp)
                ) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = "Terminal Sandbox",
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        if (isTerminalOpen) "Exit Shell" else "Terminal",
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (isTerminalOpen) {
            // Render the highly requested apt/pkg Termux Shell environment!
            MockTermuxPanel(
                themeCard = themeCard,
                isDarkTheme = isDarkTheme,
                textColor = textColor,
                textMutedColor = textMutedColor
            )
        } else {
            // Main Gemini Chat feed!
            Box(modifier = Modifier.weight(1f)) {
                val listState = rememberLazyListState()
                
                // Auto-scroll on new message
                LaunchedEffect(chatHistory.size) {
                    if (chatHistory.isNotEmpty()) {
                        listState.animateScrollToItem(chatHistory.size - 1)
                    }
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 8.dp)
                ) {
                    items(chatHistory) { msg ->
                        ChatBubbleItem(
                            msg = msg,
                            themeCard = themeCard,
                            themePrimary = themePrimary,
                            themeAccent = themeAccent,
                            textColor = textColor,
                            textMutedColor = textMutedColor,
                            isDarkTheme = isDarkTheme,
                            onExecuteAction = { action -> viewModel.executeProposedAction(action) }
                        )
                    }
                    
                    if (isLoading) {
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.Start,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .background(
                                            if (isDarkTheme) Color(0xFF1B1B1B) else themeCard,
                                            RoundedCornerShape(8.dp)
                                        )
                                        .padding(10.dp)
                                ) {
                                    Text(
                                        "Apex AI is computing neural nodes...",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isDarkTheme) Color(0xFF00FF66) else themePrimary
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Input Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = userPrompt,
                    onValueChange = { userPrompt = it },
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, color = textColor),
                    placeholder = {
                        Text(
                            "Ask Gemini (e.g., 'Change theme to indigo' or 'can we add apt?')",
                            fontSize = 10.sp
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("gemini_chat_input"),
                    shape = RoundedCornerShape(10.dp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = textColor,
                        unfocusedTextColor = textColor,
                        focusedBorderColor = if (isDarkTheme) Color(0xFF00FF66) else themePrimary,
                        unfocusedBorderColor = textMutedColor
                    )
                )

                Button(
                    onClick = {
                        if (userPrompt.trim().isNotEmpty()) {
                            viewModel.sendChatMessage(userPrompt)
                            userPrompt = ""
                            focusManager.clearFocus()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isDarkTheme) Color(0xFF00FF66) else themePrimary,
                        contentColor = if (isDarkTheme) Color.Black else Color.White
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.height(44.dp).testTag("gemini_chat_send_btn")
                ) {
                    Text("Send", fontSize = 11.sp, fontWeight = FontWeight.Black)
                }
            }
        }
    }
}

@Composable
fun ChatBubbleItem(
    msg: ChatMessage,
    themeCard: Color,
    themePrimary: Color,
    themeAccent: Color,
    textColor: Color,
    textMutedColor: Color,
    isDarkTheme: Boolean,
    onExecuteAction: (GeminiAppAction) -> Unit
) {
    val isUser = msg.isUser
    val alignment = if (isUser) Alignment.End else Alignment.Start
    val bubbleBg = if (isUser) {
        if (isDarkTheme) Color(0xFF0A2E16) else themePrimary.copy(alpha = 0.15f)
    } else {
        if (isDarkTheme) Color(0xFF1E1E1E) else themeCard
    }
    
    val bubbleBorderColor = if (isUser) {
        if (isDarkTheme) Color(0xFF00FF66) else themePrimary
    } else {
        if (isDarkTheme) Color.DarkGray else Color(0xFFCAC4D0)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalAlignment = alignment
    ) {
        // Author label
        Text(
            text = if (isUser) "System Operator (Jack)" else "Apex AI Kernel",
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
            color = if (isUser) (if (isDarkTheme) Color(0xFF00FF66) else themePrimary) else textMutedColor,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
        )

        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .background(bubbleBg, RoundedCornerShape(10.dp))
                .border(0.7.dp, bubbleBorderColor, RoundedCornerShape(10.dp))
                .padding(10.dp)
        ) {
            Column {
                Text(
                    text = msg.text,
                    fontSize = 11.sp,
                    color = textColor,
                    lineHeight = 15.sp
                )

                // Render dynamic automation proposal actions!
                if (msg.proposedActions.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        "PROPOSED OPERATIONS (Click to execute):",
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Black,
                        color = if (isDarkTheme) Color(0xFF00FF66) else themePrimary,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    msg.proposedActions.forEach { action ->
                        var localExecuted by remember { mutableStateOf(action.executed) }
                        Button(
                            onClick = {
                                if (!localExecuted) {
                                    onExecuteAction(action)
                                    action.executed = true
                                    localExecuted = true
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (localExecuted) Color.DarkGray else (if (isDarkTheme) Color(0xFF004D1A) else themeAccent),
                                contentColor = if (localExecuted) Color.Gray else (if (isDarkTheme) Color(0xFF00FF66) else Color.White)
                            ),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp)
                                .height(26.dp),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                if (localExecuted) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = "Done",
                                        modifier = Modifier.size(10.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("[EXECUTED] ${action.title}", fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                } else {
                                    Icon(
                                        Icons.Default.PlayArrow,
                                        contentDescription = "Run",
                                        modifier = Modifier.size(10.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Run Action: ${action.title}${if (action.argument.isNotEmpty()) " (${action.argument})" else ""}", fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MockTermuxPanel(
    themeCard: Color,
    isDarkTheme: Boolean,
    textColor: Color,
    textMutedColor: Color
) {
    val terminalLines = remember {
        mutableStateListOf(
            "Welcome to APEX CLI Sandbox Node. Shell active.",
            "Founders authorized: Jack (SK Jack). Code Node APEX-900456.",
            "Type 'help' to explore sandbox modules or type 'apt install' / 'pkg update'",
            ""
        )
    }
    var currentTermInput by remember { mutableStateOf("") }
    val scrollState = rememberLazyListState()

    LaunchedEffect(terminalLines.size) {
        if (terminalLines.isNotEmpty()) {
            scrollState.animateScrollToItem(terminalLines.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF050805))
            .border(1.2.dp, Color(0xFF009933), RoundedCornerShape(8.dp))
            .padding(10.dp)
    ) {
        Text(
            "APEX SANDBOX TERMINAL ENVIRONMENT v1.0.4",
            fontFamily = FontFamily.Monospace,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF00FF66),
            modifier = Modifier.padding(bottom = 6.dp)
        )

        LazyColumn(
            state = scrollState,
            modifier = Modifier
                .weight(1f)
                .background(Color.Black)
                .padding(6.dp)
        ) {
            items(terminalLines) { line ->
                Text(
                    text = line,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    color = if (line.startsWith("ERROR:") || line.startsWith("  [CRITICAL]")) Color.Red 
                            else if (line.startsWith("SUCCESS:") || line.startsWith("  -")) Color(0xFF00FF66)
                            else if (line.startsWith("$ ")) Color.Cyan
                            else Color(0xFF00CC44)
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "$ ",
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = Color.Cyan
            )
            Spacer(modifier = Modifier.width(4.dp))
            BasicTextField(
                value = currentTermInput,
                onValueChange = { currentTermInput = it },
                textStyle = androidx.compose.ui.text.TextStyle(
                    color = Color(0xFF00FF66),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp
                ),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(Color(0xFF00FF66)),
                singleLine = true,
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = {
                        val input = currentTermInput.trim()
                        if (input.isNotEmpty()) {
                            terminalLines.add("$ $input")
                            processTermCommand(input, terminalLines)
                            currentTermInput = ""
                        }
                    }
                )
            )

            Button(
                onClick = {
                    val input = currentTermInput.trim()
                    if (input.isNotEmpty()) {
                        terminalLines.add("$ $input")
                        processTermCommand(input, terminalLines)
                        currentTermInput = ""
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF003311),
                    contentColor = Color(0xFF00FF66)
                ),
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier.height(28.dp),
                contentPadding = PaddingValues(horizontal = 10.dp)
            ) {
                Text("RUN", fontSize = 8.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            }
        }
    }
}

private fun processTermCommand(cmd: String, lines: MutableList<String>) {
    val cleanCmd = cmd.lowercase()
    when {
        cleanCmd == "help" -> {
            lines.add("APEX BOX CLI help panel - Sandboxed Node:")
            lines.add("  help              - Display this documentation menu")
            lines.add("  neofetch          - Print core cyber system configuration summary")
            lines.add("  ls                - Lists active mock directory elements")
            lines.add("  apt install [pkg] - Mock Linux package installation sandbox")
            lines.add("  pkg update        - Updates mock node repository structures")
            lines.add("  clear             - Clear display log output cache")
        }
        cleanCmd == "neofetch" -> {
            lines.add("     .---.       APEX BROWSING ENGINE CORE")
            lines.add("    /     \\      OS: Android Shell Sandbox")
            lines.add("    \\.---./      Kernel: ApexSystem Core 1.0.4")
            lines.add("    /     \\      Founder: Jack (SK Jack)")
            lines.add("    \\     /      Memory: 1.57 GB / 8.00 GB")
            lines.add("     '---'       Network: Encrypted Node APEX-900456")
        }
        cleanCmd == "ls" -> {
            lines.add("  bookmarks/   downloads/   extensions/   kernel.ko   config.json")
        }
        cleanCmd == "clear" -> {
            lines.clear()
            lines.add("Apex Sandbox Buffer Cleared.")
        }
        cleanCmd.startsWith("apt install") -> {
            val parts = cmd.split(" ")
            val target = if (parts.size > 2) parts[2] else "package"
            lines.add("SUCCESS: Fetching indices from APEX update server...")
            lines.add("SUCCESS: Resolved packages dependency graph loops successfully.")
            lines.add("SUCCESS: Virtualizing sandbox filesystem inside standard Android APK...")
            lines.add("SUCCESS: Installed target package successfully: $target")
        }
        cleanCmd == "pkg update" -> {
            lines.add("SUCCESS: Contacting repository servers at node.apex.net...")
            lines.add("SUCCESS: All virtualized packages indexed correctly.")
        }
        else -> {
            lines.add("ERROR: Command unrecognized or requires root. Type 'help' to see local virtual commands.")
        }
    }
}
