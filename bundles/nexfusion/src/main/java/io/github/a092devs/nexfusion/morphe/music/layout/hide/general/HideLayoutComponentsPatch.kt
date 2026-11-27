package io.github.a092devs.nexfusion.morphe.music.layout.hide.general

import app.morphe.extension.music.patches.components.CommentsFilter
import app.morphe.extension.music.patches.components.CustomFilter
import app.morphe.extension.music.patches.components.LayoutComponentsFilter
import io.github.a092devs.nexfusion.morphe.music.misc.litho.filter.LithoFilter
import io.github.a092devs.nexfusion.morphe.shared.misc.litho.filter.addLithoFilter
import io.github.a092devs.nexfusion.patch

val HideLayoutComponents = patch(
    name = "Hide layout components",
    description = "Adds options to hide general layout components.",
    use = false,
) {
    dependsOn(LithoFilter)

    addLithoFilter(LayoutComponentsFilter())
    addLithoFilter(CustomFilter())
    addLithoFilter(CommentsFilter())
}
