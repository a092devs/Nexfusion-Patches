package io.github.nexfusion.nexfusion.linkedin

import java.nio.charset.StandardCharsets
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ProtoEditorTest {

    @Test
    fun testParseAndWriteProto() {
        val field1 = ProtoEditor.stringField(1, "hello")
        val field2 = ProtoEditor.varintField(2, 42L)
        val msg = ProtoEditor.write(listOf(field1, field2))

        val parsed = ProtoEditor.parse(msg)
        assertNotNull(parsed)
        assertEquals(2, parsed.size)
        assertEquals(1, parsed[0].number)
        assertEquals("hello", String(parsed[0].data, StandardCharsets.UTF_8))
        assertEquals(2, parsed[1].number)

        val rewritten = ProtoEditor.write(parsed)
        assertTrue(msg.contentEquals(rewritten))
    }

    @Test
    fun testReplaceString() {
        val field1 = ProtoEditor.stringField(1, "target-string")
        val msg = ProtoEditor.write(listOf(field1))

        val replaced = ProtoEditor.replaceString(msg, "target-string", "new-string")
        val parsed = ProtoEditor.parse(replaced)
        assertNotNull(parsed)
        assertEquals("new-string", String(parsed[0].data, StandardCharsets.UTF_8))
    }
}
