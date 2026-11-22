package io.github.a092devs.nexfusion.morphe.shared.misc.litho.context

import io.github.a092devs.nexfusion.morphe.Fingerprint
import io.github.a092devs.nexfusion.morphe.findFieldDirect
import io.github.a092devs.nexfusion.morphe.findFieldFromToString

internal const val IDENTIFIER_PROPERTY = ", identifierProperty="
internal const val HORIZONTAL_COLLECTION_SWIPE_PROTECTOR_PROPERTY = "horizontalCollectionSwipeProtector="
internal const val HEIGHT_CONSTRAINT_PROPERTY = "heightConstraint="

internal object ConversionContextToStringFingerprint : Fingerprint(
    name = "toString",
    parameters = listOf(),
    returnType = "Ljava/lang/String;",
    strings = listOf(
//        "ConversionContext{", // Partial string match.
        ", widthConstraint=",
        ", templateLoggerFactory=",
        ", rootDisposableContainer=",
        IDENTIFIER_PROPERTY
    )
)

val stringBuilderFieldData = findFieldDirect {
    val conversionContextClassDef = ConversionContextToStringFingerprint().declaredClass!!
    conversionContextClassDef.fields.single { field ->
        field.typeSign == "Ljava/lang/StringBuilder;"
    }
}

val identifierFieldData = findFieldDirect {
    val method = ConversionContextToStringFingerprint()
    method.findFieldFromToString(IDENTIFIER_PROPERTY)
}

val horizontalSwipeFieldData = findFieldDirect {
    val method = ConversionContextToStringFingerprint()
    method.findFieldFromToString(HORIZONTAL_COLLECTION_SWIPE_PROTECTOR_PROPERTY)
}

val heightConstraintData = findFieldDirect {
    val method = ConversionContextToStringFingerprint()
    method.findFieldFromToString(HEIGHT_CONSTRAINT_PROPERTY)
}
