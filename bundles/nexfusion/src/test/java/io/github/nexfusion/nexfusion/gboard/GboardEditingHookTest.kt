package io.github.nexfusion.nexfusion.gboard

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GboardEditingHookTest {

    @Test
    fun `test GboardPatches registration contains new ported patches`() {
        val patchNames = GboardPatches.map { it.name }.toSet()
        assertTrue(patchNames.contains("Keypress vibration intensity control"))
        assertTrue(patchNames.contains("Split keyboard on all devices"))
        assertTrue(patchNames.contains("Terminal paste & auto select-all"))
        assertTrue(patchNames.contains("G Logo on Spacebar"))
        assertTrue(patchNames.contains("Developer options & internal flags"))
        assertTrue(patchNames.contains("Backup & Restore settings"))
    }

    @Test
    fun `test patch descriptions and states`() {
        assertNotNull(GboardKeypressHaptics.description)
        assertNotNull(GboardSplitKeyboard.description)
        assertNotNull(GboardTerminalPasteAndSelectAll.description)
        assertNotNull(GboardSpacebarLogo.description)
        assertNotNull(GboardDeveloperOptions.description)
        assertNotNull(GboardBackupRestore.description)

        assertTrue(GboardKeypressHaptics.use)
        assertTrue(GboardTerminalPasteAndSelectAll.use)
    }
}
