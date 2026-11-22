package io.github.a092devs.nexfusion.morphe.youtube.misc.proto

import io.github.a092devs.nexfusion.morphe.shared.misc.proto.createElementProtoParserHookPatch
import io.github.a092devs.nexfusion.morphe.youtube.misc.playservice.VersionCheck
import io.github.a092devs.nexfusion.morphe.youtube.misc.playservice.is_21_40_or_greater

val elementProtoParserHookPatch = createElementProtoParserHookPatch(
    // YouTube 21.40+ can read the inline resources of the responses with the element proto parser.
    readsResourcesWithParser = { is_21_40_or_greater },
) {
    dependsOn(VersionCheck)
}