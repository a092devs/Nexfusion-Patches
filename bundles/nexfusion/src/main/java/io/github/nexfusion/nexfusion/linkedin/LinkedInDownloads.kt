package io.github.nexfusion.nexfusion.linkedin

import android.app.Activity
import android.app.AlertDialog
import android.app.DownloadManager
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.Toast
import io.github.nexfusion.compat.XC_MethodHook
import io.github.nexfusion.compat.XC_MethodHook.MethodHookParam
import io.github.nexfusion.compat.XposedBridge
import io.github.nexfusion.IHook
import io.github.nexfusion.PatcherLog
import io.github.nexfusion.patch
import java.lang.ref.WeakReference
import java.util.WeakHashMap

// Ported from heyymichii/michii-patches (GPL-3.0):
// - extensions/extension/src/main/java/app/linkedin/extension/DownloadMediaPatch.java
// - extensions/extension/src/main/java/app/linkedin/extension/Downloads.java
// - extensions/extension/src/main/java/app/linkedin/extension/MediaItem.java
// - extensions/extension/src/main/java/app/linkedin/extension/SduiMediaDownload.java
// - patches/src/main/kotlin/app/linkedin/patches/download/DownloadMediaPatch.kt

val DownloadMedia = patch(
    name = "Download media",
    description = "Adds a download button to the full-screen photo and video viewer.",
) {
    installSduiMediaCollectorHook()
    installSduiFragmentLifecycleHooks()
    installMediaViewerPresenterHooks()
}

// region SDUI Component Media Collector Hook

private fun IHook.installSduiMediaCollectorHook() {
    try {
        val transformer = classLoader.loadClass("com.linkedin.sdui.transformer.impl.ComponentTransformer")
        val targets = transformer.declaredMethods.filter {
            it.name.startsWith("doTransform") &&
                it.parameterTypes.any { param -> param.name.contains("Component") }
        }
        if (targets.isEmpty()) return
        val hook = object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val component = param.args.firstOrNull { it != null && it.javaClass.name.contains("Component") } ?: return
                collectMediaFromComponent(component)
            }
        }
        targets.forEach { XposedBridge.hookMethod(it, hook) }
    } catch (t: Throwable) {
        PatcherLog.info(t) { "NexFusion: LinkedIn SDUI media collector hook failed" }
    }
}

private fun collectMediaFromComponent(component: Any) {
    try {
        val toByteArrayMethod = component.javaClass.methods.firstOrNull {
            it.name == "toByteArray" && it.parameterTypes.isEmpty()
        } ?: return
        val payload = toByteArrayMethod.invoke(component) as? ByteArray ?: return

        // Quick rejection: check if payload has video or image markers
        if (!containsAscii(payload, "https://dms.licdn.com/playlist/vid/") &&
            !containsAscii(payload, "https://media.licdn.com/dms/image/")) {
            return
        }

        val screenName = (readProperty(component, "observabilityViewName") as? String).orEmpty()
        val isProfileScreen = screenName.startsWith("com.linkedin.sdui.impl.profile.destinations.")

        val scan = scanProto(payload)
        val items = extractMediaItems(scan, isProfileScreen)
        if (items.isNotEmpty()) {
            SduiMediaDownloadManager.offer(items, screenName)
        }
    } catch (t: Throwable) {
        PatcherLog.info(t) { "NexFusion: collectMediaFromComponent failed" }
    }
}

// endregion

// region SDUI Fragment Lifecycle Hooks

private fun IHook.installSduiFragmentLifecycleHooks() {
    try {
        val fragmentClass = classLoader.loadClass("com.linkedin.android.infra.sdui.view.SduiFragment")

        val onResumeMethods = fragmentClass.declaredMethods.filter {
            it.name == "onResume" && it.parameterTypes.isEmpty()
        }
        if (onResumeMethods.isNotEmpty()) {
            onResumeMethods.forEach { method ->
                XposedBridge.hookMethod(method, object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        SduiMediaDownloadManager.onFragmentResumed(param.thisObject)
                    }
                })
            }
        }

        val onPauseMethods = fragmentClass.declaredMethods.filter {
            it.name == "onPause" && it.parameterTypes.isEmpty()
        }
        if (onPauseMethods.isNotEmpty()) {
            onPauseMethods.forEach { method ->
                XposedBridge.hookMethod(method, object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        SduiMediaDownloadManager.onFragmentPaused(param.thisObject)
                    }
                })
            }
        }

        val onHiddenMethods = fragmentClass.declaredMethods.filter {
            it.name == "onHiddenChanged" && it.parameterTypes.size == 1 &&
                it.parameterTypes[0] == Boolean::class.javaPrimitiveType
        }
        if (onHiddenMethods.isNotEmpty()) {
            onHiddenMethods.forEach { method ->
                XposedBridge.hookMethod(method, object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val hidden = param.args.getOrNull(0) as? Boolean ?: false
                        SduiMediaDownloadManager.onFragmentHiddenChanged(param.thisObject, hidden)
                    }
                })
            }
        }
    } catch (t: Throwable) {
        PatcherLog.info(t) { "NexFusion: LinkedIn SduiFragment lifecycle hooks failed" }
    }
}

// endregion

// region SDUI Media Download Manager

internal object SduiMediaDownloadManager {
    private const val DOWNLOAD_TAG = "nexfusion_linkedin_download_overlay"
    private const val PENDING_WINDOW_MS = 6000L

    private val mainHandler = Handler(Looper.getMainLooper())
    private val mediaByFragment = WeakHashMap<Any, List<MediaItem>>()

    private var resumedFragmentRef: WeakReference<Any>? = null
    private var resumedIsMedia = false
    private var pendingMedia: List<MediaItem>? = null
    private var pendingAt = 0L

    fun offer(items: List<MediaItem>, source: String) {
        if (items.isEmpty()) return
        mainHandler.post {
            val resumed = resumedFragmentRef?.get()
            if (resumed != null && resumedIsMedia) {
                attach(resumed, items)
                val activity = getActivity(resumed)
                if (activity != null) {
                    showDownloadButton(activity, resumed)
                }
            } else {
                pendingMedia = merge(pendingMedia, items)
                pendingAt = SystemClock.uptimeMillis()
            }
        }
    }

    fun onFragmentResumed(fragment: Any) {
        try {
            resumedFragmentRef = WeakReference(fragment)
            val screen = getScreenId(fragment)
            val isRecentPending = pendingMedia != null && (SystemClock.uptimeMillis() - pendingAt < PENDING_WINDOW_MS)
            resumedIsMedia = isMediaScreen(screen) || isRecentPending

            val activity = getActivity(fragment) ?: return
            removeDownloadButton(activity)

            if (!resumedIsMedia) return

            val pending = pendingMedia
            if (pending != null && isRecentPending) {
                attach(fragment, pending)
            }
            pendingMedia = null

            if (mediaByFragment.containsKey(fragment)) {
                showDownloadButton(activity, fragment)
            }
        } catch (t: Throwable) {
            PatcherLog.info(t) { "NexFusion: LinkedIn onFragmentResumed failed" }
        }
    }

    fun onFragmentPaused(fragment: Any) {
        try {
            if (resumedFragmentRef?.get() == fragment) {
                resumedFragmentRef = null
                resumedIsMedia = false
            }
            val activity = getActivity(fragment)
            if (activity != null) {
                removeDownloadButton(activity)
            }
        } catch (t: Throwable) {
            PatcherLog.info(t) { "NexFusion: LinkedIn onFragmentPaused failed" }
        }
    }

    fun onFragmentHiddenChanged(fragment: Any, hidden: Boolean) {
        if (hidden) {
            onFragmentPaused(fragment)
        } else {
            onFragmentResumed(fragment)
        }
    }

    private fun isMediaScreen(screen: String): Boolean {
        if (screen.isEmpty()) return false
        return screen.startsWith("com.linkedin.sdui.flagshipnav.media.") ||
            screen.startsWith("com.linkedin.sdui.flagshipnav.profile.ProfilePicture") ||
            screen == "com.linkedin.sdui.flagshipnav.profile.ProfileCoverImageViewer" ||
            screen == "com.linkedin.sdui.flagshipnav.profile.ProfilePreviewBackgroundPhoto" ||
            screen.contains("media", ignoreCase = true) ||
            screen.contains("viewer", ignoreCase = true) ||
            screen.contains("photo", ignoreCase = true) ||
            screen.contains("video", ignoreCase = true)
    }

    private fun getScreenId(fragment: Any): String {
        return runCatching {
            val viewModel = readProperty(fragment, "viewModel") ?: return@runCatching ""
            val screenContext = readProperty(viewModel, "screenContext") ?: return@runCatching ""
            val getScreenIdMethod = screenContext.javaClass.methods.firstOrNull {
                it.name == "getScreenId" && it.parameterTypes.isEmpty()
            }
            (getScreenIdMethod?.invoke(screenContext) as? String)
                ?: (readProperty(screenContext, "screenId") as? String)
                ?: ""
        }.getOrDefault("")
    }

    private fun getActivity(fragment: Any): Activity? {
        return runCatching {
            val getActivityMethod = fragment.javaClass.methods.firstOrNull {
                it.name == "getActivity" && it.parameterTypes.isEmpty()
            }
            getActivityMethod?.invoke(fragment) as? Activity
        }.getOrNull()
    }

    private fun attach(fragment: Any, items: List<MediaItem>) {
        val existing = mediaByFragment[fragment]
        mediaByFragment[fragment] = merge(existing, items)
    }

    private fun merge(base: List<MediaItem>?, extra: List<MediaItem>): List<MediaItem> {
        val result = if (base == null) ArrayList<MediaItem>() else ArrayList(base)
        outer@ for (item in extra) {
            for (existing in result) {
                if (existing.assetId == item.assetId) continue@outer
            }
            result.add(item)
        }
        return result
    }

    private fun showDownloadButton(activity: Activity, fragment: Any) {
        val content = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
        var button = content.findViewWithTag<View>(DOWNLOAD_TAG)
        if (button == null) {
            button = createDownloadButton(activity)
            content.addView(button)
        }
        button.bringToFront()
        button.setOnClickListener {
            onDownloadClick(activity, fragment)
        }
    }

    private fun removeDownloadButton(activity: Activity) {
        val content = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
        val view = content.findViewWithTag<View>(DOWNLOAD_TAG)
        if (view != null) {
            content.removeView(view)
        }
    }

    private fun createDownloadButton(context: Context): View {
        val button = ImageButton(context).apply {
            tag = DOWNLOAD_TAG
            setImageResource(android.R.drawable.stat_sys_download)
            setColorFilter(Color.WHITE)
            contentDescription = "Download media"
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0x99000000.toInt())
            }
            val pad = dp(context, 10)
            setPadding(pad, pad, pad, pad)
        }

        val size = dp(context, 44)
        val params = FrameLayout.LayoutParams(size, size, Gravity.TOP or Gravity.END).apply {
            topMargin = dp(context, 88)
            marginEnd = dp(context, 16)
        }
        button.layoutParams = params
        return button
    }

    private fun onDownloadClick(activity: Activity, fragment: Any) {
        val items = mediaByFragment[fragment] ?: pendingMedia
        if (items.isNullOrEmpty()) {
            Toast.makeText(activity, "Media not found", Toast.LENGTH_SHORT).show()
            return
        }
        if (items.size == 1) {
            downloadItems(activity, items)
        } else {
            showChooserDialog(activity, items)
        }
    }

    private fun showChooserDialog(activity: Activity, items: List<MediaItem>) {
        val labels = items.mapIndexed { idx, it ->
            "${idx + 1}. ${it.label()}"
        }.toTypedArray()

        AlertDialog.Builder(activity)
            .setTitle("Choose media")
            .setItems(labels) { _, which ->
                downloadItems(activity, listOf(items[which]))
            }
            .setPositiveButton("All (${items.size})") { _, _ ->
                downloadItems(activity, items)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun downloadItems(context: Context, items: List<MediaItem>) {
        var started = 0
        var allVideos = true
        for (item in items) {
            if (enqueueDownload(context, item.url, item.assetId, item.isVideo)) {
                started++
            }
            allVideos = allVideos && item.isVideo
        }
        if (started > 0) {
            val destFolder = if (allVideos) "Movies/LinkedIn" else "Pictures/LinkedIn"
            val msg = if (started == 1) {
                "Downloading to $destFolder"
            } else {
                "Downloading $started files to $destFolder"
            }
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }

    private fun enqueueDownload(context: Context, url: String, assetId: String, isVideo: Boolean): Boolean {
        return try {
            val extension = if (isVideo) ".mp4" else ".jpg"
            val mimeType = if (isVideo) "video/mp4" else "image/jpeg"
            val fileName = "linkedin_${assetId}${extension}"
            val subDir = if (isVideo) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES

            val request = DownloadManager.Request(Uri.parse(url))
                .setTitle(fileName)
                .setMimeType(mimeType)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(subDir, "LinkedIn/$fileName")

            val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
            manager?.enqueue(request) != null
        } catch (t: Throwable) {
            PatcherLog.info(t) { "NexFusion: LinkedIn enqueueDownload failed" }
            Toast.makeText(context, "Download failed: ${t.message}", Toast.LENGTH_SHORT).show()
            false
        }
    }

    private fun dp(context: Context, value: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), context.resources.displayMetrics).toInt()
}

// endregion

// region Legacy RecyclerView Media Viewer Presenter Hooks

private const val OVERLAY_TAG = "nexfusion_linkedin_download_overlay"

private val MEDIA_VIEWER_PRESENTERS = listOf(
    "com.linkedin.android.media.pages.mediaviewer.MediaViewerImagePresenter",
    "com.linkedin.android.media.pages.mediaviewer.MultiPhotoImagePresenter",
    "com.linkedin.android.media.pages.mediaviewer.MediaViewerVideoPresenter",
)

private fun IHook.installMediaViewerPresenterHooks() {
    for (presenterClass in MEDIA_VIEWER_PRESENTERS) {
        try {
            val presenter = classLoader.loadClass(presenterClass)
            val onBindMethods = (presenter.declaredMethods + presenter.methods).distinct().filter {
                it.name == "onBind" && it.parameterTypes.size == 2
            }
            if (onBindMethods.isEmpty()) continue
            val hook = object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val viewData = param.args.getOrNull(0) ?: return
                    val binding = param.args.getOrNull(1) ?: return
                    attachLegacyDownloadOverlay(viewData, binding)
                }
            }
            onBindMethods.forEach { XposedBridge.hookMethod(it, hook) }
        } catch (t: Throwable) {
            PatcherLog.info(t) { "NexFusion: LinkedIn presenter hook failed for $presenterClass" }
        }
    }
}

private fun attachLegacyDownloadOverlay(viewData: Any, binding: Any) {
    try {
        val root = binding.javaClass.getMethod("getRoot").invoke(binding) as? ViewGroup ?: return
        var overlay = root.findViewWithTag<View>(OVERLAY_TAG)
        if (overlay == null) {
            overlay = FrameLayout(root.context).apply {
                tag = OVERLAY_TAG
                val button = ImageButton(root.context).apply {
                    setImageResource(android.R.drawable.stat_sys_download)
                    setColorFilter(Color.WHITE)
                    contentDescription = "Download media"
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(0x99000000.toInt())
                    }
                    val pad = dp(root.context, 10)
                    setPadding(pad, pad, pad, pad)
                }
                val size = dp(root.context, 44)
                val params = FrameLayout.LayoutParams(size, size, Gravity.TOP or Gravity.END).apply {
                    topMargin = dp(root.context, 88)
                    marginEnd = dp(root.context, 16)
                }
                addView(button, params)
                button.setOnClickListener { v ->
                    downloadLegacyMedia(v.context, viewData)
                }
            }
            val lp = if (root is FrameLayout) {
                FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            } else {
                ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            }
            root.addView(overlay, lp)
        }
        overlay.bringToFront()
    } catch (t: Throwable) {
        PatcherLog.info(t) { "NexFusion: LinkedIn attachLegacyDownloadOverlay failed" }
    }
}

private fun downloadLegacyMedia(context: Context, viewData: Any) {
    try {
        val videoUrl = findLegacyVideoUrl(viewData)
        val isVideo = videoUrl != null
        val targetUrl = videoUrl ?: findLegacyImageUrl(viewData)

        if (targetUrl == null) {
            Toast.makeText(context, "Media URL not found", Toast.LENGTH_SHORT).show()
            return
        }

        val assetId = System.currentTimeMillis().toString()
        val extension = if (isVideo) ".mp4" else ".jpg"
        val mimeType = if (isVideo) "video/mp4" else "image/jpeg"
        val fileName = "linkedin_${assetId}${extension}"
        val subDir = if (isVideo) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES

        val request = DownloadManager.Request(Uri.parse(targetUrl))
            .setTitle(fileName)
            .setMimeType(mimeType)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(subDir, "LinkedIn/$fileName")

        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
        manager?.enqueue(request)
        val destFolder = if (isVideo) "Movies/LinkedIn" else "Pictures/LinkedIn"
        Toast.makeText(context, "Downloading to $destFolder", Toast.LENGTH_SHORT).show()
    } catch (t: Throwable) {
        PatcherLog.info(t) { "NexFusion: LinkedIn downloadLegacyMedia failed" }
        Toast.makeText(context, "Download failed: ${t.message}", Toast.LENGTH_SHORT).show()
    }
}

private fun findLegacyVideoUrl(viewData: Any): String? {
    val meta = readProperty(viewData, "videoPlayMetadata") ?: return null
    val streams = readProperty(meta, "progressiveStreams") as? List<*> ?: return null

    var bestUrl: String? = null
    var bestScore = -1L

    for (stream in streams) {
        if (stream == null) continue
        val width = (readProperty(stream, "width") as? Number)?.toLong() ?: 0L
        val height = (readProperty(stream, "height") as? Number)?.toLong() ?: 0L
        val bitRate = (readProperty(stream, "bitRate") as? Number)?.toLong() ?: 0L
        val score = (width * height * 100_000L) + (bitRate / 1000L)

        val locations = readProperty(stream, "streamingLocations") as? List<*> ?: continue
        for (loc in locations) {
            val url = readProperty(loc, "url") as? String
            if (!url.isNullOrEmpty() && score > bestScore) {
                bestScore = score
                bestUrl = url
            }
        }
    }
    return bestUrl
}

private fun findLegacyImageUrl(viewData: Any): String? {
    val image = readProperty(viewData, "image") ?: readProperty(viewData, "imageViewModel") ?: return null
    val attributes = readProperty(image, "attributes") as? List<*> ?: return null

    for (attr in attributes) {
        if (attr == null) continue
        val detail = readProperty(attr, "detailData") ?: continue

        val vector = readProperty(detail, "vectorImageValue")
        if (vector != null) {
            val artifacts = readProperty(vector, "artifacts") as? List<*> ?: continue
            val rootUrl = readProperty(vector, "rootUrl") as? String ?: ""
            var bestSegment: String? = null
            var bestWidth = -1
            for (artifact in artifacts) {
                if (artifact == null) continue
                val segment = readProperty(artifact, "fileIdentifyingUrlPathSegment") as? String ?: continue
                val width = (readProperty(artifact, "width") as? Number)?.toInt() ?: 0
                if (width > bestWidth) {
                    bestWidth = width
                    bestSegment = segment
                }
            }
            if (bestSegment != null) {
                return if (bestSegment.startsWith("http") || rootUrl.isEmpty()) {
                    bestSegment
                } else {
                    rootUrl + bestSegment
                }
            }
        }

        val plain = readProperty(detail, "imageUrlValue")
        val plainUrl = readProperty(plain, "url") as? String
        if (!plainUrl.isNullOrEmpty()) return plainUrl
    }
    return null
}

private fun readProperty(target: Any?, name: String): Any? {
    if (target == null) return null
    return runCatching {
        var cls: Class<*>? = target.javaClass
        while (cls != null && cls != Any::class.java) {
            for (field in cls.declaredFields) {
                if (field.name == name) {
                    field.isAccessible = true
                    return field.get(target)
                }
            }
            cls = cls.superclass
        }
        val getterName = "get" + name.replaceFirstChar { it.uppercase() }
        val method = target.javaClass.methods.firstOrNull { it.name == getterName && it.parameterTypes.isEmpty() }
        method?.invoke(target)
    }.getOrNull()
}

private fun dp(context: Context, value: Int): Int =
    TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), context.resources.displayMetrics).toInt()

// endregion

