package io.github.nexfusion.morphe.youtube.video.speed.custom

import io.github.nexfusion.RequireAppVersion
import io.github.nexfusion.morphe.AccessFlags
import io.github.nexfusion.morphe.Fingerprint
import io.github.nexfusion.morphe.findFieldDirect
import io.github.nexfusion.morphe.findMethodDirect
import io.github.nexfusion.morphe.fingerprint
import io.github.nexfusion.morphe.literal
import io.github.nexfusion.morphe.parameters
import io.github.nexfusion.morphe.returns
import io.github.nexfusion.morphe.youtube.shared.SpeedLimiterFingerprint

internal val speedArrayGeneratorFingerprint = fingerprint {
    accessFlags(AccessFlags.PUBLIC, AccessFlags.STATIC)
    returns("[L")
    parameters("L")
    strings("0.0#")
}

// found in com.google.android.libraries.youtube.innertube.model.media.PlayerConfigModel
val speedsFloatArrayField = findFieldDirect {
    speedArrayGeneratorFingerprint().usingFields.single {
        it.field.typeSign == "[F"
    }.field
}

@RequireAppVersion("20.34.00")
internal object ServerSideMaxSpeedFeatureFlagFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "Z",
    filters = listOf(
        literal(45719140L)
    )
)

val clampFloatFingerprint = findMethodDirect {
    SpeedLimiterFingerprint().invokes.findMethod {
        matcher {
            parameters("F", "F", "F")
            returns("F")
        }
    }.single()
}
