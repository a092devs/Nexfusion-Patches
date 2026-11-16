package io.github.nexfusion.nexfusion.instagram

import io.github.nexfusion.morphe.findMethodDirect
import io.github.nexfusion.morphe.findMethodListDirect
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.query.enums.StringMatchType
import org.luckypray.dexkit.result.MethodData

/**
 * MobileConfig evaluation methods that resolve boolean feature gates on concrete manager classes.
 */
val mobileConfigFlagMethods = findMethodListDirect {
    findMethod {
        matcher {
            declaredClass("com.facebook.mobileconfig.MobileConfigManagerHolderImpl")
            paramTypes = listOf("long")
            returnType = "boolean"
        }
    } +
    findMethod {
        matcher {
            declaredClass("com.facebook.mobileconfig.MobileConfigManagerHolderImpl")
            paramTypes = listOf("long", "boolean")
            returnType = "boolean"
        }
    } +
    findMethod {
        matcher {
            declaredClass("com.facebook.mobileconfig.factory.MobileConfigSafeContext")
            paramTypes = listOf("long")
            returnType = "boolean"
        }
    } +
    findMethod {
        matcher {
            declaredClass("com.facebook.mobileconfig.factory.MobileConfigSafeContext")
            paramTypes = listOf("long", "boolean")
            returnType = "boolean"
        }
    }
}

/**
 * Meta AI gate methods in Instagram that gate tap-and-hold ask Meta AI, imagine prompts,
 * comment summary suggestions, and reels AI prompts.
 */
val metaAiGateMethods = findMethodListDirect {
    findMethod {
        matcher {
            usingNumbers(0x8103af00020eb4L)
            returnType = "boolean"
        }
    }.distinctBy { it.descriptor }
}

/**
 * Litho component render methods for Reels Meta AI pills and AI chips.
 * In Instagram bytecode, each component's render method contains the Redex purge marker:
 * e.g., "ClipsMetaAiPillComponent_render", "MetaAffiliateCTAComponent_render".
 */
val reelsMetaAiRenderMethods = findMethodListDirect {
    val markers = listOf(
        "ClipsMetaAiPillComponent_render",
        "MetaAffiliateCTAComponent_render",
    )
    markers.flatMap { marker ->
        findMethod {
            matcher {
                usingStrings(listOf(marker), StringMatchType.Contains)
            }
        }
    }.distinctBy { it.descriptor }
}

/**
 * Methods gating the Reels More menu AI Discovery ("Ask Meta AI") options.
 */
val reelsAiDiscoveryMenuMethods = findMethodListDirect {
    findMethod {
        matcher {
            usingStrings(listOf("hasAiDiscoveryMenu"), StringMatchType.Contains)
            returnType = "boolean"
        }
    }.distinctBy { it.descriptor }
}
