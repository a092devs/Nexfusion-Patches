package io.github.nexfusion.nexfusion.gboard

import kotlin.test.Test
import kotlin.test.assertEquals

class FlagOverrideTest {

    @Test
    fun `promote only raises a stock false`() {
        val promote = FlagOverride.promote()
        assertEquals(true, promote.applyTo(false))
        assertEquals(true, promote.applyTo(true))
        assertEquals(1L, promote.applyTo(1L))
        assertEquals(null, promote.applyTo(null))
    }

    @Test
    fun `force boolean replaces boxed booleans only`() {
        val force = FlagOverride.forceBoolean()
        assertEquals(true, force.applyTo(false))
        assertEquals(true, force.applyTo(true))
        assertEquals(6L, force.applyTo(6L))
        assertEquals("x", force.applyTo("x"))
        assertEquals(null, force.applyTo(null))
    }

    @Test
    fun `force long replaces boxed longs only`() {
        val force = FlagOverride.forceLong(20_000L)
        assertEquals(20_000L, force.applyTo(3L))
        assertEquals(3, force.applyTo(3))
        assertEquals(true, force.applyTo(true))
    }

    @Test
    fun `force string replaces strings only`() {
        val force = FlagOverride.forceString("*")
        assertEquals("*", force.applyTo("en"))
        assertEquals(3L, force.applyTo(3L))
        assertEquals(false, force.applyTo(false))
    }

    @Test
    fun `force false replaces null or booleans with false`() {
        val force = FlagOverride.forceFalse()
        assertEquals(false, force.applyTo(true))
        assertEquals(false, force.applyTo(false))
        assertEquals(false, force.applyTo(null))
        assertEquals("unchanged", force.applyTo("unchanged"))
        assertEquals(1L, force.applyTo(1L))
    }
}
