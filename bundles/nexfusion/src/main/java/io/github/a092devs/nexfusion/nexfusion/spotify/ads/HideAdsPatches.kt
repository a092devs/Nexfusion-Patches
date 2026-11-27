package io.github.a092devs.nexfusion.nexfusion.spotify.ads

import android.media.MediaMetadata
import android.media.session.MediaSession
import io.github.a092devs.nexfusion.compat.XC_MethodHook
import io.github.a092devs.nexfusion.compat.XposedBridge
import io.github.a092devs.nexfusion.PatcherLog
import io.github.a092devs.nexfusion.hookMethod
import io.github.a092devs.nexfusion.patch
import io.github.a092devs.nexfusion.nexfusion.extension.spotify.BlockPopupAdsPatch
import io.github.a092devs.nexfusion.nexfusion.extension.spotify.HideAdSectionsPatch
import io.github.a092devs.nexfusion.nexfusion.extension.spotify.HideContextMenuUpsellsPatch
import io.github.a092devs.nexfusion.nexfusion.extension.spotify.HideNpvBrandAdsPatch
import io.github.a092devs.nexfusion.nexfusion.extension.spotify.HidePlayerAdsPatch
import io.github.a092devs.nexfusion.nexfusion.extension.spotify.MuteAudioAdsPatch
import io.github.a092devs.nexfusion.nexfusion.spotify.browseSectionsGetters
import io.github.a092devs.nexfusion.nexfusion.spotify.contextMenuViewModelClass
import io.github.a092devs.nexfusion.nexfusion.spotify.embeddedAdPlaylistPropertiesConstructor
import io.github.a092devs.nexfusion.nexfusion.spotify.homeSectionsGetters
import io.github.a092devs.nexfusion.nexfusion.spotify.npvPluginEntryConstructors
import io.github.a092devs.nexfusion.nexfusion.spotify.npvSectionsGetter
import io.github.a092devs.nexfusion.nexfusion.spotify.pendragonRequestMethods
import io.github.a092devs.nexfusion.nexfusion.spotify.premiumUpsellField
import java.lang.reflect.Constructor

val MuteAudioAds = patch(
    name = "Mute audio ads",
    description = "Mutes the music stream while an audio ad plays and restores the volume afterwards. " +
        "Enabling \"Device broadcast status\" in the Spotify settings improves the ad detection.",
    use = true,
) {
    MuteAudioAdsPatch.initialize(appContext)

    MediaSession::class.java.getMethod("setMetadata", MediaMetadata::class.java).hookMethod {
        after { MuteAudioAdsPatch.onMetadataChanged(it.args[0] as MediaMetadata?) }
    }
}

val HideAdSections = patch(
    name = "Hide ad sections",
    description = "Removes brand ad sections from the home, search and now playing pages.",
    use = true,
) {
    val homeGetters = ::homeSectionsGetters.dexMethodList
    val browseGetters = ::browseSectionsGetters.dexMethodList
    if (homeGetters.isEmpty() && browseGetters.isEmpty()) throw Exception("Page sections not found")

    homeGetters.forEach {
        it.hookMethod {
            after { param -> (param.result as? List<*>)?.let(HideAdSectionsPatch::removeHomeSections) }
        }
    }
    browseGetters.forEach {
        it.hookMethod {
            after { param -> (param.result as? List<*>)?.let(HideAdSectionsPatch::removeBrowseSections) }
        }
    }

    // The now playing view feed is a separate structure. Older builds may not have it, so its
    // absence must not fail the patch.
    runCatching {
        ::npvSectionsGetter.hookMethod {
            after { param ->
                (param.result as? List<*>)?.let(HideNpvBrandAdsPatch::removeNpvBrandAdSections)
            }
        }
    }.onFailure {
        PatcherLog.info(it) { "Now playing view feed not found, skipping its brand ad removal" }
    }
}

val HideContextMenuUpsells = patch(
    name = "Hide context menu upsells",
    description = "Removes \"Premium\" entries from the context menus of songs, albums and playlists.",
    use = true,
) {
    val isPremiumUpsell = runCatching { ::premiumUpsellField.field }.getOrNull()
    if (isPremiumUpsell == null) {
        PatcherLog.info { "The context menu item view model was not found, skipping the premium upsell removal" }
        return@patch
    }
    isPremiumUpsell.isAccessible = true

    XposedBridge.hookAllConstructors(::contextMenuViewModelClass.clazz, object : XC_MethodHook() {
        override fun beforeHookedMethod(param: MethodHookParam) {
            val parameterTypes = (param.method as Constructor<*>).parameterTypes
            parameterTypes.forEachIndexed { index, type ->
                if (type != List::class.java) return@forEachIndexed
                val items = param.args[index] as? List<*> ?: return@forEachIndexed
                param.args[index] = HideContextMenuUpsellsPatch.filterContextMenuItems(items, isPremiumUpsell)
            }
        }
    })
}

val BlockPopupAds = patch(
    name = "Block popup ads",
    description = "Blocks fullscreen promotions (\"Pendragon\" messages) shown when opening the app.",
    use = true,
) {
    val requests = ::pendragonRequestMethods.dexMethodList
    if (requests.isEmpty()) throw Exception("Pendragon message requests not found")

    requests.forEach {
        it.hookMethod {
            after { param ->
                // A failed call has no result; setting one would swallow the exception.
                if (param.hasThrowable()) return@after
                param.result = BlockPopupAdsPatch.replaceRequest(param.result)
            }
        }
    }
}

val HidePlaylistAds = patch(
    name = "Hide playlist ads",
    description = "Removes ads embedded into playlists.",
    use = true,
) {
    ::embeddedAdPlaylistPropertiesConstructor.hookMethod {
        before { param ->
            // Every ad placement parameter is an enum with a NONE constant.
            val parameterTypes = (param.method as Constructor<*>).parameterTypes
            parameterTypes.forEachIndexed { index, type ->
                HidePlayerAdsPatch.noneOf(type)?.let { param.args[index] = it }
            }
        }
    }
}

val HideVideoAds = patch(
    name = "Hide video ads",
    description = "Disables the video ad plugin of the now playing view.",
    use = true,
) {
    val entries = ::npvPluginEntryConstructors.dexMethodList
    if (entries.isEmpty()) throw Exception("Now playing view plugin entries not found")

    entries.forEach {
        it.hookMethod {
            before { param ->
                param.args[2] = HidePlayerAdsPatch.isPluginEnabled(param.args[0] as String?, param.args[2] as Boolean)
            }
        }
    }
}
