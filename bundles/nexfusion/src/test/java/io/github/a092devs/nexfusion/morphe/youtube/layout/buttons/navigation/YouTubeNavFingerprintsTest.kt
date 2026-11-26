package io.github.a092devs.nexfusion.morphe.youtube.layout.buttons.navigation

import io.github.a092devs.nexfusion.ApkContext
import io.github.a092devs.nexfusion.morphe.youtube.layout.hide.ambientmode.CinematicContainerRendererFingerprint
import io.github.a092devs.nexfusion.morphe.youtube.layout.hide.ambientmode.CinematicContainerRendererWithStateFingerprint
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertNotNull

class YouTubeNavFingerprintsTest {
    @Test
    fun testNavigationAndClassicSubsFingerprints() {
        val apk = File("binaries/com.google.android.youtube-21.46.522.apk")
        if (!apk.exists()) return

        val context = ApkContext(apk.absolutePath)
        val dexkit = context.dexkit
        context.setupCurrentThread()

        with(dexkit) {
            val browseTabs = runCatching { BrowseResponseTabsFingerprint.match() }.onFailure { it.printStackTrace() }.getOrNull()
            println("BrowseResponseTabsFingerprint = ${browseTabs?.method}")
            assertNotNull(browseTabs, "BrowseResponseTabsFingerprint failed")

            val subsFactory = runCatching { PivotBarItemProtoFactoryFingerprint.match() }.onFailure { it.printStackTrace() }.getOrNull()
            println("PivotBarItemProtoFactoryFingerprint = ${subsFactory?.method}")
            assertNotNull(subsFactory, "PivotBarItemProtoFactoryFingerprint failed")

            val listBuilder = runCatching { PivotBarRendererListFingerprint.match() }.onFailure { it.printStackTrace() }.getOrNull()
            println("PivotBarRendererListFingerprint = ${listBuilder?.method}")
            assertNotNull(listBuilder, "PivotBarRendererListFingerprint failed")

            val cinematic = runCatching { CinematicContainerRendererFingerprint.match() }.onFailure { it.printStackTrace() }.getOrNull()
            println("CinematicContainerRendererFingerprint = ${cinematic?.method}")
            assertNotNull(cinematic, "CinematicContainerRendererFingerprint failed")

            val cinematicState = runCatching { CinematicContainerRendererWithStateFingerprint.match() }.onFailure { it.printStackTrace() }.getOrNull()
            println("CinematicContainerRendererWithStateFingerprint = ${cinematicState?.method}")
            assertNotNull(cinematicState, "CinematicContainerRendererWithStateFingerprint failed")
        }
    }
}
