package io.github.nexfusion.morphe.youtube.ad

import android.view.View
import android.view.ViewGroup
import app.morphe.extension.shared.Logger
import app.morphe.extension.shared.ResourceUtils
import app.morphe.extension.youtube.patches.components.AdsFilter
import io.github.nexfusion.compat.XC_MethodHook
import io.github.nexfusion.compat.XposedHelpers
import io.github.nexfusion.morphe.shared.ad.HideFullscreenAds
import io.github.nexfusion.morphe.shared.misc.litho.filter.addLithoFilter
import io.github.nexfusion.morphe.shared.misc.proto.hookElement
import io.github.nexfusion.morphe.shared.misc.settings.preference.SwitchPreference
import io.github.nexfusion.morphe.youtube.layout.hide.general.HideHorizontalShelves
import io.github.nexfusion.morphe.youtube.misc.contexthook.Endpoint
import io.github.nexfusion.morphe.youtube.misc.contexthook.addOSNameHook
import io.github.nexfusion.morphe.youtube.misc.contexthook.clientContextHookPatch
import io.github.nexfusion.morphe.youtube.misc.engagement.EngagementPanelHook
import io.github.nexfusion.morphe.youtube.misc.engagement.addEngagementPanelIdHook
import io.github.nexfusion.morphe.youtube.misc.litho.filter.LithoFilter
import io.github.nexfusion.morphe.youtube.misc.playservice.VersionCheck
import io.github.nexfusion.morphe.youtube.misc.proto.elementProtoParserHookPatch
import io.github.nexfusion.morphe.youtube.misc.settings.PreferenceScreen
import io.github.nexfusion.patch

val HideAds = patch(
    name = "Hide ads",
    description = "Adds options to hide general ads, Premium promotions and video ads.",
) {
    dependsOn(
        LithoFilter,
        EngagementPanelHook,
        HideHorizontalShelves,
        elementProtoParserHookPatch,

        HideFullscreenAds(PreferenceScreen.ADS),
        VersionCheck,
        clientContextHookPatch,
    )

    PreferenceScreen.ADS.addPreferences(
        SwitchPreference("morphe_hide_creator_store_shelf"),
//        SwitchPreference("morphe_hide_end_screen_store_banner"),
        SwitchPreference("morphe_hide_general_ads"),
        SwitchPreference("morphe_hide_merchandise_banners"),
        SwitchPreference("morphe_hide_paid_promotion_labels"),
        SwitchPreference("morphe_hide_player_popup_ads"),
        SwitchPreference("morphe_hide_self_sponsor_ads"),
        SwitchPreference("morphe_hide_shopping_links"),
        SwitchPreference("morphe_hide_video_ads"),
        SwitchPreference("morphe_hide_youtube_premium_promotions"),
    )

    addLithoFilter(AdsFilter())
    addEngagementPanelIdHook(AdsFilter::hidePlayerPopupAds)

    // Hide Shorts ads via Reel endpoint OS spoofing
    addOSNameHook(Endpoint.REEL) { osName ->
        AdsFilter.hideShortsAds(osName ?: "")
    }

    // Hide video ads

    setOf(
        LoadVideoAdsFingerprint,
        PlayerBytesAdLayoutFingerprint,
    ).forEach { fingerprint ->
        fingerprint.hookMethod {
            before {
                if (AdsFilter.hideVideoAds())
                    it.result = null
            }
        }
    }

    // Hide YouTube Premium promotions

    hookElement(AdsFilter::hideStatementBanner)

    // TODO: Hide end screen store banner

    // Hide get premium
    GetPremiumViewFingerprint.hookMethod {
        after {
            if (AdsFilter.hideGetPremiumView()) {
                val view = it.thisObject as View
                XposedHelpers.callMethod(view, "setMeasuredDimension", 0, 0)
            }
        }
    }

    // Hide player overlay view. This can be hidden with a regular litho filter
    // but an empty space remains.

    PlayerOverlayTimelyShelfFingerprint.hookMethod {
        val playerOverlayEventClass = ::PlayerOverlayEventType.clazz
        val playerOverlayIdField = ::PlayerOverlayIdField.field
        before {
            val obj = it.args[0]
            if (playerOverlayEventClass.isInstance(obj)) {
                val id = playerOverlayIdField.get(obj) as String

                if (!AdsFilter.allowAds(id == "player_overlay_timely_shelf"))
                    it.result = null
            }
        }
    }

    // Hide ad views
    val adAttributionId = ResourceUtils.getIdIdentifier("ad_attribution")

    XposedHelpers.findAndHookMethod(
        View::class.java.name,
        lpparam.classLoader,
        "findViewById",
        Int::class.java.name,
        object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                if (param.args[0] == adAttributionId) {
                    Logger.printDebug { "Hide Ad Attribution View" }
                    AdsFilter.hideAdAttributionView(param.result as View)
                }
            }
        })

    // Dynamic feed sponsored card and ad view suppressor
    fun isSponsoredText(text: CharSequence?): Boolean {
        if (text == null) return false
        val s = text.toString()
        return s.startsWith("Sponsored", ignoreCase = true) ||
            s.contains("Sponsored ·", ignoreCase = true) ||
            s.contains("Sponsored -", ignoreCase = true)
    }

    fun hideSponsoredCardTree(view: View) {
        val cd = view.contentDescription
        if (isSponsoredText(cd)) {
            view.visibility = View.GONE
            val lp = view.layoutParams
            if (lp != null && lp.height != 0) {
                lp.height = 0
                view.layoutParams = lp
            }
            var parent = view.parent
            while (parent is View) {
                val pName = parent.javaClass.name
                if (pName.contains("RecyclerView") || pName.contains("ViewPager")) {
                    break
                }
                parent.visibility = View.GONE
                val plp = parent.layoutParams
                if (plp != null && plp.height != 0) {
                    plp.height = 0
                    parent.layoutParams = plp
                }
                parent = parent.parent
            }
        }
    }

    runCatching {
        XposedHelpers.findAndHookMethod(
            View::class.java.name,
            lpparam.classLoader,
            "setContentDescription",
            CharSequence::class.java.name,
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    if (!AdsFilter.hideAds()) return
                    val view = param.thisObject as? View ?: return
                    hideSponsoredCardTree(view)
                }
            }
        )
    }

    runCatching {
        XposedHelpers.findAndHookMethod(
            ViewGroup::class.java.name,
            lpparam.classLoader,
            "addView",
            View::class.java.name,
            Int::class.javaPrimitiveType,
            ViewGroup.LayoutParams::class.java.name,
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    if (!AdsFilter.hideAds()) return
                    val child = param.args[0] as? View ?: return
                    if (isSponsoredText(child.contentDescription)) {
                        child.visibility = View.GONE
                        val lp = param.args[2] as? ViewGroup.LayoutParams
                        if (lp != null) {
                            lp.height = 0
                        }
                    }
                }
            }
        )
    }

    // TODO Hide paid promotion label in miniplayer

}
