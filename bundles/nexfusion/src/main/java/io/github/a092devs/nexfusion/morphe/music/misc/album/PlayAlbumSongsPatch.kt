package io.github.a092devs.nexfusion.morphe.music.misc.album

import app.morphe.extension.music.patches.album.PlayAlbumSongsPatch as PlayAlbumSongsExt
import io.github.a092devs.nexfusion.hookMethod
import io.github.a092devs.nexfusion.morphe.AccessFlags
import io.github.a092devs.nexfusion.morphe.Fingerprint
import io.github.a092devs.nexfusion.morphe.StringComparisonType
import io.github.a092devs.nexfusion.morphe.literal
import io.github.a092devs.nexfusion.morphe.methodCall
import io.github.a092devs.nexfusion.morphe.setExtensionIsPatchIncluded
import io.github.a092devs.nexfusion.morphe.shared.misc.settings.preference.SwitchPreference
import io.github.a092devs.nexfusion.morphe.string
import io.github.a092devs.nexfusion.morphe.music.misc.settings.PreferenceScreen
import io.github.a092devs.nexfusion.patch

internal object WatchNextResponseModelConstructorFingerprint : Fingerprint(
    name = "<init>",
    returnType = "V",
    parameters = listOf("L"),
    filters = listOf(literal(51779735L))
)

internal object CommandResolverFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf("L", "Ljava/util/Map;"),
    filters = listOf(
        methodCall(definingClass = "Ljava/util/concurrent/Executor;", name = "execute"),
        string("Unknown command not resolved", StringComparisonType.CONTAINS)
    )
)

val PlayAlbumSongs = patch(
    name = "Play album songs",
    description = "Adds an option to play the songs of albums instead of their music videos.",
    use = false,
) {
    PreferenceScreen.MISC.addPreferences(
        SwitchPreference("morphe_music_play_album_songs", summary = true)
    )

    runCatching {
        setExtensionIsPatchIncluded(PlayAlbumSongsExt::class.java)
    }

    runCatching {
        // Implement extension bridge methods using reflection/proxies
        val extClass = PlayAlbumSongsExt::class.java
        io.github.a092devs.nexfusion.compat.XposedHelpers.findAndHookMethod(
            extClass, "serializeMessage", Any::class.java,
            object : io.github.a092devs.nexfusion.compat.XC_MethodReplacement() {
                override fun replaceHookedMethod(param: MethodHookParam): Any? {
                    val msg = param.args[0] as? com.google.protobuf.MessageLite ?: return null
                    return msg.toByteArray()
                }
            }
        )
        io.github.a092devs.nexfusion.compat.XposedHelpers.findAndHookMethod(
            extClass, "extensionRegistry",
            object : io.github.a092devs.nexfusion.compat.XC_MethodReplacement() {
                override fun replaceHookedMethod(param: MethodHookParam): Any? {
                    return runCatching {
                        val regClass = classLoader.loadClass("com.google.protobuf.ExtensionRegistryLite")
                        regClass.getMethod("getGeneratedRegistry").invoke(null)
                    }.getOrNull()
                }
            }
        )
    }

    runCatching {
        WatchNextResponseModelConstructorFingerprint.hookMethod {
            before { param ->
                runCatching { PlayAlbumSongsExt.rewriteWatchNextResponse(param.args[0]) }
                    .getOrNull()?.let { param.args[0] = it }
            }
        }
    }

    runCatching {
        CommandResolverFingerprint.hookMethod {
            before { param ->
                runCatching { PlayAlbumSongsExt.overrideCommand(param.args[0]) }
                    .getOrNull()?.let { param.args[0] = it }
            }
        }
    }
}
