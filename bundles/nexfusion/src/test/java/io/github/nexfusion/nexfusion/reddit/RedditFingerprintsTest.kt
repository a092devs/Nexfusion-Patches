package io.github.nexfusion.nexfusion.reddit

import io.github.nexfusion.ApkContext
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
}
