package io.github.nexfusion.nexfusion.spotify

import io.github.nexfusion.ApkContext
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SpotifyFingerprintsTest {

    @Test
    fun testSpotifyFingerprints() {
        val apks = File("binaries").listFiles { file ->
            file.isFile && file.name.startsWith("com.spotify.music-") && file.name.endsWith(".apk")
        }?.sortedBy { it.name }.orEmpty()
        if (apks.isEmpty()) return

        for (apkFile in apks) {
            val context = ApkContext(apkFile.absolutePath)
            val dexkit = context.dexkit

            assertNotNull(contextMenuViewModelClass(dexkit))
            assertTrue(homeSectionsGetters(dexkit).isNotEmpty())
            assertTrue(browseSectionsGetters(dexkit).isNotEmpty())
            assertNotNull(npvSectionsGetter(dexkit))
            assertTrue(pendragonRequestMethods(dexkit).isNotEmpty())
            assertNotNull(embeddedAdPlaylistPropertiesConstructor(dexkit))
            assertTrue(npvPluginEntryConstructors(dexkit).isNotEmpty())
            assertNotNull(coroutineClientBaseConstructor(dexkit))
            assertNotNull(navigationTabEnumClass(dexkit))
            assertNotNull(navigationTabSetConstructor(dexkit))
            assertNotNull(shareCopyUrlMethod(dexkit))
            assertNotNull(formatAndroidShareSheetUrlMethod(dexkit))
        }
    }
}
