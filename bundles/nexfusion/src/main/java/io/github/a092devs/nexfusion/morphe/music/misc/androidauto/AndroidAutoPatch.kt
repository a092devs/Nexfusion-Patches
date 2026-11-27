package io.github.a092devs.nexfusion.morphe.music.misc.androidauto

import app.morphe.extension.shared.Logger
import io.github.a092devs.nexfusion.hookMethod
import io.github.a092devs.nexfusion.morphe.Fingerprint
import io.github.a092devs.nexfusion.morphe.music.misc.settings.SettingsHook
import io.github.a092devs.nexfusion.morphe.string
import io.github.a092devs.nexfusion.patch

internal object CheckCertificateFingerprint : Fingerprint(
    returnType = "Z",
    parameters = listOf("L"),
    strings = listOf("X509", "isPartnerSHAFingerprint")
)

internal object GoogleCertificatesRemoteFingerprint : Fingerprint(
    returnType = "L",
    parameters = listOf("Ljava/lang/String;"),
    filters = listOf(string("Failed to get Google certificates from remote"))
)

internal object IsGoogleSignedFingerprint : Fingerprint(
    classFingerprint = GoogleCertificatesRemoteFingerprint,
    returnType = "Z",
    parameters = listOf("Ljava/lang/String;")
)

val AndroidAuto = patch(
    name = "Android Auto",
    description = "Bypasses certificate checks and restores YouTube Music Home recommendations, Library collections, and podcasts in Android Auto.",
    use = false,
) {
    dependsOn(SettingsHook)

    runCatching {
        CheckCertificateFingerprint.hookMethod {
            before { param -> param.result = true }
        }
    }.onFailure {
        Logger.printException({ "AndroidAuto: CheckCertificate hook failed" }, it)
    }

    runCatching {
        IsGoogleSignedFingerprint.hookMethod {
            before { param -> param.result = true }
        }
    }.onFailure {
        Logger.printException({ "AndroidAuto: IsGoogleSigned hook failed" }, it)
    }
}
