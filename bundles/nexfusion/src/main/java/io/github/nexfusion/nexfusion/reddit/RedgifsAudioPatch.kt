package io.github.nexfusion.nexfusion.reddit

import android.net.Uri
import android.os.Looper
import android.util.Base64
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import io.github.nexfusion.PatcherLog
import io.github.nexfusion.patch
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.Collections
import java.util.LinkedHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

// Ported from Morphe (commit b66471189b52974ead5666ac033b6b2ec867de2e, GPL-3.0).
// Plays the original Redgifs video with audio instead of Reddit's silent re-encoded copy.

internal object RedgifsAudioHelper {
    private const val API_URL = "https://api.redgifs.com/v2/"
    private const val USER_AGENT = "Mozilla/5.0 (Linux; Android) Morphe-Reddit"
    private const val TOKEN_LIFETIME_MS = 23 * 60 * 60 * 1000L
    private const val TIMEOUT_MS = 5_000
    private const val PLAYER_WAIT_MS = 8_000L
    private const val FAILED_LOOKUP_RETRY_MS = 30_000L
    private const val MAX_CACHE_SIZE = 500

    const val PLACEHOLDER_HOST = "media.redgifs.com"
    const val PLACEHOLDER_PATH = "/morphe-pending/"
    const val PLACEHOLDER_FALLBACK_PARAM = "fallback"
    const val METADATA_CELL_ID_PREFIX = "MetadataCell-"

    private const val DOWNLOAD_WAIT_MS = 5_000L

    private val REDGIFS_ID_REGEX = Regex("^[a-z0-9]+$")
    private val executor = Executors.newFixedThreadPool(2)

    @Volatile
    var preferHd = false

    class Lookup(val id: String) {
        val createdAt = System.currentTimeMillis()
        val done = CountDownLatch(1)
        @Volatile var sdUrl: String? = null
        @Volatile var hdUrl: String? = null

        fun getUrl(): String? {
            val sd = sdUrl
            val hd = hdUrl
            return if (preferHd) hd ?: sd else sd ?: hd
        }

        fun isFailed(): Boolean = done.count == 0L && sdUrl == null && hdUrl == null
    }

    private fun <K, V> createCache(maxSize: Int = MAX_CACHE_SIZE): MutableMap<K, V> {
        return Collections.synchronizedMap(object : LinkedHashMap<K, V>(64, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?): Boolean {
                return size > maxSize
            }
        })
    }

    val lookups = createCache<String, Lookup>()
    val postIdToRedgifsId = createCache<String, String>()
    val redditVideoIdToRedgifsId = createCache<String, String>()

    @Volatile
    private var token: String? = null
    @Volatile
    private var tokenExpires: Long = 0L

    fun getRedgifsId(url: String?): String? {
        if (url == null) return null
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return null
        val host = uri.host ?: return null
        if (host != "redgifs.com" && !host.endsWith(".redgifs.com")) return null

        val segments = uri.pathSegments
        val id = when {
            segments.size == 2 && (segments[0] == "watch" || segments[0] == "ifr") -> segments[1]
            host == "media.redgifs.com" && segments.size == 1 -> segments[0].split('-', '.')[0]
            else -> null
        }?.lowercase() ?: return null

        return if (REDGIFS_ID_REGEX.matches(id)) id else null
    }

    fun getRedditVideoId(url: String?): String? {
        if (url == null) return null
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return null
        val host = uri.host ?: return null
        if (!host.endsWith("v.redd.it") && host != "packaged-media.redd.it") return null

        val segments = uri.pathSegments
        return segments.firstOrNull()
    }

    fun recordRedditVideo(url: String?, id: String) {
        val redditVideoId = getRedditVideoId(url)
        if (redditVideoId != null) {
            redditVideoIdToRedgifsId[redditVideoId] = id
        }
    }

    fun getLookup(id: String): Lookup {
        synchronized(lookups) {
            val existing = lookups[id]
            if (existing != null && (!existing.isFailed() || System.currentTimeMillis() - existing.createdAt <= FAILED_LOOKUP_RETRY_MS)) {
                return existing
            }
            val lookup = Lookup(id)
            lookups[id] = lookup
            executor.execute { fetchUrl(lookup) }
            return lookup
        }
    }

    private fun fetchUrl(lookup: Lookup) {
        try {
            val json = fetchGif(lookup.id)
            val urls = json.optJSONObject("urls")
            if (urls != null) {
                lookup.sdUrl = urls.optString("sd").takeIf { it.isNotEmpty() }
                lookup.hdUrl = urls.optString("hd").takeIf { it.isNotEmpty() }
            }
        } catch (t: Throwable) {
            PatcherLog.info { "RedgifsAudio: lookup failed for ${lookup.id}: ${t.message}" }
        } finally {
            lookup.done.countDown()
        }
    }

    private fun fetchGif(id: String): JSONObject {
        for (attempt in 0..1) {
            val currentToken = getAuthToken()
            val conn = openConnection("${API_URL}gifs/$id")
            conn.setRequestProperty("Authorization", "Bearer $currentToken")

            val responseCode = conn.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val body = conn.inputStream.bufferedReader().use(BufferedReader::readText)
                conn.disconnect()
                return JSONObject(body).getJSONObject("gif")
            }
            conn.disconnect()
            if (responseCode != HttpURLConnection.HTTP_UNAUTHORIZED && responseCode != HttpURLConnection.HTTP_FORBIDDEN) {
                throw java.io.IOException("Redgifs HTTP $responseCode")
            }
            invalidateToken(currentToken)
        }
        throw java.io.IOException("Redgifs token rejected")
    }

    @Synchronized
    private fun getAuthToken(): String {
        val cached = token
        if (cached != null && System.currentTimeMillis() < tokenExpires) {
            return cached
        }
        val conn = openConnection("${API_URL}auth/temporary")
        val body = conn.inputStream.bufferedReader().use(BufferedReader::readText)
        conn.disconnect()
        val newToken = JSONObject(body).getString("token")
        token = newToken
        tokenExpires = System.currentTimeMillis() + TOKEN_LIFETIME_MS
        return newToken
    }

    @Synchronized
    private fun invalidateToken(rejected: String) {
        if (token == rejected) {
            token = null
        }
    }

    private fun openConnection(urlString: String): HttpURLConnection {
        val conn = URL(urlString).openConnection() as HttpURLConnection
        conn.connectTimeout = TIMEOUT_MS
        conn.readTimeout = TIMEOUT_MS
        conn.setRequestProperty("User-Agent", USER_AGENT)
        conn.setRequestProperty("Accept", "application/json")
        return conn
    }

    fun getPlaybackUrl(url: String?): String? {
        if (url == null) return null
        val redditVideoId = getRedditVideoId(url) ?: return url
        val redgifsId = redditVideoIdToRedgifsId[redditVideoId] ?: return url

        val lookup = getLookup(redgifsId)
        val redgifsUrl = lookup.getUrl()
        if (redgifsUrl != null) return redgifsUrl
        if (lookup.isFailed()) return url

        val fallbackUrl = if (url.contains(".mp4")) {
            url
        } else {
            "https://v.redd.it/$redditVideoId/CMAF_220.mp4"
        }

        return Uri.Builder()
            .scheme("https")
            .authority(PLACEHOLDER_HOST)
            .path("$PLACEHOLDER_PATH$redgifsId.mp4")
            .appendQueryParameter(PLACEHOLDER_FALLBACK_PARAM, fallbackUrl)
            .build()
            .toString()
    }

    fun getDownloadUri(uri: Uri): Uri {
        val url = getDownloadUrl(uri.toString())
        return if (url == uri.toString()) uri else Uri.parse(url)
    }

    private fun getDownloadUrl(url: String): String {
        val redditVideoId = getRedditVideoId(url) ?: return url
        val id = redditVideoIdToRedgifsId[redditVideoId] ?: return url

        val lookup = getLookup(id)
        // Downloads are usually enqueued on the main thread, where the lookup can't be awaited.
        // It started when the post was loaded, so it's usually done by now.
        if (Looper.getMainLooper().thread != Thread.currentThread()) {
            runCatching { lookup.done.await(DOWNLOAD_WAIT_MS, TimeUnit.MILLISECONDS) }
        }

        // Downloads aren't streamed, so the HD quality setting doesn't apply.
        val redgifsUrl = lookup.hdUrl ?: lookup.sdUrl
        if (redgifsUrl == null) {
            PatcherLog.info { "RedgifsAudio: video url not found for download, using Reddit's video: $id" }
            return url
        }

        return redgifsUrl
    }

    fun resolvePlaceholderUri(uri: Uri): Uri {
        if (PLACEHOLDER_HOST != uri.host) return uri
        val path = uri.path ?: return uri
        if (!path.startsWith(PLACEHOLDER_PATH) || !path.endsWith(".mp4")) return uri

        val id = path.substring(PLACEHOLDER_PATH.length, path.length - ".mp4".length)
        val lookup = getLookup(id)

        if (Looper.getMainLooper().thread != Thread.currentThread()) {
            runCatching { lookup.done.await(PLAYER_WAIT_MS, TimeUnit.MILLISECONDS) }
        }

        var resolvedUrl = lookup.getUrl()
        if (resolvedUrl == null) {
            PatcherLog.info { "RedgifsAudio: video not resolved in time, using fallback: $id" }
            resolvedUrl = uri.getQueryParameter(PLACEHOLDER_FALLBACK_PARAM)
        }

        return resolvedUrl?.let { Uri.parse(it) } ?: uri
    }
}

val RedgifsAudio = patch(
    name = "Play Redgifs with audio",
    description = "Plays original Redgifs video with audio instead of Reddit's silent copy.",
) {
    // 1. Post creation: map post's Reddit video to Redgifs ID and prefetch
    val linkClass = LinkToStringFingerprint.memberOrNull?.declaringClass
        ?: runCatching { classLoader.loadClass("com.reddit.domain.model.Link") }.getOrNull()
    if (linkClass != null) {
        XposedBridge.hookAllConstructors(linkClass, object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val link = param.thisObject ?: return
                val url = runCatching {
                    (XposedHelpers.callMethod(link, "getUrl") as? String)
                        ?: (XposedHelpers.getObjectField(link, "url") as? String)
                }.getOrNull() ?: return

                val redgifsId = RedgifsAudioHelper.getRedgifsId(url) ?: return

                val preview = runCatching {
                    XposedHelpers.callMethod(link, "getPreview")
                        ?: XposedHelpers.getObjectField(link, "preview")
                }.getOrNull()
                val video = preview?.let { p ->
                    runCatching {
                        XposedHelpers.callMethod(p, "getRedditVideoPreview")
                            ?: XposedHelpers.getObjectField(p, "redditVideoPreview")
                    }.getOrNull()
                }

                if (video != null) {
                    val dash = runCatching { XposedHelpers.callMethod(video, "getDashUrl") as? String }.getOrNull()
                    val hls = runCatching { XposedHelpers.callMethod(video, "getHlsUrl") as? String }.getOrNull()
                    val fallback = runCatching { XposedHelpers.callMethod(video, "getFallBackUrl") as? String }.getOrNull()
                    RedgifsAudioHelper.recordRedditVideo(dash, redgifsId)
                    RedgifsAudioHelper.recordRedditVideo(hls, redgifsId)
                    RedgifsAudioHelper.recordRedditVideo(fallback, redgifsId)
                }
                RedgifsAudioHelper.getLookup(redgifsId)
            }
        })
    }

    // 2. Feed post header cell: map post ID to Redgifs ID
    MetadataCellToStringFingerprint.memberOrNull?.declaringClass?.let { cellClass ->
        XposedBridge.hookAllConstructors(cellClass, object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val cell = param.thisObject ?: return
                var cellId: String? = null
                var mediaPath: String? = null
                for (field in cell.javaClass.declaredFields) {
                    if (field.type == String::class.java) {
                        field.isAccessible = true
                        val str = field.get(cell) as? String ?: continue
                        if (mediaPath == null && str.contains("redgifs.com/")) {
                            mediaPath = str
                        } else if (cellId == null && str.length in 8..150) {
                            val decoded = if (str.startsWith(RedgifsAudioHelper.METADATA_CELL_ID_PREFIX)) {
                                str
                            } else {
                                runCatching {
                                    Base64.decode(str, Base64.DEFAULT).decodeToString()
                                }.getOrNull()
                            }
                            if (decoded != null && decoded.startsWith(RedgifsAudioHelper.METADATA_CELL_ID_PREFIX)) {
                                cellId = decoded.removePrefix(RedgifsAudioHelper.METADATA_CELL_ID_PREFIX)
                            }
                        }
                    }
                }
                if (cellId != null && mediaPath != null) {
                    val redgifsId = RedgifsAudioHelper.getRedgifsId(mediaPath)
                    if (redgifsId != null) {
                        RedgifsAudioHelper.postIdToRedgifsId[cellId] = redgifsId
                        RedgifsAudioHelper.getLookup(redgifsId)
                    }
                }
            }
        })
    }

    // 3. Feed video element cell: map Reddit video URL to Redgifs ID via post ID
    VideoElementToStringFingerprint.memberOrNull?.declaringClass?.let { videoElementClass ->
        XposedBridge.hookAllConstructors(videoElementClass, object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val element = param.thisObject ?: return
                var linkId: String? = null
                var defaultUrl: String? = null
                for (field in element.javaClass.declaredFields) {
                    if (field.type == String::class.java) {
                        field.isAccessible = true
                        val str = field.get(element) as? String ?: continue
                        if (str.startsWith("t3_") || str in RedgifsAudioHelper.postIdToRedgifsId) {
                            linkId = str
                        } else if (str.contains("v.redd.it") || str.contains("packaged-media.redd.it")) {
                            defaultUrl = str
                        }
                    }
                }
                if (linkId != null && defaultUrl != null) {
                    val redgifsId = RedgifsAudioHelper.postIdToRedgifsId[linkId]
                    if (redgifsId != null) {
                        RedgifsAudioHelper.recordRedditVideo(defaultUrl, redgifsId)
                    }
                }
            }
        })
    }

    // 4. Video player URLs (media viewer & post details): replace defaultUrl with Redgifs or placeholder
    VideoUrlsToStringFingerprint.memberOrNull?.declaringClass?.let { videoUrlsClass ->
        XposedBridge.hookAllConstructors(videoUrlsClass, object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val url = param.args.firstOrNull() as? String ?: return
                val playbackUrl = RedgifsAudioHelper.getPlaybackUrl(url)
                if (playbackUrl != null && playbackUrl != url) {
                    param.args[0] = playbackUrl
                }
            }
        })
    }

    // 5. Feed DomainVideoUrls: replace legacyUrl and clear DASH playlist so MP4 plays
    val domainVideoUrlsClass = DomainVideoUrlsToStringFingerprint.memberOrNull?.declaringClass
        ?: runCatching { classLoader.loadClass("com.reddit.domain.model.VideoUrls") }.getOrNull()
    if (domainVideoUrlsClass != null) {
        XposedBridge.hookAllConstructors(domainVideoUrlsClass, object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val obj = param.thisObject ?: return
                val legacyUrl = runCatching {
                    (XposedHelpers.callMethod(obj, "getLegacyUrl") as? String)
                        ?: (XposedHelpers.getObjectField(obj, "legacyUrl") as? String)
                }.getOrNull() ?: return

                val playbackUrl = RedgifsAudioHelper.getPlaybackUrl(legacyUrl)
                if (playbackUrl != null && playbackUrl != legacyUrl) {
                    runCatching { XposedHelpers.setObjectField(obj, "legacyUrl", playbackUrl) }
                    runCatching { XposedHelpers.setObjectField(obj, "dashUrl", null) }
                    runCatching { XposedHelpers.setObjectField(obj, "playbackMp4s", null) }
                }
            }
        })
    }

    // 6. Media3 DataSpec constructor: resolve pending placeholder URLs to direct Redgifs streams
    DataSpecToStringFingerprint.memberOrNull?.declaringClass?.let { dataSpecClass ->
        XposedBridge.hookAllConstructors(dataSpecClass, object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val uri = param.args.firstOrNull() as? Uri ?: return
                val resolved = RedgifsAudioHelper.resolvePlaceholderUri(uri)
                if (resolved != uri) {
                    param.args[0] = resolved
                }
            }
        })
    }

    // 7. Video downloads use Reddit's video url of the post, which the player hooks don't change.
    // The url is replaced where the download is enqueued, so other patches still see Reddit's url.
    runCatching {
        XposedBridge.hookAllConstructors(
            classLoader.loadClass("android.app.DownloadManager\$Request"),
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val uri = param.args.firstOrNull() as? Uri ?: return
                    val downloadUri = RedgifsAudioHelper.getDownloadUri(uri)
                    if (downloadUri != uri) {
                        param.args[0] = downloadUri
                    }
                }
            }
        )
    }
}

val RedgifsHd = patch(
    name = "Redgifs HD quality",
    description = "Plays HD Redgifs video if available. HD videos are larger and may load slower.",
    use = false,
) {
    RedgifsAudioHelper.preferHd = true
}
