package io.github.a092devs.nexfusion.nexfusion.reddit

import io.github.a092devs.nexfusion.ApkContext
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertNotNull

class RedditFingerprintsTest {
    @Test
    fun testRedditSettingsFingerprints() {
        val apk = File("binaries/com.reddit.frontpage-2026.45.0.apk")
        if (!apk.exists()) return

        val context = ApkContext(apk.absolutePath)
        val dexkit = context.dexkit
        context.setupCurrentThread()

        with(dexkit) {
            val registryCtor = UserSettingsRegistryConstructorFingerprint.run()
            assertNotNull(registryCtor)

            val rowCtor = PrivacyPolicySettingRowConstructorFingerprint.run()
            assertNotNull(rowCtor)

            val rowContent = PrivacyPolicySettingRowContentFingerprint.run()
            assertNotNull(rowContent)

            val composableMethod = SettingsRowComposableMethod(dexkit)
            assertNotNull(composableMethod)
        }
    }

    @Test
    fun testGlassNavigationBarFingerprints() {
        val apk = File("binaries/com.reddit.frontpage-2026.45.0.apk")
        if (!apk.exists()) return

        val context = ApkContext(apk.absolutePath)
        val dexkit = context.dexkit
        context.setupCurrentThread()

        with(dexkit) {
            assertNotNull(DrawRoundRectFingerprint.run(), "DrawRoundRectFingerprint")
            assertNotNull(DrawContentFingerprint.run(), "DrawContentFingerprint")
            assertNotNull(LottieAnimationDrawFingerprint.run(), "LottieAnimationDrawFingerprint")
            assertNotNull(OffsetFingerprint.run(), "OffsetFingerprint")
            assertNotNull(PaddingFingerprint.run(), "PaddingFingerprint")
            assertNotNull(BottomNavBarFingerprint.run(), "BottomNavBarFingerprint")
            assertNotNull(SideNavRailContentFingerprint.run(), "SideNavRailContentFingerprint")
            assertNotNull(
                runCatching { BottomNavBackdropFingerprint.run() }.getOrNull()
                    ?: BottomNavBackdropLegacyFingerprint.run(),
                "BottomNavBackdropFingerprint",
            )
            runCatching { RailBackgroundFingerprint.run() }
            runCatching { ModifierCompanionFingerprint.run() }
            assertNotNull(BottomNavBarPlacementFingerprint.run(), "BottomNavBarPlacementFingerprint")
            assertNotNull(StaticBottomNavBarFingerprint.run(), "StaticBottomNavBarFingerprint")
            assertNotNull(CreateButtonOffsetFingerprint.run(), "CreateButtonOffsetFingerprint")
            assertNotNull(BottomNavScreenListBuilderFingerprint.run(), "BottomNavScreenListBuilderFingerprint")
            assertNotNull(
                runCatching { FeatureFlagFingerprint.run() }.getOrNull()
                    ?: FeatureFlagLegacyFingerprint.run(),
                "FeatureFlagFingerprint",
            )
            assertNotNull(NavBarStateChangeFingerprint.run(), "NavBarStateChangeFingerprint")
            assertNotNull(IncognitoRevampEnabledFingerprint.run(), "IncognitoRevampEnabledFingerprint")
            assertNotNull(HomeBackPressedFingerprint.run(), "HomeBackPressedFingerprint")
            assertNotNull(SearchRowMapperFingerprint.run(), "SearchRowMapperFingerprint")
            assertNotNull(VerticalChainingFingerprint.run(), "VerticalChainingFingerprint")
            assertNotNull(RecommendedMediaRequestFingerprint.run(), "RecommendedMediaRequestFingerprint")
            assertNotNull(RecommendedVideosRequestFingerprint.run(), "RecommendedVideosRequestFingerprint")
        }
    }
}
