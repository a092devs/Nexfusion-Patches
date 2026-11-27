package io.github.a092devs.nexfusion.morphe.music.misc.litho.filter

import io.github.a092devs.nexfusion.morphe.shared.misc.litho.context.conversionContextPatch
import io.github.a092devs.nexfusion.morphe.shared.misc.litho.filter.sharedLithoFilterPatch

val LithoFilter = sharedLithoFilterPatch(
    overrideUpbFeatureFlag = { false },
) {
    dependsOn(conversionContextPatch)
}
