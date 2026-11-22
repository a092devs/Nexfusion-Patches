package io.github.a092devs.nexfusion.morphe.youtube.misc.litho.node

import io.github.a092devs.nexfusion.morphe.shared.misc.litho.context.conversionContextPatch
import io.github.a092devs.nexfusion.morphe.shared.misc.litho.node.createTreeNodeElementHookPatch
import io.github.a092devs.nexfusion.patch

val TreeNodeElementHook = createTreeNodeElementHookPatch(
    patch {},
    conversionContextPatch,
    false,
    useLegacyContextRegister = { false }
)