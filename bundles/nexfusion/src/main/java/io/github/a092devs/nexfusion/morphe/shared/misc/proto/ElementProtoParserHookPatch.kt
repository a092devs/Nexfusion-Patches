package io.github.a092devs.nexfusion.morphe.shared.misc.proto

import io.github.a092devs.nexfusion.PatchExecutor
import io.github.a092devs.nexfusion.patch


/**
 * Shared factory for the element proto parser hook used by both YouTube and YT Music.
 *
 * Hooks the generic proto parser method so that patched extensions can inspect (and modify)
 * the raw byte buffer of every parsed proto element.
 *
 * @param readsResourcesWithParser Whether the inline resources of the responses, such as the
 *                                 templates and the JavaScript modules, can be read with the
 *                                 element proto parser (YouTube 21.40+). Evaluated lazily inside
 *                                 the patch body so version flags are already set.
 * @param block Callback to add app-specific dependencies, such as versionCheckPatch.
 */
internal fun createElementProtoParserHookPatch(
    readsResourcesWithParser: () -> Boolean = { false },
    block: PatchExecutor.() -> Unit = {},
) = patch(
    description = "Hook to modify the proto message class, which can only be accessed through reflection.",
) {
    block()

    NewElementProtoParserFingerprint.hookMethod {
        after {
            var result = it.result as ByteArray?
            hooks.forEach {
                result = it(result)
            }
            it.result = result
        }
    }

    if (readsResourcesWithParser()) {
        // On YouTube 21.40+, inline resources (templates, JS modules) can be read through the
        // element proto parser as well. Upstream resolves this by retouting the resource read
        // call site into the parser's hooked clone; the runtime hook installed above already
        // applies the element hook chain to every caller of the parser, so no further work is
        // needed at bytecode level (upstream commit 2151a04d0).
    }
}

private val hooks = mutableListOf<(ByteArray?) -> ByteArray?>()
fun hookElement(hook: (ByteArray?) -> ByteArray?) {
    hooks.add(hook)
}