package io.github.a092devs.nexfusion.morphe.music.layout.applemusicartwork

import android.widget.ImageView
import app.morphe.extension.music.patches.applemusicartwork.AppleMusicArtworkPatch
import app.morphe.extension.shared.Logger
import app.morphe.extension.shared.ResourceType as ExtResourceType
import app.morphe.extension.shared.ResourceUtils
import io.github.a092devs.nexfusion.hookMethod
import io.github.a092devs.nexfusion.morphe.AccessFlags
import io.github.a092devs.nexfusion.morphe.Fingerprint
import io.github.a092devs.nexfusion.morphe.Opcode
import io.github.a092devs.nexfusion.morphe.ResourceType
import io.github.a092devs.nexfusion.morphe.checkCast
import io.github.a092devs.nexfusion.morphe.fieldAccess
import io.github.a092devs.nexfusion.morphe.findFieldDirect
import io.github.a092devs.nexfusion.morphe.music.misc.mediasession.MediaSessionHook
import io.github.a092devs.nexfusion.morphe.music.misc.mediasession.setMetadataHooks
import io.github.a092devs.nexfusion.morphe.music.misc.mediasession.setPlaybackStateHooks
import io.github.a092devs.nexfusion.morphe.music.misc.settings.PreferenceScreen
import io.github.a092devs.nexfusion.morphe.music.misc.settings.SettingsHook
import io.github.a092devs.nexfusion.morphe.resourceLiteral
import io.github.a092devs.nexfusion.morphe.shared.misc.settings.preference.SwitchPreference
import io.github.a092devs.nexfusion.patch

private const val AUTO_CROP_IMAGE_VIEW_CLASS_TYPE =
    "Lcom/google/android/apps/youtube/music/ui/image/AutoCropImageView;"

internal object PlayerThumbnailFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.CONSTRUCTOR),
    filters = listOf(
        resourceLiteral(ResourceType.ID, "player_thumbnail"),
        checkCast(AUTO_CROP_IMAGE_VIEW_CLASS_TYPE),
        fieldAccess(opcode = Opcode.IPUT_OBJECT, type = AUTO_CROP_IMAGE_VIEW_CLASS_TYPE)
    )
)

private val playerThumbnailField = findFieldDirect {
    PlayerThumbnailFingerprint.instructionMatches.last().instruction.fieldRef!!
}

val AppleMusicArtwork = patch(
    name = "Apple Music animated artwork",
    description = "Adds an option to show the animated album artwork of Apple Music in the player.",
    use = false,
) {
    dependsOn(
        SettingsHook,
        MediaSessionHook,
    )

    PreferenceScreen.PLAYER.addPreferences(
        SwitchPreference("morphe_music_apple_music_artwork", summary = true)
    )

    try {
        val thumbField = ::playerThumbnailField.field.apply { isAccessible = true }

        PlayerThumbnailFingerprint.hookMethod {
            after { param ->
                (thumbField.get(param.thisObject) as? ImageView)?.let {
                    AppleMusicArtworkPatch.setPlayerThumbnail(it)
                }
            }
        }
    } catch (t: Throwable) {
        Logger.printException({ "AppleMusicArtwork: PlayerThumbnail hook failed" }, t)
    }

    setMetadataHooks.add { AppleMusicArtworkPatch.onSetMetadata(it) }
    setPlaybackStateHooks.add { AppleMusicArtworkPatch.onSetPlaybackState(it) }
}
