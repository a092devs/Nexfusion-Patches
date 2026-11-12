package io.github.nexfusion.nexfusion.instagram

import io.github.nexfusion.ApkContext
import io.github.nexfusion.nexfusion.instagram.download.*
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class DownloadFingerprintsTest {
    @Test
    fun testInstagramDownloadFingerprints() {
        val apks = File("binaries").listFiles { file ->
            file.isFile && file.name.startsWith("com.instagram.android-") && file.name.endsWith(".apk")
        }?.sortedBy { it.name }?.takeLast(1).orEmpty()
        if (apks.isEmpty()) {
            println("No Instagram APK found, skipping")
            return
        }
        println("Instagram APKs: ${apks.joinToString { "${it.name} (${it.length()})" }}")

        for (apkFile in apks) {
            println("=== ${apkFile.name} ===")
            ApkContext(apkFile.absolutePath).use { context ->
                val dexkit = context.dexkit

            assertNotNull(overflowMenuOptionsMethod(dexkit))
            assertNotNull(overflowMenuCreatorClass(dexkit))
            assertNotNull(overflowMenuOptionClickMethod(dexkit))
            val sheets = sheetMenuMethods(dexkit)
            assertTrue(sheets.isNotEmpty(), "sheetMenuMethods must not be empty")
            println("Verified ${sheets.size} sheet menu methods")
            val builder = sheetMenuBuilderClass(dexkit)
            assertNotNull(builder)
            println("Verified sheetMenuBuilderClass: ${builder.name}")
            val addItem = sheetMenuAddItemMethod(dexkit)
            assertNotNull(addItem)
            println("Verified sheetMenuAddItemMethod: $addItem")
            val storyLabels = storyMenuLabelsMethod(dexkit)
            assertNotNull(storyLabels)
            println("Verified storyMenuLabelsMethod: $storyLabels")
            val storySheet = storyMenuSheetMethod(dexkit)
            assertNotNull(storySheet)
            println("Verified storyMenuSheetMethod: $storySheet")
            val storyRow = storyMenuRowConstructor(dexkit)
            assertNotNull(storyRow)
            println("Verified storyMenuRowConstructor: $storyRow")
            assertNotNull(profilePictureActionsMethod(dexkit))
            assertNotNull(profilePictureActionConstructor(dexkit))
            assertNotNull(highlightMenuMethod(dexkit))
            assertNotNull(highlightMenuBuilderClass(dexkit))
            assertNotNull(highlightMenuAddMethod(dexkit))
            assertNotNull(storyHeaderBinderMethod(dexkit))
            println("Verified storyHeaderBinderMethod: ${storyHeaderBinderMethod(dexkit)}")
            val positionField = mediaPositionField(dexkit)
            assertNotNull(positionField)
            println("Verified mediaPositionField: $positionField")

            // Media getters the downloader reads, including the DASH manifest that carries a reel's audio track.
            assertNotNull(mediaImageInfoGetter(dexkit))
            assertNotNull(mediaVideoVersionsGetter(dexkit))
            assertNotNull(mediaCarouselGetter(dexkit))
            val dashManifestGetter = mediaDashManifestGetter(dexkit)
            assertNotNull(dashManifestGetter)
            println("Verified mediaDashManifestGetter: $dashManifestGetter")
            assertNotNull(mediaUserGetter(dexkit))
            assertNotNull(userUsernameGetter(dexkit))
            assertNotNull(videoVersionWidthGetter(dexkit))
            assertNotNull(videoVersionHeightGetter(dexkit))
            assertNotNull(igDialogBuildMethod(dexkit))
            assertNotNull(igDialogTitleField(dexkit))
            assertNotNull(igDialogCancelableMethod(dexkit))
            assertNotNull(igDialogCanceledOnTouchOutsideMethod(dexkit))
            assertNotNull(igDialogNegativeButtonMethod(dexkit))
            assertNotNull(igDialogMenuBottomCornersField(dexkit))

            // Non-download anchors used by the rest of the Instagram patch set.
            assertNotNull(typingStatusMethod(dexkit))
            println("Verified typingStatusMethod: ${typingStatusMethod(dexkit)}")
            assertNotNull(feedDoubleTapMethod(dexkit))
            println("Verified feedDoubleTapMethod: ${feedDoubleTapMethod(dexkit)}")
            assertNotNull(reelsDoubleTapMethod(dexkit))
            println("Verified reelsDoubleTapMethod: ${reelsDoubleTapMethod(dexkit)}")

            // Kept class names and entry points the runtime hooks load directly.
            fun assertClassExists(name: String) {
                val found = dexkit.findClass { matcher { className(name) } }
                assertTrue(found.isNotEmpty(), "Class not found: $name")
                println("Verified class: $name")
            }
            assertClassExists("com.instagram.api.tigon.TigonServiceLayer")
            assertTrue(
                dexkit.findMethod {
                    matcher {
                        declaredClass("com.instagram.api.tigon.TigonServiceLayer")
                        this.name = "startRequest"
                    }
                }.isNotEmpty(),
                "TigonServiceLayer.startRequest not found",
            )
            assertClassExists("instagram.features.stories.fragment.ReelViewerFragment")
            val observerExists = dexkit.findClass {
                matcher { className("com.facebook.screenshot.ScreenshotContentObserver") }
            }.isNotEmpty()
            println("Screenshot observer present: $observerExists (framework callbacks are the fallback)")
            assertClassExists("com.instagram.inappbrowser.fragments.BrowserLiteInMainProcessIGActivity")

            println("ALL FINGERPRINTS PASSED FOR ${apkFile.name}!")
            }
        }
    }
}
