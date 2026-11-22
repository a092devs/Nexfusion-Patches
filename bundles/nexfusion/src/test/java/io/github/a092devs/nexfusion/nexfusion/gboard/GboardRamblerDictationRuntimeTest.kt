package io.github.a092devs.nexfusion.nexfusion.gboard

import kotlin.test.Test
import kotlin.test.assertEquals

class GboardRamblerDictationRuntimeTest {

    class FakeAction(
        @JvmField var c: Int = 0,
        @JvmField var d: String = "",
    )

    class FakeSpan(
        @JvmField var c: String = "",
        @JvmField var j: FakeAction? = null,
    )

    class FakeProto(
        @JvmField var b: List<FakeSpan> = emptyList(),
    )

    @Test
    fun `filters Ai prompt answer restoring verbatim transcript`() {
        val action = FakeAction(c = 4, d = "What is the capital of France?")
        val span = FakeSpan(c = "Paris is the capital of France.", j = action)
        val proto = FakeProto(b = listOf(span))

        GboardRamblerDictationRuntime.filterVoiceText(proto)

        assertEquals("What is the capital of France?", span.c)
        assertEquals(0, action.c)
    }

    @Test
    fun `preserves normal dictation verbatim`() {
        val action = FakeAction(c = 0, d = "Hello world")
        val span = FakeSpan(c = "Hello world", j = action)
        val proto = FakeProto(b = listOf(span))

        GboardRamblerDictationRuntime.filterVoiceText(proto)

        assertEquals("Hello world", span.c)
        assertEquals(0, action.c)
    }

    @Test
    fun `handles null or malformed gracefully`() {
        GboardRamblerDictationRuntime.filterVoiceText(null)
        GboardRamblerDictationRuntime.filterVoiceText("not_a_proto")
    }
}
