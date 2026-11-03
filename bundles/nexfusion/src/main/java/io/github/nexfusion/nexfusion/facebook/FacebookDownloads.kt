package io.github.nexfusion.nexfusion.facebook

import android.app.Activity
import android.app.AlertDialog
import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.Toast
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import io.github.nexfusion.PatcherLog
import io.github.nexfusion.patch
import java.io.File
import java.io.IOException
import java.lang.ref.WeakReference
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.lang.reflect.Proxy
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

// Facebook downloader, in the same shape as the Instagram downloader: a Download options row in
// the post menu, the photo viewer's Save photo routed through the same save, and a settings
// dialog for the folder, per-poster subfolders and timestamped names.
//
// The model classes are kept by Facebook, but their getters are renamed. The GraphQL field names
// hash to stable numbers, and the models read their live trees by those numbers, so this file
// reads the same fields the same way instead of naming a Redex name. The only names used are the
// kept model classes and the media fields' GraphQL names.

private const val MENU_ID = 0x4E455846 // "NEXF"
private const val DOWNLOAD_TITLE = "Download options"

/** The field hash of a photo's poster-side download flag. */
private val CAN_VIEWER_DOWNLOAD = "can_viewer_download".hashCode()

/** GraphQLMedia's image fields, largest first, and an image's own fields. */
private val IMAGE_FIELDS = listOf(
    "imageHigh".hashCode(),
    "image".hashCode(),
    "imageMedium".hashCode(),
    "imageLow".hashCode(),
)
private val IMAGE_URI = "uri".hashCode()
private val IMAGE_WIDTH = "width".hashCode()
private val IMAGE_HEIGHT = "height".hashCode()

/** GraphQLMedia's video fields, HD first. */
private val VIDEO_FIELDS = listOf(
    "hd_playable_url".hashCode(),
    "playable_url".hashCode(),
    "video_hd_url".hashCode(),
    "video_sd_url".hashCode(),
    "hd_src".hashCode(),
    "sd_src".hashCode(),
)
private val VIDEO_ID = "id".hashCode()
private val DASH_MANIFEST = "dash_manifest".hashCode()

private const val STORY = "com.facebook.graphql.model.GraphQLStory"
private const val STORY_ATTACHMENT = "com.facebook.graphql.model.GraphQLStoryAttachment"
private const val MEDIA = "com.facebook.graphql.model.GraphQLMedia"

/** The max number of photos a post menu offers, as Hushfacebook caps it. */
private const val MAX_ITEMS = 50

/** The downloader's own preferences, beside Instagram's and shaped the same way. */
internal class FacebookDownloadSettings private constructor(private val prefs: SharedPreferences) {

    enum class Location(val label: String) {
        DOWNLOADS("Downloads"),
        GALLERY("Pictures and Movies"),
    }

    enum class Kind { VIDEO, IMAGE, AUDIO }

    var location: Location
        get() = Location.values().firstOrNull { it.name == prefs.getString(KEY_LOCATION, null) } ?: Location.DOWNLOADS
        set(value) { prefs.edit().putString(KEY_LOCATION, value.name).apply() }

    var perUsername: Boolean
        get() = prefs.getBoolean(KEY_PER_USERNAME, false)
        set(value) { prefs.edit().putBoolean(KEY_PER_USERNAME, value).apply() }

    var timestamp: Boolean
        get() = prefs.getBoolean(KEY_TIMESTAMP, false)
        set(value) { prefs.edit().putBoolean(KEY_TIMESTAMP, value).apply() }

    var customFolder: String
        get() = prefs.getString(KEY_CUSTOM_FOLDER, null)?.trim()?.trim('/')?.takeIf { it.isNotEmpty() }
            ?: DEFAULT_FOLDER
        set(value) {
            var cleaned = value.trim().trim('/')
            if (cleaned.startsWith("Download/", ignoreCase = true)) {
                cleaned = cleaned.substring("Download/".length).trim('/')
            } else if (cleaned.startsWith("Downloads/", ignoreCase = true)) {
                cleaned = cleaned.substring("Downloads/".length).trim('/')
            }
            if (cleaned.isEmpty() || cleaned.equals(DEFAULT_FOLDER, ignoreCase = true)) {
                prefs.edit().remove(KEY_CUSTOM_FOLDER).apply()
            } else {
                prefs.edit().putString(KEY_CUSTOM_FOLDER, cleaned).apply()
            }
        }

    fun folder(username: String?): String {
        val name = username?.takeIf { perUsername }
            ?.filter { it.isLetterOrDigit() || it == '.' || it == '_' || it == '-' }
            ?.takeIf { it.isNotBlank() }
        return if (name != null) "${customFolder}/$name" else customFolder
    }

    fun baseDirectory(kind: Kind): String = when {
        location == Location.DOWNLOADS -> Environment.DIRECTORY_DOWNLOADS
        kind == Kind.VIDEO -> Environment.DIRECTORY_MOVIES
        kind == Kind.AUDIO -> Environment.DIRECTORY_MUSIC
        else -> Environment.DIRECTORY_PICTURES
    }

    fun collection(kind: Kind): Uri = when {
        location == Location.DOWNLOADS -> MediaStore.Downloads.EXTERNAL_CONTENT_URI
        kind == Kind.VIDEO -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        kind == Kind.AUDIO -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        else -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    }

    fun destination(username: String?, kind: Kind) = "${baseDirectory(kind)}/${folder(username)}"

    companion object {
        private const val PREFS = "nexfusion_facebook_downloads"
        private const val KEY_LOCATION = "save_location"
        private const val KEY_PER_USERNAME = "per_username_folders"
        private const val KEY_TIMESTAMP = "timestamp_names"
        private const val KEY_CUSTOM_FOLDER = "custom_download_folder"
        const val DEFAULT_FOLDER = "NexFusion/Facebook"

        fun open(context: Context) = FacebookDownloadSettings(
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE),
        )
    }
}

/** One downloadable photo or video, read from a post's GraphQL model. */
internal class FacebookItem(
    val url: String,
    val isVideo: Boolean,
    val imageUrl: String?,
    val imageVariants: List<Variant> = emptyList(),
    val videoVariants: List<Variant> = emptyList(),
    val audioUrl: String? = null,
) {
    class Variant(val url: String, val width: Int, val height: Int) {
        val label get() = if (width > 0 && height > 0) "${width}x$height" else "Original"
    }
}

@Volatile
private var lastResumedActivity: WeakReference<Activity>? = null

private fun resolveActivity(context: Context?): Activity? {
    activityOf(context)?.let { return it }
    return lastResumedActivity?.get()
}

/**
 * Caches progressive video URLs and DASH manifests by video ID as VideoPlayerParams are built.
 */
internal object PlayerSources {
    class Source(
        val videoId: String,
        val hdUrl: String?,
        val sdUrl: String?,
        val manifest: String?,
    )

    private const val MAX_SOURCES = 48
    private val sources = object : LinkedHashMap<String, Source>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: Map.Entry<String, Source>?): Boolean = size > MAX_SOURCES
    }

    @Volatile
    var latestSource: Source? = null

    private val ID_PATTERN = Regex("\\d{8,25}")

    fun record(params: Any) {
        runCatching {
            val text = params.toString()
            val videoId = parseVideoId(text, params) ?: return
            val dataSource = findField(params, "com.facebook.video.engine.api.VideoDataSource") ?: return
            val hdUrl = (getFieldValue(dataSource, "A06") as? Uri)?.toString()
                ?: findUri(dataSource, true)
            val sdUrl = (getFieldValue(dataSource, "A07") as? Uri)?.toString()
                ?: findUri(dataSource, false)
            val manifest = (getFieldValue(dataSource, "A0C") as? String)
                ?: findManifest(dataSource)

            if (hdUrl != null || sdUrl != null || manifest != null) {
                val s = Source(videoId, hdUrl, sdUrl, manifest)
                synchronized(sources) {
                    sources[videoId] = s
                }
                latestSource = s
                log("PlayerSources recorded videoId=$videoId (hd=${hdUrl != null}, manifest=${manifest != null})")
            }
        }.onFailure { log("PlayerSources record error: $it") }
    }

    fun find(idOrObject: Any?): Source? {
        if (idOrObject == null) return null
        if (idOrObject is String) {
            synchronized(sources) {
                sources[idOrObject]?.let { return it }
            }
        }
        val ids = extractIds(idOrObject)
        synchronized(sources) {
            for (id in ids) {
                sources[id]?.let { return it }
            }
        }
        return null
    }

    private fun parseVideoId(text: String, host: Any): String? {
        val marker = "VideoId: "
        val idx = text.indexOf(marker)
        if (idx != -1) {
            val end = text.indexOfAny(charArrayOf(',', ' ', '\n', '}'), idx + marker.length)
            val id = if (end != -1) text.substring(idx + marker.length, end).trim()
            else text.substring(idx + marker.length).trim()
            if (id.isNotEmpty()) return id
        }
        for (field in host.javaClass.declaredFields) {
            if (field.type == String::class.java) {
                field.isAccessible = true
                val s = field.get(host) as? String
                if (s != null && ID_PATTERN.matches(s)) return s
            }
        }
        return null
    }

    private fun extractIds(host: Any): List<String> {
        val ids = mutableListOf<String>()
        if (host is String && ID_PATTERN.matches(host)) return listOf(host)
        for (method in host.javaClass.methods) {
            if (method.name == "getId" && method.parameterCount == 0 && method.returnType == String::class.java) {
                (method.invoke(host) as? String)?.let { if (it.isNotEmpty()) ids += it }
            }
        }
        for (field in host.javaClass.declaredFields) {
            if (field.type == String::class.java) {
                field.isAccessible = true
                val v = field.get(host) as? String
                if (v != null && ID_PATTERN.matches(v)) ids += v
            }
        }
        return ids
    }

    private fun findField(host: Any, typeName: String): Any? {
        var clazz: Class<*>? = host.javaClass
        while (clazz != null && clazz != Any::class.java) {
            for (f in clazz.declaredFields) {
                if (f.type.name == typeName) {
                    f.isAccessible = true
                    return f.get(host)
                }
            }
            clazz = clazz.superclass
        }
        return null
    }

    private fun getFieldValue(host: Any, fieldName: String): Any? {
        return runCatching {
            val f = host.javaClass.getDeclaredField(fieldName)
            f.isAccessible = true
            f.get(host)
        }.getOrNull()
    }

    private fun findUri(dataSource: Any, first: Boolean): String? {
        val uris = mutableListOf<String>()
        for (f in dataSource.javaClass.declaredFields) {
            if (f.type == Uri::class.java) {
                f.isAccessible = true
                (f.get(dataSource) as? Uri)?.toString()?.let { uris += it }
            }
        }
        return if (first) uris.firstOrNull() else uris.getOrNull(1)
    }

    private fun findManifest(dataSource: Any): String? {
        for (f in dataSource.javaClass.declaredFields) {
            if (f.type == String::class.java) {
                f.isAccessible = true
                val s = f.get(dataSource) as? String
                if (s != null && (s.contains("<MPD") || s.contains("<AdaptationSet"))) return s
            }
        }
        return null
    }
}

/**
 * Reads the photos and videos of a Facebook post. The getter methods are resolved by DexKit at
 * patch time; the media fields are read by their GraphQL name hashes, which Redex leaves alone.
 */
internal class FacebookMediaReader(
    private val attachmentsGetter: Method?,
    private val attachedStoryGetter: Method?,
    private val mediaGetter: Method?,
    private val subattachmentsGetter: Method?,
    private val log: (String) -> Unit,
) {
    private val fieldsByClass = ConcurrentHashMap<Class<*>, List<Field>>()

    init {
        listOfNotNull(attachmentsGetter, attachedStoryGetter, mediaGetter, subattachmentsGetter)
            .forEach { runCatching { it.isAccessible = true } }
    }

    /** The media of [item], one item for each photo or video the post carries. */
    fun items(item: Any?): List<FacebookItem> {
        if (item == null) return emptyList()
        val media = mutableListOf<Any>()
        if (isA(item, STORY_ATTACHMENT)) {
            addAttachmentMedia(item, media)
        } else if (isA(item, MEDIA)) {
            media += item
        } else {
            val story = postOf(item)
            if (story != null) {
                var attachments = call(story, attachmentsGetter) as? List<*>
                if (attachments.isNullOrEmpty()) {
                    attachments = treeList(story, "attachments".hashCode())
                }
                if (attachments.isNullOrEmpty()) {
                    attachments = (tree(story, "attachments".hashCode()) as? List<*>)
                }
                if (attachments.isNullOrEmpty()) {
                    attachments = story.javaClass.methods.firstOrNull {
                        it.parameterTypes.isEmpty() && (it.name.contains("attachment", ignoreCase = true) || List::class.java.isAssignableFrom(it.returnType)) && it.name !in SKIP_GETTERS
                    }?.let { call(story, it) as? List<*> }
                }
                if (!attachments.isNullOrEmpty()) {
                    for (attachment in attachments) {
                        if (attachment != null) addAttachmentMedia(attachment, media)
                        if (media.size >= MAX_ITEMS) break
                    }
                }
                if (media.isEmpty()) {
                    findMediaByMethod(story)?.let { media += it }
                }
            } else {
                media += item
            }
        }
        val extracted = media.mapNotNull(::item).distinctBy { it.url }.take(MAX_ITEMS)
        if (extracted.isNotEmpty()) return extracted
        itemForMedia(item)?.let { return listOf(it) }
        return emptyList()
    }

    /**
     * The item of one media model, used by the photo viewer's Save photo action. The viewer
     * hands its own photo model rather than a GraphQLMedia, so the model's no-argument getters
     * are walked until one returns the GraphQL image or media (or an image URL) behind it.
     */
    fun itemForMedia(photo: Any?): FacebookItem? {
        if (photo == null) return null
        val seen = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<Any, Boolean>())
        return itemFromValue(photo, 2, seen)
    }

    private fun itemFromValue(value: Any?, depth: Int, seen: MutableSet<Any>): FacebookItem? {
        if (value == null || !seen.add(value)) return null
        if (value is String) {
            return if (isHttp(value)) FacebookItem(value, false, value) else null
        }
        if (value is Uri) {
            val text = value.toString()
            return if (isHttp(text)) FacebookItem(text, false, text) else null
        }
        urlFromImage(value)?.let { return FacebookItem(it, false, it) }
        if (hasIntMethod(value, "getTree")) item(value)?.let { return it }
        if (depth <= 0) return null
        if (value is List<*>) {
            for (entry in value.take(MAX_ITEMS)) {
                itemFromValue(entry, depth - 1, seen)?.let { return it }
            }
            return null
        }
        val type = value.javaClass.name
        if (type.startsWith("java.") || type.startsWith("android.")) return null
        for (method in value.javaClass.methods) {
            if (method.parameterTypes.isNotEmpty() || method.returnType == java.lang.Void.TYPE) continue
            if (method.name in SKIP_GETTERS) continue
            val result = runCatching { method.invoke(value) }.getOrNull() ?: continue
            itemFromValue(result, depth - 1, seen)?.let { return it }
        }
        return null
    }

    private fun urlFromImage(value: Any): String? {
        if (!hasIntMethod(value, "getString") && !hasIntMethod(value, "getCachedString")) return null
        return (string(value, IMAGE_URI) ?: cachedString(value, IMAGE_URI))?.takeIf { isHttp(it) }
    }

    private fun hasIntMethod(value: Any, name: String): Boolean = value.javaClass.methods.any {
        it.name == name && it.parameterTypes.contentEquals(arrayOf(Int::class.javaPrimitiveType))
    }

    private companion object {
        val SKIP_GETTERS = setOf(
            "getClass",
            "hashCode",
            "toString",
            "getTypeName",
            "isValidGraphServicesJNIModel",
        )
    }

    private fun addAttachmentMedia(attachment: Any, out: MutableList<Any>) {
        val subattachments = (call(attachment, subattachmentsGetter) as? List<*>)
            ?: treeList(attachment, "subattachments".hashCode())
            ?: (tree(attachment, "subattachments".hashCode()) as? List<*>)
        if (!subattachments.isNullOrEmpty()) {
            for (sub in subattachments) {
                if (sub == null) continue
                val m = call(sub, mediaGetter) ?: tree(sub, "media".hashCode()) ?: findMediaByMethod(sub)
                if (m != null) out += m
                if (out.size >= MAX_ITEMS) return
            }
        } else {
            val m = call(attachment, mediaGetter) ?: tree(attachment, "media".hashCode()) ?: findMediaByMethod(attachment)
            if (m != null) out += m
        }
    }

    private fun item(media: Any): FacebookItem? {
        val type = typeName(media)
        val playerSource = PlayerSources.find(media)
        val videoUrls = (videoUrls(media) + listOfNotNull(playerSource?.hdUrl, playerSource?.sdUrl)).distinct()
        val imageVariants = imageVariants(media)
        val audioUrl = dashAudioUrl(media) ?: FacebookDash.bestAudioUrl(playerSource?.manifest)
        val isVideo = type == "Video" || videoUrls.isNotEmpty() || playerSource != null

        if (isVideo) {
            val url = videoUrls.firstOrNull()
                ?: dashBestVideoUrl(media)
                ?: FacebookDash.bestVideoUrl(playerSource?.manifest)
                ?: return null
            return FacebookItem(
                url = url,
                isVideo = true,
                imageUrl = imageVariants.firstOrNull()?.url,
                imageVariants = imageVariants,
                videoVariants = videoUrls.map { FacebookItem.Variant(it, 0, 0) },
                audioUrl = audioUrl,
            )
        }

        val image = imageVariants.firstOrNull()?.url ?: firstString(media, IMAGE_FIELDS) ?: return null
        return FacebookItem(
            url = image,
            isVideo = false,
            imageUrl = image,
            imageVariants = imageVariants,
            audioUrl = audioUrl,
        )
    }

    private fun videoUrls(media: Any): List<String> {
        val urls = mutableListOf<String>()
        for (field in VIDEO_FIELDS) {
            firstString(media, listOf(field))?.let { if (isHttp(it)) urls += it }
        }
        if (urls.isEmpty()) {
            harvest(media, 2)
                .filter { it.contains(".mp4", ignoreCase = true) || it.contains("/video/", ignoreCase = true) }
                .filter { isHttp(it) }
                .forEach { urls += it }
        }
        return urls.distinct()
    }

    private fun imageVariants(media: Any): List<FacebookItem.Variant> {
        val variants = mutableListOf<FacebookItem.Variant>()
        for (field in IMAGE_FIELDS) {
            val image = tree(media, field) ?: media
            val url = string(image, IMAGE_URI)
                ?: string(media, field)
                ?: cachedString(media, field)
                ?: continue
            if (!isHttp(url)) continue
            val width = intValue(image, IMAGE_WIDTH)
            val height = intValue(image, IMAGE_HEIGHT)
            variants += FacebookItem.Variant(url, width, height)
        }
        if (variants.isEmpty()) {
            harvest(media, 2)
                .filter { isHttp(it) && !it.contains(".mp4", ignoreCase = true) }
                .forEach { variants += FacebookItem.Variant(it, 0, 0) }
        }
        return variants.distinctBy { it.url }
            .sortedByDescending { it.width.toLong() * it.height }
    }

    private fun dashAudioUrl(media: Any): String? {
        val manifest = firstString(media, listOf(DASH_MANIFEST)) ?: return null
        return FacebookDash.bestAudioUrl(manifest)
    }

    private fun dashBestVideoUrl(media: Any): String? {
        val manifest = firstString(media, listOf(DASH_MANIFEST)) ?: return null
        return FacebookDash.bestVideoUrl(manifest)
    }

    /** Follows a share to the post that holds the media, as Facebook's own menu does. */
    fun postOf(item: Any?): Any? {
        if (item == null) return null
        if (!isA(item, STORY)) {
            if (isA(item, STORY_ATTACHMENT) || isA(item, MEDIA)) return null
            val subStory = item.javaClass.methods.firstOrNull {
                it.parameterTypes.isEmpty() && isA(it.returnType, STORY) && it.name !in SKIP_GETTERS
            }?.let { call(item, it) }
            if (subStory != null && isA(subStory, STORY)) return postOf(subStory)
            return null
        }
        var story = item
        repeat(4) {
            val shared = call(story, attachedStoryGetter) ?: tree(story, "attached_story".hashCode()) ?: return@repeat
            story = shared
        }
        return story
    }

    /** The poster's name, for a per-poster subfolder. Null when the model doesn't carry one. */
    fun usernameOf(item: Any?): String? {
        val story = postOf(item) ?: item ?: return null
        val owner = tree(story, "owner".hashCode()) ?: tree(story, "actors".hashCode())
        val ownerList = if (owner is List<*>) owner.firstOrNull() else owner
        string(ownerList, "name".hashCode())?.takeIf { it.isNotBlank() }?.let { return it }
        val media = when {
            isA(story, MEDIA) -> story
            isA(story, STORY_ATTACHMENT) -> call(story, mediaGetter) ?: tree(story, "media".hashCode()) ?: findMediaByMethod(story)
            else -> {
                val attachments = (call(story, attachmentsGetter) as? List<*>) ?: treeList(story, "attachments".hashCode())
                (attachments?.firstOrNull())?.let { call(it, mediaGetter) ?: tree(it, "media".hashCode()) ?: findMediaByMethod(it) }
            }
        }
        val mediaOwner = tree(media, "owner".hashCode())
        return string(mediaOwner, "name".hashCode())?.takeIf { it.isNotBlank() }
    }

    // ---------------------------------------------------------------- reflection helpers

    private fun call(host: Any?, method: Method?): Any? {
        if (host == null || method == null) return null
        return runCatching { method.invoke(host) }.onFailure {
            log("${method.name} failed: ${it.message}")
        }.getOrNull()
    }

    private fun tree(model: Any?, field: Int): Any? = invokeInt(model, "getTree", field)

    private fun treeList(model: Any?, field: Int): List<*>? {
        if (model == null) return null
        return (invokeInt(model, "getTreeList", field) as? List<*>)
            ?: (invokeInt(model, "getImmutableListField", field) as? List<*>)
            ?: runCatching {
                model.javaClass.methods.firstOrNull {
                    it.name == "getTreeList" && it.parameterTypes.size == 1 && it.parameterTypes[0] == Int::class.javaPrimitiveType
                }?.invoke(model, field) as? List<*>
            }.getOrNull()
    }

    private fun findMediaByMethod(model: Any?): Any? {
        if (model == null) return null
        for (m in model.javaClass.methods) {
            if (m.parameterTypes.isEmpty() && m.returnType != java.lang.Void.TYPE && m.name !in SKIP_GETTERS) {
                if (isA(m.returnType, MEDIA) || m.name.equals("getMedia", ignoreCase = true)) {
                    val res = runCatching { m.invoke(model) }.getOrNull()
                    if (res != null) return res
                }
            }
        }
        return null
    }

    private fun string(model: Any?, field: Int): String? =
        invokeInt(model, "getString", field) as? String

    private fun cachedString(model: Any?, field: Int): String? =
        invokeInt(model, "getCachedString", field) as? String

    private fun intValue(model: Any?, field: Int): Int =
        (invokeInt(model, "getIntValue", field) as? Number)?.toInt() ?: 0

    private fun invokeInt(model: Any?, name: String, field: Int): Any? {
        if (model == null) return null
        return runCatching {
            model.javaClass.getMethod(name, Int::class.javaPrimitiveType).invoke(model, field)
        }.getOrNull()
    }

    private fun firstString(model: Any?, fields: List<Int>): String? {
        for (field in fields) {
            cachedString(model, field)?.takeIf { isHttp(it) }?.let { return it }
            string(model, field)?.takeIf { isHttp(it) }?.let { return it }
            tree(model, field)?.let { image ->
                string(image, IMAGE_URI)?.takeIf { isHttp(it) }?.let { return it }
            }
        }
        return null
    }

    private fun typeName(model: Any?): String? {
        if (model == null) return null
        return runCatching {
            val valid = model.javaClass.getMethod("isValidGraphServicesJNIModel").invoke(model)
            if (valid != true) return@runCatching null
            model.javaClass.getMethod("getTypeName").invoke(model) as? String
        }.getOrNull()
    }

    private fun harvest(host: Any?, depth: Int): List<String> {
        val found = mutableListOf<String>()
        if (host == null || depth < 0 || found.size >= MAX_ITEMS) return found
        if (host is String) {
            if (isHttp(host)) found += host
            return found
        }
        if (host is List<*>) {
            for (value in host.take(MAX_ITEMS)) {
                found += harvest(value, depth - 1)
                if (found.size >= MAX_ITEMS) break
            }
            return found
        }
        for (field in instanceFields(host.javaClass)) {
            val value = runCatching { field.get(host) }.getOrNull() ?: continue
            when (value) {
                is String -> if (isHttp(value)) found += value
                is List<*> -> if (depth > 0) {
                    for (item in value.take(20)) {
                        found += harvest(item, depth - 1)
                        if (found.size >= MAX_ITEMS) break
                    }
                }
                else -> if (depth > 0 && !value.javaClass.isPrimitive && !value.javaClass.name.startsWith("java.")) {
                    found += harvest(value, depth - 1)
                }
            }
            if (found.size >= MAX_ITEMS) break
        }
        return found
    }

    private fun instanceFields(clazz: Class<*>): List<Field> = fieldsByClass.getOrPut(clazz) {
        generateSequence(clazz) { it.superclass }
            .takeWhile { it != Any::class.java }
            .flatMap { it.declaredFields.asSequence() }
            .filter { !Modifier.isStatic(it.modifiers) && !it.type.isPrimitive }
            .onEach { runCatching { it.isAccessible = true } }
            .toList()
    }

    private fun isA(value: Any?, className: String): Boolean {
        if (value == null) return false
        return isA(value.javaClass, className)
    }

    private fun isA(clazz: Class<*>?, className: String): Boolean {
        if (clazz == null) return false
        val targetSimple = className.substringAfterLast('.')
        val visited = mutableSetOf<Class<*>>()
        val queue = ArrayDeque<Class<*>>()
        queue.add(clazz)
        while (queue.isNotEmpty()) {
            val curr = queue.removeFirst()
            if (!visited.add(curr)) continue
            if (curr.name == className || curr.simpleName == targetSimple) return true
            curr.superclass?.let { queue.add(it) }
            for (iface in curr.interfaces) queue.add(iface)
        }
        return false
    }

    private fun isHttp(value: String) = value.startsWith("http://") || value.startsWith("https://")
}

/** Parses Facebook's DASH manifest enough to pick a video or audio track when no progressive URL exists. */
internal object FacebookDash {
    private val ADAPTATION_SET = Regex("<AdaptationSet\\b([^>]*)>(.*?)</AdaptationSet>", RegexOption.DOT_MATCHES_ALL)
    private val REPRESENTATION = Regex("<Representation\\b([^>]*?)(?:/>|>(.*?)</Representation>)", RegexOption.DOT_MATCHES_ALL)
    private val BASE_URL = Regex("<BaseURL[^>]*>(.*?)</BaseURL>", RegexOption.DOT_MATCHES_ALL)
    private val ATTRIBUTE = Regex("([\\w:]+)\\s*=\\s*\"([^\"]*)\"")

    private class Track(
        val url: String,
        val mime: String,
        val codecs: String,
        val bandwidth: Long,
        val width: Int,
        val height: Int,
    )

    fun bestAudioUrl(manifest: String?): String? {
        val tracks = tracks(manifest).filter { it.mime.startsWith("audio/") || it.codecs.startsWith("mp4a") }
        return tracks.sortedWith(
            compareByDescending<Track> { rankAudio(it.codecs) }.thenByDescending { it.bandwidth },
        ).firstOrNull()?.url
    }

    fun bestVideoUrl(manifest: String?): String? {
        val tracks = tracks(manifest).filter { it.mime.startsWith("video/") || it.codecs.startsWith("avc") || it.codecs.startsWith("hev") }
        return tracks.sortedWith(
            compareByDescending<Track> { it.width.toLong() * it.height }
                .thenByDescending { rankVideo(it.codecs) }
                .thenByDescending { it.bandwidth },
        ).firstOrNull()?.url
    }

    private fun tracks(manifest: String?): List<Track> {
        if (manifest.isNullOrBlank()) return emptyList()
        val out = mutableListOf<Track>()
        for (set in ADAPTATION_SET.findAll(manifest)) {
            val setAttributes = set.groupValues[1]
            val setMime = attribute(setAttributes, "mimeType").orEmpty()
            val setCodecs = attribute(setAttributes, "codecs").orEmpty()
            for (rep in REPRESENTATION.findAll(set.groupValues[2])) {
                val attributes = rep.groupValues[1]
                val body = rep.groupValues[2]
                val base = BASE_URL.find(body)?.groupValues?.get(1)?.trim() ?: continue
                val url = unescape(base)
                if (!url.startsWith("https://", ignoreCase = true)) continue
                out += Track(
                    url = url,
                    mime = attribute(attributes, "mimeType") ?: setMime,
                    codecs = attribute(attributes, "codecs") ?: setCodecs,
                    bandwidth = attribute(attributes, "bandwidth")?.toLongOrNull() ?: 0L,
                    width = attribute(attributes, "width")?.toIntOrNull() ?: 0,
                    height = attribute(attributes, "height")?.toIntOrNull() ?: 0,
                )
            }
        }
        return out
    }

    private fun rankAudio(codecs: String): Int = when {
        codecs.contains("mp4a.40.2") -> 3
        codecs.contains("mp4a.40.5") || codecs.contains("mp4a.40.29") -> 2
        codecs.startsWith("mp4a") -> 1
        else -> 0
    }

    private fun rankVideo(codecs: String): Int = when {
        codecs.startsWith("hev") || codecs.startsWith("hvc") -> 3
        codecs.startsWith("avc") -> 2
        codecs.startsWith("vp9") || codecs.startsWith("av01") -> 1
        else -> 0
    }

    private fun attribute(attributes: String, name: String): String? =
        ATTRIBUTE.findAll(attributes).firstOrNull { it.groupValues[1] == name }?.groupValues?.get(2)

    private fun unescape(text: String): String = text
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&apos;", "'")
        .replace("&amp;", "&")
}

private val mainHandler by lazy { Handler(Looper.getMainLooper()) }

private fun log(message: String) {
    PatcherLog.info { "Facebook download: $message" }
}

private fun toast(context: Context, text: String) {
    mainHandler.post { Toast.makeText(context, text, Toast.LENGTH_SHORT).show() }
}

private fun activityOf(context: Context?): Activity? {
    var current = context
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return current as? Activity
}

private fun isHttp(value: String) = value.startsWith("http://") || value.startsWith("https://")

private fun safeName(value: String): String =
    value.replace(Regex("[^A-Za-z0-9._-]"), "_").trim('_', '.').take(120).ifBlank { "facebook_media" }

private fun fileName(item: FacebookItem, timestamp: Boolean): String {
    val fallback = if (item.isVideo) "facebook_${System.currentTimeMillis()}.mp4"
    else "facebook_${System.currentTimeMillis()}.jpg"
    val original = Uri.parse(item.url).lastPathSegment?.takeIf { it.isNotBlank() } ?: fallback
    val base = safeName(original)
    val name = if (base.contains('.')) base else "$base${if (item.isVideo) ".mp4" else ".jpg"}"
    if (!timestamp) return name
    val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    val dot = name.lastIndexOf('.')
    return if (dot > 0) "${name.substring(0, dot)}_$stamp${name.substring(dot)}" else "${name}_$stamp"
}

private fun mimeType(item: FacebookItem, name: String) = when {
    item.isVideo -> "video/mp4"
    name.endsWith(".webp", ignoreCase = true) -> "image/webp"
    name.endsWith(".png", ignoreCase = true) -> "image/png"
    else -> "image/jpeg"
}

/** Fetches the file itself, as Instagram's downloader does, and puts it in the chosen folder. */
private fun save(context: Context, item: FacebookItem, settings: FacebookDownloadSettings, username: String?) {
    val name = fileName(item, settings.timestamp)
    val mime = mimeType(item, name)
    val kind = if (item.isVideo) FacebookDownloadSettings.Kind.VIDEO else FacebookDownloadSettings.Kind.IMAGE
    val connection = URL(item.url).openConnection() as HttpURLConnection
    try {
        connection.connectTimeout = 15_000
        connection.readTimeout = 30_000
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) NexFusion")
        if (connection.responseCode !in 200..299) throw IOException("HTTP ${connection.responseCode}")
        connection.inputStream.use { input ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                    put(MediaStore.MediaColumns.MIME_TYPE, mime)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "${settings.baseDirectory(kind)}/${settings.folder(username)}")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
                val uri = resolver.insert(settings.collection(kind), values)
                    ?: throw IOException("MediaStore did not create $name")
                try {
                    resolver.openOutputStream(uri)!!.use { output -> input.copyTo(output) }
                    resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
                } catch (e: Exception) {
                    resolver.delete(uri, null, null)
                    throw e
                }
            } else {
                @Suppress("DEPRECATION")
                val base = Environment.getExternalStoragePublicDirectory(settings.baseDirectory(kind))
                val folder = File(base, settings.folder(username))
                folder.mkdirs()
                File(folder, name).outputStream().use { output -> input.copyTo(output) }
            }
        }
    } finally {
        connection.disconnect()
    }
    log("saved $name to ${settings.destination(username, kind)}")
}

private fun enqueue(context: Context, items: List<FacebookItem>, username: String?) {
    if (items.isEmpty()) return
    val appContext = context.applicationContext ?: context
    val settings = FacebookDownloadSettings.open(appContext)
    val mediaType = if (items.first().isVideo) "video" else "photo"
    toast(context, if (items.size == 1) "Downloading $mediaType..." else "Downloading ${items.size} items...")
    Thread {
        var saved = 0
        items.forEach { item ->
            try {
                save(appContext, item, settings, username)
                saved++
            } catch (e: Exception) {
                log("failed to save ${item.url}: ${e.stackTraceToString().take(1500)}")
            }
        }
        val destination = settings.destination(username, if (items.first().isVideo) {
            FacebookDownloadSettings.Kind.VIDEO
        } else {
            FacebookDownloadSettings.Kind.IMAGE
        })
        val text = when {
            saved == items.size -> "Saved to $destination"
            saved == 0 -> "Download failed"
            else -> "Saved $saved of ${items.size} to $destination"
        }
        toast(appContext, text)
    }.apply { name = "NexFusion Facebook download" }.start()
}

private fun showVariants(
    activity: Activity,
    title: String,
    variants: List<FacebookItem.Variant>,
    isVideo: Boolean,
    username: String?,
) {
    activity.runOnUiThread {
        if (activity.isFinishing || activity.isDestroyed) return@runOnUiThread
        val isDark = (activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val themeRes = if (isDark) android.R.style.Theme_DeviceDefault_Dialog_Alert else android.R.style.Theme_DeviceDefault_Light_Dialog_Alert
        AlertDialog.Builder(activity, themeRes)
            .setTitle(title)
            .setItems(variants.map { it.label }.toTypedArray()) { _, which ->
                val variant = variants.getOrNull(which) ?: return@setItems
                enqueue(
                    activity,
                    listOf(FacebookItem(variant.url, isVideo, imageUrl = variant.url.takeIf { !isVideo })),
                    username,
                )
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}

private fun copyLink(activity: Activity, url: String) {
    activity.getSystemService(ClipboardManager::class.java)
        .setPrimaryClip(ClipData.newPlainText("Media link", url))
    toast(activity, "Copied media link")
}

private fun openExternally(activity: Activity, url: String, isVideo: Boolean) {
    val intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(Uri.parse(url), if (isVideo) "video/*" else "image/*")
    runCatching { activity.startActivity(Intent.createChooser(intent, null)) }
        .onFailure { toast(activity, "No app found to open the media") }
}

private fun downloadAudio(context: Context, item: FacebookItem, username: String?) {
    val appContext = context.applicationContext ?: context
    val settings = FacebookDownloadSettings.open(appContext)
    toast(context, "Downloading audio...")
    Thread {
        val success = runCatching {
            val audioUrl = item.audioUrl
            if (audioUrl != null) saveDirectAudio(appContext, audioUrl, item, settings, username)
            else extractAndSaveAudio(appContext, item, settings, username)
        }.onFailure { log("audio failed: ${it.stackTraceToString().take(1500)}") }.getOrDefault(false)
        val destination = settings.destination(username, FacebookDownloadSettings.Kind.AUDIO)
        toast(appContext, if (success) "Saved to $destination" else "Audio download failed")
    }.apply { name = "NexFusion Facebook audio" }.start()
}

private fun saveDirectAudio(
    context: Context,
    audioUrl: String,
    item: FacebookItem,
    settings: FacebookDownloadSettings,
    username: String?,
): Boolean {
    val raw = Uri.parse(audioUrl).lastPathSegment?.takeIf { it.contains('.') }
        ?: Uri.parse(item.url).lastPathSegment?.takeIf { it.contains('.') }
        ?: "facebook_audio_${System.currentTimeMillis()}.m4a"
    val base = safeName(if (raw.contains('.')) raw.substring(0, raw.lastIndexOf('.')) else raw)
    val name = "$base.m4a".withTimestamp(settings.timestamp)
    val connection = URL(audioUrl).openConnection() as HttpURLConnection
    try {
        connection.connectTimeout = 15_000
        connection.readTimeout = 30_000
        if (connection.responseCode !in 200..299) return extractAndSaveAudio(context, item, settings, username)
        connection.inputStream.use { input ->
            writeAudio(context, input, name, settings, username)
        }
        return true
    } finally {
        connection.disconnect()
    }
}

private fun extractAndSaveAudio(
    context: Context,
    item: FacebookItem,
    settings: FacebookDownloadSettings,
    username: String?,
): Boolean {
    val raw = Uri.parse(item.url).lastPathSegment?.takeIf { it.contains('.') }
        ?: "facebook_audio_${System.currentTimeMillis()}.mp4"
    val base = safeName(if (raw.contains('.')) raw.substring(0, raw.lastIndexOf('.')) else raw)
    val name = "$base.m4a".withTimestamp(settings.timestamp)
    val cache = context.cacheDir ?: return false
    val tempVideo = File.createTempFile("nex_fb_vid_", ".mp4", cache)
    val tempAudio = File.createTempFile("nex_fb_aud_", ".m4a", cache)
    return try {
        val connection = URL(item.url).openConnection() as HttpURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 30_000
        if (connection.responseCode !in 200..299) return false
        connection.inputStream.use { input -> tempVideo.outputStream().use { input.copyTo(it) } }
        connection.disconnect()
        if (!extractAudioTrack(tempVideo, tempAudio) || tempAudio.length() == 0L) return false
        tempAudio.inputStream().use { input -> writeAudio(context, input, name, settings, username) }
        true
    } catch (e: Exception) {
        log("audio extraction failed: $e")
        false
    } finally {
        tempVideo.delete()
        tempAudio.delete()
    }
}

private fun extractAudioTrack(source: File, output: File): Boolean {
    val extractor = android.media.MediaExtractor()
    var muxer: android.media.MediaMuxer? = null
    return try {
        extractor.setDataSource(source.absolutePath)
        var track = -1
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(android.media.MediaFormat.KEY_MIME).orEmpty()
            if (mime.startsWith("audio/")) {
                track = i
                extractor.selectTrack(i)
                muxer = android.media.MediaMuxer(output.absolutePath, android.media.MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
                muxer.addTrack(format)
                muxer.start()
                break
            }
        }
        if (track == -1 || muxer == null) return false
        val buffer = java.nio.ByteBuffer.allocateDirect(256 * 1024)
        val info = android.media.MediaCodec.BufferInfo()
        while (true) {
            info.offset = 0
            info.size = extractor.readSampleData(buffer, 0)
            if (info.size < 0) break
            info.presentationTimeUs = extractor.sampleTime
            info.flags = extractor.sampleFlags
            muxer.writeSampleData(0, buffer, info)
            extractor.advance()
        }
        true
    } catch (e: Exception) {
        log("audio extraction failed: $e")
        false
    } finally {
        runCatching { muxer?.stop() }
        runCatching { muxer?.release() }
        runCatching { extractor.release() }
    }
}

private fun writeAudio(
    context: Context,
    input: java.io.InputStream,
    name: String,
    settings: FacebookDownloadSettings,
    username: String?,
) {
    val kind = FacebookDownloadSettings.Kind.AUDIO
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "audio/mp4")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "${settings.baseDirectory(kind)}/${settings.folder(username)}")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(settings.collection(kind), values)
            ?: throw IOException("MediaStore did not create $name")
        try {
            resolver.openOutputStream(uri)!!.use { input.copyTo(it) }
            resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
    } else {
        @Suppress("DEPRECATION")
        val base = Environment.getExternalStoragePublicDirectory(settings.baseDirectory(kind))
        val folder = File(base, settings.folder(username))
        folder.mkdirs()
        File(folder, name).outputStream().use { input.copyTo(it) }
    }
}

private fun String.withTimestamp(timestamp: Boolean): String {
    if (!timestamp) return this
    val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    val dot = lastIndexOf('.')
    return if (dot > 0) "${substring(0, dot)}_$stamp${substring(dot)}" else "${this}_$stamp"
}

private fun showDownloadSettings(activity: Activity) {
    activity.runOnUiThread {
        if (activity.isFinishing || activity.isDestroyed) return@runOnUiThread
        val settings = FacebookDownloadSettings.open(activity)
        val labels = arrayOf(
            "Save location: ${settings.location.label}",
            "Per-poster folders: ${if (settings.perUsername) "On" else "Off"}",
            "Timestamp names: ${if (settings.timestamp) "On" else "Off"}",
            "Custom folder: ${settings.customFolder}",
            "Reset to defaults",
        )
        val isDark = (activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val themeRes = if (isDark) android.R.style.Theme_DeviceDefault_Dialog_Alert else android.R.style.Theme_DeviceDefault_Light_Dialog_Alert
        AlertDialog.Builder(activity, themeRes)
            .setTitle("Download settings")
            .setItems(labels) { _, which ->
                when (which) {
                    0 -> {
                        settings.location = if (settings.location == FacebookDownloadSettings.Location.DOWNLOADS) {
                            FacebookDownloadSettings.Location.GALLERY
                        } else {
                            FacebookDownloadSettings.Location.DOWNLOADS
                        }
                        toast(activity, "Save location: ${settings.location.label}")
                    }
                    1 -> {
                        settings.perUsername = !settings.perUsername
                        toast(activity, "Per-poster folders: ${if (settings.perUsername) "On" else "Off"}")
                    }
                    2 -> {
                        settings.timestamp = !settings.timestamp
                        toast(activity, "Timestamp names: ${if (settings.timestamp) "On" else "Off"}")
                    }
                    3 -> {
                        val input = EditText(activity).apply {
                            setText(settings.customFolder)
                            setSelection(text.length)
                        }
                        AlertDialog.Builder(activity, themeRes)
                            .setTitle("Download folder")
                            .setMessage("Folder under Downloads or Pictures and Movies:")
                            .setView(input)
                            .setPositiveButton("Save") { _, _ ->
                                settings.customFolder = input.text.toString()
                                toast(activity, "Folder: ${settings.customFolder}")
                            }
                            .setNegativeButton("Cancel", null)
                            .show()
                    }
                    4 -> {
                        settings.customFolder = FacebookDownloadSettings.DEFAULT_FOLDER
                        settings.perUsername = false
                        settings.timestamp = false
                        settings.location = FacebookDownloadSettings.Location.DOWNLOADS
                        toast(activity, "Download settings reset")
                    }
                }
            }
            .setNegativeButton("Close", null)
            .show()
    }
}

private fun showOptions(activity: Activity, items: List<FacebookItem>, username: String?) {
    activity.runOnUiThread {
        if (activity.isFinishing || activity.isDestroyed) return@runOnUiThread
        if (items.isEmpty()) {
            toast(activity, "No media found")
            return@runOnUiThread
        }
        val current = items.first()
        val options = mutableListOf<Pair<String, () -> Unit>>()
        if (current.isVideo) {
            options += "Download video" to { enqueue(activity, listOf(current), username) }
            if (current.audioUrl != null || current.isVideo) {
                options += "Download audio" to { downloadAudio(activity, current, username) }
            }
            if (current.videoVariants.size > 1) {
                options += "Video quality variants" to {
                    showVariants(activity, "Video variants", current.videoVariants, isVideo = true, username)
                }
            }
            options += "Copy media link" to { copyLink(activity, current.url) }
        } else {
            options += "Download photo" to { enqueue(activity, listOf(current), username) }
            if (current.imageVariants.size > 1) {
                options += "Image quality variants" to {
                    showVariants(activity, "Image variants", current.imageVariants, isVideo = false, username)
                }
            }
            options += "Copy media link" to { copyLink(activity, current.url) }
        }
        if (items.size > 1) {
            options += "Download all (${items.size})" to { enqueue(activity, items, username) }
        }
        options += "Download settings" to { showDownloadSettings(activity) }

        val isDark = (activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val themeRes = if (isDark) android.R.style.Theme_DeviceDefault_Dialog_Alert else android.R.style.Theme_DeviceDefault_Light_Dialog_Alert

        AlertDialog.Builder(activity, themeRes)
            .setTitle(DOWNLOAD_TITLE)
            .setItems(options.map { it.first }.toTypedArray()) { _, which ->
                options.getOrNull(which)?.second?.invoke()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}

private fun resolveDownloadIcon(sampleRow: Any, app: Context): Any? {
    return runCatching {
        sampleRow.javaClass.declaredFields.firstOrNull { field ->
            !Modifier.isStatic(field.modifiers) &&
                !field.type.isPrimitive &&
                field.type != String::class.java &&
                field.type != CharSequence::class.java &&
                field.type != java.lang.Boolean.TYPE &&
                field.type != java.lang.Boolean::class.java &&
                !field.type.isInterface
        }?.apply { isAccessible = true }?.get(sampleRow)
    }.getOrNull() ?: runCatching {
        val eeLClass = Class.forName("X.eeL", true, app.classLoader)
        val u1fClass = Class.forName("X.1Uf", true, app.classLoader)
        val a8lField = u1fClass.getField("A8L")
        val a8lVal = a8lField.get(null)
        val a00Method = eeLClass.getMethod("A00", u1fClass)
        a00Method.invoke(null, a8lVal)
    }.getOrNull()
}

private fun rowMatchesIdOrTitle(row: Any, query: String): Boolean {
    return runCatching {
        val fields = row.javaClass.declaredFields
        fields.any { field ->
            if (field.type == CharSequence::class.java || field.type == String::class.java) {
                field.isAccessible = true
                val value = field.get(row)?.toString() ?: ""
                value.contains(query, ignoreCase = true)
            } else false
        }
    }.getOrDefault(false)
}

private val BFS_SKIP_METHODS = setOf(
    "getClass", "hashCode", "toString", "equals", "notify", "notifyAll", "wait",
    "isValidGraphServicesJNIModel", "getTypeName", "getTree", "getTreeList",
    "getImmutableListField", "getString", "getCachedString", "getIntValue"
)

private fun isGraphQlModel(value: Any?): Boolean {
    if (value == null) return false
    return isGraphQlModel(value, STORY) ||
        isGraphQlModel(value, STORY_ATTACHMENT) ||
        isGraphQlModel(value, MEDIA) ||
        isGraphQlModel(value, "com.facebook.graphql.model.FeedUnit") ||
        isGraphQlModel(value, "com.facebook.graphql.model.GraphQLStory") ||
        isGraphQlModel(value, "com.facebook.graphql.model.GraphQLStoryAttachment") ||
        isGraphQlModel(value, "com.facebook.graphql.model.GraphQLMedia")
}

private fun isGraphQlModelClass(clazz: Class<*>): Boolean {
    val name = clazz.name
    return name.contains("GraphQLStory") ||
        name.contains("GraphQLMedia") ||
        name.contains("GraphQLStoryAttachment") ||
        name.endsWith("FeedUnit")
}

/** The item behind a post menu's props object: searches props and its fields for a GraphQL model. */
private fun itemOfProps(props: Any?): Any? {
    if (props == null) return null
    if (isGraphQlModel(props)) return props

    // Check direct static methods that convert this props object into a GraphQL model
    for (method in props.javaClass.declaredMethods) {
        if (Modifier.isStatic(method.modifiers) &&
            method.parameterCount == 1 &&
            method.parameterTypes[0].isAssignableFrom(props.javaClass) &&
            isGraphQlModelClass(method.returnType)
        ) {
            val res = runCatching { method.invoke(null, props) }.getOrNull()
            if (res != null) return res
        }
    }

    val visited = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<Any, Boolean>())
    val queue = ArrayDeque<Any>()
    queue.add(props)
    visited.add(props)

    var depth = 0
    var itemsAtCurrentDepth = 1
    var itemsAtNextDepth = 0

    while (queue.isNotEmpty() && depth < 4) {
        val curr = queue.removeFirst()
        itemsAtCurrentDepth--

        if (isGraphQlModel(curr)) return curr

        // Inspect zero-arg methods
        for (method in curr.javaClass.methods) {
            if (method.parameterCount == 0 &&
                method.returnType != java.lang.Void.TYPE &&
                !method.returnType.isPrimitive &&
                method.name !in BFS_SKIP_METHODS
            ) {
                if (isGraphQlModelClass(method.returnType)) {
                    val res = runCatching { method.invoke(curr) }.getOrNull()
                    if (res != null) return res
                } else if (depth < 2) {
                    val res = runCatching { method.invoke(curr) }.getOrNull()
                    if (res != null && visited.add(res) && !res.javaClass.name.startsWith("android.")) {
                        queue.add(res)
                        itemsAtNextDepth++
                    }
                }
            }
        }

        // Inspect fields
        var c: Class<*>? = curr.javaClass
        while (c != null && c != Any::class.java && !c.name.startsWith("java.") && !c.name.startsWith("android.")) {
            for (field in c.declaredFields) {
                if (Modifier.isStatic(field.modifiers) || field.type.isPrimitive) continue
                runCatching {
                    field.isAccessible = true
                    val value = field.get(curr) ?: return@runCatching
                    if (isGraphQlModel(value)) return value
                    if (value is Iterable<*>) {
                        for (elem in value) {
                            if (elem != null && isGraphQlModel(elem)) return elem
                            if (elem != null && visited.add(elem) && !elem.javaClass.name.startsWith("java.")) {
                                queue.add(elem)
                                itemsAtNextDepth++
                            }
                        }
                    } else if (visited.add(value) && !value.javaClass.name.startsWith("java.") && !value.javaClass.name.startsWith("android.")) {
                        queue.add(value)
                        itemsAtNextDepth++
                    }
                }
            }
            c = c.superclass
        }

        if (itemsAtCurrentDepth == 0) {
            itemsAtCurrentDepth = itemsAtNextDepth
            itemsAtNextDepth = 0
            depth++
        }
    }
    return null
}

private fun isGraphQlModel(value: Any?, className: String): Boolean {
    if (value == null) return false
    var type: Class<*>? = value.javaClass
    while (type != null) {
        if (type.name == className || type.interfaces.any { it.name == className }) return true
        type = type.superclass
    }
    return false
}

/** The listener that runs our save first and Facebook's own when there is nothing to save. */
private class FacebookPhotoAction(
    private val original: MenuItem.OnMenuItemClickListener,
    private val photo: Any,
    private val reader: FacebookMediaReader,
    private val context: Context,
    private val username: String?,
) : MenuItem.OnMenuItemClickListener {
    override fun onMenuItemClick(item: MenuItem): Boolean {
        val media = runCatching { reader.itemForMedia(photo) }.getOrNull()
        if (media != null) {
            enqueue(context, listOf(media), username)
            return true
        }
        return original.onMenuItemClick(item)
    }
}

/**
 * The Facebook download patch. It adds Download options to every post menu, reels and stories,
 * unlocks the photo viewer's Save photo, and wraps that action so the biggest copy goes through the same save.
 */
val DownloadFacebookMedia = patch(
    name = "Download media",
    description = "Adds Download options to Facebook posts, reels, stories and photos, with photo, video, cover and " +
        "audio saves. Files go to Download/NexFusion/Facebook by default; the download settings " +
        "set the folder, per-poster subfolders and timestamped names.",
) {
    val app = appContext
    val application = app as? Application ?: (app.applicationContext as? Application)
    application?.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
        override fun onActivityResumed(activity: Activity) { lastResumedActivity = WeakReference(activity) }
        override fun onActivityPaused(activity: Activity) {}
        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
        override fun onActivityStarted(activity: Activity) {}
        override fun onActivityStopped(activity: Activity) {}
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
        override fun onActivityDestroyed(activity: Activity) {}
    })

    val reader = FacebookMediaReader(
        attachmentsGetter = runCatching { ::storyAttachmentsGetter.method }.getOrNull(),
        attachedStoryGetter = runCatching { ::storyAttachedStoryGetter.method }.getOrNull(),
        mediaGetter = runCatching { ::attachmentMediaGetter.method }.getOrNull(),
        subattachmentsGetter = runCatching { ::attachmentSubattachmentsGetter.method }.getOrNull(),
        log = ::log,
    )

    // Player sources listener: caches video URLs & DASH manifest from VideoPlayerParams
    var playerParamsHooks = 0
    runCatching {
        for (dex in ::videoPlayerParamsConstructors.dexMethodList) {
            dex.hookMethod {
                after { param ->
                    param.thisObject?.let { PlayerSources.record(it) }
                }
            }
            playerParamsHooks++
        }
    }.onFailure { log("videoPlayerParamsConstructors hooks skipped: ${it.message}") }

    // Post menus: every filler's after-hook sees the menu once Facebook has filled it.
    var menuHooks = 0
    runCatching {
        for (dex in ::postMenuFillers.dexMethodList) {
            dex.hookMethod {
                after { param ->
                    runCatching {
                        val menu = param.args.getOrNull(0) as? Menu ?: return@after
                        val view = param.args.getOrNull(1) as? View ?: return@after
                        if (menu.findItem(MENU_ID) != null) return@after

                        var item: Any? = null
                        for (arg in param.args.drop(2)) {
                            if (arg != null) {
                                item = itemOfProps(arg)
                                if (item != null) break
                            }
                        }

                        // Add Download options at index 0 (top of menu)
                        val entry = menu.add(Menu.NONE, MENU_ID, 0, DOWNLOAD_TITLE)
                        val activity = resolveActivity(view.context)
                        entry.setOnMenuItemClickListener {
                            val items = if (item != null) reader.items(item) else emptyList()
                            val finalItems = if (items.isNotEmpty()) items else {
                                val src = PlayerSources.latestSource
                                val url = src?.hdUrl ?: src?.sdUrl ?: FacebookDash.bestVideoUrl(src?.manifest)
                                if (url != null) {
                                    listOf(
                                        FacebookItem(
                                            url = url,
                                            isVideo = true,
                                            imageUrl = null,
                                            audioUrl = FacebookDash.bestAudioUrl(src?.manifest),
                                        ),
                                    )
                                } else emptyList()
                            }
                            if (finalItems.isNotEmpty()) {
                                val username = item?.let { reader.usernameOf(it) }
                                if (activity != null) {
                                    showOptions(activity, finalItems, username)
                                } else {
                                    enqueue(view.context, finalItems, username)
                                }
                            } else {
                                Toast.makeText(view.context, "No media found to download", Toast.LENGTH_SHORT).show()
                            }
                            true
                        }
                    }.onFailure { log("post menu: ${it.message}") }
                }
            }
            menuHooks++
        }
    }.onFailure { log("postMenuFillers hooks skipped: ${it.message}") }

    // Reel More Sheet: hook bQt->A04 to add "Download options" row to the sheet's List
    var reelHooks = 0
    runCatching {
        for (dex in ::reelMoreSheetBuilders.dexMethodList) {
            dex.hookMethod {
                after { param ->
                    runCatching {
                        val rows = (param.args.firstOrNull { it is MutableList<*> } as? MutableList<Any>)
                            ?: (param.result as? MutableList<Any>)
                            ?: return@after
                        if (rows.isEmpty()) return@after

                        var reelAttachment: Any? = null
                        for (arg in param.args) {
                            if (arg == null) continue
                            if (isGraphQlModel(arg)) {
                                reelAttachment = arg
                                break
                            }
                            for (m in arg.javaClass.declaredMethods) {
                                if (Modifier.isStatic(m.modifiers) &&
                                    m.parameterCount == 1 &&
                                    m.parameterTypes[0] == arg.javaClass &&
                                    isGraphQlModelClass(m.returnType)
                                ) {
                                    val res = runCatching { m.invoke(null, arg) }.getOrNull()
                                    if (res != null) {
                                        reelAttachment = res
                                        break
                                    }
                                }
                            }
                            if (reelAttachment != null) break
                        }

                        val videoId = param.args.filterIsInstance<String>().firstOrNull { str ->
                            PlayerSources.find(str) != null || (str.all { it.isDigit() } && str.length in 10..25)
                        } ?: param.args.filterIsInstance<String>().firstOrNull { it.length > 5 }
                            ?: PlayerSources.latestSource?.videoId

                        val playerOrigin = param.args.firstOrNull { it != null && it.javaClass.name.contains("PlayerOrigin") }
                        val playerStore = param.args.firstOrNull { obj ->
                            obj != null && obj.javaClass.methods.any {
                                it.parameterTypes.size == 2 && it.parameterTypes[1] == String::class.java
                            }
                        }

                        fun resolveReelItems(): Pair<List<FacebookItem>, String?> {
                            var items: List<FacebookItem> = emptyList()
                            var username: String? = null
                            if (reelAttachment != null) {
                                items = reader.items(reelAttachment)
                                username = reader.usernameOf(reelAttachment)
                            }
                            if (items.isEmpty() && playerStore != null && playerOrigin != null && videoId != null) {
                                val storeMethod = playerStore.javaClass.methods.firstOrNull {
                                    it.parameterTypes.size == 2 && it.parameterTypes[1] == String::class.java
                                }
                                val z3E = storeMethod?.invoke(playerStore, playerOrigin, videoId)
                                if (z3E != null) {
                                    val mediaField = z3E.javaClass.declaredFields.firstOrNull {
                                        it.type.name == "com.facebook.graphql.model.GraphQLMedia"
                                    }?.apply { isAccessible = true }
                                    val media = mediaField?.get(z3E)
                                    if (media != null) {
                                        items = reader.items(media)
                                        username = reader.usernameOf(media)
                                    }
                                }
                            }
                            if (items.isEmpty()) {
                                val source = (if (videoId != null) PlayerSources.find(videoId) else null)
                                    ?: PlayerSources.latestSource
                                val url = source?.hdUrl ?: source?.sdUrl ?: FacebookDash.bestVideoUrl(source?.manifest)
                                if (url != null) {
                                    items = listOf(
                                        FacebookItem(
                                            url = url,
                                            isVideo = true,
                                            imageUrl = null,
                                            audioUrl = FacebookDash.bestAudioUrl(source?.manifest),
                                        ),
                                    )
                                }
                            }
                            return items to username
                        }

                        val sampleRow = rows.first()
                        val downloadIcon = resolveDownloadIcon(sampleRow, app)

                        val constructor = sampleRow.javaClass.constructors.maxByOrNull {
                            it.parameterTypes.size
                        } ?: return@after

                        val clickInterface = constructor.parameterTypes.firstOrNull { it.isInterface }
                            ?: return@after

                        val videoListener = Proxy.newProxyInstance(
                            clickInterface.classLoader,
                            arrayOf(clickInterface),
                        ) { _, method, _ ->
                            if (method.declaringClass != Any::class.java) {
                                val (items, username) = resolveReelItems()
                                if (items.isNotEmpty()) {
                                    enqueue(app, items, username)
                                } else {
                                    Toast.makeText(app, "No media found to download", Toast.LENGTH_SHORT).show()
                                }
                            }
                            null
                        }

                        val audioListener = Proxy.newProxyInstance(
                            clickInterface.classLoader,
                            arrayOf(clickInterface),
                        ) { _, method, _ ->
                            if (method.declaringClass != Any::class.java) {
                                val (items, username) = resolveReelItems()
                                if (items.isNotEmpty()) {
                                    downloadAudio(app, items.first(), username)
                                } else {
                                    Toast.makeText(app, "No media found to download", Toast.LENGTH_SHORT).show()
                                }
                            }
                            null
                        }

                        fun createRow(listener: Any, title: String, controlId: String): Any? {
                            val constructors = sampleRow.javaClass.constructors.sortedByDescending { it.parameterTypes.size }
                            for (ctor in constructors) {
                                val params = ctor.parameterTypes
                                val args = arrayOfNulls<Any?>(params.size)
                                val charSeqIndices = params.indices.filter {
                                    params[it] == String::class.java || params[it] == CharSequence::class.java
                                }
                                for (i in params.indices) {
                                    val p = params[i]
                                    args[i] = when {
                                        p.isInterface && p.isAssignableFrom(listener.javaClass) -> listener
                                        p == java.lang.Boolean.TYPE || p == java.lang.Boolean::class.java -> false
                                        p == java.lang.Integer.TYPE || p == java.lang.Integer::class.java -> 0
                                        p == java.lang.Long.TYPE || p == java.lang.Long::class.java -> 0L
                                        p == java.lang.Float.TYPE || p == java.lang.Float::class.java -> 0f
                                        p == java.lang.Double.TYPE || p == java.lang.Double::class.java -> 0.0
                                        p == String::class.java || p == CharSequence::class.java -> {
                                            when (i) {
                                                charSeqIndices.firstOrNull() -> title
                                                charSeqIndices.lastOrNull() -> controlId
                                                else -> null
                                            }
                                        }
                                        downloadIcon != null && p.isAssignableFrom(downloadIcon.javaClass) -> downloadIcon
                                        else -> {
                                            sampleRow.javaClass.declaredFields.firstOrNull { f ->
                                                !Modifier.isStatic(f.modifiers) && p.isAssignableFrom(f.type)
                                            }?.apply { isAccessible = true }?.get(sampleRow)
                                        }
                                    }
                                }
                                val instance = runCatching { ctor.newInstance(*args) }.getOrNull()
                                if (instance != null) return instance
                            }
                            return runCatching {
                                val unsafeField = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe").apply { isAccessible = true }
                                val unsafe = unsafeField.get(null)
                                val allocateInstance = unsafe.javaClass.getMethod("allocateInstance", Class::class.java)
                                val copy = allocateInstance.invoke(unsafe, sampleRow.javaClass)
                                for (field in sampleRow.javaClass.declaredFields) {
                                    if (Modifier.isStatic(field.modifiers)) continue
                                    field.isAccessible = true
                                    when {
                                        field.type.isInterface && field.type.isAssignableFrom(listener.javaClass) -> field.set(copy, listener)
                                        field.type == String::class.java || field.type == CharSequence::class.java -> {
                                            val cur = (field.get(sampleRow) as? CharSequence)?.toString() ?: ""
                                            if (cur.contains("fds_control") || cur.contains("control")) {
                                                field.set(copy, controlId)
                                            } else if (cur.isNotEmpty()) {
                                                field.set(copy, title)
                                            } else {
                                                field.set(copy, cur)
                                            }
                                        }
                                        else -> field.set(copy, field.get(sampleRow))
                                    }
                                }
                                copy
                            }.getOrNull()
                        }

                        val downloadVideoRow = createRow(videoListener, "Download reel", "fds_control_download_reel")
                        val downloadAudioRow = createRow(audioListener, "Download audio", "fds_control_download_audio")
                        if (downloadVideoRow == null) return@after

                        // Insert at index 0 (top of sheet) so it is immediately visible
                        if (downloadAudioRow != null) rows.add(0, downloadAudioRow)
                        rows.add(0, downloadVideoRow)
                        log("added Download reel and Download audio rows to top of Reel More Sheet (videoId=$videoId)")
                    }.onFailure { log("Reel more sheet hook error: ${it.message}") }
                }
            }
            reelHooks++
        }
    }.onFailure { log("Reel more sheet hook failed: ${it.message}") }

    // Story capability: CsG.<init> and CsG.A0G
    var storyHooks = 0
    runCatching {
        for (dex in ::storyCapabilityConstructors.dexMethodList) {
            dex.hookMethod {
                after { param ->
                    runCatching {
                        val csG = param.thisObject ?: return@after
                        val field = csG.javaClass.declaredFields.firstOrNull {
                            it.name == "A0P" || (it.type == java.lang.Boolean::class.java && Modifier.isPublic(it.modifiers))
                        }?.apply { isAccessible = true }
                        field?.set(csG, java.lang.Boolean.TRUE)
                    }
                }
            }
            storyHooks++
        }
    }.onFailure { log("story capability constructor hook failed: ${it.message}") }

    runCatching {
        for (dex in ::storySaveCapabilityMethods.dexMethodList) {
            dex.hookMethod {
                after { param -> param.result = true }
            }
            storyHooks++
        }
    }.onFailure { log("story capability method hook failed: ${it.message}") }

    // Story save action: eVD->A00
    runCatching {
        for (dex in ::storySaveActions.dexMethodList) {
            dex.hookMethod {
                before { param ->
                    runCatching {
                        val storyCard = param.args.firstOrNull { it != null && it.javaClass.name.endsWith("StoryCard") }
                            ?: param.args.getOrNull(0) ?: return@before
                        val id = runCatching {
                            storyCard.javaClass.getMethod("getId").invoke(storyCard) as? String
                        }.getOrNull()
                        var items = reader.items(storyCard)
                        if (items.isEmpty()) {
                            val bbd = runCatching {
                                storyCard.javaClass.getMethod("getMedia").invoke(storyCard)
                            }.getOrNull()
                            if (bbd != null) items = reader.items(bbd)
                        }
                        if (items.isEmpty() && id != null) {
                            val src = PlayerSources.find(id)
                            val url = src?.hdUrl ?: src?.sdUrl ?: FacebookDash.bestVideoUrl(src?.manifest)
                            if (url != null) {
                                items = listOf(
                                    FacebookItem(
                                        url = url,
                                        isVideo = true,
                                        imageUrl = null,
                                        audioUrl = FacebookDash.bestAudioUrl(src?.manifest),
                                    ),
                                )
                            }
                        }
                        if (items.isNotEmpty()) {
                            val username = reader.usernameOf(storyCard)
                            enqueue(app, items, username)
                            param.result = null
                            log("story save action handled for story id=$id, items=${items.size}")
                        }
                    }.onFailure { log("story save action hook error: ${it.message}") }
                }
            }
            storyHooks++
        }
    }.onFailure { log("story save action hook failed: ${it.message}") }

    // Photo viewer: the two reads of can_viewer_download say yes while this patch is on.
    var gateHooks = 0
    runCatching {
        for (dex in ::canViewerDownloadGates.dexMethodList) {
            dex.hookMethod {
                after { param -> param.result = true }
            }
            gateHooks++
        }
    }.onFailure { log("canViewerDownloadGates hooks skipped: ${it.message}") }

    // The photo viewer's own Save photo action is wrapped, so a tap saves the biggest image.
    var photoActionHooked = false
    runCatching {
        for (dex in ::savePhotoActions.dexMethodList) {
            dex.hookMethod {
                after { param ->
                    val original = param.result as? MenuItem.OnMenuItemClickListener ?: return@after
                    val photo = param.args.getOrNull(0) ?: return@after
                    param.result = FacebookPhotoAction(
                        original = original,
                        photo = photo,
                        reader = reader,
                        context = app,
                        username = runCatching { reader.usernameOf(photo) }.getOrNull(),
                    )
                }
            }
            photoActionHooked = true
        }
    }.onFailure { log("savePhotoAction hook skipped: ${it.message}") }

    val totalHooks = menuHooks + gateHooks + (if (photoActionHooked) 1 else 0) + reelHooks + storyHooks + playerParamsHooks
    log("post menus=$menuHooks, can_viewer_download gates=$gateHooks, photo action=$photoActionHooked, reel=$reelHooks, story=$storyHooks, playerParams=$playerParamsHooks (total=$totalHooks)")
    check(totalHooks > 0) { "Download media: no Facebook download hook could be installed" }
}
