package io.github.nexfusion.nexfusion.linkedin

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.Toast
import io.github.nexfusion.compat.XC_MethodHook
import io.github.nexfusion.compat.XC_MethodHook.MethodHookParam
import io.github.nexfusion.compat.XposedBridge
import io.github.nexfusion.IHook
import io.github.nexfusion.PatcherLog
import io.github.nexfusion.patch
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.util.Collections
import java.util.WeakHashMap
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

// Tap to hide in viewers: Tap full screen video or document to toggle caption and controls visibility.
val ViewerTapToHide = patch(
    name = "Tap to hide in viewers",
    description = "Tap a video or document in full screen to hide captions and buttons.",
) {
    installViewerStateHooks()
}

val DocumentPdfDownload = patch(
    name = "Save document as PDF",
    description = "Saves multi-page documents and carousels as a unified PDF in Downloads.",
) {
    installDocumentCaptureHook()
}

// region Viewer Tap To Hide

private val stateManagers = Collections.newSetFromMap(WeakHashMap<Any, Boolean>())

private fun IHook.installViewerStateHooks() {
    try {
        val stateManagerClass = classLoader.loadClass("com.linkedin.android.infra.sdui.state.StateManager")
        stateManagerClass.declaredConstructors.forEach { constructor ->
            XposedBridge.hookMethod(constructor, object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    param.thisObject?.let { synchronized(stateManagers) { stateManagers.add(it) } }
                }
            })
        }
    } catch (t: Throwable) {
        PatcherLog.info(t) { "NexFusion: LinkedIn StateManager hook failed" }
    }

    try {
        val composeViewClass = classLoader.loadClass("androidx.compose.ui.platform.AndroidComposeView")
        val dispatchTouch = composeViewClass.declaredMethods.firstOrNull {
            it.name == "dispatchTouchEvent" && it.parameterTypes.size == 1 && it.parameterTypes[0] == MotionEvent::class.java
        } ?: return

        val viewerTapHandler = ViewerTapHandler()
        XposedBridge.hookMethod(dispatchTouch, object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val view = param.thisObject as? View ?: return
                val event = param.args[0] as? MotionEvent ?: return
                viewerTapHandler.onTouch(view, event)
            }
        })
    } catch (t: Throwable) {
        PatcherLog.info(t) { "NexFusion: LinkedIn ViewerTapHandler hook failed" }
    }
}

private class ViewerTapHandler {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var downX = 0f
    private var downY = 0f
    private var downTime = 0L
    private var multiTouch = false
    private var pendingToggle: Runnable? = null

    fun onTouch(view: View, event: MotionEvent) {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.rawX
                downY = event.rawY
                downTime = event.eventTime
                multiTouch = false
                pendingToggle?.let {
                    mainHandler.removeCallbacks(it)
                    pendingToggle = null
                }
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                multiTouch = true
            }
            MotionEvent.ACTION_UP -> {
                if (multiTouch) return
                if (event.eventTime - downTime > ViewConfiguration.getLongPressTimeout()) return
                val slop = ViewConfiguration.get(view.context).scaledTouchSlop
                if (Math.hypot((event.rawX - downX).toDouble(), (event.rawY - downY).toDouble()) > slop) return

                val toggleRunnable = Runnable {
                    pendingToggle = null
                    toggleDocumentControls()
                }
                pendingToggle = toggleRunnable
                mainHandler.postDelayed(toggleRunnable, ViewConfiguration.getDoubleTapTimeout().toLong())
            }
        }
    }

    private fun toggleDocumentControls() {
        val managers = synchronized(stateManagers) { stateManagers.toList() }
        for (sm in managers) {
            runCatching {
                val field = sm.javaClass.declaredFields.firstOrNull { it.type == Map::class.java } ?: return@runCatching
                field.isAccessible = true
                val map = field.get(sm) as? Map<*, *> ?: return@runCatching
                for ((k, v) in map) {
                    if (k?.toString()?.contains("showControlsBinding") == true) {
                        val stateProp = v?.javaClass?.declaredFields?.firstOrNull { it.name == "value" || it.name == "state" }
                        if (stateProp != null) {
                            stateProp.isAccessible = true
                            val cur = stateProp.get(v) as? Boolean ?: true
                            stateProp.set(v, !cur)
                        }
                    }
                }
            }
        }
    }
}

// endregion

// region Document PDF Downloader

internal data class DocumentSlice(
    val suffix: String,
    val startPage: Int,
    val endPage: Int
)

internal data class DocumentModel(
    val id: String,
    val rootUrl: String,
    val downloadSuffix: String?,
    val slices: List<DocumentSlice>,
    val pages: Int
)

internal object DocumentPdfManager {
    val documents = ConcurrentHashMap<String, DocumentModel>()
    private val worker = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    fun saveDocument(context: Context, doc: DocumentModel) {
        Toast.makeText(context, "Saving document as PDF...", Toast.LENGTH_SHORT).show()
        worker.execute {
            try {
                if (!doc.downloadSuffix.isNullOrEmpty()) {
                    val fullUrl = URI(doc.rootUrl).resolve(doc.downloadSuffix).toString()
                    val bytes = fetchBytes(fullUrl)
                    writeToDownloads(context, "linkedin_doc_${doc.id}.pdf") { out ->
                        out.write(bytes)
                    }
                } else if (doc.slices.isNotEmpty()) {
                    writeToDownloads(context, "linkedin_doc_${doc.id}.pdf") { out ->
                        renderSlicesToPdf(doc, out)
                    }
                } else {
                    postToast(context, "Document has no downloadable content")
                    return@execute
                }
                postToast(context, "Document saved to Downloads/LinkedIn")
            } catch (t: Throwable) {
                PatcherLog.info(t) { "NexFusion: LinkedIn PDF save failed" }
                postToast(context, "Failed to save PDF: ${t.message}")
            }
        }
    }

    private fun renderSlicesToPdf(doc: DocumentModel, out: OutputStream) {
        val document = PdfDocument()
        var pageNumber = 0
        try {
            for (slice in doc.slices) {
                val sliceUrl = URI(doc.rootUrl).resolve(slice.suffix).toString()
                val sliceBytes = fetchBytes(sliceUrl)
                val tempFile = File.createTempFile("slice_", ".pdf")
                try {
                    FileOutputStream(tempFile).use { it.write(sliceBytes) }
                    ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                        PdfRenderer(pfd).use { renderer ->
                            for (p in 0 until renderer.pageCount) {
                                renderer.openPage(p).use { page ->
                                    val w = page.width
                                    val h = page.height
                                    val renderWidth = 1700
                                    val renderHeight = Math.max(1, (renderWidth * h / w.toFloat()).toInt())
                                    val bitmap = Bitmap.createBitmap(renderWidth, renderHeight, Bitmap.Config.ARGB_8888)
                                    bitmap.eraseColor(Color.WHITE)
                                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)

                                    val targetPage = document.startPage(
                                        PdfDocument.PageInfo.Builder(w, h, pageNumber++).create()
                                    )
                                    val canvas = targetPage.canvas
                                    canvas.drawBitmap(bitmap, null, Rect(0, 0, w, h), null)
                                    document.finishPage(targetPage)
                                    bitmap.recycle()
                                }
                            }
                        }
                    }
                } finally {
                    tempFile.delete()
                }
            }
            document.writeTo(out)
        } finally {
            document.close()
        }
    }

    private fun fetchBytes(urlString: String): ByteArray {
        val conn = URL(urlString).openConnection() as HttpURLConnection
        conn.connectTimeout = 15000
        conn.readTimeout = 30000
        try {
            conn.inputStream.use { input ->
                val buffer = ByteArrayOutputStream()
                val chunk = ByteArray(16384)
                var read: Int
                while (input.read(chunk).also { read = it } != -1) {
                    buffer.write(chunk, 0, read)
                }
                return buffer.toByteArray()
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun writeToDownloads(context: Context, fileName: String, writer: (OutputStream) -> Unit) {
        val folder = "LinkedIn"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$folder")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("MediaStore insert failed")
            try {
                resolver.openOutputStream(uri)?.use { out -> writer(out) }
            } catch (e: Exception) {
                resolver.delete(uri, null, null)
                throw e
            }
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        } else {
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), folder)
            dir.mkdirs()
            FileOutputStream(File(dir, fileName)).use { out -> writer(out) }
        }
    }

    private fun postToast(context: Context, message: String) {
        mainHandler.post {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }
}

private fun IHook.installDocumentCaptureHook() {
    try {
        val docClass = classLoader.loadClass("com.linkedin.sdui.viewdata.document.DocumentSingleViewData")
        for (constructor in docClass.declaredConstructors) {
            val params = constructor.parameterTypes
            if (params.size >= 4 && params[2].name.contains("DocumentAsset") && params[3].name.contains("Source")) {
                XposedBridge.hookMethod(constructor, object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val source = param.args.getOrNull(3) ?: return
                        captureDocumentSource(source)
                    }
                })
            }
        }
    } catch (t: Throwable) {
        PatcherLog.info(t) { "NexFusion: LinkedIn DocumentSingleViewData hook failed" }
    }
}

private fun captureDocumentSource(source: Any) {
    runCatching {
        val root = invokeGetter(source, "getRootUrl") as? String ?: return
        val download = invokeGetter(source, "getDownloadUrlSuffix") as? String
        val pageCount = (invokeGetter(source, "getAvailablePageCount") as? Number)?.toInt() ?: 1
        val urn = invokeGetter(source, "getAssetUrn")?.toString() ?: ""
        val docId = urn.substringAfterLast(":", System.currentTimeMillis().toString())

        val slicesList = invokeGetter(source, "getDocumentSlices") as? List<*> ?: emptyList<Any>()
        val parsedSlices = ArrayList<DocumentSlice>()
        for (item in slicesList) {
            if (item == null) continue
            val suffix = invokeGetter(item, "getDownloadUrlSuffix") as? String ?: ""
            val start = (invokeGetter(item, "getStartPage") as? Number)?.toInt() ?: 0
            val end = (invokeGetter(item, "getEndPage") as? Number)?.toInt() ?: 0
            if (suffix.isNotEmpty()) {
                parsedSlices.add(DocumentSlice(suffix, start, end))
            }
        }

        val doc = DocumentModel(docId, root, download, parsedSlices, pageCount)
        DocumentPdfManager.documents[docId] = doc
        if (urn.isNotEmpty()) {
            DocumentPdfManager.documents[urn] = doc
        }
    }
}

private fun invokeGetter(target: Any, baseName: String): Any? {
    var cls: Class<*>? = target.javaClass
    while (cls != null && cls != Any::class.java) {
        for (m in cls.declaredMethods) {
            if (m.name.startsWith(baseName) && m.parameterTypes.isEmpty()) {
                m.isAccessible = true
                return runCatching { m.invoke(target) }.getOrNull()
            }
        }
        cls = cls.superclass
    }
    return null
}

// endregion

