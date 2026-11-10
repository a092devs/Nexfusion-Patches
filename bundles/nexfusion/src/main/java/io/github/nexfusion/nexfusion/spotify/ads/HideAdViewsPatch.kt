package io.github.nexfusion.nexfusion.spotify.ads

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import io.github.nexfusion.compat.XposedHelpers
import io.github.nexfusion.PatcherLog
import io.github.nexfusion.hookMethod
import io.github.nexfusion.patch
import io.github.nexfusion.nexfusion.extension.spotify.HideAdViewsPatch
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

private val adClassMarkers = listOf(
    "adsinternal", "brandad", "videoad", "audioad", "adslot", "countdownbar", "displayad",
)

/**
 * Resource entry names of the ad views of recent Spotify builds (checked against 9.1.90 - 9.1.94+):
 *
 * - `ad_container` and `ad_info_row` belong to the ad player UI (the ad canvas and the ad overlay).
 * - `ad_context_header_stub` and `ad_context_header_text` are the "Advertisement" header shown in
 *   the now playing view while an ad plays (the stub is a ViewStub, the text view is its content).
 *
 * The remaining names are used by older builds.
 */
private val adResourceMarkers = listOf(
    "ad_container", "ad_info_row", "ad_context_header_stub", "ad_context_header_text",
    "audio_ad", "video_ad", "banner_ad", "ad_nudge", "brand_ad",
)

private val adDescriptionMarkers = listOf("advertisement", "werbung")

/** Caches the per-class marker check, since the class name of a view never changes. */
private val adClassNameCache = ConcurrentHashMap<Class<*>, Boolean>()

private fun isAdView(view: View): Boolean {
    val matchesClass = adClassNameCache.getOrPut(view.javaClass) {
        val className = view.javaClass.name.lowercase(Locale.ROOT)
        adClassMarkers.any { className.contains(it) }
    }
    if (matchesClass) return true

    val id = view.id
    if (id != View.NO_ID) {
        val resourceName = runCatching {
            view.resources.getResourceEntryName(id).lowercase(Locale.ROOT)
        }.getOrNull() ?: ""
        if (adResourceMarkers.any { resourceName.contains(it) }) return true
    }

    val description = view.contentDescription?.toString() ?: return false
    if (description.isEmpty()) return false
    val lowerDescription = description.lowercase(Locale.ROOT)
    return lowerDescription == "ad" || adDescriptionMarkers.any { lowerDescription.contains(it) }
}

private fun hideAdView(view: View) {
    if (!isAdView(view)) return
    PatcherLog.info { "Hide ad views: hiding ${view.javaClass.name}" }
    HideAdViewsPatch.hideView(view)
}

private fun hideAdViewsInTree(view: View) {
    try {
        hideAdView(view)
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) hideAdViewsInTree(view.getChildAt(i))
        }
    } catch (t: Throwable) {
        PatcherLog.info { "Hide ad views: view tree sweep failed: $t" }
    }
}

val HideAdViews = patch(
    name = "Hide ad views",
    description = "Hides ad banners, display ads and the ad player.",
    use = false,
) {
    HideAdViewsPatch.AD_VIEW_CLASSES.forEach { className ->
        val adView = XposedHelpers.findClassIfExists(className, classLoader)
        if (adView == null) {
            PatcherLog.info { "Hide ad views: ad view class is not present in this Spotify version: $className" }
            return@forEach
        }

        XposedHelpers.findMethodExactIfExists(adView, "onMeasure", Int::class.java, Int::class.java)?.hookMethod {
            before { param ->
                HideAdViewsPatch.hideView(param.thisObject as View)
                param.result = null
            }
        }
        XposedHelpers.findMethodExactIfExists(adView, "onAttachedToWindow")?.hookMethod {
            after { param -> HideAdViewsPatch.hideView(param.thisObject as View) }
        }
    }

    // Ad views that are not covered by known classes are found by their class, resource and
    // content description names. The ad player UI is created while an ad plays, so views are
    // checked when they are attached to a window in addition to the activity tree sweeps below.
    View::class.java.getDeclaredMethod("onAttachedToWindow").hookMethod {
        after { param -> hideAdView(param.thisObject as View) }
    }

    Activity::class.java.getDeclaredMethod("onPostResume").hookMethod {
        after { param ->
            (param.thisObject as Activity).window?.decorView?.let(::hideAdViewsInTree)
        }
    }
    Activity::class.java.getDeclaredMethod("onCreate", Bundle::class.java).hookMethod {
        after { param ->
            val decorView = (param.thisObject as Activity).window?.decorView ?: return@after
            decorView.post { hideAdViewsInTree(decorView) }
        }
    }
}
