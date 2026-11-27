package io.github.a092devs.nexfusion.morphe.music.layout.buttons.navigation

import android.app.Activity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import app.morphe.extension.music.patches.MusicGlassNavigationBar
import app.morphe.extension.music.patches.NavigationBarPatch as ExtNavBar
import app.morphe.extension.music.settings.preference.MusicGlassNavigationBarPreference
import app.morphe.extension.shared.Logger
import app.morphe.extension.shared.ResourceType as ExtResourceType
import app.morphe.extension.shared.ResourceUtils
import io.github.a092devs.nexfusion.hookMethod
import io.github.a092devs.nexfusion.morphe.AccessFlags
import io.github.a092devs.nexfusion.morphe.Fingerprint
import io.github.a092devs.nexfusion.morphe.InstructionLocation.MatchAfterImmediately
import io.github.a092devs.nexfusion.morphe.InstructionLocation.MatchAfterWithin
import io.github.a092devs.nexfusion.morphe.Opcode
import io.github.a092devs.nexfusion.morphe.ResourceType
import io.github.a092devs.nexfusion.morphe.anyInstruction
import io.github.a092devs.nexfusion.morphe.checkCast
import io.github.a092devs.nexfusion.morphe.fieldAccess
import io.github.a092devs.nexfusion.morphe.findMethodDirect
import io.github.a092devs.nexfusion.morphe.literal
import io.github.a092devs.nexfusion.morphe.methodCall
import io.github.a092devs.nexfusion.morphe.music.misc.settings.PreferenceScreen
import io.github.a092devs.nexfusion.morphe.music.misc.settings.SettingsHook
import io.github.a092devs.nexfusion.morphe.newInstance
import io.github.a092devs.nexfusion.morphe.opcode
import io.github.a092devs.nexfusion.morphe.resourceLiteral
import io.github.a092devs.nexfusion.morphe.shared.misc.settings.preference.NonInteractivePreference
import io.github.a092devs.nexfusion.morphe.shared.misc.settings.preference.PreferenceScreenPreference
import io.github.a092devs.nexfusion.morphe.shared.misc.settings.preference.SwitchPreference
import io.github.a092devs.nexfusion.morphe.string
import io.github.a092devs.nexfusion.patch
import io.github.a092devs.nexfusion.scopedHook
import java.lang.reflect.Modifier

internal object TabLayoutTextFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf("L"),
    filters = listOf(
        anyInstruction(
            string("FEmusic_search"),
            string("FEsearch"),
            newInstance("Ljava/util/ArrayList;")
        ),
        resourceLiteral(type = ResourceType.ID, name = "text1"),
        methodCall(
            smali = "Landroid/view/View;->findViewById(I)Landroid/view/View;",
            location = MatchAfterWithin(5)
        ),
        checkCast(type = "Landroid/widget/TextView;", location = MatchAfterWithin(5)),
        methodCall(opcode = Opcode.INVOKE_STATIC, returnType = "L", parameters = listOf("I")),
        methodCall(name = "getVisibility")
    )
)

internal object MusicActivityOnCreateOptionsMenuFingerprint : Fingerprint(
    definingClass = "Lcom/google/android/apps/youtube/music/activities/MusicActivity;",
    name = "onCreateOptionsMenu",
    returnType = "Z",
    parameters = listOf("Landroid/view/Menu;"),
    filters = listOf(
        resourceLiteral(ResourceType.MENU, "main_menu"),
        resourceLiteral(ResourceType.ID, "action_search")
    )
)

internal object ToolbarServerButtonsFingerprint : Fingerprint(
    returnType = "V",
    filters = listOf(
        methodCall(smali = "Ljava/util/List;->isEmpty()Z"),
        methodCall(smali = "Landroid/view/Menu;->clear()V"),
        methodCall(name = "forEach", location = MatchAfterWithin(10))
    )
)

internal object FloatingMiniplayerFeatureFlagFingerprint : Fingerprint(
    filters = listOf(literal(45823962L))
)

internal object MediaRouteButtonFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PRIVATE, AccessFlags.FINAL),
    returnType = "Z",
    strings = listOf("MediaRouteButton")
)

internal object PlayerOverlayChipFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "L",
    filters = listOf(resourceLiteral(ResourceType.ID, "player_overlay_chip"))
)

internal object HistoryMenuItemFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf("Landroid/view/Menu;"),
    filters = listOf(
        resourceLiteral(ResourceType.ID, "history_menu_item"),
        methodCall(smali = "Landroid/view/MenuItem;->setVisible(Z)Landroid/view/MenuItem;"),
        opcode(Opcode.RETURN_VOID, MatchAfterImmediately()),
    ),
    custom = {
        declaredClass {
            methodCount(4..5)
        }
    }
)

internal object HistoryMenuItemOfflineTabFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf("Landroid/view/Menu;"),
    filters = listOf(
        resourceLiteral(ResourceType.ID, "offline_settings_menu_item"),
        resourceLiteral(ResourceType.ID, "history_menu_item"),
        methodCall(smali = "Landroid/view/MenuItem;->setVisible(Z)Landroid/view/MenuItem;"),
    )
)

internal object SearchActionViewFingerprint : Fingerprint(
    definingClass = "Lcom/google/android/apps/youtube/music/search/SearchActionProvider;",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "Landroid/view/View;",
    parameters = listOf(),
    filters = listOf(resourceLiteral(ResourceType.LAYOUT, "search_button"))
)

internal object SearchVoiceButtonsFingerprint : Fingerprint(
    filters = listOf(
        resourceLiteral(ResourceType.ID, "voice_search"),
        opcode(Opcode.MOVE_RESULT_OBJECT, location = MatchAfterWithin(3)),
        resourceLiteral(ResourceType.ID, "sound_search"),
        opcode(Opcode.MOVE_RESULT_OBJECT, location = MatchAfterWithin(3))
    )
)

internal object PlayerAskMusicButtonFingerprint : Fingerprint(
    name = "<init>",
    returnType = "V",
    filters = listOf(resourceLiteral(ResourceType.ID, "ask_music_global_entry_button"))
)

internal object PlayerAskMusicButtonBindFingerprint : Fingerprint(
    classFingerprint = PlayerAskMusicButtonFingerprint,
    returnType = "V",
    parameters = listOf("L"),
    filters = listOf(methodCall(smali = "Landroid/widget/Space;->setVisibility(I)V"))
)

internal object TopBarMenuItemImageViewFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "Landroid/view/View;",
    parameters = listOf(),
    filters = listOf(
        resourceLiteral(ResourceType.ID, "top_bar_menu_item_image_view"),
        opcode(Opcode.MOVE_RESULT_OBJECT),
        fieldAccess(
            opcode = Opcode.IGET_OBJECT,
            definingClass = "this",
            type = "Lj$/util/Optional;"
        )
    )
)

private val navEnumLookupMethod = findMethodDirect {
    TabLayoutTextFingerprint.instructionMatches[
        TabLayoutTextFingerprint.instructionMatches.size - 2
    ].instruction.methodRef!!
}

private val askMusicRendererCallerMethod = findMethodDirect {
    val bindData = PlayerAskMusicButtonBindFingerprint()
    val rendererType = "L${bindData.paramTypeNames.first().replace('.', '/')};"
    val bindClass = "L${bindData.className.replace('.', '/')};"
    Fingerprint(
        filters = listOf(
            methodCall(opcode = Opcode.INVOKE_STATIC, returnType = rendererType),
            opcode(Opcode.MOVE_RESULT_OBJECT, MatchAfterImmediately()),
            methodCall(definingClass = bindClass, name = bindData.name, returnType = "V", parameters = listOf(rendererType), location = MatchAfterWithin(5))
        )
    ).instructionMatches.first().instruction.methodRef!!
}

val NavigationBar = patch(
    name = "Navigation bar",
    description = "Adds options to hide and change the bottom navigation bar (such as the Upgrade button) " +
        "and the upper navigation toolbar.",
    use = false,
) {
    dependsOn(SettingsHook)

    PreferenceScreen.GENERAL.addPreferences(
        PreferenceScreenPreference(
            key = "morphe_navigation_bar_screen",
            sorting = PreferenceScreenPreference.Sorting.UNSORTED,
            preferences = setOf(
                SwitchPreference("morphe_music_hide_navigation_bar_home_button"),
                SwitchPreference("morphe_music_hide_navigation_bar_samples_button"),
                SwitchPreference("morphe_music_hide_navigation_bar_explore_button"),
                SwitchPreference("morphe_music_hide_navigation_bar_library_button"),
                SwitchPreference("morphe_music_hide_navigation_bar_upgrade_button"),
                SwitchPreference("morphe_music_hide_navigation_bar", titleKey = "morphe_hide_navigation_bar_title"),
                SwitchPreference("morphe_music_hide_navigation_bar_labels", titleKey = "morphe_hide_navigation_button_labels_title"),
                SwitchPreference("morphe_music_navigation_bar_glass", titleKey = "morphe_navigation_bar_glass_title", summary = true),
                NonInteractivePreference(
                    key = "morphe_music_navigation_bar_glass_height",
                    titleKey = "morphe_music_navigation_bar_glass_size_title",
                    summaryKey = "morphe_music_navigation_bar_glass_size_summary",
                    tag = MusicGlassNavigationBarPreference::class.java,
                    selectable = true
                ),
            )
        ),
        PreferenceScreenPreference(
            key = "morphe_toolbar_screen",
            sorting = PreferenceScreenPreference.Sorting.UNSORTED,
            preferences = setOf(
                SwitchPreference("morphe_music_hide_activity_button"),
                SwitchPreference("morphe_music_hide_ask_music_button"),
                SwitchPreference("morphe_music_hide_cast_button", titleKey = "morphe_hide_cast_button_title"),
                SwitchPreference("morphe_music_hide_history_button"),
                SwitchPreference(
                    "morphe_music_hide_samples_button",
                    titleKey = "morphe_music_hide_navigation_bar_samples_button_title"
                ),
                SwitchPreference("morphe_music_hide_search_button", titleKey = "morphe_hide_search_button_title"),
                SwitchPreference("morphe_music_hide_sound_search_button"),
                SwitchPreference("morphe_music_hide_voice_search_button"),
            )
        )
    )

    // Bottom navigation bar: hide tabs, labels, and notify glass nav bar.
    try {
        val findViewById = View::class.java.getMethod("findViewById", Int::class.javaPrimitiveType)
        val getVisibility = View::class.java.getMethod("getVisibility")
        val text1Id = ResourceUtils.getIdentifier(ExtResourceType.ID, "text1")

        TabLayoutTextFingerprint.hookMethod(
            scopedHook(
                findViewById to {
                    after { param ->
                        if (innerDepth == 0 && (param.args[0] as? Int) == text1Id) {
                            (param.result as? TextView)?.let { ExtNavBar.hideNavigationLabel(it) }
                        }
                    }
                },
                getVisibility to {
                    before { param ->
                        if (innerDepth == 0) {
                            val view = param.thisObject as? View ?: return@before
                            ExtNavBar.hideNavigationButton(view)
                            MusicGlassNavigationBar.navigationTabCreated(view)
                        }
                    }
                }
            )
        )

        ::navEnumLookupMethod.method.hookMethod {
            after { param ->
                (param.result as? Enum<*>)?.let { ExtNavBar.setLastAppNavigationEnum(it) }
            }
        }
    } catch (t: Throwable) {
        Logger.printException({ "NavigationBar: TabLayoutText hook failed" }, t)
    }

    // Floating miniplayer feature flag.
    runCatching {
        FloatingMiniplayerFeatureFlagFingerprint.hookMethod {
            after { param ->
                val orig = param.result as? Boolean ?: return@after
                param.result = MusicGlassNavigationBar.useFloatingMiniplayer(orig)
            }
        }
    }.onFailure {
        Logger.printException({ "NavigationBar: FloatingMiniplayer hook failed" }, it)
    }

    // History menu item visibility.
    val historyMenuItemId = ResourceUtils.getIdentifier(ExtResourceType.ID, "history_menu_item")
    val hookHistoryMenu: (Any?) -> Unit = { menuObj ->
        val menu = menuObj as? android.view.Menu
        val item = menu?.findItem(historyMenuItemId)
        if (item != null) {
            item.isVisible = ExtNavBar.hideHistoryButton(item.isVisible)
        }
    }
    runCatching {
        HistoryMenuItemFingerprint.hookMethod {
            after { param -> hookHistoryMenu(param.args.firstOrNull()) }
        }
    }.onFailure {
        Logger.printException({ "NavigationBar: HistoryMenuItem hook failed" }, it)
    }
    runCatching {
        HistoryMenuItemOfflineTabFingerprint.hookMethod {
            after { param -> hookHistoryMenu(param.args.firstOrNull()) }
        }
    }.onFailure {
        Logger.printException({ "NavigationBar: HistoryMenuItemOfflineTab hook failed" }, it)
    }

    // Cast & Search buttons in toolbar.
    runCatching {
        val findViewById = View::class.java.getMethod("findViewById", Int::class.javaPrimitiveType)
        val playerOverlayChipId = ResourceUtils.getIdentifier(ExtResourceType.ID, "player_overlay_chip")
        PlayerOverlayChipFingerprint.hookMethod(
            scopedHook(findViewById) {
                after { param ->
                    if (innerDepth == 0 && (param.args[0] as? Int) == playerOverlayChipId) {
                        (param.result as? View)?.let { ExtNavBar.hideCastButton(it) }
                    }
                }
            }
        )
    }.onFailure {
        Logger.printException({ "NavigationBar: PlayerOverlayChip hook failed" }, it)
    }

    runCatching {
        SearchActionViewFingerprint.hookMethod {
            after { param ->
                (param.result as? View)?.let { ExtNavBar.hideSearchButton(it) }
            }
        }
    }.onFailure {
        Logger.printException({ "NavigationBar: SearchActionView hook failed" }, it)
    }

    // TopBarMenuItem (activity / Ask Music / Samples buttons).
    runCatching {
        val topBarClass = TopBarMenuItemImageViewFingerprint.method.declaringClass
        val optionalField = topBarClass.declaredFields.firstOrNull {
            it.type.name == "j$.util.Optional" || it.type.name == "java.util.Optional"
        }?.apply { isAccessible = true }

        TopBarMenuItemImageViewFingerprint.hookMethod {
            after { param ->
                val view = param.result as? View ?: return@after
                val opt = optionalField?.get(param.thisObject)
                val isPresent = opt?.javaClass?.getMethod("isPresent")?.invoke(opt) as? Boolean ?: false
                if (isPresent) {
                    ExtNavBar.hideActivityButton(view)
                }
            }
        }
    }.onFailure {
        Logger.printException({ "NavigationBar: TopBarMenuItem hook failed" }, it)
    }

    // Search voice & sound search buttons.
    runCatching {
        val findViewById = View::class.java.getMethod("findViewById", Int::class.javaPrimitiveType)
        val voiceSearchId = ResourceUtils.getIdentifier(ExtResourceType.ID, "voice_search")
        val soundSearchId = ResourceUtils.getIdentifier(ExtResourceType.ID, "sound_search")
        SearchVoiceButtonsFingerprint.hookMethod(
            scopedHook(findViewById) {
                after { param ->
                    if (innerDepth != 0) return@after
                    val id = param.args[0] as? Int ?: return@after
                    val view = param.result as? View ?: return@after
                    when (id) {
                        voiceSearchId -> ExtNavBar.hideVoiceSearchButton(view)
                        soundSearchId -> ExtNavBar.hideSoundSearchButton(view)
                    }
                }
            }
        )
    }.onFailure {
        Logger.printException({ "NavigationBar: SearchVoiceButtons hook failed" }, it)
    }

    // Cast button visibility in player.
    runCatching {
        val mediaRouteClass = MediaRouteButtonFingerprint.method.declaringClass
        mediaRouteClass.getDeclaredMethod("setVisibility", Int::class.javaPrimitiveType).hookMethod {
            before { param ->
                param.args[0] = ExtNavBar.hideCastButton(param.args[0] as Int)
            }
        }
    }.onFailure {
        Logger.printException({ "NavigationBar: MediaRouteButton hook failed" }, it)
    }

    // Player Ask Music button renderer.
    try {
        ::askMusicRendererCallerMethod.method.hookMethod {
            after { param ->
                param.result = ExtNavBar.hidePlayerAskMusicButton(param.result)
            }
        }
    } catch (t: Throwable) {
        Logger.printException({ "NavigationBar: PlayerAskMusicButton hook failed" }, t)
    }
}
