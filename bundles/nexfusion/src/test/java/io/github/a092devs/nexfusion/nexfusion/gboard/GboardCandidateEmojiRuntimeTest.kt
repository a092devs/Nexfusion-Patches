package io.github.a092devs.nexfusion.nexfusion.gboard

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GboardCandidateEmojiRuntimeTest {

    @Test
    fun `test containsEmoji empty and null`() {
        assertFalse(GboardCandidateEmojiRuntime.containsEmoji(null))
        assertFalse(GboardCandidateEmojiRuntime.containsEmoji(""))
        assertFalse(GboardCandidateEmojiRuntime.containsEmoji("   "))
    }

    @Test
    fun `test containsEmoji plain text`() {
        assertFalse(GboardCandidateEmojiRuntime.containsEmoji("Hello world"))
        assertFalse(GboardCandidateEmojiRuntime.containsEmoji("1234567890"))
        assertFalse(GboardCandidateEmojiRuntime.containsEmoji("What is this?"))
        assertFalse(GboardCandidateEmojiRuntime.containsEmoji("test@example.com"))
    }

    @Test
    fun `test containsEmoji surrogate pairs`() {
        assertTrue(GboardCandidateEmojiRuntime.containsEmoji("😊"))
        assertTrue(GboardCandidateEmojiRuntime.containsEmoji("🚀"))
        assertTrue(GboardCandidateEmojiRuntime.containsEmoji("🔥"))
        assertTrue(GboardCandidateEmojiRuntime.containsEmoji("Good morning 😊"))
        assertTrue(GboardCandidateEmojiRuntime.containsEmoji("👍🏻"))
    }

    @Test
    fun `test containsEmoji bmp symbols and presentation selectors`() {
        assertTrue(GboardCandidateEmojiRuntime.containsEmoji("❤️"))
        assertTrue(GboardCandidateEmojiRuntime.containsEmoji("☕️"))
        assertTrue(GboardCandidateEmojiRuntime.containsEmoji("✨"))
        assertTrue(GboardCandidateEmojiRuntime.containsEmoji("⭐"))
        assertTrue(GboardCandidateEmojiRuntime.containsEmoji("⚡"))
    }
}

