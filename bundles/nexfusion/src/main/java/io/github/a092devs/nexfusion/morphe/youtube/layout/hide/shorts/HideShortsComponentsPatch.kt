package io.github.a092devs.nexfusion.morphe.youtube.layout.hide.shorts

import android.view.View
import app.morphe.extension.shared.ResourceUtils.getIdIdentifier
import app.morphe.extension.youtube.patches.components.ShortsFilter
import io.github.a092devs.nexfusion.compat.XC_MethodReplacement
import io.github.a092devs.nexfusion.morphe.shared.misc.litho.filter.addLithoFilter
import io.github.a092devs.nexfusion.morphe.shared.misc.settings.preference.PreferenceCategory
import io.github.a092devs.nexfusion.morphe.shared.misc.settings.preference.SwitchPreference
import io.github.a092devs.nexfusion.morphe.youtube.layout.buttons.navigation.NavigationBar
import io.github.a092devs.nexfusion.morphe.youtube.misc.engagement.EngagementPanelHook
import io.github.a092devs.nexfusion.morphe.youtube.misc.litho.filter.LithoFilter
import io.github.a092devs.nexfusion.morphe.youtube.misc.litho.observer.LayoutReloadObserver
import io.github.a092devs.nexfusion.morphe.youtube.misc.playservice.VersionCheck
import io.github.a092devs.nexfusion.morphe.youtube.misc.playservice.is_21_42_or_greater
import io.github.a092devs.nexfusion.morphe.youtube.misc.settings.PreferenceScreen
import io.github.a092devs.nexfusion.patch
import org.luckypray.dexkit.wrap.DexMethod

val HideShortsComponents = patch(
    name = "Hide Shorts components",
    description = "Adds options to hide components related to Shorts.",
    use = false,
) {
    dependsOn(
        EngagementPanelHook,
        LayoutReloadObserver,
        LithoFilter,
        NavigationBar,
        VersionCheck
    )

    PreferenceScreen.addShortsPlayerPreferences(
        // Shorts player components.
        // Ideally each group should be ordered similar to how they appear in the UI

        // Vertical row of buttons on right side of the screen.
        // Like fountain may no longer be used by YT anymore.
        //SwitchPreference("morphe_hide_shorts_like_fountain"),
        SwitchPreference("morphe_disable_shorts_double_tap_to_like"),
        SwitchPreference("morphe_hide_shorts_like_button"),
        SwitchPreference("morphe_hide_shorts_comments_button", titleKey = "morphe_hide_comments_button_title"),
        SwitchPreference("morphe_hide_shorts_save_button", titleKey = "morphe_hide_save_button_title"),
        SwitchPreference("morphe_hide_shorts_share_button", titleKey = "morphe_hide_share_button_title"),
        SwitchPreference("morphe_hide_shorts_remix_button", titleKey = "morphe_hide_remix_button_title"),
//                SwitchPreference("morphe_hide_shorts_sound_button"),

        // Upper and middle area of the player.
        SwitchPreference("morphe_hide_shorts_join_button", titleKey = "morphe_hide_join_button_title"),
        SwitchPreference("morphe_hide_shorts_subscribe_button", titleKey = "morphe_hide_subscribe_button_title"),
        SwitchPreference("morphe_hide_shorts_gesture_hints"),
        SwitchPreference("morphe_hide_shorts_paused_overlay_buttons"),

        // Suggested actions.
        SwitchPreference("morphe_hide_shorts_preview_comment", titleKey = "morphe_hide_comments_preview_comment_title"),
        SwitchPreference("morphe_hide_shorts_save_sound_button"),
        SwitchPreference("morphe_hide_shorts_use_sound_button"),
        SwitchPreference("morphe_hide_shorts_use_template_button"),
        SwitchPreference("morphe_hide_shorts_upcoming_button"),
        SwitchPreference("morphe_hide_shorts_effect_button"),
        SwitchPreference("morphe_hide_shorts_green_screen_button"),
        SwitchPreference("morphe_hide_shorts_hashtag_button"),
        SwitchPreference("morphe_hide_shorts_live_preview"),
        SwitchPreference("morphe_hide_shorts_new_posts_button"),
        SwitchPreference("morphe_hide_shorts_shop_button", titleKey = "morphe_hide_shop_button_title"),
        SwitchPreference("morphe_hide_shorts_tagged_products"),
        SwitchPreference("morphe_hide_shorts_search_suggestions"),
        SwitchPreference("morphe_hide_shorts_super_thanks_button"),

        // Bottom of the screen.
        SwitchPreference("morphe_hide_shorts_ai_button"),
        SwitchPreference("morphe_hide_shorts_auto_dubbed_label"),
        SwitchPreference("morphe_hide_shorts_location_label"),
        SwitchPreference("morphe_hide_shorts_channel_bar", titleKey = "morphe_hide_channel_bar_title"),
        SwitchPreference("morphe_hide_shorts_info_panel"),
        SwitchPreference("morphe_hide_shorts_full_video_link_label"),
        SwitchPreference("morphe_hide_shorts_video_title", titleKey = "morphe_hide_video_title_title"),
        SwitchPreference("morphe_hide_shorts_sound_metadata_label"),
//                SwitchPreference("morphe_hide_shorts_navigation_bar", titleKey = "morphe_hide_navigation_bar_title"),
    )

    PreferenceScreen.SHORTS.addPreferences(
        PreferenceCategory(
            titleKey = "morphe_hide_shorts_category_title",
            preferences = setOf(
                SwitchPreference("morphe_hide_shorts_channel"),
                SwitchPreference("morphe_hide_shorts_home"),
                SwitchPreference("morphe_hide_shorts_search"),
                SwitchPreference("morphe_hide_shorts_subscriptions"),
                SwitchPreference("morphe_hide_shorts_video_description"),
                SwitchPreference("morphe_hide_shorts_history"),
                SwitchPreference("morphe_hide_shorts_notifications"),
            )
        )
    )

    addLithoFilter(ShortsFilter())

    // TODO Hide sound button.
    // TODO Hide the navigation bar.

    // region Disable experimental Shorts flags.

    // Flags might be present in earlier targets, but they are not found in 19.47.53.
    // If these flags are forced on, the experimental layout is still not used, and
    // it appears the features requires additional server side data to fully use.

    // Experimental Shorts player uses Android native buttons and not Litho,
    // and the layout is provided by the server.
    //
    // Since the buttons are native components and not Litho, it should be possible to
    // fix the RYD Shorts loading delay by asynchronously loading RYD and updating
    // the button text after RYD has loaded.
    // Skipped on 21.42+ where disabling the experimental Shorts player / RenderNext UI flags
    // causes Shorts to crash (ClassPropertiesConverter.nativeResolveStyle: FlexDirection must have an identifier value).
    if (!is_21_42_or_greater) {
        ShortsExperimentalPlayerFeatureFlagFingerprint.hookMethod(
            XC_MethodReplacement.returnConstant(
                false
            )
        )

        // Experimental UI renderer must also be disabled since it requires the
        // experimental Shorts player. If this is enabled but Shorts player
        // is disabled then the app crashes when the Shorts player is opened.
        RenderNextUIFeatureFlagFingerprint.hookMethod(XC_MethodReplacement.returnConstant(false))
    }
    // endregion

    // region Disable double-tap to like.

    DoubleTapToLikeLogicFingerprint.hookMethod {
        val doubleTapField = ::isDoubleTapField.field
        before {
            val originalValue = doubleTapField.get(it.thisObject) as Boolean
            val newValue = ShortsFilter.allowDoubleTapToLike(originalValue)
            doubleTapField.set(it.thisObject, newValue)
        }
    }

    // endregion


    // region Hide Shorts gesture hints.

    DexMethod("Landroid/view/ViewStub;->setInflatedId(I)V").hookMethod {
        val speedmaster_indicator_chip = getIdIdentifier("speedmaster_indicator_chip")
        val reel_speedmaster_edu_container = getIdIdentifier("reel_speedmaster_edu_container")
        after {
            val id = it.args[0]
            if (id == speedmaster_indicator_chip || id == reel_speedmaster_edu_container) {
                ShortsFilter.hideGestureHints(it.thisObject as View?)
            }
        }
    }

    // endregion
}
