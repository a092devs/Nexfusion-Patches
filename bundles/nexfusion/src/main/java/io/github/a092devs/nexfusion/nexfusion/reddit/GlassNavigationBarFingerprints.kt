package io.github.a092devs.nexfusion.nexfusion.reddit

import io.github.a092devs.nexfusion.morphe.AccessFlags
import io.github.a092devs.nexfusion.morphe.Fingerprint
import io.github.a092devs.nexfusion.morphe.InstructionLocation.MatchAfterImmediately
import io.github.a092devs.nexfusion.morphe.InstructionLocation.MatchAfterWithin
import io.github.a092devs.nexfusion.morphe.Opcode
import io.github.a092devs.nexfusion.morphe.StringComparisonType
import io.github.a092devs.nexfusion.morphe.fieldAccess
import io.github.a092devs.nexfusion.morphe.findFieldDirect
import io.github.a092devs.nexfusion.morphe.findMethodDirect
import io.github.a092devs.nexfusion.morphe.literal
import io.github.a092devs.nexfusion.morphe.methodCall
import io.github.a092devs.nexfusion.morphe.newInstance
import io.github.a092devs.nexfusion.morphe.opcode
import io.github.a092devs.nexfusion.morphe.string

private const val BOTTOM_NAV_SCREEN_CLASS = "Lcom/reddit/launch/bottomnav/BottomNavScreen;"

// region Compose utilities

internal object DrawRoundRectFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC, AccessFlags.SYNTHETIC),
    returnType = "V",
    parameters = listOf("L", "J", "J", "J", "J", "L", "F", "I"),
    filters = listOf(
        methodCall(
            opcode = Opcode.INVOKE_INTERFACE,
            parameters = listOf(),
            returnType = "J",
        ),
        methodCall(
            opcode = Opcode.INVOKE_INTERFACE_RANGE,
            parameters = listOf("J", "J", "J", "J", "L", "F"),
            returnType = "V",
        ),
    ),
)

internal object DrawContentFingerprint : Fingerprint(
    returnType = "V",
    parameters = listOf(),
    filters = listOf(
        string(
            "Attempting to drawContent for a `null` node",
            comparison = StringComparisonType.STARTS_WITH,
        ),
    ),
)

internal object LottieAnimationDrawFingerprint : Fingerprint(
    definingClass = "Lcom/airbnb/lottie/compose/LottieAnimationKt\$LottieAnimation\$2;",
    name = "invoke",
    returnType = "V",
    filters = listOf(
        methodCall(parameters = listOf(), returnType = "L"),
        opcode(Opcode.MOVE_RESULT_OBJECT, location = MatchAfterImmediately()),
        methodCall(parameters = listOf(), returnType = "L", location = MatchAfterWithin(5)),
        opcode(Opcode.MOVE_RESULT_OBJECT, location = MatchAfterImmediately()),
        fieldAccess(opcode = Opcode.IGET_OBJECT, type = "Landroid/graphics/Canvas;"),
    ),
)

internal object OffsetFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC, AccessFlags.FINAL),
    returnType = "L",
    parameters = listOf("L", "F", "F"),
    filters = listOf(
        methodCall(
            name = "<init>",
            parameters = listOf("F", "F", "L"),
        ),
        methodCall(
            opcode = Opcode.INVOKE_INTERFACE,
            parameters = listOf("L"),
            returnType = "L",
            location = MatchAfterWithin(5),
        ),
    ),
)

internal object PaddingFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC, AccessFlags.FINAL),
    returnType = "L",
    parameters = listOf("L", "F", "F", "F", "F"),
    filters = listOf(
        methodCall(
            name = "<init>",
            parameters = listOf("F", "F", "F", "F", "Lkotlin/jvm/functions/Function1;"),
        ),
        methodCall(
            opcode = Opcode.INVOKE_INTERFACE,
            parameters = listOf("L"),
            returnType = "L",
            location = MatchAfterWithin(5),
        ),
    ),
)

// endregion

// region Glass navigation bar

internal object BottomNavBarFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC, AccessFlags.FINAL),
    returnType = "V",
    filters = listOf(
        methodCall(
            opcode = Opcode.INVOKE_STATIC,
            parameters = listOf("L", "Lkotlin/jvm/functions/Function1;"),
            returnType = "L",
        ),
        string("bottom_nav", location = MatchAfterWithin(40)),
        methodCall(
            parameters = listOf("L", "L", "F", "J", "L", "Lkotlin/jvm/functions/Function2;", "L", "I", "I"),
            returnType = "V",
            location = MatchAfterWithin(40),
        ),
    ),
)

internal object SideNavRailContentFingerprint : Fingerprint(
    filters = listOf(
        literal(500f),
        methodCall(
            opcode = Opcode.INVOKE_STATIC,
            parameters = listOf("F", "F"),
            returnType = "I",
            location = MatchAfterWithin(5),
        ),
        methodCall(
            opcode = Opcode.INVOKE_DIRECT,
            name = "<init>",
            parameters = listOf("F", "Z", "L"),
        ),
        methodCall(
            opcode = Opcode.INVOKE_STATIC,
            definingClass = "Landroidx/compose/ui/b;",
            parameters = listOf("L", "L"),
            returnType = "L",
        ),
        literal(64f),
        literal(48f, location = MatchAfterWithin(10)),
        methodCall(
            opcode = Opcode.INVOKE_DIRECT,
            name = "<init>",
            parameters = listOf("Ljava/lang/String;", "Z"),
        ),
    ),
)

private val themeBackgroundFilters = listOf(
    opcode(Opcode.CHECK_CAST),
    opcode(Opcode.IGET_OBJECT, location = MatchAfterImmediately()),
    methodCall(
        opcode = Opcode.INVOKE_VIRTUAL,
        parameters = listOf(),
        returnType = "J",
        location = MatchAfterWithin(5),
    ),
    opcode(Opcode.MOVE_RESULT_WIDE, location = MatchAfterImmediately()),
    methodCall(
        opcode = Opcode.INVOKE_STATIC,
        parameters = listOf("L", "J", "L"),
        returnType = "L",
        location = MatchAfterWithin(5),
    ),
)

private val bottomNavBackdropFilters = themeBackgroundFilters + methodCall(
    definingClass = "Landroidx/compose/ui/graphics/d;",
    parameters = listOf("L", "Lkotlin/jvm/functions/Function1;"),
    location = MatchAfterWithin(40),
)

internal object BottomNavBackdropFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf(
        "Lkotlin/jvm/functions/Function0;",
        "Z",
        "L",
        "L",
        "Landroidx/compose/runtime/internal/a;",
        "L",
        "I",
    ),
    filters = bottomNavBackdropFilters,
)

internal object BottomNavBackdropLegacyFingerprint : Fingerprint(
    definingClass = BOTTOM_NAV_SCREEN_CLASS,
    returnType = "V",
    parameters = listOf("L", "L", "I"),
    filters = bottomNavBackdropFilters,
)

internal object RailBackgroundFingerprint : Fingerprint(
    definingClass = BOTTOM_NAV_SCREEN_CLASS,
    returnType = "V",
    filters = listOf(
        newInstance("Lcom/reddit/launch/bottomnav/BottomNavScreen\$NavStackHostContent\$2\$1;"),
    ) + themeBackgroundFilters,
)

internal object BottomNavStackHostFingerprint : Fingerprint(
    filters = listOf(
        fieldAccess(
            opcode = Opcode.IGET_OBJECT,
            definingClass = BOTTOM_NAV_SCREEN_CLASS,
            type = "Lcom/reddit/navstack/",
        ),
        methodCall(
            opcode = Opcode.INVOKE_STATIC_RANGE,
            definingClass = "Lcom/reddit/navstack/",
            parameters = listOf("L", "L", "Z", "L", "L", "I", "I"),
            returnType = "V",
        ),
    ),
)

private object ModifierCompanionToStringFingerprint : Fingerprint(
    name = "toString",
    returnType = "Ljava/lang/String;",
    parameters = listOf(),
    filters = listOf(
        string("Modifier"),
    ),
)

internal object ModifierCompanionFingerprint : Fingerprint(
    classFingerprint = ModifierCompanionToStringFingerprint,
    name = "<clinit>",
    filters = listOf(
        fieldAccess(opcode = Opcode.SPUT_OBJECT),
    ),
)

private object NavScreenContentStateToStringFingerprint : Fingerprint(
    name = "toString",
    returnType = "Ljava/lang/String;",
    filters = listOf(
        string(
            "NavScreenContentState(showBottomNav=",
            comparison = StringComparisonType.STARTS_WITH,
        ),
    ),
)

internal object NavScreenContentStateConstructorFingerprint : Fingerprint(
    classFingerprint = NavScreenContentStateToStringFingerprint,
    name = "<init>",
    parameters = listOf("Z", "Z", "Z", "F", "Z"),
)

internal object BottomNavBarPlacementFingerprint : Fingerprint(
    name = "invoke",
    returnType = "Ljava/lang/Object;",
    parameters = listOf("Ljava/lang/Object;"),
    filters = listOf(
        methodCall(definingClass = "Ljava/lang/Number;", name = "floatValue"),
        opcode(Opcode.SUB_FLOAT_2ADDR, location = MatchAfterWithin(5)),
        fieldAccess(opcode = Opcode.IGET, type = "I", location = MatchAfterImmediately()),
        opcode(Opcode.INT_TO_FLOAT, location = MatchAfterWithin(5)),
        opcode(Opcode.MUL_FLOAT_2ADDR, location = MatchAfterWithin(5)),
        methodCall(
            opcode = Opcode.INVOKE_STATIC,
            parameters = listOf("F"),
            returnType = "I",
            location = MatchAfterWithin(5),
        ),
        methodCall(
            opcode = Opcode.INVOKE_STATIC,
            parameters = listOf("L", "L", "I", "I"),
            returnType = "V",
            location = MatchAfterWithin(5),
        ),
    ),
)

internal object StaticBottomNavBarFingerprint : Fingerprint(
    definingClass = BOTTOM_NAV_SCREEN_CLASS,
    returnType = "V",
    parameters = listOf("L", "L", "I"),
    filters = listOf(
        methodCall(
            opcode = Opcode.INVOKE_STATIC,
            definingClass = "Landroidx/compose/ui/b;",
            parameters = listOf("L", "L"),
            returnType = "L",
        ),
        methodCall(
            definingClass = "Ljava/lang/Integer;",
            name = "valueOf",
            location = MatchAfterWithin(35),
        ),
        fieldAccess(
            opcode = Opcode.IGET_OBJECT,
            definingClass = BOTTOM_NAV_SCREEN_CLASS,
            type = "Landroidx/compose/runtime/internal/a;",
            location = MatchAfterWithin(25),
        ),
    ),
)

internal object CreateButtonOffsetFingerprint : Fingerprint(
    name = "invoke",
    returnType = "Ljava/lang/Object;",
    parameters = listOf("Ljava/lang/Object;"),
    filters = listOf(
        fieldAccess(
            opcode = Opcode.IGET_OBJECT,
            definingClass = "this",
            type = BOTTOM_NAV_SCREEN_CLASS,
        ),
        methodCall(definingClass = "Ljava/lang/Number;", name = "floatValue"),
        opcode(Opcode.INT_TO_FLOAT),
        methodCall(
            definingClass = "Ljava/lang/Math;",
            name = "max",
            parameters = listOf("I", "I"),
            returnType = "I",
        ),
        opcode(Opcode.MOVE_RESULT, location = MatchAfterImmediately()),
        opcode(Opcode.NEG_INT, location = MatchAfterWithin(5)),
    ),
)

internal object HomeTopBarFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC, AccessFlags.FINAL),
    returnType = "V",
    filters = listOf(
        string("home_revamp_m1_app_bar"),
        methodCall(
            opcode = Opcode.INVOKE_INTERFACE,
            name = "invoke",
            parameters = listOf("Ljava/lang/Object;", "Ljava/lang/Object;", "Ljava/lang/Object;"),
            returnType = "Ljava/lang/Object;",
        ),
        string("main_top_app_bar_search"),
        methodCall(
            opcode = Opcode.INVOKE_STATIC_RANGE,
            returnType = "V",
        ),
        methodCall(
            opcode = Opcode.INVOKE_STATIC,
            parameters = listOf("I", "I", "L", "L", "Landroidx/compose/runtime/internal/a;"),
            returnType = "V",
        ),
    ),
)

internal object ScreenAttachFingerprint : Fingerprint(
    definingClass = "Lcom/reddit/navstack/",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf("Landroid/view/View;"),
    filters = listOf(
        fieldAccess(definingClass = "Landroidx/lifecycle/Lifecycle\$State;", name = "DESTROYED"),
        fieldAccess(definingClass = "Landroidx/lifecycle/Lifecycle\$State;", name = "RESUMED"),
    ),
)

internal object ScreenDetachFingerprint : Fingerprint(
    definingClass = "Lcom/reddit/navstack/",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf("Landroid/view/View;"),
    filters = listOf(
        fieldAccess(definingClass = "Landroidx/lifecycle/Lifecycle\$State;", name = "RESUMED"),
        methodCall(definingClass = "Landroidx/lifecycle/Lifecycle\$State;", name = "isAtLeast"),
        fieldAccess(definingClass = "Landroidx/lifecycle/Lifecycle\$State;", name = "STARTED"),
    ),
)

// Direct instruction-match resolvers (context(DexKitBridge) via findMethodDirect / findFieldDirect)

internal val drawWithContentMethodRef = findMethodDirect {
    BottomNavBarFingerprint.instructionMatches[0].instruction.methodRef!!
}

internal val surfaceMethodRef = findMethodDirect {
    BottomNavBarFingerprint.instructionMatches[2].instruction.methodRef!!
}

internal val drawScopeSizeMethodRef = findMethodDirect {
    DrawRoundRectFingerprint.instructionMatches[0].instruction.methodRef!!
}

internal val drawContextMethodRef = findMethodDirect {
    LottieAnimationDrawFingerprint.instructionMatches[0].instruction.methodRef!!
}

internal val drawContextCanvasMethodRef = findMethodDirect {
    LottieAnimationDrawFingerprint.instructionMatches[2].instruction.methodRef!!
}

internal val internalCanvasFieldRef = findFieldDirect {
    LottieAnimationDrawFingerprint.instructionMatches[4].instruction.fieldRef!!
}

internal val modernBackgroundMethodRef = findMethodDirect {
    BottomNavBackdropFingerprint.instructionMatches[2].instruction.methodRef!!
}

internal val legacyBackgroundMethodRef = findMethodDirect {
    BottomNavBackdropLegacyFingerprint.instructionMatches[2].instruction.methodRef!!
}

internal val railBackgroundMethodRef = findMethodDirect {
    RailBackgroundFingerprint.instructionMatches[3].instruction.methodRef!!
}

internal val sideRailDpCompareMethodRef = findMethodDirect {
    SideNavRailContentFingerprint.instructionMatches[1].instruction.methodRef!!
}

internal val sideRailModifierThenMethodRef = findMethodDirect {
    SideNavRailContentFingerprint.instructionMatches[3].instruction.methodRef!!
}

internal val sideRailLabelCtorRef = findMethodDirect {
    SideNavRailContentFingerprint.instructionMatches.last().instruction.methodRef!!
}

internal val modifierCompanionFieldRef = findFieldDirect {
    ModifierCompanionFingerprint.instructionMatches.first().instruction.fieldRef!!
}

internal val navStackHostMethodRef = findMethodDirect {
    BottomNavStackHostFingerprint.instructionMatches[1].instruction.methodRef!!
}

internal val barHeightFieldRef = findFieldDirect {
    BottomNavBarPlacementFingerprint.instructionMatches[2].instruction.fieldRef!!
}

internal val roundToIntMethodRef = findMethodDirect {
    BottomNavBarPlacementFingerprint.instructionMatches[5].instruction.methodRef!!
}

internal val staticBarModifierMethodRef = findMethodDirect {
    StaticBottomNavBarFingerprint.instructionMatches[0].instruction.methodRef!!
}

internal val createBtnMathMaxMethodRef = findMethodDirect {
    CreateButtonOffsetFingerprint.instructionMatches[3].instruction.methodRef!!
}

internal val homeMenuInvokeMethodRef = findMethodDirect {
    HomeTopBarFingerprint.instructionMatches[1].instruction.methodRef!!
}

internal val homeSearchFieldMethodRef = findMethodDirect {
    HomeTopBarFingerprint.instructionMatches[3].instruction.methodRef!!
}

internal val homeAvatarMethodRef = findMethodDirect {
    HomeTopBarFingerprint.instructionMatches[4].instruction.methodRef!!
}

// endregion
