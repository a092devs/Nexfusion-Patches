package io.github.nexfusion.nexfusion.instagram.download

import android.app.Activity
import android.app.AlertDialog
import android.app.Application
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import io.github.nexfusion.compat.XC_MethodHook.MethodHookParam
import io.github.nexfusion.HookDsl
import io.github.nexfusion.IScopedHookCallback
import io.github.nexfusion.PatcherLog
import io.github.nexfusion.hookMethod
import io.github.nexfusion.patch
import io.github.nexfusion.scopedHook
import java.io.File
import java.io.IOException
import java.lang.reflect.Field
import java.lang.ref.WeakReference
import java.lang.reflect.Modifier
import java.lang.reflect.Proxy
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.text.SimpleDateFormat
import java.util.Collections
import java.util.Date
import java.util.Locale
import java.util.WeakHashMap
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

// Restored from this repository's Piko bundle: the Instagram download patches as they shipped
// until release 2.6.0 replaced that bundle's Instagram side with the HushGram ports (recovered
// from commit 5d4f3b7^; originally ported from crimera/piko (GPL-3.0) — see the NOTICE). Only the
// package, the imports and the main handler's initialization changed (the handler is lazy now, so
// the unit test JVM can load this file); the menus, the media reading and the saving are unchanged.

private const val DOWNLOAD_OPTION = "NEXFUSION_DOWNLOAD"
private const val DOWNLOAD_LABEL = "Download options"
private const val PROFILE_PICTURE_LABEL = "Download"
private const val HIGHLIGHT_COVER_LABEL = "Download highlight cover"
private const val HIGHLIGHT_MENU_MODULE = "HighlightsTrayDialogHelper"

/**
 * Logs what the download hooks do, so a missing download option can be traced in the logcat (tag NexFusion).
 */
private fun log(message: String) {
    Log.i("NexFusion", "Download media: $message")
}

/**
 * Creates an option of the post overflow menu. Like Piko's, it is an extra constant of `MediaOption$Option`,
 * created with the enum constructor `(name, ordinal, iconDrawable)`.
 */
private fun newMenuOption(optionClass: Class<*>, name: String, ordinal: Int, icon: Int): Any = try {
    optionClass.getDeclaredConstructor(String::class.java, Int::class.java, Int::class.java)
        .apply { isAccessible = true }
        .newInstance(name, ordinal, icon)
} catch (e: ReflectiveOperationException) {
    PatcherLog.info(e) { "Creating the menu option without its constructor" }
    val unsafeClass = Class.forName("sun.misc.Unsafe")
    val unsafe = unsafeClass.getDeclaredField("theUnsafe").apply { isAccessible = true }.get(null)
    unsafeClass.getMethod("allocateInstance", Class::class.java).invoke(unsafe, optionClass)!!.also { option ->
        Enum::class.java.getDeclaredField("name").apply { isAccessible = true }.set(option, name)
        Enum::class.java.getDeclaredField("ordinal").apply { isAccessible = true }.setInt(option, ordinal)
        optionClass.declaredFields.single { !Modifier.isStatic(it.modifiers) && it.type == Int::class.java }
            .apply { isAccessible = true }.setInt(option, icon)
    }
}

private val objectFields = ConcurrentHashMap<Class<*>, List<Field>>()

/**
 * The object of [type] captured by the lambda [owner].
 */
private fun captured(owner: Any, type: Class<*>): Any? =
    objectFields.getOrPut(owner.javaClass) {
        owner.javaClass.declaredFields.filter { !Modifier.isStatic(it.modifiers) && !it.type.isPrimitive }
            .onEach { it.isAccessible = true }
    }.firstNotNullOfOrNull { field -> field.get(owner)?.takeIf(type::isInstance) }

private fun toast(context: Context, text: String) = Toast.makeText(context, text, Toast.LENGTH_SHORT).show()

// Lazy so the unit test JVM, which stubs the Android framework, can load this file; on device it
// is still one handler on the main looper, created on first use.
private val mainHandler by lazy { Handler(Looper.getMainLooper()) }

/**
 * The file name of [item], the last path segment of its URL or a new name, with the time of the save added when
 * [DownloadSettings.timestamp] is on.
 */
private fun fileName(item: MediaExtractor.Item, timestamp: Boolean): String {
    val extension = if (item.isVideo) ".mp4" else ".jpg"
    val name = Uri.parse(item.url).lastPathSegment?.takeIf { it.contains('.') }
        ?: "instagram_${System.currentTimeMillis()}$extension"
    val dated = name.withTimestamp(timestamp)
    // A cover image keeps the name of its video file otherwise.
    return if (!item.isVideo && !dated.endsWith(".jpg") && !dated.endsWith(".webp") && !dated.endsWith(".heic")) "$dated.jpg"
    else dated
}

/**
 * [name] with the time of the save before its extension: `clip.mp4` becomes `clip_20261017_143005.mp4`.
 */
private fun String.withTimestamp(timestamp: Boolean): String {
    if (!timestamp) return this
    val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    val dot = lastIndexOf('.')
    return if (dot > 0) "${substring(0, dot)}_$stamp${substring(dot)}" else "${this}_$stamp"
}

/**
 * Saves the file of [item] to the folder [settings] names, for [username]. Like Piko, the file is fetched by the app
 * itself, as not every device has the download manager.
 */
private fun save(context: Context, item: MediaExtractor.Item, settings: DownloadSettings, username: String?) {
    val name = fileName(item, settings.timestamp)
    val mimeType = if (item.isVideo) "video/mp4" else if (name.endsWith(".webp")) "image/webp" else "image/jpeg"
    val connection = URL(item.url).openConnection() as HttpURLConnection
    try {
        connection.connectTimeout = 15_000
        connection.readTimeout = 30_000
        if (connection.responseCode !in 200..299) throw IOException("HTTP ${connection.responseCode}")
        connection.inputStream.use { input ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                    put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "${settings.baseDirectory(item.isVideo)}/${settings.folder(username)}")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
                val uri = resolver.insert(settings.collection(item.isVideo), values)
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
                val base = Environment.getExternalStoragePublicDirectory(settings.baseDirectory(item.isVideo))
                val folder = File(base, settings.folder(username))
                folder.mkdirs()
                File(folder, name).outputStream().use { output -> input.copyTo(output) }
            }
        }
    } finally {
        connection.disconnect()
    }
    log("saved $name to ${settings.destination(username, item.isVideo)}")
}

/**
 * Saves [items] of [username] one by one on a worker thread, the settings read once for the whole batch, and toasts
 * where they landed.
 */
private fun enqueue(context: Context, items: List<MediaExtractor.Item>, username: String? = null) {
    val appContext = context.applicationContext ?: context
    val settings = DownloadSettings.open(appContext)
    toast(context, if (items.size == 1) "Downloading" else "Downloading ${items.size} items")
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
        val destination = settings.destination(username, items.first().isVideo)
        val text = when {
            saved == items.size -> "Saved to $destination"
            saved == 0 -> "Download failed"
            else -> "Saved $saved of ${items.size} to $destination"
        }
        mainHandler.post { toast(appContext, text) }
    }.apply { name = "NexFusion download" }.start()
}

/**
 * The photo of [item], the cover image when it is a video.
 */
private fun asImage(item: MediaExtractor.Item) = item.imageUrl?.let {
    MediaExtractor.Item(it, isVideo = false, imageUrl = it, item.imageVariants)
}

private fun copyLink(context: Context, url: String) {
    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Media link", url))
    toast(context, "Copied media link")
}

private fun openExternally(activity: Activity, url: String, isVideo: Boolean) {
    val intent = Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(url), if (isVideo) "video/*" else "image/*")
    try {
        activity.startActivity(Intent.createChooser(intent, null))
    } catch (e: ActivityNotFoundException) {
        toast(activity, "No app found to open the media")
    }
}

/**
 * Shows the sizes of a photo or video to download one of them.
 */
private fun showVariants(
    dialog: InstagramDialog,
    activity: Activity,
    title: String,
    variants: List<MediaExtractor.Variant>,
    isVideo: Boolean,
    username: String?,
) = dialog.show(activity, title, variants.map { variant ->
    variant.label to {
        enqueue(
            activity,
            listOf(MediaExtractor.Item(variant.url, isVideo, imageUrl = variant.url.takeIf { !isVideo })),
            username,
        )
    }
})

/**
 * Saves the audio of [item] for [username], fetching either its direct audio stream (DASH/music) or
 * extracting the AAC audio track from its video file with Android's MediaExtractor and MediaMuxer.
 */
private fun downloadAudio(activity: Activity, item: MediaExtractor.Item, username: String?) {
    val appContext = activity.applicationContext ?: activity
    val settings = DownloadSettings.open(appContext)
    toast(activity, "Downloading audio")

    Thread {
        try {
            val audioUrl = item.audioUrl
            val success = if (audioUrl != null) {
                saveDirectAudio(appContext, audioUrl, item, settings, username)
            } else if (item.isVideo) {
                extractAndSaveAudio(appContext, item, settings, username)
            } else {
                false
            }

            val destination = settings.destination(username, DownloadSettings.Kind.AUDIO)
            mainHandler.post {
                if (success) {
                    toast(appContext, "Saved to $destination")
                } else {
                    toast(appContext, "Audio download failed")
                }
            }
        } catch (e: Exception) {
            log("failed to save audio: ${e.stackTraceToString().take(1500)}")
            mainHandler.post { toast(appContext, "Audio download failed") }
        }
    }.apply { name = "NexFusion audio download" }.start()
}

private fun saveDirectAudio(
    context: Context,
    audioUrl: String,
    item: MediaExtractor.Item,
    settings: DownloadSettings,
    username: String?,
): Boolean {
    val rawName = Uri.parse(audioUrl).lastPathSegment?.takeIf { it.contains('.') }
        ?: Uri.parse(item.url).lastPathSegment?.takeIf { it.contains('.') }
        ?: "audio_${System.currentTimeMillis()}.m4a"
    val base = if (rawName.contains('.')) rawName.substring(0, rawName.lastIndexOf('.')) else rawName
    val ext = if (rawName.endsWith(".mp3", ignoreCase = true)) ".mp3" else ".m4a"
    val name = "$base$ext".withTimestamp(settings.timestamp)
    val mimeType = if (ext == ".mp3") "audio/mpeg" else "audio/mp4"

    val connection = URL(audioUrl).openConnection() as HttpURLConnection
    try {
        connection.connectTimeout = 15_000
        connection.readTimeout = 30_000
        if (connection.responseCode !in 200..299) {
            if (item.isVideo) return extractAndSaveAudio(context, item, settings, username)
            return false
        }

        connection.inputStream.use { input ->
            writeAudioToDestination(context, input, name, mimeType, settings, username)
        }
        return true
    } finally {
        connection.disconnect()
    }
}

private fun extractAndSaveAudio(
    context: Context,
    item: MediaExtractor.Item,
    settings: DownloadSettings,
    username: String?,
): Boolean {
    val rawName = Uri.parse(item.url).lastPathSegment?.takeIf { it.contains('.') }
        ?: "audio_${System.currentTimeMillis()}.mp4"
    val base = if (rawName.contains('.')) rawName.substring(0, rawName.lastIndexOf('.')) else rawName
    val name = "$base.m4a".withTimestamp(settings.timestamp)
    val mimeType = "audio/mp4"

    val cacheDir = context.cacheDir ?: return false
    val tempVideo = File.createTempFile("nex_vid_", ".mp4", cacheDir)
    val tempAudio = File.createTempFile("nex_aud_", ".m4a", cacheDir)
    try {
        val connection = URL(item.url).openConnection() as HttpURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 30_000
        if (connection.responseCode !in 200..299) return false
        connection.inputStream.use { input ->
            tempVideo.outputStream().use { output -> input.copyTo(output) }
        }
        connection.disconnect()

        val extracted = extractAudioTrack(tempVideo, tempAudio)
        if (!extracted || tempAudio.length() == 0L) {
            log("no audio track found in ${item.url}")
            return false
        }

        tempAudio.inputStream().use { input ->
            writeAudioToDestination(context, input, name, mimeType, settings, username)
        }
        return true
    } catch (e: Exception) {
        log("audio extraction failed: $e")
        return false
    } finally {
        tempVideo.delete()
        tempAudio.delete()
    }
}

private fun extractAudioTrack(sourceFile: File, outputFile: File): Boolean {
    val extractor = android.media.MediaExtractor()
    var muxer: android.media.MediaMuxer? = null
    try {
        extractor.setDataSource(sourceFile.absolutePath)
        var audioTrackIndex = -1
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(android.media.MediaFormat.KEY_MIME) ?: ""
            if (mime.startsWith("audio/")) {
                audioTrackIndex = i
                extractor.selectTrack(i)
                muxer = android.media.MediaMuxer(
                    outputFile.absolutePath,
                    android.media.MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4,
                )
                muxer.addTrack(format)
                muxer.start()
                break
            }
        }
        if (audioTrackIndex == -1 || muxer == null) return false

        val buffer = java.nio.ByteBuffer.allocateDirect(256 * 1024)
        val bufferInfo = android.media.MediaCodec.BufferInfo()
        while (true) {
            bufferInfo.offset = 0
            bufferInfo.size = extractor.readSampleData(buffer, 0)
            if (bufferInfo.size < 0) break
            bufferInfo.presentationTimeUs = extractor.sampleTime
            bufferInfo.flags = extractor.sampleFlags
            muxer.writeSampleData(0, buffer, bufferInfo)
            extractor.advance()
        }
        return true
    } catch (e: Exception) {
        log("MediaExtractor/Muxer error: $e")
        return false
    } finally {
        runCatching { muxer?.stop() }
        runCatching { muxer?.release() }
        runCatching { extractor.release() }
    }
}

private fun writeAudioToDestination(
    context: Context,
    input: java.io.InputStream,
    name: String,
    mimeType: String,
    settings: DownloadSettings,
    username: String?,
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, "${settings.baseDirectory(DownloadSettings.Kind.AUDIO)}/${settings.folder(username)}")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(settings.collection(DownloadSettings.Kind.AUDIO), values)
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
        val base = Environment.getExternalStoragePublicDirectory(settings.baseDirectory(DownloadSettings.Kind.AUDIO))
        val folder = File(base, settings.folder(username))
        folder.mkdirs()
        File(folder, name).outputStream().use { output -> input.copyTo(output) }
    }
    log("saved audio $name to ${settings.destination(username, DownloadSettings.Kind.AUDIO)}")
}

/**
 * Shows Piko's download options for the item at [position], the item of a carousel on screen, of [username].
 */
private fun download(
    dialog: InstagramDialog,
    activity: Activity,
    items: List<MediaExtractor.Item>,
    position: Int,
    extraOptions: List<Pair<String, () -> Unit>>,
    username: String?,
) {
    if (items.isEmpty()) return toast(activity, "No media found")
    val current = items[position.takeIf { it in items.indices } ?: 0]
    val options = mutableListOf<Pair<String, () -> Unit>>()
    options += "Download current media" to { enqueue(activity, listOf(current), username) }
    asImage(current)?.let { image -> options += "Download as image" to { enqueue(activity, listOf(image), username) } }
    if (current.isVideo || current.audioUrl != null) {
        options += "Download audio" to { downloadAudio(activity, current, username) }
    }
    options += "Copy media link" to { copyLink(activity, current.url) }
    if (current.imageVariants.isNotEmpty()) {
        options += "Image variants" to {
            showVariants(dialog, activity, "Image variants", current.imageVariants, isVideo = false, username)
        }
    }
    if (current.isVideo) {
        if (current.videoVariants.isNotEmpty()) {
            options += "Video variants" to {
                showVariants(dialog, activity, "Video variants", current.videoVariants, isVideo = true, username)
            }
        }
        options += "Open video externally" to { openExternally(activity, current.url, isVideo = true) }
    } else {
        options += "Open image externally" to { openExternally(activity, current.url, isVideo = false) }
    }
    if (items.size > 1) options += "Download all" to { enqueue(activity, items, username) }
    options += extraOptions
    options += "Download settings" to { showDownloadSettings(dialog, activity) }
    dialog.show(activity, "Download options", options)
}

private const val FOLDER_PICKER_REQUEST_CODE = 0x51F0

@Volatile
private var lastSettingsDialog: Pair<InstagramDialog, Activity>? = null
private val activeFolderPicker = AtomicBoolean(false)
private val hookedActivityClasses = Collections.synchronizedSet(mutableSetOf<Class<*>>())

private fun hookActivityResultIn(clazz: Class<*>) {
    if (!hookedActivityClasses.add(clazz)) return
    val method = clazz.declaredMethods.firstOrNull {
        it.name == "onActivityResult" && it.parameterTypes.contentEquals(
            arrayOf(Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Intent::class.java)
        )
    } ?: return
    runCatching {
        method.apply { isAccessible = true }.hookMethod {
            before { param -> handleActivityResult(param) }
        }
        log("hooked onActivityResult in ${clazz.name}")
    }.onFailure { log("failed to hook onActivityResult in ${clazz.name}: $it") }
}

private fun ensureActivityResultHooked(activity: Activity) {
    var clazz: Class<*>? = activity.javaClass
    while (clazz != null && clazz != Any::class.java) {
        hookActivityResultIn(clazz)
        clazz = clazz.superclass
    }
}

private fun handleActivityResult(param: MethodHookParam) {
    val requestCode = param.args[0] as? Int ?: return
    if (requestCode != FOLDER_PICKER_REQUEST_CODE) return
    param.result = null
    if (!activeFolderPicker.compareAndSet(true, false)) return

    val activity = param.thisObject as? Activity ?: return
    val resultCode = param.args[1] as? Int ?: Activity.RESULT_CANCELED
    val data = param.args[2] as? Intent

    if (resultCode != Activity.RESULT_OK || data?.data == null) {
        log("folder picker cancelled or no data")
        mainHandler.post {
            lastSettingsDialog?.let { (d, a) -> showDownloadSettings(d, a) }
        }
        return
    }

    val uri = data.data!!
    log("folder picker returned: $uri")
    runCatching {
        val takeFlags = data.flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        activity.contentResolver.takePersistableUriPermission(uri, takeFlags)
    }

    val settings = DownloadSettings.open(activity)
    val path = parseTreeUriToFolder(uri)
    if (path.isNotBlank()) {
        if (path.startsWith("Download/", ignoreCase = true) || path.equals("Download", ignoreCase = true) ||
            path.startsWith("Downloads/", ignoreCase = true) || path.equals("Downloads", ignoreCase = true)) {
            settings.location = DownloadSettings.Location.DOWNLOADS
            val sub = if (path.contains('/')) path.substringAfter('/') else ""
            settings.customFolder = sub.ifBlank { DownloadSettings.DEFAULT_FOLDER }
        } else if (path.startsWith("Pictures/", ignoreCase = true) || path.startsWith("Movies/", ignoreCase = true) || path.startsWith("DCIM/", ignoreCase = true)) {
            settings.location = DownloadSettings.Location.GALLERY
            val sub = if (path.contains('/')) path.substringAfter('/') else ""
            settings.customFolder = sub.ifBlank { DownloadSettings.DEFAULT_FOLDER }
        } else {
            settings.customFolder = path
        }
        toast(activity, "Download folder: ${settings.destination(null, false)}")
    }

    mainHandler.post {
        lastSettingsDialog?.let { (d, a) -> showDownloadSettings(d, a) }
    }
}

internal fun parseTreeUriToFolder(uri: Uri): String {
    val docId = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            DocumentsContract.getTreeDocumentId(uri)
        } else null
    }.getOrNull()

    val raw = docId ?: runCatching {
        val decoded = URLDecoder.decode(uri.toString(), "UTF-8")
        val treeIndex = decoded.indexOf("/tree/")
        if (treeIndex != -1) {
            val after = decoded.substring(treeIndex + "/tree/".length)
            after.substringBefore("/document/")
        } else null
    }.getOrNull() ?: uri.lastPathSegment

    if (raw != null) {
        val path = if (raw.startsWith("primary:", ignoreCase = true)) {
            raw.substring("primary:".length).trim('/')
        } else if (raw.contains(':')) {
            raw.substringAfter(':').trim('/')
        } else {
            raw.trim('/')
        }
        if (path.isNotBlank()) return path
    }

    return DownloadSettings.DEFAULT_FOLDER
}

private fun openFolderPicker(dialog: InstagramDialog, activity: Activity) {
    ensureActivityResultHooked(activity)
    lastSettingsDialog = Pair(dialog, activity)
    activeFolderPicker.set(true)
    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
        addFlags(
            Intent.FLAG_GRANT_READ_URI_PERMISSION or
            Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
            Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
            Intent.FLAG_GRANT_PREFIX_URI_PERMISSION
        )
    }
    try {
        activity.startActivityForResult(intent, FOLDER_PICKER_REQUEST_CODE)
    } catch (e: Exception) {
        log("failed to launch folder picker: $e")
        toast(activity, "Could not open file manager")
        activeFolderPicker.set(false)
        showManualFolderDialog(dialog, activity)
    }
}

private fun showManualFolderDialog(dialog: InstagramDialog, activity: Activity) {
    val settings = DownloadSettings.open(activity)
    val density = activity.resources.displayMetrics.density
    val dp = { value: Int -> (value * density).toInt() }
    val input = EditText(activity).apply {
        setText(settings.customFolder)
        setSelection(text.length)
        setSingleLine(true)
    }
    val container = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(20), dp(10), dp(20), dp(10))
        addView(input)
    }
    val isDark = (activity.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
    val themeRes = if (isDark) android.R.style.Theme_DeviceDefault_Dialog_Alert else android.R.style.Theme_DeviceDefault_Light_Dialog_Alert
    AlertDialog.Builder(activity, themeRes)
        .setTitle("Change download folder")
        .setMessage("Base folder path under ${settings.location.label}:")
        .setView(container)
        .setPositiveButton("Save") { _, _ ->
            val newFolder = input.text.toString().trim()
            settings.customFolder = newFolder
            toast(activity, "Download folder: ${settings.destination(null, false)}")
            showDownloadSettings(dialog, activity)
        }
        .setNeutralButton("Reset") { _, _ ->
            settings.customFolder = DownloadSettings.DEFAULT_FOLDER
            toast(activity, "Reset to default: ${settings.destination(null, false)}")
            showDownloadSettings(dialog, activity)
        }
        .setNegativeButton("Cancel") { _, _ ->
            showDownloadSettings(dialog, activity)
        }
        .show()
}

/**
 * The download settings: where saves land, custom download folder, per-username folders and timestamp names.
 * Displayed in Instagram's native bottom sheet dialog. Each row shows its current state; tapping toggles or edits it.
 */
private fun showDownloadSettings(dialog: InstagramDialog, activity: Activity) {
    val settings = DownloadSettings.open(activity)
    fun onOff(value: Boolean) = if (value) "On" else "Off"
    val options = mutableListOf<Pair<String, () -> Unit>>()

    options += "Save to: ${settings.location.label}" to {
        settings.location = if (settings.location == DownloadSettings.Location.DOWNLOADS) {
            DownloadSettings.Location.GALLERY
        } else {
            DownloadSettings.Location.DOWNLOADS
        }
        toast(activity, "Saves go to ${settings.location.label}")
        showDownloadSettings(dialog, activity)
    }

    options += "Download folder: ${settings.destination(null, false)}" to {
        openFolderPicker(dialog, activity)
    }

    if (settings.customFolder != DownloadSettings.DEFAULT_FOLDER) {
        options += "Reset to default folder" to {
            settings.customFolder = DownloadSettings.DEFAULT_FOLDER
            settings.location = DownloadSettings.Location.DOWNLOADS
            toast(activity, "Reset to default: ${settings.destination(null, false)}")
            showDownloadSettings(dialog, activity)
        }
    }

    options += "Folder per username: ${onOff(settings.perUsername)}" to {
        settings.perUsername = !settings.perUsername
        toast(activity, "Folder per username: ${onOff(settings.perUsername)}")
        showDownloadSettings(dialog, activity)
    }

    options += "Timestamp names: ${onOff(settings.timestamp)}" to {
        settings.timestamp = !settings.timestamp
        toast(activity, "Timestamp names: ${onOff(settings.timestamp)}")
        showDownloadSettings(dialog, activity)
    }

    dialog.show(activity, "Download settings", options)
}



@Volatile
private var lastResumedActivity: WeakReference<Activity>? = null

private fun contextToActivity(c: Context?): Activity? {
    var context = c
    while (context != null) {
        if (context is Activity) return context
        context = (context as? ContextWrapper)?.baseContext
    }
    return null
}

/**
 * The activity [owner] is, wraps, or keeps in a field.
 */
private fun activityIn(owner: Any): Activity? {
    (owner as? Activity)?.let { return it }
    (owner as? Context)?.let { contextToActivity(it)?.let { act -> return act } }
    (owner as? View)?.context?.let { contextToActivity(it)?.let { act -> return act } }
    try {
        val getActivity = owner.javaClass.methods.firstOrNull {
            (it.name == "getActivity" || it.name == "requireActivity") && it.parameterCount == 0
        }
        (getActivity?.invoke(owner) as? Activity)?.let { return it }
    } catch (_: Throwable) {}

    var clazz: Class<*>? = owner.javaClass
    while (clazz != null && clazz != Any::class.java) {
        for (field in clazz.declaredFields) {
            if (Modifier.isStatic(field.modifiers)) continue
            try {
                field.isAccessible = true
                val value = field.get(owner) ?: continue
                (value as? Activity)?.let { return it }
                (value as? Context)?.let { contextToActivity(it)?.let { act -> return act } }
                (value as? View)?.context?.let { contextToActivity(it)?.let { act -> return act } }
                val valueGetActivity = value.javaClass.methods.firstOrNull {
                    (it.name == "getActivity" || it.name == "requireActivity") && it.parameterCount == 0
                }
                (valueGetActivity?.invoke(value) as? Activity)?.let { return it }
                val valueGetContext = value.javaClass.methods.firstOrNull {
                    (it.name == "getContext" || it.name == "requireContext") && it.parameterCount == 0
                }
                (valueGetContext?.invoke(value) as? Context)?.let { contextToActivity(it)?.let { act -> return act } }
            } catch (_: Throwable) {}
        }
        clazz = clazz.superclass
    }
    return lastResumedActivity?.get()
}

val DownloadMedia = patch(
    name = "Download media",
    description = "Adds download options to the menus of posts, reels and stories, and to profile pictures. " +
        "Files are saved to Download/NexFusion/Instagram by default; the download settings allow customizing " +
        "the folder path, save location, per-username subfolders and timestamp names.",
) {
    val app = appContext as? Application ?: appContext.applicationContext as? Application
    app?.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
        override fun onActivityResumed(activity: Activity) { lastResumedActivity = WeakReference(activity) }
        override fun onActivityPaused(activity: Activity) {}
        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) { lastResumedActivity = WeakReference(activity) }
        override fun onActivityStarted(activity: Activity) { lastResumedActivity = WeakReference(activity) }
        override fun onActivityStopped(activity: Activity) {}
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
        override fun onActivityDestroyed(activity: Activity) {
            if (lastResumedActivity?.get() === activity) lastResumedActivity = null
        }
    })

    val mediaClass = classLoader.loadClass(MEDIA_CLASS)
    fun <T> optional(name: String, find: () -> T) =
        runCatching(find).onFailure { PatcherLog.error(it) { "$name not found" } }.getOrNull()
    val extractor = MediaExtractor(
        classLoader,
        optional("Media image getter") { ::mediaImageInfoGetter.method },
        optional("Media video getter") { ::mediaVideoVersionsGetter.method },
        optional("Media carousel getter") { ::mediaCarouselGetter.method },
        optional("Video width getter") { ::videoVersionWidthGetter.method },
        optional("Video height getter") { ::videoVersionHeightGetter.method },
        optional("Media dash manifest getter") { ::mediaDashManifestGetter.method },
        ::log,
    )
    val dialog = InstagramDialog(
        optional("Dialog builder") { ::igDialogBuildMethod.method },
        optional("Dialog title") { ::igDialogTitleField.field },
        optional("Dialog cancelable") { ::igDialogCancelableMethod.method },
        optional("Dialog canceled on touch outside") { ::igDialogCanceledOnTouchOutsideMethod.method },
        optional("Dialog negative button") { ::igDialogNegativeButtonMethod.method },
        optional("Dialog menu corners") { ::igDialogMenuBottomCornersField.field },
        ::log,
    )
    val positionField = optional("Carousel position") { ::mediaPositionField.field.apply { isAccessible = true } }

    /**
     * The carousel position of the post state among [owners] or captured by them, read when the option is tapped so
     * it is the item on screen.
     */
    fun position(owners: List<Any?>): Int {
        val field = positionField ?: return 0
        val candidates = owners.filterNotNull()
        val state = candidates.firstOrNull { field.declaringClass.isInstance(it) }
            ?: candidates.firstNotNullOfOrNull { captured(it, field.declaringClass) }
            ?: return 0.also { log("no carousel position") }
        return field.getInt(state)
    }
    val mediaUserGetter =
        optional("Media user getter") { ::mediaUserGetter.method.apply { isAccessible = true } }
    val userUsernameGetter =
        optional("Username getter") { ::userUsernameGetter.method.apply { isAccessible = true } }

    /**
     * The username of the poster of [media], for the per-username folders. Missing getters, or a build that renamed
     * them, leave the save to the plain folder, so the download keeps working.
     */
    fun usernameOf(media: Any): String? = runCatching {
        val user = mediaUserGetter?.invoke(media)
        if (user == null) null else userUsernameGetter?.invoke(user) as? String
    }.onFailure { log("username not read: $it") }.getOrNull()
    val icon = appContext.resources.getIdentifier("instagram_download_outline_24", "drawable", appContext.packageName)
    val audioIcon = appContext.resources.getIdentifier("instagram_audio_extract_outline_24", "drawable", appContext.packageName).takeIf { it != 0 }
        ?: appContext.resources.getIdentifier("instagram_music_outline_24", "drawable", appContext.packageName).takeIf { it != 0 }
        ?: icon
    val moreIcon = appContext.resources.getIdentifier("instagram_more_horizontal_outline_24", "drawable", appContext.packageName).takeIf { it != 0 }
        ?: icon

    val highlightCover = optional("Highlight cover") { HighlightCover(classLoader, ::log) }

    /**
     * The option to download the cover of the highlight reachable from [owners], when a highlight is shown.
     */
    fun highlightCoverOptions(activity: Activity, owners: List<Any?>): List<Pair<String, () -> Unit>> {
        val url = runCatching { highlightCover?.url(owners) }
            .onFailure { log("highlight cover failed: $it") }.getOrNull() ?: return emptyList()
        return listOf(HIGHLIGHT_COVER_LABEL to { enqueue(activity, listOf(MediaExtractor.Item(url, false, url))) })
    }

    fun downloadMedia(
        activity: Activity,
        media: Any?,
        owners: List<Any?> = emptyList(),
        storyOwners: List<Any?> = emptyList(),
    ) {
        try {
            if (media == null) {
                log("no media to download")
                return toast(activity, "No media found")
            }
            val items = extractor.items(media)
            val position = if (items.size > 1) position(owners) else 0
            val username = usernameOf(media)
            log("${items.size} items, at $position")
            download(dialog, activity, items, position, highlightCoverOptions(activity, storyOwners), username)
        } catch (e: Exception) {
            PatcherLog.error(e) { "Could not download the media" }
            log("failed: ${e.stackTraceToString().take(1500)}")
            toast(activity, "Download failed")
        }
    }

    // Hook onActivityResult for file manager folder selection
    hookActivityResultIn(Activity::class.java)
    runCatching {
        var c: Class<*>? = classLoader.loadClass("com.instagram.mainactivity.InstagramMainActivity")
        while (c != null && c != Activity::class.java) {
            hookActivityResultIn(c)
            c = c.superclass
        }
    }

    val menus = mutableMapOf<String, Result<Unit>>()

    // The full overflow menu of posts, built from MediaOption$Option values.
    menus["post menu"] = runCatching {
        val optionClass = classLoader.loadClass(MEDIA_OPTION_CLASS)
        val creatorClass = ::overflowMenuCreatorClass.clazz

        // addOption(style, option, creator, CharSequence label, ArrayList options, boolean), adding Instagram's own options.
        val addOption = creatorClass.declaredMethods.single { method ->
            val parameters = method.parameterTypes
            Modifier.isStatic(method.modifiers) && parameters.size == 6 && parameters[0].isEnum &&
                parameters[1] == optionClass && parameters[2] == creatorClass &&
                parameters[3] == CharSequence::class.java && parameters[4] == ArrayList::class.java &&
                parameters[5] == Boolean::class.java
        }.apply { isAccessible = true }
        val style = addOption.parameterTypes[0].enumConstants!!.single { (it as Enum<*>).name == "NORMAL" }
        val option = newMenuOption(optionClass, DOWNLOAD_OPTION, 500, icon)

        ::overflowMenuOptionsMethod.hookMethod {
            after { param ->
                @Suppress("UNCHECKED_CAST")
                val options = param.result as? ArrayList<Any?> ?: return@after
                try {
                    val creator = param.args.filterNotNull().firstNotNullOfOrNull { captured(it, creatorClass) }
                        ?: return@after
                    val added = ArrayList<Any?>()
                    addOption.invoke(null, style, option, creator, DOWNLOAD_LABEL, added, false)
                    // Like Piko's, the option is the first of the menu.
                    if (added.isNotEmpty() && options.none { it in added }) {
                        options.addAll(0, added)
                        log("added to the post menu")
                    }
                } catch (e: Exception) {
                    PatcherLog.error(e) { "Could not add the download option to the post menu" }
                }
            }
        }

        ::overflowMenuOptionClickMethod.hookMethod {
            before { param ->
                if (param.args[0] !== option) return@before
                param.result = null
                log("tapped in the post menu")
                val helper = param.thisObject
                val activity = activityIn(helper) ?: return@before log("no activity in the post menu")
                // The menu reads its post with a static getter of the helper.
                val media = runCatching {
                    helper.javaClass.declaredMethods.first { method ->
                        Modifier.isStatic(method.modifiers) && method.returnType == mediaClass &&
                            method.parameterTypes.contentEquals(arrayOf(helper.javaClass))
                    }.apply { isAccessible = true }.invoke(null, helper)
                }.onFailure { PatcherLog.error(it) { "Could not read the post of the menu" } }.getOrNull()
                downloadMedia(activity, media, listOf(helper))
            }
        }
    }

    // The bottom sheet menus of reels, of posts shown like reels and of the "≡" button of posts in the home feed.
    // Adds direct one-tap download rows matching Facebook's clean layout and Instagram's native theme.
    menus["sheet menus"] = runCatching {
        val builderClass = ::sheetMenuBuilderClass.clazz
        // A simple label-and-icon row. 451 adds a trailing boolean; 452 has a four-parameter overload.
        val addItem = ::sheetMenuAddItemMethod.method.apply { isAccessible = true }

        fun addSheetRow(builder: Any, act: Activity, label: String, rowIcon: Int, action: () -> Unit) {
            val onClick = View.OnClickListener {
                runCatching(action).onFailure { log("$label action error: $it") }
            }
            val itemArguments = mutableListOf<Any?>(act, onClick, label, rowIcon)
            if (addItem.parameterCount == 5) itemArguments += false
            addItem.invoke(builder, *itemArguments.toTypedArray())
        }

        val handledBuilders: MutableSet<Any> = Collections.synchronizedSet(Collections.newSetFromMap(WeakHashMap()))
        val addDownload: HookDsl<IScopedHookCallback>.() -> Unit = {
            after { param ->
                val module = param.args.firstOrNull { it in SHEET_MENU_MODULES }
                    ?: outerParam.args.firstOrNull { it in SHEET_MENU_MODULES }
                    ?: return@after
                // Once per sheet, even when menu methods call each other.
                if (!handledBuilders.add(param.thisObject)) return@after
                try {
                    val owners = listOfNotNull(outerParam.thisObject) + outerParam.args.filterNotNull() + listOfNotNull(param.thisObject) + param.args.filterNotNull()
                    val media = owners.firstOrNull { mediaClass.isInstance(it) }
                        ?: owners.firstNotNullOfOrNull { captured(it, mediaClass) }
                        ?: return@after log("no post for $module")
                    val activity = owners.firstNotNullOfOrNull(::activityIn) ?: lastResumedActivity?.get()
                        ?: return@after log("no activity for $module")

                    val items = runCatching { extractor.items(media) }.getOrNull().orEmpty()
                    val username = usernameOf(media)
                    val currentPos = if (items.size > 1) position(owners) else 0
                    val current = items.getOrNull(currentPos) ?: items.firstOrNull()

                    if (current != null) {
                        if (current.isVideo) {
                            addSheetRow(param.thisObject, activity, "Download reel", icon) {
                                log("tapped Download reel in $module")
                                enqueue(activity, listOf(current), username)
                            }
                            addSheetRow(param.thisObject, activity, "Download audio", audioIcon) {
                                log("tapped Download audio in $module")
                                downloadAudio(activity, current, username)
                            }
                        } else {
                            addSheetRow(param.thisObject, activity, "Download photo", icon) {
                                log("tapped Download photo in $module")
                                enqueue(activity, listOf(current), username)
                            }
                            if (items.size > 1) {
                                addSheetRow(param.thisObject, activity, "Download all (${items.size})", icon) {
                                    log("tapped Download all in $module")
                                    enqueue(activity, items, username)
                                }
                            }
                            if (current.audioUrl != null) {
                                addSheetRow(param.thisObject, activity, "Download audio", audioIcon) {
                                    log("tapped Download audio in $module")
                                    downloadAudio(activity, current, username)
                                }
                            }
                        }
                        addSheetRow(param.thisObject, activity, DOWNLOAD_LABEL, moreIcon) {
                            log("tapped Download options in $module")
                            downloadMedia(activity, media, owners)
                        }
                    } else {
                        addSheetRow(param.thisObject, activity, DOWNLOAD_LABEL, icon) {
                            log("tapped $DOWNLOAD_LABEL in $module")
                            downloadMedia(activity, media, owners)
                        }
                    }
                    log("added download rows to $module")
                } catch (e: Exception) {
                    PatcherLog.error(e) { "Could not add the download option to $module" }
                }
            }
        }
        val builderConstructors = builderClass.declaredConstructors.map { it to addDownload }.toTypedArray()
        val menuMethods = ::sheetMenuMethods.dexMethodList.ifEmpty { throw Exception("Sheet menus not found") }
        menuMethods.forEach { it.hookMethod(scopedHook(*builderConstructors)) }
        log("hooked ${menuMethods.size} sheet menus")
    }

    // The "⋯" menu of other people's stories. Like Piko, a label is added to the labels of the
    // "ReelOptionsOverflowHelper", which become the rows of the menu, and handled by its label click handlers.
    val reelItemClass = classLoader.loadClass("com.instagram.model.reels.ReelItem")
    fun storyMedia(reelItem: Any?): Any? = reelItem?.let { item ->
        // The main Media of a ReelItem is its last Media field, like in Piko.
        item.javaClass.declaredFields.lastOrNull { it.type == mediaClass }?.apply { isAccessible = true }?.get(item)
    }

    menus["story menu"] = runCatching {
        val labelsMethod = ::storyMenuLabelsMethod.method
        val helperClass = labelsMethod.declaringClass
        fun fieldOf(type: Class<*>) = helperClass.declaredFields.first {
            !Modifier.isStatic(it.modifiers) && type.isAssignableFrom(it.type)
        }.apply { isAccessible = true }
        val activityField = fieldOf(Activity::class.java)
        val reelItemField = fieldOf(reelItemClass)

        labelsMethod.hookMethod {
            after { param ->
                val labels = param.result as? Array<*> ?: return@after
                if (labels.any { it?.toString() == DOWNLOAD_LABEL }) return@after
                // Like Piko's, the label is the first of the menu.
                @Suppress("UNCHECKED_CAST")
                val withDownload = java.lang.reflect.Array.newInstance(CharSequence::class.java, labels.size + 1)
                    as Array<CharSequence?>
                withDownload[0] = DOWNLOAD_LABEL
                labels.forEachIndexed { index, label -> withDownload[index + 1] = label as CharSequence? }
                param.result = withDownload
                log("added to the story menu")
            }
        }

        // The icon of a label: icon(helper, CharSequence label).
        helperClass.declaredMethods.filter { method ->
            Modifier.isStatic(method.modifiers) && method.returnType == Int::class.java &&
                method.parameterTypes.contentEquals(arrayOf(helperClass, CharSequence::class.java))
        }.forEach { method ->
            method.hookMethod {
                after { param -> if (param.args[1]?.toString() == DOWNLOAD_LABEL) param.result = icon }
            }
        }

        // The label click handlers: static void methods taking the helper and the label as last parameter.
        val clickHandlers = helperClass.declaredMethods.filter { method ->
            val parameters = method.parameterTypes
            Modifier.isStatic(method.modifiers) && method.returnType == Void.TYPE && parameters.isNotEmpty() &&
                parameters.last() == CharSequence::class.java && helperClass in parameters
        }.ifEmpty { throw Exception("Story menu click handlers not found") }
        clickHandlers.forEach { method ->
            method.hookMethod {
                before { param ->
                    if (param.args.last()?.toString() != DOWNLOAD_LABEL) return@before
                    param.result = null
                    log("tapped in the story menu")
                    val helper = param.args.first { helperClass.isInstance(it) }
                    val activity = activityField.get(helper) as Activity? ?: return@before log("no story activity")
                    downloadMedia(activity, storyMedia(reelItemField.get(helper)), storyOwners = listOf(helper))
                }
            }
        }
        log("hooked the story menu with ${clickHandlers.size} click handlers")
    }

    // Instagram can leave the options of the "ReelOptionsOverflowHelper" out of the story menu sheet, so a row is
    // also added to the rows the sheet is shown with, for the story on screen.
    menus["story menu rows"] = runCatching {
        val viewerClass = classLoader.loadClass("instagram.features.stories.fragment.ReelViewerFragment")
        val currentItem = viewerClass.declaredMethods.single {
            it.parameterCount == 0 && it.returnType == reelItemClass
        }.apply { isAccessible = true }
        var resumedViewer = WeakReference<Any>(null)
        viewerClass.getDeclaredMethod("onResume").hookMethod {
            after { param -> resumedViewer = WeakReference(param.thisObject) }
        }
        viewerClass.getDeclaredMethod("onPause").hookMethod {
            before { param -> if (resumedViewer.get() === param.thisObject) resumedViewer = WeakReference(null) }
        }

        val rowConstructor = ::storyMenuRowConstructor.constructor.apply { isAccessible = true }
        val rowParameterTypes = rowConstructor.parameterTypes
        val onClickIndex = rowParameterTypes.indexOfFirst { it.name == "kotlin.jvm.functions.Function0" }
        if (onClickIndex < 0) throw Exception("Story menu row has no click handler parameter")
        val function0 = rowParameterTypes[onClickIndex]
        val titleIndex = rowParameterTypes.indexOfFirst { it == String::class.java }
        if (titleIndex < 0) throw Exception("Story menu row has no title parameter")
        val iconIndex = (onClickIndex + 1 until rowParameterTypes.size)
            .firstOrNull { rowParameterTypes[it] == Int::class.javaPrimitiveType }
            ?: throw Exception("Story menu row has no icon parameter")
        val storySheetMethod = ::storyMenuSheetMethod.method
        val rowsParamIndex = storySheetMethod.parameterTypes.indexOfFirst { List::class.java.isAssignableFrom(it) }
            .takeIf { it >= 0 } ?: 0
        ::storyMenuSheetMethod.hookMethod {
            before { param ->
                val viewer = resumedViewer.get() ?: return@before
                val rows = param.args.getOrNull(rowsParamIndex) as? List<*> ?: param.args.firstOrNull { it is List<*> } as? List<*> ?: return@before
                try {
                    val hasDownload = rows.any { row ->
                        row != null && row.javaClass.declaredFields.any { field ->
                            field.type == String::class.java &&
                                field.apply { isAccessible = true }.get(row) == DOWNLOAD_LABEL
                        }
                    }
                    if (hasDownload) return@before
                    val activity = viewer.javaClass.getMethod("getActivity").invoke(viewer) as Activity?
                        ?: return@before log("no activity for the story menu rows")
                    val reelItem = currentItem.invoke(viewer)
                    val media = storyMedia(reelItem)
                    val onClick = Proxy.newProxyInstance(classLoader, arrayOf(function0)) { proxy, method, args ->
                        when (method.name) {
                            "invoke" -> {
                                log("tapped in the story menu rows")
                                downloadMedia(activity, media, storyOwners = listOf(viewer, reelItem))
                                Unit
                            }
                            "equals" -> proxy === args?.firstOrNull()
                            "hashCode" -> System.identityHashCode(proxy)
                            else -> "Download"
                        }
                    }
                    // Fill primitives with their defaults and leave nullable values null, then place the
                    // title, subtitle, click handler and icon where this release expects them.
                    val args = arrayOfNulls<Any?>(rowParameterTypes.size)
                    for (i in rowParameterTypes.indices) {
                        args[i] = when (rowParameterTypes[i]) {
                            java.lang.Boolean.TYPE -> false
                            java.lang.Integer.TYPE -> 0
                            java.lang.Long.TYPE -> 0L
                            java.lang.Float.TYPE -> 0f
                            java.lang.Double.TYPE -> 0.0
                            java.lang.Character.TYPE -> '\u0000'
                            java.lang.Byte.TYPE -> 0.toByte()
                            java.lang.Short.TYPE -> 0.toShort()
                            else -> null
                        }
                    }
                    args[titleIndex] = DOWNLOAD_LABEL
                    if (titleIndex + 1 < rowParameterTypes.size &&
                        rowParameterTypes[titleIndex + 1] == String::class.java
                    ) {
                        args[titleIndex + 1] = ""
                    }
                    args[onClickIndex] = onClick
                    args[iconIndex] = icon
                    val row = rowConstructor.newInstance(*args)
                    param.args[0] = listOf(row) + rows
                    log("added to the story menu rows")
                } catch (e: Exception) {
                    PatcherLog.error(e) { "Could not add the download row to the story menu" }
                }
            }
        }
    }

    // The menu of a long pressed highlight on a profile. Like the sheet menus, the item is added to the dialog builder
    // right after the menu creates it, so it is the first item.
    menus["highlight menu"] = runCatching {
        val addItem = ::highlightMenuAddMethod.method.apply { isAccessible = true }
        val cover = highlightCover ?: throw Exception("Highlight cover not available")
        val addCover: HookDsl<IScopedHookCallback>.() -> Unit = {
            after { param ->
                if (HIGHLIGHT_MENU_MODULE !in param.args) return@after
                try {
                    val helper = outerParam.thisObject
                    val owners = listOf(helper) + outerParam.args
                    val onClick = View.OnClickListener { view ->
                        log("tapped in the highlight menu")
                        try {
                            val activity = helper?.let(::activityIn) ?: activityIn(view)
                                ?: return@OnClickListener log("no activity for the highlight menu")
                            val url = cover.url(owners)
                                ?: return@OnClickListener toast(activity, "No highlight cover found")
                            enqueue(activity, listOf(MediaExtractor.Item(url, false, url)))
                        } catch (e: Exception) {
                            log("highlight cover failed: ${e.stackTraceToString().take(1500)}")
                            toast(view.context, "Download failed")
                        }
                    }
                    addItem.invoke(param.thisObject, HIGHLIGHT_COVER_LABEL, onClick)
                    log("added to the highlight menu")
                } catch (e: Exception) {
                    PatcherLog.error(e) { "Could not add the download option to the highlight menu" }
                }
            }
        }
        val builderConstructors =
            ::highlightMenuBuilderClass.clazz.declaredConstructors.map { it to addCover }.toTypedArray()
        ::highlightMenuMethod.method.hookMethod(scopedHook(*builderConstructors))
    }

    // The buttons below the expanded profile picture, a "Download" button is added after Instagram's.
    menus["profile picture"] = runCatching {
        val actionConstructor = ::profilePictureActionConstructor.constructor.apply { isAccessible = true }
        val function0 = actionConstructor.parameterTypes[1]
        val imageViewClass = classLoader.loadClass("com.instagram.common.ui.widget.imageview.IgImageView")
        val imageUrl = runCatching {
            imageViewClass.getDeclaredMethod("getUrlForViewTrackers").apply { isAccessible = true }
        }.getOrElse {
            imageViewClass.getMethod("getTypedUrl")
        }
        val pictureId = appContext.resources.getIdentifier("expanded_profile_pic", "id", appContext.packageName)
        val pictureIcon = appContext.resources.getIdentifier(
            "instagram_download_pano_outline_24", "drawable", appContext.packageName,
        ).takeIf { it != 0 } ?: icon

        /**
         * The URL of the profile picture shown in the screen of [bar].
         */
        fun pictureUrl(bar: View): String? {
            val picture = bar.rootView.findViewById<View>(pictureId)?.takeIf(imageViewClass::isInstance)
                ?: return null.also { log("no profile picture view") }
            val url = imageUrl.invoke(picture) ?: return null
            return url.javaClass.getMethod("getUrl").invoke(url) as String?
        }

        ::profilePictureActionsMethod.hookMethod {
            before { param ->
                val bar = param.args[0] as? View ?: return@before
                val actions = param.args[2] as? List<*> ?: return@before
                try {
                    val onClick = Proxy.newProxyInstance(classLoader, arrayOf(function0)) { proxy, method, args ->
                        when (method.name) {
                            "invoke" -> {
                                log("tapped below the profile picture")
                                try {
                                    val activity = activityIn(bar)
                                    val url = pictureUrl(bar)
                                    when {
                                        activity == null -> log("no activity for the profile picture")
                                        url == null -> toast(activity, "No profile picture found")
                                        else -> enqueue(activity, listOf(MediaExtractor.Item(url, false, url)))
                                    }
                                } catch (e: Exception) {
                                    log("profile picture failed: ${e.stackTraceToString().take(1500)}")
                                    toast(bar.context, "Download failed")
                                }
                                Unit
                            }
                            "equals" -> proxy === args?.firstOrNull()
                            "hashCode" -> System.identityHashCode(proxy)
                            else -> "Download profile picture"
                        }
                    }
                    val download = actionConstructor.newInstance(PROFILE_PICTURE_LABEL, onClick, pictureIcon, false)
                    param.args[2] = actions + download
                    log("added below the profile picture")
                } catch (e: Exception) {
                    PatcherLog.error(e) { "Could not add the download button below the profile picture" }
                }
            }
        }
    }

    menus.forEach { (menu, result) ->
        result.onFailure { PatcherLog.error(it) { "No download option in the $menu" } }
    }
    if (menus.values.all { it.isFailure }) throw Exception("No menu found for the download option")
}

