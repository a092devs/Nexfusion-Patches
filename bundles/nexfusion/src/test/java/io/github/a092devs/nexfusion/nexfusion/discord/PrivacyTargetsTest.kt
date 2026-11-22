package io.github.a092devs.nexfusion.nexfusion.discord

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PrivacyTargetsTest {

    @Test
    fun `tables hold the ported upstream targets`() {
        assertEquals(42, PrivacyTargets.crash.size)
        assertEquals(59, PrivacyTargets.telemetry.size)
        assertEquals(2, PrivacyTargets.advertising.size)
        assertEquals(27, PrivacyTargets.attribution.size)
    }

    @Test
    fun `builds JVM descriptors`() {
        val target = PrivacyTarget(
            "Lcom/appsflyer/internal/AFa1uSDK;", "logLocation",
            listOf("Landroid/content/Context;", "D", "D"), "V", PrivacyResult.VOID,
        )
        assertEquals("Lcom/appsflyer/internal/AFa1uSDK;->logLocation(Landroid/content/Context;DD)V", target.descriptor)
    }

    @Test
    fun `descriptors are unique within each table`() {
        for (table in tables) {
            val descriptors = table.map { it.descriptor }
            assertEquals(descriptors.size, descriptors.toSet().size, "duplicate target: $descriptors")
        }
    }

    @Test
    fun `obfuscated upstream targets are not ported`() {
        assertFalse(all.any { it.owner == "Lpe/a;" })
        assertFalse(all.any { it.owner == "Lio/sentry/android/core/c1;" })
    }

    @Test
    fun `every table entry is a well-formed descriptor`() {
        val primitive = setOf("Z", "B", "C", "S", "I", "J", "F", "D", "V")
        fun isType(type: String) = type in primitive ||
            (type.startsWith("L") && type.endsWith(";")) ||
            (type.startsWith("[") && type.endsWith(";"))
        for (target in all) {
            assertTrue(isType(target.returns), "bad return type: ${target.descriptor}")
            for (parameter in target.parameters) {
                assertTrue(isType(parameter), "bad parameter: ${target.descriptor}")
            }
            assertFalse(target.name.contains(' '), "bad name: ${target.descriptor}")
        }
    }

    @Test
    fun `every upstream result kind is exercised`() {
        assertEquals(PrivacyResult.entries.toSet(), all.map { it.result }.toSet())
    }

    private val tables
        get() = listOf(
            PrivacyTargets.crash, PrivacyTargets.telemetry,
            PrivacyTargets.advertising, PrivacyTargets.attribution,
        )

    private val all
        get() = tables.flatten()
}
