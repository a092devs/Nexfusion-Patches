package io.github.a092devs.nexfusion.morphe.youtube.misc.engagement

import io.github.a092devs.nexfusion.morphe.AccessFlags
import io.github.a092devs.nexfusion.morphe.Fingerprint
import io.github.a092devs.nexfusion.morphe.Opcode
import io.github.a092devs.nexfusion.morphe.fieldAccess
import io.github.a092devs.nexfusion.morphe.findClassDirect
import io.github.a092devs.nexfusion.morphe.findFieldDirect
import io.github.a092devs.nexfusion.morphe.findMethodDirect
import io.github.a092devs.nexfusion.morphe.youtube.shared.EngagementPanelControllerFingerprint

internal object EngagementPanelUpdateFingerprint : Fingerprint(
    classFingerprint = EngagementPanelControllerFingerprint,
    accessFlags = listOf(AccessFlags.PRIVATE, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf("L", "Z"),
    filters = listOf(
        fieldAccess(
            opcode = Opcode.IGET_OBJECT,
            type = "Landroid/app/Activity;"
        )
    )
)

val panelInitFingerprint = findMethodDirect {
    val inits = panelClass().findMethod {
        matcher {
            name = "<init>"
        }
    }
    inits.firstOrNull { it.paramTypeNames.isNotEmpty() && it.paramTypeNames[0] == "java.lang.String" }
        ?: inits.firstOrNull() ?: throw NoSuchElementException("panelInitFingerprint not found")
}

val panelIdField = findFieldDirect {
    panelClass().fields.firstOrNull { it.typeName == "java.lang.String" }
        ?: throw NoSuchElementException("panelIdField not found")
}

val panelClass = findClassDirect {
    EngagementPanelControllerFingerprint.instructionMatches[3].instruction.classRef!!
}