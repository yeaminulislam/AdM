package com.example.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [DownloadJob::class, Bookmark::class, UserExtension::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun downloadDao(): DownloadDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun extensionDao(): ExtensionDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "apex_browser_db"
                )
                .addCallback(DatabaseCallback(context))
                .build()
                INSTANCE = instance
                instance
            }
        }
    }

    private class DatabaseCallback(val context: Context) : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            INSTANCE?.let { database ->
                CoroutineScope(Dispatchers.IO).launch {
                    populateInitialData(database)
                }
            }
        }

        private suspend fun populateInitialData(database: AppDatabase) {
            // Populate Bookmarks
            val bookmarkDao = database.bookmarkDao()
            bookmarkDao.insertBookmark(Bookmark(title = "Google", url = "https://www.google.com"))
            bookmarkDao.insertBookmark(Bookmark(title = "DuckDuckGo", url = "https://duckduckgo.com"))
            bookmarkDao.insertBookmark(Bookmark(title = "Internet Archive", url = "https://archive.org"))
            bookmarkDao.insertBookmark(Bookmark(title = "GitHub", url = "https://github.com"))

            // Populate Extensions
            val extensionDao = database.extensionDao()
            
            // 1. uBlock Lite AdBlocker
            extensionDao.insertExtension(
                UserExtension(
                    id = "ublock-lite",
                    name = "uBlock Lite Simulator",
                    description = "Blocks popups, tracking, and injects clean stylesheets to hide generic advertising containers.",
                    version = "1.0.2",
                    enabled = true,
                    isBuiltIn = true,
                    script = """
                        (function() {
                            console.log("uBlock Lite Loaded!");
                            // Block known ad popup methods
                            window.open = function(url) {
                                console.log("Blocked popup window to: " + url);
                                return null;
                            };
                            
                            // Inject CSS to hide common ad frames and classes
                            const adSelectors = [
                                'iframe[src*="doubleclick"]',
                                'iframe[src*="googleads"]',
                                'iframe[src*="adsystem"]',
                                '.ads', '.advertisement', '.ad-box', '.ad-banner',
                                '[id*="google_ads_iframe"]',
                                '.banner-ad', '[class*="sponsored"]'
                            ];
                            
                            const style = document.createElement('style');
                            style.innerHTML = adSelectors.join(', ') + ' { display: none !important; pointer-events: none !important; height: 0 !important; width: 0 !important; opacity: 0 !important; }';
                            document.head.appendChild(style);
                        })();
                    """.trimIndent()
                )
            )

            // 2. Dark Reader
            extensionDao.insertExtension(
                UserExtension(
                    id = "dark-reader",
                    name = "Dark Reader Theme",
                    description = "Adapts contrast and background color schemes beautifully, transforming websites into eye-friendly dark mode styles.",
                    version = "4.9.5",
                    enabled = false, // Disabled by default, can be toggled in settings
                    isBuiltIn = true,
                    script = """
                        (function() {
                            console.log("Dark Reader Loaded!");
                            const style = document.createElement('style');
                            style.id = "dark-reader-styles";
                            style.innerHTML = `
                                html, body, div, p, span, section, header, nav, footer, article, aside, main {
                                    background-color: #121212 !important;
                                    color: #e2e2e2 !important;
                                    border-color: #2b2b2b !important;
                                }
                                h1, h2, h3, h4, h5, h6 {
                                    color: #ffffff !important;
                                }
                                input, textarea, select {
                                    background-color: #1e1e1e !important;
                                    color: #ffffff !important;
                                    border: 1px solid #444444 !important;
                                }
                                a {
                                    color: #8ab4f8 !important;
                                }
                                button {
                                    background-color: #242424 !important;
                                    color: #ffffff !important;
                                }
                                img {
                                    opacity: 0.82;
                                    transition: opacity 0.5s;
                                }
                                img:hover {
                                    opacity: 1;
                                }
                            `;
                            document.head.appendChild(style);
                        })();
                    """.trimIndent()
                )
            )

            // 3. Media Sniffer Helper
            extensionDao.insertExtension(
                UserExtension(
                    id = "media-sniffer",
                    name = "Apex Video Grabber",
                    description = "Extracts dynamically embedded MP4 or MP3 sources from dynamic video players and logs files directly for download.",
                    version = "1.0.0",
                    enabled = true,
                    isBuiltIn = true,
                    script = """
                        (function() {
                            console.log("Media Sniffer Extension Active!");
                            // Periodic scanning of video & audio tags
                            setInterval(function() {
                                const mediaFiles = [];
                                document.querySelectorAll('video, audio, source').forEach(el => {
                                    const src = el.src || el.currentSrc;
                                    if (src && (src.startsWith('http') || src.startsWith('blob'))) {
                                        mediaFiles.push(src);
                                    }
                                });
                                if (mediaFiles.length > 0) {
                                    // Send messages to the Native host (Android JavaScriptInterface)
                                    if (window.ApexNative) {
                                        window.ApexNative.onVideosDetected(JSON.stringify(mediaFiles));
                                    }
                                }
                            }, 3000);
                        })();
                    """.trimIndent()
                )
            )
        }
    }
}
