package io.github.nexfusion.morphe.youtube.interaction.shorts

import android.content.Context

import io.github.nexfusion.compat.XposedHelpers
import io.github.nexfusion.FindMethodFunc
import io.github.nexfusion.PatchExecutor
import io.github.nexfusion.PatcherLog
import io.github.nexfusion.hookMethod
import io.github.nexfusion.morphe.AccessFlags
import io.github.nexfusion.morphe.Fingerprint
import io.github.nexfusion.morphe.StrictQuery
import io.github.nexfusion.morphe.InstructionLocation.MatchAfterWithin
import io.github.nexfusion.morphe.Opcode
import io.github.nexfusion.morphe.anyInstruction
import io.github.nexfusion.morphe.fieldAccess
import io.github.nexfusion.morphe.findMethodDirect
import io.github.nexfusion.morphe.methodCall
import io.github.nexfusion.morphe.string
import io.github.nexfusion.morphe.youtube.misc.playservice.is_21_24_or_greater
import io.github.nexfusion.morphe.youtube.misc.playservice.is_21_42_or_greater
import io.github.nexfusion.morphe.youtube.misc.settings.PreferenceScreen
import io.github.nexfusion.morphe.shared.misc.settings.preference.SwitchPreference
import io.github.nexfusion.morphe.youtube.video.information.VideoInformationPatch
import io.github.nexfusion.morphe.youtube.video.videoid.VideoId
import io.github.nexfusion.patch
import org.luckypray.dexkit.wrap.DexField
import org.luckypray.dexkit.wrap.DexMethod
import java.lang.ref.WeakReference
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method

// Port of upstream SkipLiveShortsPatch.kt (upstream commit 3731d97f6). Upstream adds an interface
// plus a `patch_skipToNextShort` method to the Shorts player class and hands the instance to the
// extension. Those are bytecode edits, so the runtime port resolves the same anchors
// (user action constructor, reel sequence controller method and instance field, player field)
// out of the reel playback fingerprints, and implements the controller interface with a Proxy.

internal object ReelPlaybackRepeatParentFingerprint : Fingerprint(
    returnType = "V",
    filters = listOf(
        methodCall(smali = "Lj$/time/Instant;->toEpochMilli()J"),
        string("r_tr"),
    )
)

@StrictQuery
internal object ReelPlaybackRepeatFingerprint : Fingerprint(
    classFingerprint = ReelPlaybackRepeatParentFingerprint,
    returnType = "V",
    parameters = listOf("L"),
    filters = listOf(
        anyInstruction(
            methodCall(smali = "Lcom/google/common/util/concurrent/ListenableFuture;->isDone()Z"),
            methodCall(smali = "Lj$/util/Optional;->ofNullable(Ljava/lang/Object;)Lj$/util/Optional;"), // 21.17+
        )
    )
)

/**
 * 21.42+
 */
internal object ReelPlaybackFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf("J"),
    filters = listOf(
        fieldAccess(
            definingClass = "Ljava/util/concurrent/TimeUnit;",
            name = "MILLISECONDS",
            opcode = Opcode.SGET_OBJECT
        ),
        methodCall(
            name = "<init>",
            parameters = listOf("I", "L", "L", "Z"),
            location = MatchAfterWithin(15)
        ),
        methodCall(
            opcode = Opcode.INVOKE_VIRTUAL,
            parameters = listOf("L"),
            returnType = "I",
            location = MatchAfterWithin(5)
        )
    )
)

internal object ReelPlaybackLegacyFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf("J"),
    filters = listOf(
        fieldAccess(
            definingClass = "Ljava/util/concurrent/TimeUnit;",
            name = "MILLISECONDS",
            opcode = Opcode.SGET_OBJECT
        ),
        methodCall(
            name = "<init>",
            parameters = listOf("I", "L", "L"),
            location = MatchAfterWithin(15)
        ),
        methodCall(
            opcode = Opcode.INVOKE_VIRTUAL,
            parameters = listOf("L"),
            returnType = "I",
            location = MatchAfterWithin(5)
        )
    )
)

/**
 * The anchors needed to dispatch the "skip to next Short" user action at runtime. Resolved once
 * through DexKit by [resolveReelSkipAnchors] and converted to reflection members by
 * [resolveReelSkipMembers].
 */
internal object ReelSkipAnchors {
    @Volatile var userActionConstructorDescriptor: String? = null
    @Volatile var controllerMethodDescriptor: String? = null
    @Volatile var playerClassDescriptor: String? = null
    @Volatile var controllerFieldDescriptor: String? = null
    @Volatile var playerFieldDescriptor: String? = null

    val isInitialized: Boolean
        get() = userActionConstructorDescriptor != null && controllerMethodDescriptor != null && playerClassDescriptor != null

    private fun getAppVersionCode(context: Context): Long = runCatching {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo(context.packageName, 0).versionCode.toLong()
    }.getOrDefault(0L)

    fun clear(context: Context) {
        userActionConstructorDescriptor = null
        controllerMethodDescriptor = null
        playerClassDescriptor = null
        controllerFieldDescriptor = null
        playerFieldDescriptor = null
        context.getSharedPreferences("nexfusion_anchors_reel_skip", Context.MODE_PRIVATE).edit().clear().apply()
    }

    fun save(context: Context) {
        if (!isInitialized) return
        val prefs = context.getSharedPreferences("nexfusion_anchors_reel_skip", Context.MODE_PRIVATE)

        val currentVersion = getAppVersionCode(context)
        prefs.edit()
            .putLong("cached_version_code", currentVersion)
            .putString("userActionConstructorDescriptor", userActionConstructorDescriptor)
            .putString("controllerMethodDescriptor", controllerMethodDescriptor)
            .putString("playerClassDescriptor", playerClassDescriptor)
            .putString("controllerFieldDescriptor", controllerFieldDescriptor)
            .putString("playerFieldDescriptor", playerFieldDescriptor)
            .apply()
    }

    fun load(context: Context): Boolean {
        val prefs = context.getSharedPreferences("nexfusion_anchors_reel_skip", Context.MODE_PRIVATE)

        val currentVersion = getAppVersionCode(context)
        val cachedVersion = prefs.getLong("cached_version_code", -1L)
        if (cachedVersion != currentVersion || currentVersion == 0L) {
            clear(context)
            return false
        }
        userActionConstructorDescriptor = prefs.getString("userActionConstructorDescriptor", null) ?: return false
        controllerMethodDescriptor = prefs.getString("controllerMethodDescriptor", null) ?: return false
        playerClassDescriptor = prefs.getString("playerClassDescriptor", null) ?: return false
        controllerFieldDescriptor = prefs.getString("controllerFieldDescriptor", null)
        playerFieldDescriptor = prefs.getString("playerFieldDescriptor", null)
        return true
    }
}

/**
 * Resolves and populates [ReelSkipAnchors] within the DexKit query context.
 */
private val resolveReelSkipAnchors: FindMethodFunc = findMethodDirect {
    val reelMatch = (if (is_21_42_or_greater) ReelPlaybackFingerprint else ReelPlaybackLegacyFingerprint)
        .match()
    val reelMethod = reelMatch.method
    // Instruction matches: [0] the MILLISECONDS field read, [1] the user action constructor,
    // [2] the reel sequence controller method the action is dispatched to.
    val matches = reelMatch.instructionMatches
    val userActionConstructor = matches[1].instruction.methodRef!!
    val controllerMethod = matches[2].instruction.methodRef!!

    val playerDescriptor = reelMethod.declaredClass!!.descriptor
    val controllerDescriptor = controllerMethod.declaredClass!!.descriptor

    var controllerFieldDescriptor: String? = null
    if (is_21_24_or_greater) {
        // Upstream getInstanceFieldGetFingerprint(controllerType).match(reel playback method):
        // the first IGET_OBJECT that reads a controller-typed field of the player class.
        controllerFieldDescriptor = reelMethod.instructions.firstOrNull { instruction ->
            val field = instruction.fieldRef
            Opcode.fromInt(instruction.opcode) == Opcode.IGET_OBJECT &&
                field != null &&
                field.declaredClassName == playerDescriptor &&
                field.typeName == controllerDescriptor
        }?.fieldRef?.descriptor
    }

    // Player instance field read in the repeat method (upstream getInstanceFieldGetFingerprint
    // for the player type, matched against the repeat method). Constrain to the field being
    // declared on the receiver's own class, the same way upstream does.
    val repeatMethod = ReelPlaybackRepeatFingerprint.match().method
    val playerFieldDescriptor = repeatMethod.instructions.firstOrNull { instruction ->
        val field = instruction.fieldRef
        Opcode.fromInt(instruction.opcode) == Opcode.IGET_OBJECT &&
            field != null &&
            field.typeName == playerDescriptor &&
            field.declaredClassName == repeatMethod.declaredClassName
    }?.fieldRef?.descriptor

    ReelSkipAnchors.userActionConstructorDescriptor = userActionConstructor.descriptor
    ReelSkipAnchors.controllerMethodDescriptor = controllerMethod.descriptor
    ReelSkipAnchors.playerClassDescriptor = playerDescriptor
    ReelSkipAnchors.controllerFieldDescriptor = controllerFieldDescriptor
    ReelSkipAnchors.playerFieldDescriptor = playerFieldDescriptor

    reelMethod
}

/**
 * Runtime implementation of the extension's `SkipLiveShortsPatch$ReelSequenceController`
 * interface: builds the same user action the app uses for autoplay and dispatches it on the reel
 * sequence controller, which the app uses to scroll the Shorts feed to the next Short.
 */
internal object ReelSkipController {
    @Volatile private var playerRef: WeakReference<Any>? = null

    @Volatile var userActionConstructor: Constructor<*>? = null

    @Volatile var controllerMethod: Method? = null

    @Volatile var controllerField: Field? = null

    fun setReelPlayer(player: Any) {
        playerRef = WeakReference(player)
    }

    fun skipToNextShort() {
        val player = playerRef?.get() ?: return
        val action = runCatching { buildUserAction() }.getOrNull() ?: return
        runCatching {
            val receiver = controllerField?.get(player) ?: player
            controllerMethod!!.invoke(receiver, action)
        }
    }

    private fun buildUserAction(): Any? {
        val constructor = userActionConstructor ?: return null
        val args = constructor.parameterTypes.mapIndexed { index, type ->
            when {
                // The playback action code for jumping to the next Short, as upstream hardcodes.
                index == 0 -> 3
                type == java.lang.Boolean.TYPE -> false
                type.isPrimitive -> 0
                else -> null
            }
        }.toTypedArray()
        return constructor.newInstance(*args)
    }
}

/**
 * Resolves the anchor descriptors into reflection members with the app class loader, and hooks the
 * method that carries the Shorts player so it can be handed to the extension controller listener.
 */
private fun PatchExecutor.resolveReelSkipMembers() {
    val anchors = ReelSkipAnchors
    val userActionConstructorDesc = anchors.userActionConstructorDescriptor ?: return
    val controllerMethodDesc = anchors.controllerMethodDescriptor ?: return

    val userActionConstructor = runCatching {
        DexMethod(userActionConstructorDesc).getConstructorInstance(classLoader)
    }.getOrNull()
    val controllerMethod = runCatching {
        DexMethod(controllerMethodDesc).getMethodInstance(classLoader)
    }.getOrNull()
    val controllerField = anchors.controllerFieldDescriptor?.let {
        runCatching { org.luckypray.dexkit.util.InstanceUtil.getFieldInstance(classLoader, DexField(it)) }.getOrNull()
    }
    val playerField = anchors.playerFieldDescriptor?.let {
        runCatching { org.luckypray.dexkit.util.InstanceUtil.getFieldInstance(classLoader, DexField(it)) }.getOrNull()
    }

    ReelSkipController.userActionConstructor = userActionConstructor
    ReelSkipController.controllerMethod = controllerMethod
    ReelSkipController.controllerField = controllerField

    // Hand the Shorts player to the skip logic through the same listener the extension exposes
    // (setReelSequenceController), with a Proxy implementing the controller interface.
    val skipClass = app.morphe.extension.youtube.patches.SkipLiveShortsPatch::class.java
    val controllerInterface = app.morphe.extension.youtube.patches.SkipLiveShortsPatch.ReelSequenceController::class.java
    val controllerProxy = java.lang.reflect.Proxy.newProxyInstance(
        skipClass.classLoader,
        arrayOf(controllerInterface),
    ) { _, method, _ ->
        if (method.name == "patch_skipToNextShort") {
            ReelSkipController.skipToNextShort()
        }
        null
    }
    app.morphe.extension.youtube.patches.SkipLiveShortsPatch.setReelSequenceController(
        controllerProxy as app.morphe.extension.youtube.patches.SkipLiveShortsPatch.ReelSequenceController
    )

    ReelPlaybackRepeatFingerprint.dexMethod.hookMethod {
        after { param ->
            val player = playerField?.get(param.thisObject) ?: param.thisObject
            ReelSkipController.setReelPlayer(player)
        }
    }
}

val SkipLiveShorts = patch(
    name = "Skip live Shorts",
    description = "Automatically skips live streams that appear in the Shorts feed."
) {
    dependsOn(
        VideoId,
        VideoInformationPatch,
    )

    PreferenceScreen.SHORTS.addPreferences(
        SwitchPreference("morphe_skip_live_shorts")
    )

    runCatching {
        if (!ReelSkipAnchors.load(appContext)) {
            ::resolveReelSkipAnchors.dexMethod
            ReelSkipAnchors.save(appContext)
        }
        try {
            resolveReelSkipMembers()
        } catch (e: Throwable) {
            ReelSkipAnchors.clear(appContext)
            ::resolveReelSkipAnchors.dexMethod
            ReelSkipAnchors.save(appContext)
            resolveReelSkipMembers()
        }
    }.onFailure {
        PatcherLog.error(it) { "Skip live shorts initialization failed: ${it.message ?: it.javaClass.simpleName}" }
    }
}

