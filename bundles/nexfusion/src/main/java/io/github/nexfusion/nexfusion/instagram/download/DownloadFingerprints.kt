package io.github.nexfusion.nexfusion.instagram.download

import io.github.nexfusion.morphe.Opcode
import io.github.nexfusion.morphe.findClassDirect
import io.github.nexfusion.morphe.findFieldDirect
import io.github.nexfusion.morphe.findMethodDirect
import io.github.nexfusion.morphe.findMethodListDirect
import io.github.nexfusion.morphe.opcodeEnum
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.query.enums.StringMatchType
import org.luckypray.dexkit.query.matchers.MethodMatcher
import org.luckypray.dexkit.result.ClassData
import org.luckypray.dexkit.result.FieldData
import org.luckypray.dexkit.result.MethodData
import java.lang.reflect.Modifier

// Ported from crimera/piko (GPL-3.0). This repository shipped these finders in its Piko bundle
// until 2.6.0 replaced it with the HushGram ports; they are restored here with the download
// implementation they belong to (see the download/ package and the NOTICE).
//
// The download menu anchors of Instagram: the post overflow menu, the bottom sheet menus of reels
// and feed posts, the story menu and its rows, the highlight long-press menu, the profile picture
// buttons, the download dialog, and the Media getters the media URLs are read with.

internal const val USER_SESSION_CLASS = "com.instagram.common.session.UserSession"
internal const val MEDIA_CLASS = "com.instagram.feed.media.Media"
internal const val MEDIA_OPTION_CLASS = "com.instagram.feed.media.mediaoption.MediaOption\$Option"

/** Methods using all [strings], preferring exact matches over partial ones. */
private fun DexKitBridge.findMethodWithStrings(
    strings: List<String>,
    block: MethodMatcher.() -> Unit = {},
): List<MethodData> {
    fun query(matchType: StringMatchType) = findMethod {
        matcher {
            block()
            usingStrings(strings, matchType)
        }
    }
    return query(StringMatchType.Equals).ifEmpty { query(StringMatchType.Contains) }
}

private fun DexKitBridge.methodsUsingStrings(vararg strings: String) = findMethodWithStrings(strings.toList())

private val MethodData.isStatic get() = Modifier.isStatic(modifiers)

/**
 * Builds the options of the post overflow menu, like Piko's fingerprint it is the lambda using "TEXT_POST_APP_INACTIVE".
 */
val overflowMenuOptionsMethod = findMethodDirect {
    methodsUsingStrings("TEXT_POST_APP_INACTIVE").single()
}

/**
 * Creates the options of the post overflow menu, its module name is "MediaOptionsOverflowMenuCreator".
 */
val overflowMenuCreatorClass = findClassDirect {
    methodsUsingStrings("MediaOptionsOverflowMenuCreator").single().declaredClass!!
}

/**
 * Handles the option tapped in the post overflow menu.
 */
val overflowMenuOptionClickMethod = findMethodDirect {
    findMethodWithStrings(listOf("MediaOptionsOverflowHelper")) {
        returnType = "void"
        paramTypes(MEDIA_OPTION_CLASS)
    }.single()
}

/**
 * The getter of a media value by its JSON key. Instagram 447 keeps many posts in a native live tree instead of the
 * fields of `Media`, and these getters read either.
 */
private fun DexKitBridge.mediaGetter(key: String, returnType: String) =
    findMethodWithStrings(listOf(key)) {
        declaredClass(MEDIA_CLASS)
        paramCount = 0
        this.returnType = returnType
    }.single()

val mediaImageInfoGetter = findMethodDirect {
    mediaGetter("image_versions2", "com.instagram.model.mediasize.ImageInfo")
}

val mediaVideoVersionsGetter = findMethodDirect {
    mediaGetter("video_versions", "java.util.List")
}

val mediaCarouselGetter = findMethodDirect {
    mediaGetter("carousel_media", "java.util.List")
}

val mediaDashManifestGetter = findMethodDirect {
    runCatching { pandoGetter(MEDIA_CLASS, "video_dash_manifest", "java.lang.String") }
        .getOrElse { mediaGetter("video_dash_manifest", "java.lang.String") }
}

internal const val USER_CLASS = "com.instagram.user.model.User"

/**
 * The getter of a value of Instagram's live tree by the key it reads: a parameterless getter of [type] returning
 * [returnType] whose body loads the hash of the key. HushGram pins its pando getters this way and `username`
 * needs it, as its getter holds no string, only the number. Verified on 451: `Media.A3R` reads "user" as both
 * the key's string and its hash 3599307, and `User.A8D` reads -265713450 for "username".
 */
private fun DexKitBridge.pandoGetter(type: String, key: String, returnType: String) =
    findMethod {
        matcher {
            declaredClass(type)
            paramCount = 0
            this.returnType = returnType
            usingNumbers(key.hashCode())
        }
    }.single()

/** The poster of a media, its `user`. */
val mediaUserGetter = findMethodDirect { pandoGetter(MEDIA_CLASS, "user", USER_CLASS) }

/** A user's `username`, for the per-username folders. */
val userUsernameGetter = findMethodDirect { pandoGetter(USER_CLASS, "username", "java.lang.String") }

/**
 * Module names of the bottom sheets showing the menu of a post: the reels menu, also used for posts shown like
 * reels, and the simplified menu of the "≡" button of posts in the home feed.
 */
internal val SHEET_MENU_MODULES = listOf(
    "ClipsOrganicMediaItemViewMoreOptionsController",
    "SimplifiedMediaOverflowBottomSheet",
    "MediaOverflowBottomSheetHelper",
    "ClipsLongPressOverflowBottomSheet",
)

/**
 * The bottom sheet builder created right after a menu module name, like in Piko's reels menu hook.
 */
private fun MethodData.sheetMenuBuilderClass(): ClassData? {
    val instructions = instructions
    val moduleIndex = instructions.indexOfFirst { it.string in SHEET_MENU_MODULES }
    if (moduleIndex < 0) return null
    return instructions.drop(moduleIndex + 1).firstOrNull { it.opcodeEnum == Opcode.NEW_INSTANCE }?.classRef
}

/**
 * Show the bottom sheet menus of posts and reels. The home feed filters the options of its simplified menu to a few
 * allowed ones, so the download item is added to the sheet itself.
 */
val sheetMenuMethods = findMethodListDirect {
    SHEET_MENU_MODULES.flatMap { module -> findMethodWithStrings(listOf(module)) }
        .filter { it.sheetMenuBuilderClass() != null }
        .distinctBy { it.descriptor }
}

val sheetMenuBuilderClass = findClassDirect {
    sheetMenuMethods().mapNotNull { it.sheetMenuBuilderClass() }.distinctBy { it.name }.single()
}

/**
 * The method of the sheet builder that adds a simple label-and-icon row.
 *
 * 451 uses `(Context, OnClickListener, String, int, boolean)`. 452 dropped the boolean and has three
 * `(Context, OnClickListener, String, int)` overloads, so the one the sheet menus call most is the row
 * adder; the other two are convenience overloads that forward to the full multi-line method.
 */
val sheetMenuAddItemMethod = findMethodDirect {
    val builder = sheetMenuBuilderClass().name
    val candidates = findMethod {
        matcher {
            declaredClass(builder)
            returnType = "void"
        }
    }
    val simpleParameters = listOf(
        "android.content.Context",
        "android.view.View\$OnClickListener",
        "java.lang.String",
        "int",
    )
    val withBoolean = candidates.singleOrNull { it.paramTypeNames == simpleParameters + "boolean" }
    if (withBoolean != null) {
        withBoolean
    } else {
        val menuReferences = sheetMenuMethods().flatMap { method ->
            method.instructions.mapNotNull { it.methodRef }
        }
        candidates.filter { it.paramTypeNames == simpleParameters }
            .maxByOrNull { candidate -> menuReferences.count { it.descriptor == candidate.descriptor } }
            ?: error("Sheet menu add-item method not found")
    }
}

/**
 * The labels of the menu of other people's stories, built by the "ReelOptionsOverflowHelper". Like Piko, the
 * download label is added there.
 */
val storyMenuLabelsMethod = findMethodDirect {
    findMethodWithStrings(listOf("[INTERNAL] Pause Playback")) {
        returnType = "java.lang.CharSequence[]"
    }.single()
}

/**
 * Shows the story menu sheet with its rows, the method creating the sheet whose module is
 * "stories_mimicry_menu_bottom_sheet".
 */
val storyMenuSheetMethod = findMethodDirect {
    val sheetClass = methodsUsingStrings("stories_mimicry_menu_bottom_sheet").single().declaredClassName
    findMethod {
        matcher {
            returnType = "void"
        }
    }.single { method ->
        "java.util.List" in method.paramTypeNames &&
            "kotlin.jvm.functions.Function1" in method.paramTypeNames &&
            method.instructions.any { it.classRef?.name == sheetClass }
    }
}

/**
 * A row of the story menu sheet. Instagram 451 creates it with
 * `(?, ImageUrl, ?, Integer, String title, String subtitle, String, Function0 onClick, int icon, boolean...)`,
 * and 452 adds two `Integer` values before the strings. The row class is the one the sheet builds, so its
 * constructor is resolved from the sheet method instead of pinning a parameter list that changes between
 * releases.
 */
val storyMenuRowConstructor = findMethodDirect {
    val rowClasses = storyMenuSheetMethod().instructions
        .mapNotNull { it.classRef?.name }
        .distinct()

    rowClasses.mapNotNull { className ->
        findMethod {
            matcher {
                declaredClass(className)
                this.name = "<init>"
            }
        }.firstOrNull { constructor ->
            val params = constructor.paramTypeNames
            params.getOrNull(1) == "com.instagram.common.typedurl.ImageUrl" &&
                "kotlin.jvm.functions.Function0" in params &&
                params.count { it == "java.lang.String" } >= 2
        }
    }.single()
}

/**
 * Adds the buttons below the expanded profile picture (Follow, Share profile, Copy link, QR code):
 * `(ViewGroup bar, UserSession, List actions, boolean, boolean)`, inflating a view for each action between spaces.
 */
val profilePictureActionsMethod = findMethodDirect {
    findMethod {
        matcher {
            returnType = "void"
            paramTypes("android.view.ViewGroup", USER_SESSION_CLASS, "java.util.List", "boolean", "boolean")
            addInvoke {
                declaredClass("android.view.LayoutInflater")
                name = "inflate"
            }
            addInvoke {
                declaredClass("android.widget.Space")
                name = "<init>"
            }
        }
    }.single()
}

/**
 * A button below the expanded profile picture, created with `(String label, Function0 onClick, int icon, boolean)`.
 */
val profilePictureActionConstructor = findMethodDirect {
    profilePictureActionsMethod().instructions.mapNotNull { it.classRef }.distinctBy { it.name }
        .flatMap { it.methods }
        .single {
            it.isConstructor && it.paramTypeNames ==
                listOf("java.lang.String", "kotlin.jvm.functions.Function0", "int", "boolean")
        }
}

/**
 * Shows the menu of a long pressed story highlight on a profile (Send to, Copy link, Show QR code, Share to, Report),
 * built with the dialog builder of the module "HighlightsTrayDialogHelper".
 */
val highlightMenuMethod = findMethodDirect {
    findMethodWithStrings(listOf("HighlightsTrayDialogHelper")) {
        returnType = "void"
    }.single()
}

/**
 * The dialog builder the highlight menu adds its items to. 451 builds it right after the module
 * name, while 452 creates a lambda first and receives the builder from a helper, so the class is
 * resolved as the one whose `(String, View.OnClickListener)` add method the menu calls the most.
 */
val highlightMenuBuilderClass = findClassDirect {
    val builder = highlightMenuMethod().instructions
        .mapNotNull { it.methodRef }
        .filter {
            it.returnTypeName == "void" &&
                it.paramTypeNames == listOf("java.lang.String", "android.view.View\$OnClickListener")
        }
        .groupBy { it.declaredClassName }
        .maxByOrNull { it.value.size }
        ?.key
        ?: error("Highlight menu builder not found")

    findClass {
        matcher {
            className(builder)
        }
    }.single()
}

/**
 * Adds an item to the highlight menu: `(String label, View.OnClickListener)`, the one the menu calls the most, as
 * the builder has another method of this signature.
 */
val highlightMenuAddMethod = findMethodDirect {
    val builder = highlightMenuBuilderClass().name
    highlightMenuMethod().instructions.mapNotNull { it.methodRef }
        .filter {
            it.declaredClassName == builder && it.returnTypeName == "void" &&
                it.paramTypeNames == listOf("java.lang.String", "android.view.View\$OnClickListener")
        }
        .groupBy { it.descriptor }.values.maxBy { it.size }.first()
}

/**
 * The carousel position of a post, the int field of the post state read by the getter of `EditMediaInfoFragment`
 * returning the current media, like Piko's decoder:
 * `iget-object state; if-eqz; iget position, state; ...; invoke-static (List, int)Media`.
 *
 * 452 reads `List.get(position)` and converts the element separately instead of calling the static
 * `(List, int) -> Media` helper, so either tail is accepted once the state/position pattern matches.
 */
val mediaPositionField = findFieldDirect {
    fun MethodData.positionField(): FieldData? {
        val instructions = instructions.take(20)
        if (instructions.size < 10 || instructions[1].opcodeEnum != Opcode.IF_EQZ) return null
        val state = instructions[0].takeIf { it.opcodeEnum == Opcode.IGET_OBJECT }?.fieldRef ?: return null
        val position = instructions[2].takeIf { it.opcodeEnum == Opcode.IGET }?.fieldRef ?: return null
        if (position.declaredClassName != state.typeName || position.typeName != "int") return null

        val staticMedia = instructions[9].takeIf { it.opcodeEnum == Opcode.INVOKE_STATIC }?.methodRef
        val hasStaticMedia = staticMedia != null &&
            staticMedia.paramTypeNames == listOf("java.util.List", "int") &&
            staticMedia.returnTypeName == MEDIA_CLASS
        val hasListGet = instructions.any { instruction ->
            instruction.opcodeEnum == Opcode.INVOKE_INTERFACE &&
                instruction.methodRef?.declaredClassName == "java.util.List" &&
                instruction.methodRef?.name == "get" &&
                instruction.methodRef?.paramTypeNames == listOf("int")
        }
        if (!hasStaticMedia && !hasListGet) return null

        return position
    }
    findMethod {
        matcher {
            declaredClass("instagram.features.creation.fragment.EditMediaInfoFragment")
            returnType = "java.lang.String"
        }
    }.mapNotNull { it.positionField() }.distinctBy { it.descriptor }.single()
}

private const val VIDEO_VERSION_CLASS = "com.instagram.api.schemas.VideoVersionIntf"

/**
 * The getter of a value of a video version, called right after its key in the method mapping a video version to
 * JSON, like Piko's video data decoder.
 */
private fun DexKitBridge.videoVersionGetter(key: String): MethodData {
    val mapper = findMethodWithStrings(listOf("fallback", "height", "type", "url", "url_expiration_timestamp_us", "width")) {
        returnType = "java.util.Map"
        paramTypes(VIDEO_VERSION_CLASS)
    }.single { it.isStatic }
    val instructions = mapper.instructions
    val keyIndex = instructions.indexOfFirst { it.string == key }
    return instructions[keyIndex + 1].methodRef!!.also {
        require(it.declaredClassName == VIDEO_VERSION_CLASS && it.paramTypeNames.isEmpty())
    }
}

val videoVersionWidthGetter = findMethodDirect { videoVersionGetter("width") }

val videoVersionHeightGetter = findMethodDirect { videoVersionGetter("height") }

/**
 * Builds the dialog of Instagram's dialog builder, the native dialog Piko shows its download options in.
 */
val igDialogBuildMethod = findMethodDirect {
    findMethodWithStrings(listOf("DialogBuilder - Activity is finishing")) {
        returnType = "android.app.Dialog"
    }.single()
}

/**
 * Shows a dialog of React Native with the dialog builder, the calls of its options name the builder methods.
 */
private fun DexKitBridge.igDialogHelperMethod() = findMethod {
    matcher {
        declaredClass("com.instagram.react.modules.base.IgReactDialogModule")
        name = "showDialogHelper"
    }
}.single()

/**
 * The builder method called after the option [key] is read.
 */
private fun DexKitBridge.igDialogOptionMethod(key: String): MethodData {
    val builder = igDialogBuildMethod().declaredClassName
    val instructions = igDialogHelperMethod().instructions
    val keyIndex = instructions.indexOfFirst { it.string == key }
    require(keyIndex >= 0) { "$key not found" }
    return instructions.drop(keyIndex + 1).first {
        it.opcodeEnum == Opcode.INVOKE_VIRTUAL && it.methodRef?.declaredClassName == builder
    }.methodRef!!
}

val igDialogCancelableMethod = findMethodDirect { igDialogOptionMethod("IS_CANCELABLE") }

val igDialogCanceledOnTouchOutsideMethod = findMethodDirect { igDialogOptionMethod("CANCELED_ON_TOUCH_OUTSIDE") }

val igDialogNegativeButtonMethod = findMethodDirect { igDialogOptionMethod("NEGATIVE_BUTTON_TEXT") }

/**
 * The title of the dialog builder, the first builder field set by the React Native dialog.
 */
val igDialogTitleField = findFieldDirect {
    val builder = igDialogBuildMethod().declaredClassName
    igDialogHelperMethod().instructions.first {
        it.opcodeEnum == Opcode.IPUT_OBJECT && it.fieldRef?.declaredClassName == builder
    }.fieldRef!!
}

/**
 * The flag rounding the bottom corners of the menu of a dialog. The builder rounds the last menu row even when
 * buttons follow it, so like Piko it is cleared. It is set right after the flag of the top corners:
 * `const/4 v, 1; iput-boolean v, adapter, top; iput-boolean v, adapter, bottom`.
 */
val igDialogMenuBottomCornersField = findFieldDirect {
    val buildMethod = igDialogBuildMethod()
    buildMethod.instructions.windowed(3).single { (constant, top, bottom) ->
        val topField = top.fieldRef
        val bottomField = bottom.fieldRef
        constant.opcodeEnum == Opcode.CONST_4 && constant.literal == 1L &&
            top.opcodeEnum == Opcode.IPUT_BOOLEAN && bottom.opcodeEnum == Opcode.IPUT_BOOLEAN &&
            topField != null && bottomField != null &&
            top.registerCount == 2 && bottom.registerCount == 2 &&
            top.register(0) == constant.register(0) && bottom.register(0) == top.register(0) &&
            bottom.register(1) == top.register(1) &&
            topField.declaredClassName == bottomField.declaredClassName &&
            topField.declaredClassName != buildMethod.declaredClassName && topField.name != bottomField.name
    }[2].fieldRef!!
}
