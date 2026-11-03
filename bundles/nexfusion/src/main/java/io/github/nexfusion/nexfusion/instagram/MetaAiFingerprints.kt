package io.github.nexfusion.nexfusion.instagram

import io.github.nexfusion.morphe.findMethodListDirect
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.result.MethodData

/**
 * MobileConfig evaluation methods that resolve boolean feature gates (Bae, Baf, Bal, Bam).
 */
val mobileConfigFlagMethods = findMethodListDirect {
    findMethod {
        matcher {
            name = "Bae"
            paramTypes = listOf("long")
            returnType = "boolean"
        }
    }.distinctBy { it.descriptor } +
    findMethod {
        matcher {
            name = "Baf"
            paramTypes = listOf("long", "boolean")
            returnType = "boolean"
        }
    }.distinctBy { it.descriptor } +
    findMethod {
        matcher {
            name = "Bal"
            returnType = "boolean"
        }
    }.distinctBy { it.descriptor } +
    findMethod {
        matcher {
            name = "Bam"
            returnType = "boolean"
        }
    }.distinctBy { it.descriptor }
}

/**
 * Meta AI gate methods in Instagram that gate tap-and-hold ask Meta AI, imagine prompts,
 * comment summary suggestions, and reels AI prompts.
 */
val metaAiGateMethods = findMethodListDirect {
    findMethod {
        matcher {
            usingNumbers(0x8103af00020eb4L)
        }
    }.firstOrNull()?.let { sampleMethod ->
        getClassData(sampleMethod.declaredClassName)?.methods?.filter {
            it.returnTypeName == "boolean"
        }
    } ?: emptyList()
}

