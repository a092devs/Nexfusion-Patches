package io.github.nexfusion.nexfusion.protonvpn

import io.github.nexfusion.nexfusion.NexFusionPatches
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ProtonVpnPatchesTest {

    @Test
    fun `bundle includes all expected proton vpn patches`() {
        assertEquals(8, ProtonVpnPatches.size)
        val names = ProtonVpnPatches.map { it.name }.toSet()
        assertTrue(names.contains("Disable telemetry"))
        assertTrue(names.contains("Remove server change delay"))
        assertTrue(names.contains("Unlock advanced settings & NetShield"))
        assertTrue(names.contains("Show free server locations"))
        assertTrue(names.contains("Unlock connection preferences"))
        assertTrue(names.contains("Hide upgrade promotions"))
        assertTrue(names.contains("Plus UI badge"))
        assertTrue(names.contains("Disable in-app update checks"))
    }

    @Test
    fun `NexFusion bundle declares proton vpn with universal dynamic architecture`() {
        val protonApp = NexFusionPatches.apps.firstOrNull { it.packageName == "ch.protonvpn.android" }
        assertNotNull(protonApp)
        assertEquals("Proton VPN", protonApp.appName)
        assertEquals(8, protonApp.patches.size)
        assertTrue(protonApp.targets.isEmpty(), "Proton VPN uses universal dynamic hooks")
    }
}
