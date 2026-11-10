package io.github.nexfusion.nexfusion.spotify

import io.github.nexfusion.SkipTest
import io.github.nexfusion.morphe.AccessFlags
import io.github.nexfusion.morphe.Opcode
import io.github.nexfusion.morphe.fingerprint
import io.github.nexfusion.morphe.findClassDirect
import io.github.nexfusion.morphe.findFieldDirect
import io.github.nexfusion.morphe.findMethodDirect
import io.github.nexfusion.morphe.findMethodListDirect
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.query.enums.StringMatchType
import org.luckypray.dexkit.query.enums.UsingType
import org.luckypray.dexkit.result.FieldData
import org.luckypray.dexkit.result.MethodData
import java.lang.reflect.Modifier

// region Ads

val contextMenuViewModelClass = findClassDirect {
    runCatching {
        fingerprint { strings("ContextMenuViewModel(header=") }
    }.getOrElse {
        fingerprint {
            accessFlags(AccessFlags.CONSTRUCTOR)
            strings("ContextMenuViewModel cannot contain items with duplicate itemResId. id=")
            parameters("L", "Ljava/util/List;", "Z")
        }
    }.declaredClass!!
}

/**
 * The context menu item view model is `ViewModel(available, enabled, isPremiumUpsell, ...)`.
 *
 * Newer app versions no longer return the view model from a `getViewModel` method, so the
 * premium upsell filter skips itself when this field cannot be found.
 */
@get:SkipTest
val premiumUpsellField = findFieldDirect {
    val viewModelClass = findMethod {
        findFirst = true
        matcher { name = "getViewModel" }
    }.single().returnType!!
    viewModelClass.fields.filter { it.typeName == "boolean" }[1]
}

private fun DexKitBridge.sectionsGetters(structureClass: String): List<MethodData> {
    val classes = findClass {
        matcher { className(structureClass, StringMatchType.EndsWith) }
    }
    if (classes.isEmpty()) return emptyList()
    // The list is stored in the `sections_` protobuf field and read by its getter.
    return findMethod {
        searchInClass(classes)
        matcher { addUsingField { name("sections_") } }
    }.filterNot { it.isConstructor }
}

/** Getters of the sections list of the home page model. */
val homeSectionsGetters = findMethodListDirect {
    sectionsGetters("casita.v1.resolved.HomeStructure") + sectionsGetters("homeapi.proto.HomeStructure")
}

/** Getter of the sections list of the browse (search page) model. */
val browseSectionsGetters = findMethodListDirect {
    sectionsGetters("browsita.v1.resolved.BrowseStructure")
}

/**
 * Getter of the sections list of the now playing view scroll feed. The feed embeds a brand-ad
 * card below the lyrics card.
 *
 * Unlike Home/Browse, `NpvScrollStructure` also has an `addSection(Section)` method that reads
 * the backing `sections_` field, so the getter is pinned to the methods without parameters.
 */
val npvSectionsGetter = findMethodDirect {
    findMethod {
        matcher {
            declaredClass("scrollsita.v1.NpvScrollStructure", StringMatchType.EndsWith)
            addUsingField {
                usingType = UsingType.Read
                name = "sections_"
            }
            paramTypes(emptyList())
        }
    }.first { it.returnTypeName != "void" }
}

private fun DexKitBridge.pendragonRequests(requestClass: String) = findMethod {
    matcher {
        name = "apply"
        addInvoke {
            name = "<init>"
            declaredClass(requestClass, StringMatchType.EndsWith)
        }
    }
}

val pendragonRequestMethods = findMethodListDirect {
    pendragonRequests(".FetchMessageRequest") + pendragonRequests(".FetchMessageListRequest")
}

val embeddedAdPlaylistPropertiesConstructor = findMethodDirect {
    findClass {
        matcher { usingStrings("android-embeddedadplaylist", "embedded_ad_placement") }
    }.flatMap { classes ->
        classes.methods.filter { it.isConstructor && it.paramCount > 2 }
    }.single()
}

/**
 * Plugin entries of the now playing view are created with `(String name, Plugin plugin, boolean enabled)`.
 */
val npvPluginEntryConstructors = findMethodListDirect {
    findMethod {
        matcher { usingStrings("EmbeddedAdNpvPlugin") }
    }.flatMap { it.invokes }.filter { method ->
        val parameters = method.paramTypeNames
        method.isConstructor && parameters.size == 3 &&
            parameters[0] == "java.lang.String" && parameters[2] == "boolean"
    }.distinctBy { it.descriptor }
}

/**
 * Constructor of `com.spotify.esperanto.esperanto.CoroutineClientBase`, the base class of all
 * esperanto clients, taking the `CoroutineTransport`.
 */
val coroutineClientBaseConstructor = findMethodDirect {
    findMethod {
        matcher {
            declaredClass("com.spotify.esperanto.esperanto.CoroutineClientBase")
            name = "<init>"
            paramTypes("com.spotify.esperanto.esperanto.CoroutineTransport")
        }
    }.first()
}

// endregion

// region Layout

val navigationTabEnumClass = findClassDirect {
    findClass {
        matcher {
            superClass("java.lang.Enum")
            usingStrings(listOf("HOME", "SEARCH", "YOUR_LIBRARY", "PREMIUM", "CREATE"), StringMatchType.Equals)
        }
    }.single()
}

/**
 * The bottom navigation is created with `(Tab, Tab, Tab, Tab, Tab, int flags)`.
 */
val navigationTabSetConstructor = findMethodDirect {
    val tabEnum = navigationTabEnumClass().name
    val tab = findMethod {
        matcher {
            addUsingField {
                usingType = UsingType.Read
                declaredClass = tabEnum
            }
        }
    }.first { it.isConstructor && it.paramCount >= 4 }.declaredClass!!

    tab.methods.filter { method ->
        val parameters = method.paramTypeNames
        method.isConstructor && parameters.size == 6 &&
            parameters.subList(0, 5).all { it == tab.name } && parameters[5] == "int"
    }.single()
}

// endregion

// region Misc

val canBindAppWidgetPermissionMethod = findMethodDirect {
    findMethod {
        matcher {
            returnType = "boolean"
            usingStrings("android.permission.BIND_APPWIDGET")
        }
    }.first()
}

// endregion

// region Privacy

val shareCopyUrlMethod = findMethodDirect {
    findMethod {
        matcher {
            name = "invokeSuspend"
            returnType = "java.lang.Object"
            paramTypes("java.lang.Object")
            usingStrings("clipboard", "Spotify Link")
        }
    }.firstOrNull() ?: findMethod {
        matcher {
            name = "apply"
            returnType = "java.lang.Object"
            paramTypes("java.lang.Object")
            usingStrings("clipboard", "createNewSession failed")
        }
    }.first()
}

val formatAndroidShareSheetUrlMethod = findMethodDirect {
    findMethod {
        matcher {
            modifiers = Modifier.PUBLIC or Modifier.STATIC
            returnType = "java.lang.String"
            paramTypes(null, "java.lang.String")
            addUsingNumber('\n'.code)
        }
    }.firstOrNull() ?: findMethod {
        matcher {
            modifiers = Modifier.PUBLIC or Modifier.STATIC
            returnType = "java.lang.String"
            paramTypes(null, "java.lang.String")
            usingStrings("\n")
        }
    }.firstOrNull() ?: findMethod {
        matcher {
            modifiers = Modifier.PUBLIC
            returnType = "java.lang.String"
            paramTypes("com.spotify.share.social.sharedata.ShareData", "java.lang.String")
            addUsingNumber('\n'.code)
        }
    }.first()
}

// endregion

// region Settings

private const val SETTINGS_STATE_SERIAL_NAME = "com.spotify.settings.rxsettings.SettingsState"

/**
 * Locates the boolean field of the settings state class through its generated
 * kotlinx.serialization serializer.
 */
private fun DexKitBridge.settingsStateFieldBySerializer(elementName: String): FieldData {
    val serializerClass = findMethod { matcher { usingStrings(elementName) } }
        .mapNotNull { it.declaredClass }
        .distinct()
        .firstOrNull { candidate ->
            candidate.methods.any { method ->
                method.invokes.any { it.isConstructor && it.paramCount >= 10 }
            }
        } ?: error("SettingsState serializer not found")

    val descriptorInit = serializerClass.methods.first { it.usingStrings.contains(elementName) }
    val strings = descriptorInit.usingStrings
    val serialNameIndex = strings.indexOf(SETTINGS_STATE_SERIAL_NAME)
    require(serialNameIndex >= 0) { "Serial name not found in ${serializerClass.descriptor}" }
    val elementIndex = strings.indexOf(elementName) - serialNameIndex - 1
    require(elementIndex >= 0) { "Element $elementName not found in ${serializerClass.descriptor}" }

    val stateConstructor = serializerClass.methods
        .flatMap { it.invokes }
        .filter { it.isConstructor && it.paramCount >= 10 }
        .maxByOrNull { it.paramCount }
        ?: error("SettingsState constructor not found in ${serializerClass.descriptor}")
    val stateClass = stateConstructor.declaredClass
        ?: error("SettingsState class not found in ${stateConstructor.descriptor}")

    val fields = stateClass.fields.filter { !Modifier.isStatic(it.modifiers) }
    val field = fields.getOrNull(elementIndex)
        ?: error("No field #$elementIndex in ${stateClass.descriptor}")
    require(field.typeName == "boolean") {
        "Field ${field.descriptor} for $elementName is not boolean"
    }
    return field
}

/**
 * Legacy lookup through the generated `…showUnavailableTracks` property reference.
 */
private fun DexKitBridge.settingsStateFieldByPropertyReference(propertyName: String): FieldData {
    val propertyReferenceClass = findMethod { matcher { usingStrings("${propertyName}()Z") } }
        .mapNotNull { it.declaredClass }
        .distinct()
        .firstOrNull { candidate ->
            candidate.methods.any { method ->
                method.name == "get" &&
                    method.returnTypeName == "java.lang.Object" &&
                    method.paramTypeNames == listOf("java.lang.Object")
            }
        } ?: error("Property reference class for $propertyName not found")

    val getter = propertyReferenceClass.methods.first { method ->
        method.name == "get" &&
            method.returnTypeName == "java.lang.Object" &&
            method.paramTypeNames == listOf("java.lang.Object")
    }
    return getter.usingFields.single().field
}

/**
 * The boolean field backing Spotify's own "Show unplayable songs" setting (`show_unavailable_tracks`
 * in the server-synced settings state).
 */
val showUnavailableTracksField = findFieldDirect {
    runCatching { settingsStateFieldBySerializer("show_unavailable_tracks") }
        .recoverCatching { settingsStateFieldByPropertyReference("showUnavailableTracks") }
        .getOrThrow()
}

// endregion
