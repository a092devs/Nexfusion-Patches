package io.github.a092devs.nexfusion.nexfusion.instagram

import io.github.a092devs.nexfusion.ApkContext
import io.github.a092devs.nexfusion.nexfusion.instagram.download.*
import org.junit.jupiter.api.Assertions.assertEquals
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

            // Verify candidates ported from HushGram
            val lockoutMethods = dexkit.findMethod {
                matcher {
                    returnType = "void"
                    paramTypes = listOf("androidx.fragment.app.FragmentActivity", null)
                    usingStrings(listOf("lockout_active"), org.luckypray.dexkit.query.enums.StringMatchType.Contains)
                }
            }
            assertEquals(1, lockoutMethods.size, "Expected exactly 1 build expired lockout method")
            println("Verified buildExpiredLockoutMethod: ${lockoutMethods.first().className}.${lockoutMethods.first().name}")

            val seenMutationMethods = dexkit.findMethod {
                matcher {
                    returnType = "void"
                    paramCount = 3
                    usingStrings(listOf("IGDirectItemSeenMutation"), org.luckypray.dexkit.query.enums.StringMatchType.Contains)
                }
            }
            assertEquals(1, seenMutationMethods.size, "Expected exactly 1 IGDirectItemSeenMutation handler method")
            println("Verified directSeenMethod: ${seenMutationMethods.first().className}.${seenMutationMethods.first().name} params: ${seenMutationMethods.first().paramTypes}")
            val directCb = dexkit.findClass { matcher { className(seenMutationMethods.first().paramTypes[1].name) } }.firstOrNull()
            println("Direct Callback class: ${directCb?.name} methods: ${directCb?.methods?.map { "${it.name}(${it.paramTypes.joinToString()})${it.returnType}" }}")
            val impls = dexkit.findClass { matcher { addInterface(directCb!!.name) } }
            println("Implementations of ${directCb?.name}: ${impls.map { it.name }}")
            val threadSeenKeyMethods = dexkit.findMethod {
                matcher {
                    usingStrings(listOf("mark_thread_seen-"), org.luckypray.dexkit.query.enums.StringMatchType.Contains)
                }
            }
            println("Found threadSeenKeyMethods: ${threadSeenKeyMethods.size}")
            threadSeenKeyMethods.forEach { 
                println("threadSeenKeyMethod: ${it.className}.${it.name} return: ${it.returnType} modifiers: ${it.modifiers} params: ${it.paramTypes}")
            }

            val visualSeenMethods = dexkit.findMethod {
                matcher {
                    returnType = "void"
                    usingStrings(listOf("direct_v2/visual_threads/%s/item_seen/", "raven_media"), org.luckypray.dexkit.query.enums.StringMatchType.Contains)
                    paramCount = 3
                }
            }
            println("Found visualSeenMethods: ${visualSeenMethods.size}")
            visualSeenMethods.forEach { 
                println("visualSeenMethod: ${it.className}.${it.name} params: ${it.paramTypes}") 
                val cbClass = dexkit.findClass { matcher { className(it.paramTypes[1].name) } }.firstOrNull()
                println("Callback class: ${cbClass?.name} methods: ${cbClass?.methods?.map { "${it.name}(${it.paramTypes.joinToString()})${it.returnType}" }}")
            }

            val setThreadUnreadMethods = dexkit.findMethod {
                matcher {
                    returnType = "void"
                    modifiers = java.lang.reflect.Modifier.STATIC
                    paramCount = 3
                    paramTypes = listOf(
                        "com.instagram.common.session.UserSession",
                        "com.instagram.model.direct.DirectThreadKey",
                        "boolean",
                    )
                }
            }
            println("Found setThreadUnreadMethods: ${setThreadUnreadMethods.size}")
            setThreadUnreadMethods.forEach { 
                println("setThreadUnreadMethod: ${it.className}.${it.name}")
                val m = dexkit.findMethod { matcher { declaredClass(it.className); this.name = it.name } }.firstOrNull()
                println("Method callers/strings: ${m?.usingStrings}")
            }

            println("ALL FINGERPRINTS PASSED FOR ${apkFile.name}!")
            }
        }
    }
}
