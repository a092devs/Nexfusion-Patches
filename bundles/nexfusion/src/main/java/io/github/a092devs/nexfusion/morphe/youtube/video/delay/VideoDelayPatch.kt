package io.github.a092devs.nexfusion.morphe.youtube.video.delay

import android.content.Context
import android.content.IntentFilter
import android.os.Build
import app.morphe.extension.shared.Logger
import app.morphe.extension.shared.settings.preference.SeekBarPreference
import app.morphe.extension.youtube.patches.VideoDelayPatch
import app.morphe.extension.youtube.patches.VideoDelayReceiver
import app.morphe.extension.youtube.videoplayer.VideoDelayDialogButton
import io.github.a092devs.nexfusion.morphe.setExtensionIsPatchIncluded
import io.github.a092devs.nexfusion.morphe.shared.misc.settings.preference.NonInteractivePreference
import io.github.a092devs.nexfusion.morphe.shared.misc.settings.preference.PreferenceScreenPreference
import io.github.a092devs.nexfusion.morphe.shared.misc.settings.preference.SwitchPreference
import io.github.a092devs.nexfusion.morphe.youtube.layout.buttons.overlay.addPlayerOverlayPreferences
import io.github.a092devs.nexfusion.morphe.youtube.layout.player.buttons.addPlayerBottomButton
import io.github.a092devs.nexfusion.morphe.youtube.layout.player.buttons.playerOverlayButtonsHook
import io.github.a092devs.nexfusion.morphe.youtube.misc.playercontrols.ControlInitializer
import io.github.a092devs.nexfusion.morphe.youtube.misc.playercontrols.LegacyPlayerControls
import io.github.a092devs.nexfusion.morphe.youtube.misc.playercontrols.addLegacyBottomControl
import io.github.a092devs.nexfusion.morphe.youtube.misc.playercontrols.initializeLegacyBottomControl
import io.github.a092devs.nexfusion.morphe.youtube.misc.settings.PreferenceScreen
import io.github.a092devs.nexfusion.patch
import io.github.a092devs.nexfusion.patches.R
import org.luckypray.dexkit.wrap.DexMethod

private var videoDelayReceiverRegistered = false

/**
 * Manifest receivers are not portable to the runtime module, so the SET_VIDEO_DELAY receiver
 * is registered dynamically the first time an Application onCreate is hooked.
 */
private fun registerVideoDelayReceiverIfNeeded(context: Context) {
    if (videoDelayReceiverRegistered) return
    videoDelayReceiverRegistered = true

    try {
        val appContext = context.applicationContext
        val receiver = VideoDelayReceiver()
        val filter = IntentFilter(VideoDelayReceiver.ACTION_SET_VIDEO_DELAY)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            appContext.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            appContext.registerReceiver(receiver, filter)
        }
    } catch (ex: Exception) {
        Logger.printException({ "Video delay receiver registration failure" }, ex)
    }
}

val VideoDelay = patch(
    description = "Adds an option to delay the video, to keep it in sync with laggy Bluetooth audio.",
) {
    dependsOn(
        LegacyPlayerControls,
        playerOverlayButtonsHook,
    )

    // Video delay settings.
    PreferenceScreen.VIDEO.addPreferences(
        NonInteractivePreference(
            key = "morphe_video_delay",
            tag = SeekBarPreference::class.java,
            // Upstream inflates this from XML where AttributeSet handles 'selectable'.
            // NexFusion instantiates via reflection, applying 'selectable' AFTER the constructor.
            // Aligning with SeekBar's init() default to avoid patching extension code.
            selectable = true,
        ),
        SwitchPreference("morphe_video_delay_bluetooth_only", summary = true),
    )

    addPlayerOverlayPreferences(
        SwitchPreference("morphe_video_delay_button_overlay", summary = true),
    )

    // Upstream groups the player flyout button settings under 'morphe_hide_player_flyout'.
    // The extension self-registers the flyout button when its isPatchIncluded returns true,
    // so no additional player flyout hooking is needed.
    PreferenceScreen.PLAYER.addPreferences(
        PreferenceScreenPreference(
            key = "morphe_hide_player_flyout",
            preferences = setOf(SwitchPreference("morphe_video_delay_button_flyout")),
        ),
    )

    addPlayerBottomButton(VideoDelayDialogButton::initializeButton)

    addLegacyBottomControl(R.layout.morphe_video_delay_dialog_button_container)
    initializeLegacyBottomControl(
        ControlInitializer(
            R.id.morphe_video_delay_dialog_button_container,
            VideoDelayDialogButton::initializeLegacyButton,
        )
    )

    // Activate the extension side feature gating of the delay and its flyout button.
    setExtensionIsPatchIncluded(VideoDelayPatch::class.java)

    // Video frames are released when the audio clock reaches their timestamp, so reporting an
    // earlier audio position holds every frame back by the same amount. Both audio renderers
    // are hooked, as the player uses either depending on the audio codec.
    listOf(
        MediaCodecAudioRendererPositionFingerprint,
        DecoderAudioRendererPositionFingerprint,
    ).forEach { fingerprint ->
        fingerprint.hookMethod {
            after {
                val positionUs = it.result as? Long ?: return@after
                it.result = VideoDelayPatch.delayPositionUs(positionUs)
            }
        }
    }

    // The receiver registration needs a context, which is only available once the host
    // Application has created. Cold-started broadcasts sent before the app is opened are
    // delivered after Application.onCreate, so registration there covers them.
    DexMethod("Landroid/app/Application;->onCreate()V").hookMethod {
        after {
            val context = it.thisObject as? Context ?: return@after
            registerVideoDelayReceiverIfNeeded(context)
        }
    }
}
