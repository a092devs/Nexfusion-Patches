package io.github.a092devs.nexfusion.nexfusion.cloudflare

import io.github.a092devs.nexfusion.nexfusion.NexFusionPatches
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CloudflareWarpPatchesTest {

    @Test
    fun `bundle includes all expected 1 1 1 1 patches`() {
        assertEquals(3, CloudflareWarpPatches.size)
        val names = CloudflareWarpPatches.map { it.name }.toSet()
        assertTrue(names.contains("Spoof WARP+ Unlimited UI"))
        assertTrue(names.contains("Disable telemetry"))
        assertTrue(names.contains("Disable SSL pinning"))
    }

    @Test
    fun `NexFusion bundle declares 1 1 1 1 with universal dynamic architecture`() {
        val warpApp = NexFusionPatches.apps.firstOrNull { it.packageName == "com.cloudflare.onedotonedotonedotone" }
        assertNotNull(warpApp)
        assertEquals("1.1.1.1", warpApp.appName)
        assertEquals(3, warpApp.patches.size)
        assertTrue(warpApp.targets.isEmpty(), "1.1.1.1 uses universal dynamic hooks")
    }
}
