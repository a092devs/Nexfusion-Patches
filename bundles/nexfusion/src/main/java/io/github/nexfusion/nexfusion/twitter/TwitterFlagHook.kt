package io.github.nexfusion.nexfusion.twitter

import io.github.nexfusion.IHook
import io.github.nexfusion.PatcherLog
import io.github.nexfusion.hookMethod

// Ported from kveld9/kveld-extra-morphe-patches (GPL-3.0), TwitterFlagUtils.kt's neutralizeFlag.
// The upstream patch rewrites the flag read in each call site's bytecode; this bundle instead hooks
// the feature-switch repositories' boolean accessors and substitutes false for the registered names
// (X 12.35: com.x.featureswitches.FeatureSwitchesRepositoryImpl.getBoolean and
// com.x.featureswitches.FakeFeatureSwitchesRepository.getBoolean/peekBoolean, both (String, Z) -> Z).

/** Classes that serve FeatureSwitchesRepository boolean reads in X 12.35. */
private val FLAG_REPOSITORY_CLASSES = listOf(
    "com.x.featureswitches.FeatureSwitchesRepositoryImpl",
    "com.x.featureswitches.FakeFeatureSwitchesRepository",
)

private val neutralizedFlags = LinkedHashSet<String>()
private val zeroedIntFlags = LinkedHashSet<String>()
private var flagHookInstalled = false

/**
 * Registers integer [flags] to be forced to 0 (such as timeline startup trim thresholds)
 * and installs the shared accessor hook on first use.
 */
fun IHook.zeroTwitterIntFlags(vararg flags: String) {
    synchronized(zeroedIntFlags) { zeroedIntFlags.addAll(flags) }
    ensureTwitterFlagHooks()
}

/**
 * Registers [flags] to be forced to false and installs the shared accessor hook on first use.
 * Each patch registers its own flags, so a disabled patch never contributes any.
 */
fun IHook.neutralizeTwitterFlags(vararg flags: String) {
    synchronized(neutralizedFlags) { neutralizedFlags.addAll(flags) }
    ensureTwitterFlagHooks()
}

private fun IHook.ensureTwitterFlagHooks() {
    if (flagHookInstalled) return

    var hooked = 0
    for (className in FLAG_REPOSITORY_CLASSES) {
        val cls = runCatching { classLoader.loadClass(className) }.getOrNull() ?: continue
        for (methodName in listOf("getBoolean", "peekBoolean")) {
            for (method in cls.declaredMethods) {
                if (method.name != methodName) continue
                if (method.parameterTypes.size != 2) continue
                if (method.parameterTypes[0] != String::class.java) continue
                if (method.parameterTypes[1] != Boolean::class.javaPrimitiveType) continue
                if (method.returnType != Boolean::class.javaPrimitiveType) continue
                runCatching {
                    method.hookMethod {
                        before = { param ->
                            val flagName = param.args.getOrNull(0) as? String
                            if (flagName != null &&
                                synchronized(neutralizedFlags) { neutralizedFlags.contains(flagName) }
                            ) {
                                param.result = false
                            }
                        }
                    }
                }.onSuccess { hooked++ }
            }
        }
        for (methodName in listOf("getInt", "peekInt")) {
            for (method in cls.declaredMethods) {
                if (method.name != methodName) continue
                if (method.parameterTypes.size != 2) continue
                if (method.parameterTypes[0] != String::class.java) continue
                if (method.parameterTypes[1] != Int::class.javaPrimitiveType) continue
                if (method.returnType != Int::class.javaPrimitiveType) continue
                runCatching {
                    method.hookMethod {
                        before = { param ->
                            val flagName = param.args.getOrNull(0) as? String
                            if (flagName != null &&
                                synchronized(zeroedIntFlags) { zeroedIntFlags.contains(flagName) }
                            ) {
                                param.result = 0
                            }
                        }
                    }
                }.onSuccess { hooked++ }
            }
        }
    }

    if (hooked == 0) {
        PatcherLog.info { "NexFusion: X feature-switch repositories not found; flag overrides inactive" }
    } else {
        flagHookInstalled = true
        PatcherLog.info { "NexFusion: X flag hook installed on $hooked accessor(s)" }
    }
}
