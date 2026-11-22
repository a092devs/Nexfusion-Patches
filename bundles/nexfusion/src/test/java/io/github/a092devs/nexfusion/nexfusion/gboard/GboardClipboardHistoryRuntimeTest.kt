package io.github.a092devs.nexfusion.nexfusion.gboard

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GboardClipboardHistoryRuntimeTest {

    class FakePreference(
        @JvmField var K: Array<CharSequence>? = null,
        @JvmField var L: Array<CharSequence>? = null,
        @JvmField var b: Int = 10, // out-of-range value
        @JvmField var d: Int = 0,
    ) {
        var notified = false
        var persistedIndex = -1

        fun w(fallback: String): String = "168" // 7 days

        fun al(max: Int) {
            d = max
        }

        fun ap(index: Int, persist: Boolean) {
            persistedIndex = index
            b = index
        }

        fun d() {
            notified = true
        }
    }

    @Test
    fun `verifies discrete retention constants and mapping`() {
        assertEquals(5, GboardClipboardHistoryRuntime.RETENTION_LABELS.size)
        assertEquals(5, GboardClipboardHistoryRuntime.RETENTION_VALUES.size)
        assertEquals("1", GboardClipboardHistoryRuntime.RETENTION_VALUES[0])
        assertEquals("24", GboardClipboardHistoryRuntime.RETENTION_VALUES[1])
        assertEquals("168", GboardClipboardHistoryRuntime.RETENTION_VALUES[2])
        assertEquals("720", GboardClipboardHistoryRuntime.RETENTION_VALUES[3])
        assertEquals("876000", GboardClipboardHistoryRuntime.RETENTION_VALUES[4])
    }

    @Test
    fun `configures discrete steps and clamps out-of-range slider value`() {
        val pref = FakePreference()
        GboardClipboardHistoryRuntime.configureClipboardHistorySlider(pref, null)

        assertEquals(5, pref.K?.size)
        assertEquals(5, pref.L?.size)
        assertEquals(4, pref.d) // max index = 4
        assertEquals(2, pref.b) // "168" is index 2
        assertEquals(2, pref.persistedIndex)
        assertTrue(pref.notified)
    }
}
