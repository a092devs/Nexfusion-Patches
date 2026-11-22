package io.github.a092devs.nexfusion.nexfusion.gboard

import android.text.TextPaint
import android.widget.TextView
import java.lang.reflect.Field

/**
 * Runtime helper for candidate strip emoji rendering and positioning.
 *
 * Ensures emoji glyphs in suggestion candidates render at full opacity (alpha 255)
 * and positions primary suggested emoji in the right slot of the suggestion strip.
 *
 * Ported from Akshayykadam/PixelBoard (GPL-3.0).
 */
object GboardCandidateEmojiRuntime {

    private var candidateCapacityField: Field? = null
    private var candidateTextField: Field? = null
    private var candidateSlotField: Field? = null

    /**
     * Checks if the character sequence contains one or more emoji characters.
     */
    fun containsEmoji(cs: CharSequence?): Boolean {
        if (cs.isNullOrEmpty()) return false
        val len = cs.length
        for (i in 0 until len) {
            val c = cs[i]
            // High/low surrogate pairs (SMP Plane 1: modern emojis U+1F000 - U+1FAFF)
            if (Character.isSurrogate(c)) return true
            // BMP emoji and symbol blocks (Dingbats, Misc Symbols, Arrows, Enclosed)
            if (c.code in 0x203C..0x3299) return true
            // Variation Selector-16 (emoji presentation selector)
            if (c.code == 0xFE0F) return true
        }
        return false
    }

    /**
     * Adjusts the candidate TextView TextPaint to full opacity if the candidate text contains emojis.
     */
    fun adjustCandidatePaint(textView: TextView?, paint: TextPaint? = textView?.paint) {
        if (textView == null || paint == null) return
        val text = textView.text ?: return
        if (containsEmoji(text)) {
            paint.color = paint.color or 0xFF000000.toInt()
            paint.alpha = 255
        }
    }

    /**
     * Reorders candidates passed into LatinFixedCountCandidatesHolderView so that
     * any emoji candidate is placed in the right-most slot (Slot 2 for a standard 3-slot strip),
     * while text candidates occupy Slot 0 (left / raw verbatim) and Slot 1 (middle / autocompletion).
     */
    fun reorderCandidatesForStrip(holderView: Any?, candidates: List<*>?): List<*>? {
        if (candidates == null || candidates.size < 2) return candidates

        return try {
            val maxSlots = resolveCandidateStripCapacity(holderView)
            val rightSlot = maxOf(1, maxSlots - 1)

            val textCandidates = mutableListOf<Any>()
            val emojiCandidates = mutableListOf<Any>()

            for (candidate in candidates) {
                if (candidate == null) continue
                val text = getCandidateText(candidate)
                if (containsEmoji(text)) {
                    emojiCandidates.add(candidate)
                } else {
                    textCandidates.add(candidate)
                }
            }

            if (emojiCandidates.isEmpty() || textCandidates.isEmpty()) return candidates

            val reordered = ArrayList<Any>(candidates.size)

            val textSlotsBeforeRight = minOf(textCandidates.size, rightSlot)
            for (i in 0 until textSlotsBeforeRight) {
                reordered.add(textCandidates[i])
            }

            reordered.add(emojiCandidates[0])

            for (i in textSlotsBeforeRight until textCandidates.size) {
                reordered.add(textCandidates[i])
            }

            for (i in 1 until emojiCandidates.size) {
                reordered.add(emojiCandidates[i])
            }

            for (i in 0 until reordered.size) {
                setCandidateSlot(reordered[i], i)
            }

            try {
                @Suppress("UNCHECKED_CAST")
                val mutable = candidates as? MutableList<Any>
                mutable?.clear()
                mutable?.addAll(reordered)
            } catch (_: Throwable) {
            }

            reordered
        } catch (_: Throwable) {
            candidates
        }
    }

    private fun resolveCandidateStripCapacity(holderView: Any?): Int {
        if (holderView == null) return 3
        return try {
            if (candidateCapacityField == null) {
                candidateCapacityField = runCatching {
                    holderView.javaClass.getDeclaredField("d").apply { isAccessible = true }
                }.getOrNull() ?: holderView.javaClass.declaredFields.firstOrNull {
                    it.type == Int::class.javaPrimitiveType && it.name == "d"
                }?.apply { isAccessible = true }
            }
            val cap = candidateCapacityField?.getInt(holderView) ?: 3
            if (cap > 0) cap else 3
        } catch (_: Throwable) {
            3
        }
    }

    private fun getCandidateText(candidate: Any?): CharSequence? {
        if (candidate == null) return null
        return try {
            if (candidateTextField == null) {
                candidateTextField = runCatching {
                    candidate.javaClass.getDeclaredField("a").apply { isAccessible = true }
                }.getOrNull() ?: candidate.javaClass.declaredFields.firstOrNull {
                    CharSequence::class.java.isAssignableFrom(it.type)
                }?.apply { isAccessible = true }
            }
            candidateTextField?.get(candidate) as? CharSequence
        } catch (_: Throwable) {
            null
        }
    }

    private fun setCandidateSlot(candidate: Any?, slot: Int) {
        if (candidate == null) return
        try {
            if (candidateSlotField == null) {
                candidateSlotField = runCatching {
                    candidate.javaClass.getDeclaredField("l").apply { isAccessible = true }
                }.getOrNull() ?: candidate.javaClass.declaredFields.firstOrNull {
                    it.type == Int::class.javaPrimitiveType && it.name == "l"
                }?.apply { isAccessible = true }
            }
            candidateSlotField?.setInt(candidate, slot)
        } catch (_: Throwable) {
        }
    }
}

