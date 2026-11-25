package io.github.a092devs.nexfusion.morphe.shared.misc.textcomponent

import app.morphe.extension.shared.patches.components.ContextInterface
import io.github.a092devs.nexfusion.morphe.shared.SpannableStringBuilderFingerprint
import io.github.a092devs.nexfusion.morphe.shared.misc.litho.context.ConversionContext
import io.github.a092devs.nexfusion.morphe.shared.spannableStringBuilderGetSpannedMethod
import io.github.a092devs.nexfusion.patch

val textComponentPatch = patch(
    name = "textComponentPatch",
    description = "Provides hooks into text components for extension filtering.",
    use = false,
) {
    SpannableStringBuilderFingerprint.hookMethod {
        val getSpannedMethod = ::spannableStringBuilderGetSpannedMethod.method
        after {
            if (it.result == "")
                return@after

            val spannedContext = it.args[0]
            val spanned = getSpannedMethod(it.args[2]) as String
            // TODO EmojiCompat.process(spanned)
            val ctx = ConversionContext(spannedContext)
            hooks.forEach { fn -> fn(ctx, spanned) }
            if (charSeqOverrides.isNotEmpty() && it.result is CharSequence) {
                var current = it.result as CharSequence
                charSeqOverrides.forEach { fn -> current = fn(ctx, current) }
                it.result = current
            }
        }
    }
}

private val hooks = mutableListOf<(ContextInterface, CharSequence) -> Unit>()
private val charSeqOverrides = mutableListOf<(ContextInterface, CharSequence) -> CharSequence>()

internal fun hookSpannableString(
    hook: (ContextInterface, CharSequence) -> Unit,
//    overrideSpan: Boolean = false
) {
    hooks.add { a, b -> hook(a, b) }
}

internal fun hookSpannableStringOverride(
    hook: (ContextInterface, CharSequence) -> CharSequence,
) {
    charSeqOverrides.add(hook)
}