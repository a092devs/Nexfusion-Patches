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
    val bdz = findMethod {
        matcher {
            name = "Bdz"
            paramTypes = listOf("long")
            returnType = "boolean"
        }
    }.filter { java.lang.reflect.Modifier.isFinal(it.modifiers) || !java.lang.reflect.Modifier.isAbstract(it.modifiers) }

    val be0 = findMethod {
        matcher {
            name = "Be0"
            paramTypes = listOf("long", "boolean")
            returnType = "boolean"
        }
    }.filter { java.lang.reflect.Modifier.isFinal(it.modifiers) || !java.lang.reflect.Modifier.isAbstract(it.modifiers) }

    val legacy = findMethod {
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

    (bdz + be0 + legacy).distinctBy { it.descriptor }
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
 * On Instagram 456+, falls back to matching methods referencing resource 0x7f0801d5L.
 */
val reelsMetaAiRenderMethods = findMethodListDirect {
    val markers = listOf(
        "ClipsMetaAiPillComponent_render",
        "MetaAffiliateCTAComponent_render",
    )
    val byMarkers = markers.flatMap { marker ->
        findMethod {
            matcher {
                usingStrings(listOf(marker), StringMatchType.Contains)
            }
        }
    }
    if (byMarkers.isNotEmpty()) {
        byMarkers.distinctBy { it.descriptor }
    } else {
        findMethod {
            matcher {
                usingNumbers(0x7f0801d5L)
            }
        }.distinctBy { it.descriptor }
    }
}

/**
 * Methods gating the Reels More menu AI Discovery ("Ask Meta AI") options.
 * On Instagram 456+, falls back to the AI summary factory matching flag 0x81103000115aeaL.
 */
val reelsAiDiscoveryMenuMethods = findMethodListDirect {
    val byStrings = findMethod {
        matcher {
            usingStrings(listOf("hasAiDiscoveryMenu"), StringMatchType.Contains)
            returnType = "boolean"
        }
    }
    if (byStrings.isNotEmpty()) {
        byStrings.distinctBy { it.descriptor }
    } else {
        findMethod {
            matcher {
                usingNumbers(0x81103000115aeaL)
                returnType = "boolean"
            }
        }.distinctBy { it.descriptor }
    }
}
