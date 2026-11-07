package io.github.nexfusion.nexfusion.gboard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.inputmethodservice.InputMethodService
import android.text.InputType
import android.util.Log
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import java.lang.ref.WeakReference

/**
 * Ported from jasonwu1994/Gboard-patches (GPL-3.0) GboardEditorActions:
 *
 * 1. Terminal Paste Fallback: In raw terminal input fields (e.g. Termux, where
 *    (inputType and TYPE_MASK_CLASS) == TYPE_NULL), standard context-menu paste fails.
 *    We commit plain text directly via InputConnection.commitText.
 *
 * 2. Auto Select-All: When copy or cut is dispatched without any text selected,
 *    automatically selects all text first before performing the copy/cut.
 */
object GboardEditingHook {

    private const val TAG = "NexFusion"
    private var currentIme = WeakReference<InputMethodService?>(null)

    fun install(classLoader: ClassLoader) {
        val imeClass = runCatching {
            classLoader.loadClass("android.inputmethodservice.InputMethodService")
        }.getOrNull() ?: return

        runCatching {
            XposedBridge.hookAllMethods(imeClass, "onStartInputView", object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val service = param.thisObject as? InputMethodService ?: return
                    currentIme = WeakReference(service)
                }
            })

            XposedBridge.hookAllMethods(imeClass, "onFinishInputView", object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    currentIme.clear()
                }
            })
        }

        // Hook InputConnectionWrapper.performContextMenuAction
        runCatching {
            val icWrapper = classLoader.loadClass("android.view.inputmethod.InputConnectionWrapper")
            val method = icWrapper.getDeclaredMethod("performContextMenuAction", Int::class.javaPrimitiveType)
            XposedBridge.hookMethod(method, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val actionId = param.args[0] as? Int ?: return
                    val connection = param.thisObject as? InputConnection ?: return
                    val service = currentIme.get()

                    if (actionId == android.R.id.paste) {
                        val editorInfo = service?.currentInputEditorInfo
                        if (editorInfo != null && (editorInfo.inputType and InputType.TYPE_MASK_CLASS) == InputType.TYPE_NULL) {
                            if (pasteRawText(service, connection)) {
                                param.result = true
                            }
                        }
                    } else if (actionId == android.R.id.copy || actionId == android.R.id.cut) {
                        val selected = connection.getSelectedText(0)
                        if (selected.isNullOrEmpty()) {
                            connection.performContextMenuAction(android.R.id.selectAll)
                        }
                    }
                }
            })
            Log.i(TAG, "NexFusion: Hooked Gboard terminal paste & auto select-all")
        }
    }

    internal fun pasteRawText(service: InputMethodService, connection: InputConnection): Boolean {
        val clipboard = service.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return false
        val clip = clipboard.primaryClip ?: return false
        if (clip.itemCount == 0) return false
        val text = clip.getItemAt(0).text ?: return false
        if (text.isEmpty()) return false

        connection.beginBatchEdit()
        return try {
            connection.finishComposingText()
            connection.commitText(text, 1)
        } finally {
            connection.endBatchEdit()
        }
    }
}
