package io.github.nexfusion.morphe.shared

import io.github.nexfusion.morphe.AccessFlags
import io.github.nexfusion.morphe.Fingerprint
import io.github.nexfusion.morphe.Opcode
import io.github.nexfusion.morphe.StringComparisonType
import io.github.nexfusion.morphe.findMethodDirect
import io.github.nexfusion.morphe.methodCall
import io.github.nexfusion.morphe.string

internal object FormatStreamModelToStringFingerprint : Fingerprint(
    name = "toString",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "Ljava/lang/String;",
    filters = listOf(
        string(" isDefaultAudioTrack="),
        string(" audioTrackId="),
        string(" audioTrackDisplayName="),
        string(" width="),
        string(" height="),
        string("FormatStream(itag="),
        string(" mimeType=")
    )
)

internal object SpannableStringBuilderFingerprint : Fingerprint(
    returnType = "Ljava/lang/CharSequence;",
    filters = listOf(
        methodCall(
            opcode = Opcode.INVOKE_INTERFACE,
            returnType = "Ljava/lang/String;",
            parameters = emptyList()
        ),
        methodCall(
            opcode = Opcode.INVOKE_STATIC,
            smali = "Landroid/text/SpannableString;->valueOf(Ljava/lang/CharSequence;)Landroid/text/SpannableString;"
        ),
        methodCall(
            opcode = Opcode.INVOKE_STATIC,
            returnType = "V",
            parameters = listOf(
                "Landroid/text/SpannableString;",
                "Ljava/lang/Object;",
                "I",
                "Z",
                "I"
            )
        ),
        string(
            "Failed to set PB Style Run Extension in TextComponentSpec.",
            comparison = StringComparisonType.STARTS_WITH
        )
    )
)

val spannableStringBuilderGetSpannedMethod = findMethodDirect {
    SpannableStringBuilderFingerprint.instructionMatches[0].instruction.methodRef!!
}
