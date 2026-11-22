package io.github.a092devs.nexfusion.morphe.youtube.misc.litho.rendernext

import io.github.a092devs.nexfusion.RequireAppVersion
import io.github.a092devs.nexfusion.SkipTest
import io.github.a092devs.nexfusion.morphe.AccessFlags
import io.github.a092devs.nexfusion.morphe.Fingerprint
import io.github.a092devs.nexfusion.morphe.Match
import io.github.a092devs.nexfusion.morphe.Opcode
import io.github.a092devs.nexfusion.morphe.fieldAccess
import io.github.a092devs.nexfusion.morphe.findFieldDirect
import io.github.a092devs.nexfusion.morphe.findMethodListDirect
import io.github.a092devs.nexfusion.morphe.literal
import io.github.a092devs.nexfusion.morphe.methodCall
import io.github.a092devs.nexfusion.morphe.parameters
import io.github.a092devs.nexfusion.morphe.string
import org.luckypray.dexkit.DexKitBridge


/**
 * Reads the enable_rendernext field of an Element proto, set by the server.
 * If true, the element is presented with an ElementsView instead of Litho.
 */
internal object RenderNextEnablementCheckFingerprint : Fingerprint(
    returnType = "Z",
    filters = listOf(
        string("Failed to read Element proto passed in to RenderNextEnablementCheck: ")
    )
)

/**
 * Callers of the enablement check, that decide if an element is RenderNext.
 * The first boolean they read is the RenderNext field of the element config, set by the server.
 */
val RenderNextConfigField = findFieldDirect {
    val enablementCheck = RenderNextEnablementCheckFingerprint()
    Fingerprint(
        filters = listOf(
            fieldAccess(
                opcode = Opcode.IGET_BOOLEAN,
                type = "Z"
            ),
            methodCall(reference = enablementCheck)
        )
    ).matchAll().first()
        .instructionMatches.first().instruction.fieldRef!!
}

@SkipTest
fun DexKitBridge.RenderNextConfigFieldReadMatches(): List<Match> {
    val configField = RenderNextConfigField()

    return Fingerprint(
        filters = listOf(
            fieldAccess(
                opcode = Opcode.IGET_OBJECT,
                type = configField.declaredClass.descriptor
            ),
            fieldAccess(
                opcode = Opcode.IGET_BOOLEAN,
                reference = configField
            )
        )
    ).matchAll().also {
        /*
        void M(A aVar) {
            B bVar = aVar.b;       // RenderNextConfigParentField
            boolean cVar = bVar.c; // RenderNextConfigField
            if (cVar)
            ...
        }
        */

        val all = it.all { match ->
            match.method.parameters[0] ==
                    match.instructionMatches[0].instruction.fieldRef!!.declaredClass.descriptor
        }
        if (!all) throw Exception()
    }
}

val RenderNextConfigParentField = findFieldDirect {
    RenderNextConfigFieldReadMatches().first().instructionMatches[0].instruction.fieldRef!!
}

val RenderNextConfigFieldReadFingerprint = findMethodListDirect {
    RenderNextConfigFieldReadMatches().map { it.method }
}

/**
 * Checks if a component identifier is in the comma separated list of the flag 45661551
 * ("template" or "parentTemplate:template"). If true, the Litho component is converted to RenderNext.
 */
@RequireAppVersion(minVersion = "20.29.00")
internal object RenderNextTemplateCheckFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.STATIC),
    returnType = "Z",
    parameters = listOf("L", "Ljava/lang/String;", "Ljava/lang/String;"),
    filters = listOf(
        literal(58),
        methodCall(smali = "Ljava/lang/String;->indexOf(I)I"),
        methodCall(smali = "Ljava/lang/String;->substring(II)Ljava/lang/String;"),
    )
)
