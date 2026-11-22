package io.github.a092devs.nexfusion.morphe.youtube.misc.litho.filter

import io.github.a092devs.nexfusion.morphe.shared.misc.litho.context.conversionContextPatch
import io.github.a092devs.nexfusion.morphe.shared.misc.litho.filter.sharedLithoFilterPatch
import io.github.a092devs.nexfusion.morphe.youtube.misc.litho.rendernext.disableRenderNextPatch
import io.github.a092devs.nexfusion.morphe.youtube.misc.playservice.VersionCheck
import io.github.a092devs.nexfusion.morphe.youtube.misc.playservice.is_21_15_or_greater
import io.github.a092devs.nexfusion.morphe.youtube.misc.verticalscroll.FixVerticalScroll

val LithoFilter = sharedLithoFilterPatch(
    // Flag was removed in 21.15+.
    overrideUpbFeatureFlag = { !is_21_15_or_greater }
) {
    dependsOn(
        FixVerticalScroll,
        VersionCheck,
        conversionContextPatch,
        disableRenderNextPatch,
    )
}
