package io.github.a092devs.nexfusion.morphe.music.interaction.dislikeredirection

import app.morphe.extension.music.patches.DisableDislikeRedirectionPatch
import app.morphe.extension.shared.Logger
import io.github.a092devs.nexfusion.hookMethod
import io.github.a092devs.nexfusion.morphe.AccessFlags
import io.github.a092devs.nexfusion.morphe.Fingerprint
import io.github.a092devs.nexfusion.morphe.InstructionLocation.MatchAfterWithin
import io.github.a092devs.nexfusion.morphe.Opcode
import io.github.a092devs.nexfusion.morphe.anyInstruction
import io.github.a092devs.nexfusion.morphe.findMethodDirect
import io.github.a092devs.nexfusion.morphe.methodCall
import io.github.a092devs.nexfusion.morphe.music.misc.settings.PreferenceScreen
import io.github.a092devs.nexfusion.morphe.music.misc.settings.SettingsHook
import io.github.a092devs.nexfusion.morphe.shared.misc.settings.preference.SwitchPreference
import io.github.a092devs.nexfusion.morphe.string
import io.github.a092devs.nexfusion.patch
import io.github.a092devs.nexfusion.scopedHook

internal object NotificationLikeButtonOnClickListenerFingerprint : Fingerprint(
    classFingerprint = Fingerprint(
        name = "<clinit>",
        returnType = "V",
        parameters = listOf(),
        filters = listOf(
            string(
                "NotificationLikeButtonController",
                comparison = io.github.a092devs.nexfusion.morphe.StringComparisonType.CONTAINS
            )
        )
    ),
    accessFlags = listOf(AccessFlags.PRIVATE, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf("L"),
    filters = listOf(
        methodCall(opcode = Opcode.INVOKE_VIRTUAL, parameters = listOf("L", "L", "Ljava/util/Map;")),
        methodCall(opcode = Opcode.INVOKE_INTERFACE, returnType = "V", parameters = listOf("L"))
    )
)

internal object DislikeSkipToNextFingerprint : Fingerprint(
    returnType = "V",
    filters = listOf(
        methodCall(definingClass = "Ljava/lang/String;", name = "equals"),
        methodCall(opcode = Opcode.INVOKE_INTERFACE, returnType = "Z", parameters = listOf("L"), location = MatchAfterWithin(5)),
        methodCall(opcode = Opcode.INVOKE_VIRTUAL, parameters = listOf("L", "L", "Ljava/util/Map;"), location = MatchAfterWithin(5)),
        methodCall(opcode = Opcode.INVOKE_INTERFACE, returnType = "V", parameters = listOf("L"), location = MatchAfterWithin(2))
    )
)

private val notifSkipMethodRef = findMethodDirect {
    NotificationLikeButtonOnClickListenerFingerprint.instructionMatches.last().instruction.methodRef!!
}

private val dislikeSkipMethodRef = findMethodDirect {
    DislikeSkipToNextFingerprint.instructionMatches.last().instruction.methodRef!!
}

val DisableDislikeRedirection = patch(
    name = "Disable dislike redirection",
    description = "Adds an option to prevent skipping to the next track when the dislike button is pressed.",
    use = false,
) {
    dependsOn(SettingsHook)

    PreferenceScreen.PLAYER.addPreferences(
        SwitchPreference("morphe_music_disable_dislike_redirection", summary = true)
    )

    fun hookSkipTarget(
        parentFp: Fingerprint,
        targetMethod: java.lang.reflect.Method,
    ) {
        if (targetMethod.declaringClass.name.startsWith("java.")) return
        val isAbstractOrInterface = java.lang.reflect.Modifier.isAbstract(targetMethod.modifiers) ||
            targetMethod.declaringClass.isInterface

        if (!isAbstractOrInterface) {
            parentFp.hookMethod(
                scopedHook(targetMethod) {
                    before { param ->
                        if (innerDepth == 0 && DisableDislikeRedirectionPatch.disableDislikeRedirection()) {
                            param.result = null
                        }
                    }
                }
            )
            return
        }

        val scopeDepth = ThreadLocal.withInitial { 0 }
        val hookedClasses = java.util.Collections.newSetFromMap(java.util.concurrent.ConcurrentHashMap<Class<*>, Boolean>())
        val ifaceClass = targetMethod.declaringClass
        val candidateFields = parentFp.method.declaringClass.declaredFields.filter {
            ifaceClass.isAssignableFrom(it.type) || it.type.isAssignableFrom(ifaceClass)
        }.onEach { it.isAccessible = true }

        parentFp.hookMethod {
            before { param ->
                scopeDepth.set(scopeDepth.get() + 1)
                val owner = param.thisObject ?: return@before
                for (field in candidateFields) {
                    val impl = runCatching { field.get(owner) }.getOrNull() ?: continue
                    val implClass = impl.javaClass
                    if (hookedClasses.add(implClass)) {
                        runCatching {
                            val concreteMethod = implClass.getMethod(targetMethod.name, *targetMethod.parameterTypes)
                            if (!java.lang.reflect.Modifier.isAbstract(concreteMethod.modifiers)) {
                                concreteMethod.hookMethod {
                                    before { innerParam ->
                                        if (scopeDepth.get() > 0 && DisableDislikeRedirectionPatch.disableDislikeRedirection()) {
                                            innerParam.result = null
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            after {
                val next = scopeDepth.get() - 1
                scopeDepth.set(if (next > 0) next else 0)
            }
        }
    }

    try {
        hookSkipTarget(NotificationLikeButtonOnClickListenerFingerprint, ::notifSkipMethodRef.method)
    } catch (t: Throwable) {
        Logger.printException({ "DisableDislikeRedirection: Notification hook failed" }, t)
    }

    try {
        hookSkipTarget(DislikeSkipToNextFingerprint, ::dislikeSkipMethodRef.method)
    } catch (t: Throwable) {
        Logger.printException({ "DisableDislikeRedirection: DislikeSkipToNext hook failed" }, t)
    }
}
