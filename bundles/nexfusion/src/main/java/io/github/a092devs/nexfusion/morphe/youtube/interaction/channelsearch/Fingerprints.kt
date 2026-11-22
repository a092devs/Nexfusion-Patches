/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches/pull/2964
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package io.github.a092devs.nexfusion.morphe.youtube.interaction.channelsearch

import io.github.a092devs.nexfusion.morphe.AccessFlags
import io.github.a092devs.nexfusion.morphe.Fingerprint
import io.github.a092devs.nexfusion.morphe.InstructionLocation.MatchAfterImmediately
import io.github.a092devs.nexfusion.morphe.InstructionLocation.MatchAfterWithin
import io.github.a092devs.nexfusion.morphe.Opcode
import io.github.a092devs.nexfusion.morphe.ResourceType
import io.github.a092devs.nexfusion.morphe.StrictQuery
import io.github.a092devs.nexfusion.morphe.anyInstruction
import io.github.a092devs.nexfusion.morphe.fieldAccess
import io.github.a092devs.nexfusion.morphe.findFieldDirect
import io.github.a092devs.nexfusion.morphe.findMethodDirect
import io.github.a092devs.nexfusion.morphe.methodCall
import io.github.a092devs.nexfusion.morphe.opcode
import io.github.a092devs.nexfusion.morphe.resourceLiteral
import io.github.a092devs.nexfusion.morphe.string

/**
 * Every browse page is shown by this one fragment, and the endpoint it is handed names the page.
 */
internal object BrowseFragmentOnCreateViewFingerprint : Fingerprint(
    returnType = "Landroid/view/View;",
    parameters = listOf(
        "Landroid/view/LayoutInflater;",
        "Landroid/view/ViewGroup;",
        "Landroid/os/Bundle;"
    ),
    filters = listOf(
        string("Browse Fragment was given a navigation endpoint without browse data.")
    )
)

val browseFragmentEndpointField = findFieldDirect {
    val onCreateViewMethod = BrowseFragmentOnCreateViewFingerprint()
    val stringIndex = onCreateViewMethod.instructions.indexOfFirst {
        it.string == "Browse Fragment was given a navigation endpoint without browse data."
    }
    val igetBefore = if (stringIndex > 0) {
        onCreateViewMethod.instructions.subList(0, stringIndex).findLast {
            it.opcode == 84 && it.fieldRef?.declaredClassName == onCreateViewMethod.declaredClassName
        }
    } else null
    igetBefore?.fieldRef ?: BrowseFragmentOnCreateViewFingerprint.instructionMatches.getOrNull(1)?.instruction?.fieldRef!!
}

val browseFragmentSetterMethod = findMethodDirect {
    val endpointField = browseFragmentEndpointField()
    val browseFragmentClass = BrowseFragmentOnCreateViewFingerprint().declaredClass!!
    browseFragmentClass.methods.firstOrNull { m ->
        m.returnTypeName == "void" && m.paramCount == 1 &&
                m.instructions.any { it.opcode == 91 && it.fieldRef?.name == endpointField.name }
    } ?: browseFragmentClass.methods.first { m ->
        m.returnTypeName == "void" && m.paramCount == 1 &&
                (m.paramTypeNames.first() == endpointField.typeName || m.paramTypeNames.first().contains(endpointField.typeName))
    }
}

/**
 * The search feed is its own fragment, so it does not go through the browse fragment when
 * the user returns to it from a channel.
 */
internal object SearchResultsFragmentOnCreateViewFingerprint : Fingerprint(
    returnType = "Landroid/view/View;",
    parameters = listOf(
        "Landroid/view/LayoutInflater;",
        "Landroid/view/ViewGroup;",
        "Landroid/os/Bundle;"
    ),
    filters = listOf(
        string("search_cache_key")
    )
)

/**
 * Picks the hint of the search box.
 */
internal object SearchBoxHintFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC),
    returnType = "Ljava/lang/String;",
    filters = listOf(
        resourceLiteral(ResourceType.STRING, "shorts_search_hint"),
        resourceLiteral(ResourceType.STRING, "playlists_search_hint"),
        resourceLiteral(ResourceType.STRING, "search_hint"),
        opcode(Opcode.MOVE_RESULT_OBJECT)
    )
)

/**
 * Every search submit path funnels through this method.
 */
@StrictQuery
internal object SearchSubmitFingerprint : Fingerprint(
    returnType = "V",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    custom = {
        paramCount(6, 8)
        addInvoke {
            name = "toByteArray"
        }
    },
    filters = listOf(
        anyInstruction(
            methodCall( // 21.43+
                parameters = listOf(
                    "Ljava/lang/String;",
                    "[B",
                    "Ljava/lang/String;",
                    "I",
                    "L",
                    "L",
                    "Ljava/lang/String;",
                    "Ljava/lang/String;",
                    "Ljava/lang/String;",
                    "Ljava/lang/String;",
                    "Z",
                    "Ljava/lang/String;",
                ),
                returnType = "V"
            ),
            methodCall( // 21.31 - 21.42
                parameters = listOf(
                    "Ljava/lang/String;",
                    "[B",
                    "Ljava/lang/String;",
                    "I",
                    "L",
                    "L",
                    "Ljava/lang/String;",
                    "Ljava/lang/String;",
                    "Ljava/lang/String;",
                    "Ljava/lang/String;",
                    "Z"
                ),
                returnType = "V"
            ),
            methodCall( // 21.30 and older.
                parameters = listOf(
                    "Ljava/lang/String;",
                    "[B",
                    "Ljava/lang/String;",
                    "I",
                    "L",
                    "L",
                    "Ljava/lang/String;",
                    "Ljava/lang/String;",
                    "Ljava/lang/String;",
                    "Ljava/lang/String;"
                ),
                returnType = "V"
            )
        )
    )
)

internal object ToolBarButtonFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "V",
    filters = listOf(
        resourceLiteral(ResourceType.ID, "menu_item_view"),
        methodCall(smali = "Landroid/view/MenuItem;->setShowAsAction(I)V"),
        methodCall(
            opcode = Opcode.INVOKE_STATIC,
            returnType = "L",
            parameters = listOf("I")
        ),
        opcode(Opcode.MOVE_RESULT_OBJECT, MatchAfterImmediately()),
        methodCall(
            opcode = Opcode.INVOKE_INTERFACE,
            returnType = "I",
            parameters = listOf("L")
        ),
        opcode(Opcode.MOVE_RESULT, MatchAfterImmediately()),
        fieldAccess(
            opcode = Opcode.IGET_OBJECT,
            type = "Landroid/widget/ImageView;",
            location = MatchAfterWithin(6)
        ),
        methodCall(
            definingClass = "Landroid/content/res/Resources;",
            name = "getDrawable",
            location = MatchAfterWithin(8)
        ),
        methodCall(
            definingClass = "Landroid/widget/ImageView;",
            name = "setImageDrawable",
            location = MatchAfterWithin(4)
        )
    )
)

val resolveToolbarIconEnumMethod = findMethodDirect {
    ToolBarButtonFingerprint.instructionMatches[2].instruction.methodRef!!
}
