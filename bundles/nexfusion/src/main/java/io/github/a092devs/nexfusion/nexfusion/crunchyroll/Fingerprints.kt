package io.github.a092devs.nexfusion.nexfusion.crunchyroll

import io.github.a092devs.nexfusion.morphe.Fingerprint
import io.github.a092devs.nexfusion.morphe.Opcode
import io.github.a092devs.nexfusion.morphe.methodCall
import io.github.a092devs.nexfusion.morphe.string

internal object PictureInPictureProviderFingerprint : Fingerprint(
    filters = listOf(
        string("picture_in_picture"),
    ),
)

internal object PictureInPictureAutoEnterFingerprint : Fingerprint(
    returnType = "V",
    parameters = listOf("Z"),
    filters = listOf(
        string("SecurityException registering pipActionReceiver: "),
        methodCall(
            definingClass = "Landroid/app/PictureInPictureParams\$Builder;",
            name = "setAutoEnterEnabled",
        ),
    ),
)

internal object PictureInPictureSkipForwardFingerprint : Fingerprint(
    definingClass = "Lcom/crunchyroll/player/presentation/playerview/PlayerViewLayout;",
    name = "L",
    returnType = "V",
    parameters = emptyList(),
)

internal object SkipSegmentButtonFingerprint : Fingerprint(
    returnType = "V",
    filters = listOf(
        methodCall(name = "onSkipSegmentClick"),
    ),
)

internal object WatchScreenUserLeaveHintFingerprint : Fingerprint(
    definingClass = "Lcom/crunchyroll/watchscreen/screen/WatchScreenActivity;",
    name = "onUserLeaveHint",
    returnType = "V",
)

internal object ContinueWatchingOverflowMenuFingerprint : Fingerprint(
    returnType = "Ljava/util/List;",
    parameters = listOf("Ljava/lang/Object;"),
    filters = listOf(
        string("data"),
        methodCall(
            parameters = emptyList(),
            returnType = "Z",
            opcode = Opcode.INVOKE_INTERFACE,
        ),
    ),
)

internal object CrunchyrollApplicationOnCreateFingerprint : Fingerprint(
    name = "onCreate",
    returnType = "V",
    parameters = emptyList(),
    filters = listOf(
        methodCall(
            definingClass = "Landroid/app/Application;",
            name = "onCreate",
        ),
        string("APP onCreate"),
    ),
)

internal object ContinueWatchingFeedMapperFingerprint : Fingerprint(
    filters = listOf(
        methodCall(
            definingClass = "Lcom/ellation/crunchyroll/model/Panel;",
            name = "getContinueWatchingImages",
        ),
        methodCall(
            definingClass = "Lcom/ellation/crunchyroll/model/Panel;",
            name = "getId",
        ),
    ),
)

