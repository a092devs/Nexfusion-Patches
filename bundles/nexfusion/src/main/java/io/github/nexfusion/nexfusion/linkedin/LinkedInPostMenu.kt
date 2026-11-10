package io.github.nexfusion.nexfusion.linkedin

import android.app.Activity
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import io.github.nexfusion.compat.XC_MethodHook
import io.github.nexfusion.compat.XC_MethodHook.MethodHookParam
import io.github.nexfusion.compat.XposedBridge
import io.github.nexfusion.IHook
import io.github.nexfusion.PatcherLog
import io.github.nexfusion.patch
import java.util.Collections
import java.util.regex.Pattern

val PostMenuItems = patch(
    name = "Post menu items",
    description = "Adds Copy text, Download media, and Save document as PDF to the ⋯ menu of posts.",
) {
    installPostMenuActivityInterceptor()
}

internal object PostMenuCache {
    data class CachedPost(
        var text: String? = null,
        val media: MutableList<MediaItem> = ArrayList(),
        val documentIds: MutableSet<String> = LinkedHashSet()
    )

    private const val MAX_POSTS = 300
    val posts = Collections.synchronizedMap(object : LinkedHashMap<String, CachedPost>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CachedPost>?): Boolean {
            return size > MAX_POSTS
        }
    })

    private val POST_URN = Pattern.compile("urn:li:(?:activity|ugcPost|share):\\d+")
    private val DOCUMENT_URN = Pattern.compile("urn:li:digitalmediaAsset:([A-Za-z0-9_-]+)")

    fun remember(payload: ByteArray) {
        runCatching {
            val scan = scanProto(payload)
            val urns = mutableSetOf<String>()
            val post = CachedPost()
            for (s in scan.strings) {
                val m = POST_URN.matcher(s)
                while (m.find()) urns.add(m.group())
                val d = DOCUMENT_URN.matcher(s)
                while (d.find()) {
                    val docId = d.group(1)
                    if (docId != null) post.documentIds.add(docId)
                }
            }
            if (urns.isNotEmpty()) {
                val commentary = scan.strings.firstOrNull {
                    it.length > 40 && !it.startsWith("http") && !it.startsWith("urn:") && !it.contains("feed-")
                }
                post.text = commentary
                post.media.addAll(extractMediaItems(scan, isProfileScreen = false))
                for (u in urns) {
                    posts[u] = post
                }
            }
        }
    }
}

internal object PostMenuInjector {
    private const val SCHEME = "nexfusion"
    private const val HOST = "post"
    private const val PAGE = "feed-control-menu-page"
    private const val ROW_MARKER = "feed-control-menu-"
    private const val TEMPLATE = "feed-control-menu-save"
    private const val TRIGGERS_FIELD = 25
    private const val VIEW_TRACKING_FIELD = 26
    private const val ACTION_TYPE = 26628L
    private const val URL_NAVIGATION_TYPE_EXTERNAL = 1L
    private val POST_URN = Pattern.compile("urn:li:(?:activity|ugcPost|share):\\d+")

    fun inject(component: Any, bytes: ByteArray): Any {
        try {
            if (!containsAscii(bytes, PAGE) || !containsAscii(bytes, TEMPLATE) || containsAscii(bytes, "$SCHEME://")) {
                return component
            }
            var urn: String? = null
            for (s in scanStrings(bytes)) {
                val m = POST_URN.matcher(s)
                if (m.find()) {
                    urn = m.group()
                    break
                }
            }
            if (urn == null) return component
            val post = PostMenuCache.posts[urn]

            val items = ArrayList<Array<String>>()
            items.add(arrayOf("copy", "Copy text", "post-menu-copy-text"))
            if (post != null && post.media.isNotEmpty()) {
                items.add(arrayOf("download", "Download media", "post-menu-download"))
            }
            if (post != null && post.documentIds.isNotEmpty()) {
                items.add(arrayOf("pdf", "Save document as PDF", "post-menu-pdf"))
            }
            if (items.isEmpty()) return component

            val edited = addRows(bytes, urn, items) ?: return component
            val parseFrom = component.javaClass.getMethod("parseFrom", ByteArray::class.java)
            val copy = parseFrom.invoke(null, edited)
            return copy ?: component
        } catch (_: Throwable) {
            return component
        }
    }

    private fun addRows(message: ByteArray, urn: String, items: List<Array<String>>): ByteArray? {
        val fields = ProtoEditor.parse(message) ?: return null
        var rows = 0
        for (f in fields) {
            if (isRow(f)) rows++
        }
        for (i in fields.indices) {
            val f = fields[i]
            if (f.wireType != 2 || f.number == VIEW_TRACKING_FIELD || !containsAscii(f.data, TEMPLATE)) {
                continue
            }
            if (rows > 1) {
                for (k in items.indices.reversed()) {
                    val item = items[k]
                    val newRow = createRow(f, urn, item[0], item[1], item[2]) ?: return null
                    fields.add(i + 1, newRow)
                }
            } else {
                val inner = addRows(f.data, urn, items) ?: return null
                f.data = inner
            }
            return ProtoEditor.write(fields)
        }
        return null
    }

    private fun isRow(field: ProtoField): Boolean {
        return field.wireType == 2 && field.number != VIEW_TRACKING_FIELD &&
            containsAscii(field.data, ROW_MARKER) && !containsAscii(field.data, PAGE)
    }

    private fun createRow(template: ProtoField, urn: String, action: String, label: String, viewName: String): ProtoField? {
        val row = template.copy()
        val fields = ProtoEditor.parse(row.data) ?: return null
        var hasDirectTrigger = false
        for (f in fields) {
            if (f.number == TRIGGERS_FIELD && f.wireType == 2) hasDirectTrigger = true
        }

        if (!hasDirectTrigger) {
            var edited = false
            for (f in fields) {
                if (f.wireType == 2 && f.number != VIEW_TRACKING_FIELD && containsAscii(f.data, TEMPLATE)) {
                    val inner = createRow(f, urn, action, label, viewName) ?: return null
                    f.data = inner.data
                    edited = true
                }
            }
            if (!edited) return null
            row.data = relabel(ProtoEditor.write(fields), label, viewName)
            return row
        }

        val kept = ArrayList<ProtoField>()
        var replaced = false
        for (f in fields) {
            if (f.number == TRIGGERS_FIELD) {
                if (!replaced) {
                    val url = "$SCHEME://$HOST/$action?u=${Uri.encode(urn)}"
                    kept.add(urlAction(url))
                    replaced = true
                }
            } else {
                kept.add(f)
            }
        }
        row.data = relabel(ProtoEditor.write(kept), label, viewName)
        return row
    }

    private fun relabel(data: ByteArray, label: String, viewName: String): ByteArray {
        var result = ProtoEditor.replaceString(data, TEMPLATE, viewName)
        val texts = ArrayList<String>()
        for (s in scanStrings(result)) {
            if (s.length > 50 || s.isBlank() || s == viewName || s.contains(":") || s.contains("/") || texts.contains(s)) {
                continue
            }
            texts.add(s)
        }
        if (texts.isEmpty()) return result
        var shortest = texts[0]
        for (s in texts) {
            if (s.length < shortest.length) shortest = s
        }
        for (s in texts) {
            result = ProtoEditor.replaceString(result, s, if (s == shortest) label else "")
        }
        return result
    }

    private fun urlAction(url: String): ProtoField {
        val innerPayload = ProtoEditor.write(listOf(
            ProtoEditor.stringField(1, url),
            ProtoEditor.varintField(2, URL_NAVIGATION_TYPE_EXTERNAL)
        ))
        val navAction = ProtoEditor.write(listOf(
            ProtoField(2, 2, innerPayload)
        ))
        val actionMessage = ProtoEditor.write(listOf(
            ProtoEditor.varintField(1, ACTION_TYPE),
            ProtoField(2, 2, navAction)
        ))
        val wrapper5 = ProtoEditor.write(listOf(
            ProtoField(5, 2, actionMessage)
        ))
        val wrapper1 = ProtoEditor.write(listOf(
            ProtoField(1, 2, wrapper5)
        ))
        return ProtoField(TRIGGERS_FIELD, 2, ProtoEditor.write(listOf(
            ProtoField(1, 2, wrapper1),
            ProtoEditor.stringField(2, "")
        )))
    }
}

private fun IHook.installPostMenuActivityInterceptor() {
    try {
        val activityClass = classLoader.loadClass("android.app.Activity")
        val startActivityMethods = activityClass.declaredMethods.filter {
            it.name == "startActivity" && it.parameterTypes.isNotEmpty() && it.parameterTypes[0] == Intent::class.java
        }
        for (method in startActivityMethods) {
            XposedBridge.hookMethod(method, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val intent = param.args.firstOrNull() as? Intent ?: return
                    val uri = intent.data ?: return
                    val scheme = uri.scheme
                    if (scheme == "nexfusion" || scheme == "badfellapatches") {
                        if (uri.host == "post") {
                            param.result = null
                            val activity = param.thisObject as? Activity ?: return
                            handlePostMenuAction(activity, uri)
                        }
                    }
                }
            })
        }
    } catch (t: Throwable) {
        PatcherLog.info(t) { "NexFusion: LinkedIn post menu activity interceptor hook failed" }
    }
}

private fun handlePostMenuAction(activity: Activity, uri: Uri) {
    val action = uri.lastPathSegment ?: ""
    val urn = uri.getQueryParameter("u") ?: ""
    val post = PostMenuCache.posts[urn]

    when (action) {
        "copy" -> {
            val text = post?.text
            if (text.isNullOrBlank()) {
                Toast.makeText(activity, "This post has no text", Toast.LENGTH_SHORT).show()
            } else {
                CopyTextGestureHandler.copyToClipboard(activity, text)
            }
        }
        "download" -> {
            val media = post?.media.orEmpty()
            if (media.isEmpty()) {
                Toast.makeText(activity, "No media found in this post", Toast.LENGTH_SHORT).show()
            } else {
                val downloadManager = activity.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
                for (item in media) {
                    val subDir = if (item.isVideo) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES
                    val ext = if (item.isVideo) ".mp4" else ".jpg"
                    val mime = if (item.isVideo) "video/mp4" else "image/jpeg"
                    val fileName = "linkedin_${item.assetId}$ext"
                    val request = DownloadManager.Request(Uri.parse(item.url))
                        .setTitle(fileName)
                        .setMimeType(mime)
                        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                        .setDestinationInExternalPublicDir(subDir, "LinkedIn/$fileName")
                    downloadManager?.enqueue(request)
                }
                Toast.makeText(activity, "Downloading ${media.size} media item(s)", Toast.LENGTH_SHORT).show()
            }
        }
        "pdf" -> {
            var doc: DocumentModel? = null
            if (post != null) {
                for (id in post.documentIds) {
                    val found = DocumentPdfManager.documents[id]
                    if (found != null) {
                        doc = found
                        break
                    }
                }
            }
            if (doc != null) {
                DocumentPdfManager.saveDocument(activity, doc)
            } else {
                Toast.makeText(activity, "Open the document once, then try again", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

