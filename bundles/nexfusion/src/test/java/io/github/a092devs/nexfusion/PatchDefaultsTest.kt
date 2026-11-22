package io.github.a092devs.nexfusion

import io.github.a092devs.nexfusion.nexfusion.NexFusionPatches
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PatchDefaultsTest {

    @Test
    fun testAllUserFacingPatchesDefaultToDisabled() {
        val allApps = NexFusionPatches.apps
        assertTrue(allApps.isNotEmpty(), "NexFusionPatches must expose apps")

        val violations = mutableListOf<String>()
        for (app in allApps) {
            for (patch in app.patches) {
                if (patch.name.isNotEmpty() && !patch.name.startsWith("<")) {
                    if (patch.use) {
                        violations.add("App '${app.appName}' (${app.packageName}) -> Patch '${patch.name}' has use=true, expected false (disabled by default)")
                    }
                } else {
                    if (!patch.use) {
                        violations.add("App '${app.appName}' (${app.packageName}) -> Internal hook '${patch.name}' has use=false, expected true")
                    }
                }
            }
        }

        assertTrue(
            violations.isEmpty(),
            "Found patches with incorrect default toggles:\n" + violations.joinToString("\n")
        )
    }
}
