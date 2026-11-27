package io.github.a092devs.nexfusion.morphe.youtube.misc.litho.rendernext

import io.github.a092devs.nexfusion.compat.XC_MethodReplacement
import io.github.a092devs.nexfusion.morphe.youtube.misc.playservice.VersionCheck
import io.github.a092devs.nexfusion.morphe.youtube.insertLiteralOverride
import io.github.a092devs.nexfusion.morphe.youtube.misc.playservice.is_20_29_or_greater
import io.github.a092devs.nexfusion.morphe.youtube.misc.playservice.is_20_47_or_greater
import io.github.a092devs.nexfusion.morphe.youtube.misc.playservice.is_21_35_or_greater
import io.github.a092devs.nexfusion.morphe.youtube.misc.playservice.is_21_42_or_greater
import io.github.a092devs.nexfusion.patch
import io.github.a092devs.nexfusion.scopedHook

val disableRenderNextPatch = patch(
    description = "Disables RenderNext, so the app is always rendered with Litho.",
) {

    dependsOn(VersionCheck)

    // Do not provide ElementsServices to the section list, required to convert Litho components.
    insertLiteralOverride(45661418L)

    // Do not present elements with RenderNext, even if the server enables it.
    // An element is RenderNext if the enablement check of its proto
    // or the RenderNext field of its config is true.
    // Skipped on 21.42+ where forcing RenderNextEnablementCheck false crashes Shorts
    // (ClassPropertiesConverter.nativeResolveStyle: FlexDirection must have an identifier value).
    if (!is_21_42_or_greater) {
        RenderNextEnablementCheckFingerprint.hookMethod(XC_MethodReplacement.returnConstant(false))
    }

    // Mutating the shared RenderNextConfigField on the config object causes Shorts to crash on
    // YouTube 21.47+ (ClassPropertiesConverter.nativeResolveStyle: FlexDirection must have an
    // identifier value). RenderNextEnablementCheckFingerprint + RenderNextTemplateCheckFingerprint
    // already disable RenderNext without mutating the shared config object.

    if (is_20_29_or_greater && !is_21_42_or_greater) {
        // Do not convert Litho components to RenderNext.
        // Skipped on 21.42+ where hooking RenderNextTemplateCheckFingerprint causes Shorts to crash
        // (ClassPropertiesConverter.nativeResolveStyle: FlexDirection must have an identifier value).
        RenderNextTemplateCheckFingerprint.hookMethod(XC_MethodReplacement.returnConstant(false))
    }

    if (is_21_35_or_greater) {
        // Do not open the Home with the page fragment, which renders the whole page with RenderNext.
        runCatching {
            PageFragmentRouteFingerprint.hookMethod(scopedHook(::pageFragmentRouteFlagMethod.method) {
                before {
                    if (it.args.firstOrNull() == 1073830434) {
                        it.result = false
                    }
                }
            })
        }
    }

    if (is_20_47_or_greater) {
        // TOD Safeguard: always present elements with Litho, even if they are RenderNext elements.
    }
}
