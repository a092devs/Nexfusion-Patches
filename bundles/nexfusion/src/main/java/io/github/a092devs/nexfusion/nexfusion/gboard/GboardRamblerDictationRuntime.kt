package io.github.a092devs.nexfusion.nexfusion.gboard

import android.util.Log
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.Locale

/**
 * Runtime interceptor for Rambler Agentic Dictation voice text results.
 * Ported from Akshay Kadam's PixelBoard (GPL-3.0).
 *
 * Handles:
 * 1. Restoring verbatim transcripts for AI prompt/question answers (INTENT_COMPOSE = 4).
 * 2. Preventing dropping of repeated phrases.
 * 3. Preventing diff-replacement from overwriting existing text when dictating similar phrases.
 * 4. Preserving genuine voice editing commands ("make it all caps", "translate to...", "clear that", "undo").
 */
object GboardRamblerDictationRuntime {

    private const val TAG = "NexFusion"

    /**
     * Backward-compatible entrypoint without extension instance.
     */
    fun filterVoiceText(voiceTextProto: Any?) {
        filterVoiceText(null, voiceTextProto)
    }

    /**
     * Inspects incoming voice text spans, ensuring verbatim spoken transcripts are preserved,
     * repeated phrases are appended rather than dropped, and preceding text is not mutated
     * by diff-match-patch unless an explicit voice editing command was spoken.
     *
     * @param extension the AgenticDictationExtension instance (may be null)
     * @param voiceTextProto the VoiceText proto instance
     */
    fun filterVoiceText(extension: Any?, voiceTextProto: Any?) {
        if (voiceTextProto == null) return
        try {
            var textBefore = ""
            var selectedText = ""
            var textAfter = ""

            if (extension != null) {
                try {
                    val iField = findField(extension.javaClass, "i")
                    val oxs = iField?.get(extension)
                    if (oxs != null) {
                        val nMethod = findMethod(oxs.javaClass, "n")
                        val beforeCs = nMethod?.invoke(oxs)
                        if (beforeCs != null) textBefore = beforeCs.toString()

                        val lMethod = findMethod(oxs.javaClass, "l")
                        val selCs = lMethod?.invoke(oxs)
                        if (selCs != null) selectedText = selCs.toString()

                        val mMethod = findMethod(oxs.javaClass, "m")
                        val afterCs = mMethod?.invoke(oxs)
                        if (afterCs != null) textAfter = afterCs.toString()
                    }

                    if (textBefore.isEmpty()) {
                        val vMethod = findMethod(extension.javaClass, "v")
                        val operator = vMethod?.invoke(extension)
                        if (operator != null) {
                            val kMethod = findMethod(operator.javaClass, "K", Int::class.javaPrimitiveType)
                            val beforeCs = kMethod?.invoke(operator, 4000)
                            if (beforeCs != null) textBefore = beforeCs.toString()

                            val lOpMethod = findMethod(operator.javaClass, "L", Int::class.javaPrimitiveType)
                            if (lOpMethod != null && textAfter.isEmpty()) {
                                val afterCs = lOpMethod.invoke(operator, 4000)
                                if (afterCs != null) textAfter = afterCs.toString()
                            }
                        }
                    }
                } catch (t: Throwable) {
                    Log.w(TAG, "Failed to retrieve surrounding text from extension", t)
                }
            }

            val spansField = findField(voiceTextProto.javaClass, "b") ?: return
            spansField.isAccessible = true
            val spansObj = spansField.get(voiceTextProto)
            if (spansObj !is List<*>) return

            for (span in spansObj) {
                if (span == null) continue

                val actionField = findField(span.javaClass, "j") ?: continue
                actionField.isAccessible = true
                val action = actionField.get(span) ?: continue

                val transcriptField = findField(action.javaClass, "d")
                var transcript = ""
                if (transcriptField != null) {
                    transcriptField.isAccessible = true
                    val transcriptObj = transcriptField.get(action)
                    if (transcriptObj is String) {
                        transcript = transcriptObj
                    }
                }

                val intentField = findField(action.javaClass, "c") ?: continue
                intentField.isAccessible = true
                var intent = intentField.getInt(action)

                val textSpanField = findField(span.javaClass, "c") ?: continue
                textSpanField.isAccessible = true
                var spanText = ""
                val spanTextObj = textSpanField.get(span)
                if (spanTextObj is String) {
                    spanText = spanTextObj
                }

                // Restore verbatim transcript for INTENT_COMPOSE (4) where backend generated AI response
                if (intent == 4) {
                    if (transcript.isNotEmpty()) {
                        spanText = transcript
                        textSpanField.set(span, transcript)
                        intentField.setInt(action, 0)
                        clearDiffField(action)
                        intent = 0
                    }
                }

                // Check whether an errant diff was generated
                val isErrantDiff = isErrantDictationDiff(
                    action, transcript, textBefore, textAfter, selectedText, intent
                )

                if (isErrantDiff) {
                    clearDiffField(action)
                    if (intent != 0 && intent != 5) {
                        intentField.setInt(action, 0)
                    }
                    if (transcript.isNotEmpty()) {
                        textSpanField.set(span, transcript)
                    }
                } else {
                    // Legitimate diff or normal dictation without an errant diff
                    val diffField = findField(action.javaClass, "e")
                    val diff = diffField?.get(action)
                    if (diff == null && (intent == 0 || intent == 5) && transcript.isNotEmpty() &&
                        (spanText.isEmpty() || spanText == textBefore)
                    ) {
                        textSpanField.set(span, transcript)
                    }
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to filter voice text transcript", t)
        }
    }

    private fun isErrantDictationDiff(
        action: Any?,
        transcript: String,
        textBefore: String,
        textAfter: String,
        selectedText: String,
        intent: Int
    ): Boolean {
        if (action == null) return false
        val diffField = findField(action.javaClass, "e") ?: return false
        val diff = runCatching { diffField.get(action) }.getOrNull() ?: return false

        // 1. If text is actively selected, user specifically highlighted text
        if (selectedText.trim().isNotEmpty()) return false

        // 2. If text is after the cursor, user placed cursor mid-sentence
        if (textAfter.trim().isNotEmpty()) return false

        var diffPrefix = ""
        var diffToReplace = ""
        var diffSuffix = ""
        try {
            val cField = findField(diff.javaClass, "c")
            if (cField?.get(diff) is String) diffPrefix = cField.get(diff) as String
            val dField = findField(diff.javaClass, "d")
            if (dField?.get(diff) is String) diffToReplace = dField.get(diff) as String
            val eField = findField(diff.javaClass, "e")
            if (eField?.get(diff) is String) diffSuffix = eField.get(diff) as String
        } catch (_: Throwable) {}

        // 3. Suffix indicates in-place word edit
        if (diffSuffix.trim().isNotEmpty()) return false

        // 4. Intent 1 (INTENT_EDIT) and Intent 2 (INTENT_EDIT_HISTORY) are genuine actions
        if (intent == 1 || intent == 2) return false

        // 5. Explicit edit or rewrite command
        val lower = transcript.trim().lowercase(Locale.ROOT)
        if (isEditCommand(lower) || isRewriteCommand(lower)) return false

        // 6. Check if diff is trying to replace the tail of textBefore when dictating at end
        if (diffPrefix.isNotEmpty() && diffPrefix.length >= 4) {
            val normTranscript = normalizeForComparison(transcript)
            val normPrefix = normalizeForComparison(diffPrefix)
            if (normPrefix.isNotEmpty() && normTranscript.startsWith(normPrefix)) {
                if (textBefore.isNotEmpty()) {
                    val trimmedBefore = textBefore.trim()
                    if (diffToReplace.isNotEmpty() && trimmedBefore.endsWith(diffToReplace.trim())) {
                        return true
                    }
                    if ((diffPrefix + diffToReplace).trim() == trimmedBefore) {
                        return true
                    }
                }
                if (diffToReplace.isNotEmpty()) {
                    return true
                }
            }
        }

        return false
    }

    private fun isEditCommand(lower: String): Boolean {
        if (lower.startsWith("delete") || lower.startsWith("clear") || lower.startsWith("erase") ||
            lower.startsWith("remove") || lower.startsWith("scratch that") ||
            lower.startsWith("undo") || lower.startsWith("redo") || lower.startsWith("revert") ||
            lower.startsWith("add ") || lower.startsWith("insert ") || lower.startsWith("put ") ||
            lower.startsWith("добавь") || lower.startsWith("вставь") || lower.startsWith("поставь") ||
            lower.startsWith("удали") || lower.startsWith("стереть") || lower.startsWith("очисти") ||
            lower.startsWith("отмени")
        ) {
            return true
        }
        return lower == "delete" || lower == "clear" || lower == "undo" || lower == "redo"
    }

    private fun isRewriteCommand(lower: String): Boolean {
        if (lower.startsWith("add ") || lower.startsWith("insert ") || lower.startsWith("put ") ||
            lower.startsWith("добавь") || lower.startsWith("вставь") || lower.startsWith("поставь")
        ) {
            return true
        }
        if (lower.startsWith("translate") || lower.startsWith("переведи") ||
            lower.startsWith("traduire") || lower.startsWith("traducir") || lower.startsWith("übersetz")
        ) {
            return true
        }
        if (lower.startsWith("make it") || lower.startsWith("make this") || lower.startsWith("сделай")) {
            return true
        }
        if (lower.startsWith("capitalize") || lower.startsWith("uppercase") || lower.startsWith("lowercase") ||
            lower.startsWith("all caps")
        ) {
            return true
        }
        if (lower.startsWith("fix grammar") || lower.startsWith("fix spelling") || lower.startsWith("proofread") ||
            lower.startsWith("check grammar") || lower.startsWith("исправь ошибки") ||
            lower.startsWith("исправь грамматику") || lower.startsWith("проверь грамматику")
        ) {
            return true
        }
        if (lower.startsWith("rewrite") || lower.startsWith("rephrase") || lower.startsWith("paraphrase") ||
            lower.startsWith("перефразируй") || lower.startsWith("перепиши")
        ) {
            return true
        }
        if (lower.startsWith("replace ") || lower.startsWith("change ") ||
            lower.startsWith("замени ") || lower.startsWith("поменяй ")
        ) {
            return true
        }
        return false
    }

    private fun clearDiffField(action: Any) {
        try {
            val diffField = findField(action.javaClass, "e")
            if (diffField != null) {
                diffField.isAccessible = true
                diffField.set(action, null)
            }
            val bField = findField(action.javaClass, "b")
            if (bField != null) {
                bField.isAccessible = true
                val b = bField.getInt(action)
                bField.setInt(action, b and 0x10.inv())
            }
        } catch (_: Throwable) {}
    }

    private fun normalizeForComparison(s: String?): String {
        if (s == null) return ""
        val sb = java.lang.StringBuilder()
        for (c in s) {
            val lower = c.lowercaseChar()
            if (lower.isLetterOrDigit()) {
                sb.append(lower)
            }
        }
        return sb.toString()
    }

    private fun findField(clazz: Class<*>, fieldName: String): Field? {
        var current: Class<*>? = clazz
        while (current != null && current != Any::class.java) {
            try {
                val f = current.getDeclaredField(fieldName)
                f.isAccessible = true
                return f
            } catch (_: NoSuchFieldException) {
                current = current.superclass
            }
        }
        return null
    }

    private fun findMethod(clazz: Class<*>, methodName: String, vararg paramTypes: Class<*>?): Method? {
        var current: Class<*>? = clazz
        while (current != null && current != Any::class.java) {
            try {
                @Suppress("UNCHECKED_CAST")
                val m = current.getDeclaredMethod(methodName, *paramTypes as Array<Class<*>>)
                m.isAccessible = true
                return m
            } catch (_: NoSuchMethodException) {
                current = current.superclass
            }
        }
        return null
    }
}
