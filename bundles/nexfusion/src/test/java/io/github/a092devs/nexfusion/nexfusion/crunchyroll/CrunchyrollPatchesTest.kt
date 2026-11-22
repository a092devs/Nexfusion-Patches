package io.github.a092devs.nexfusion.nexfusion.crunchyroll

import io.github.a092devs.nexfusion.nexfusion.NexFusionPatches
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CrunchyrollPatchesTest {

    @Test
    fun `bundle includes all expected crunchyroll patches`() {
        assertEquals(2, CrunchyrollPatches.size)
        val names = CrunchyrollPatches.map { it.name }.toSet()
        assertTrue(names.contains("Enable Picture-in-Picture"))
        assertTrue(names.contains("Remove from Continue Watching"))
    }

    @Test
    fun `NexFusion bundle declares crunchyroll with universal dynamic architecture`() {
        val crApp = NexFusionPatches.apps.firstOrNull { it.packageName == "com.crunchyroll.crunchyroid" }
        assertNotNull(crApp)
        assertEquals("Crunchyroll", crApp.appName)
        assertEquals(0xFF5C00, crApp.appIconColor)
        assertEquals(2, crApp.patches.size)
        assertTrue(crApp.targets.isEmpty(), "Crunchyroll uses universal dynamic hooks")
    }
}
