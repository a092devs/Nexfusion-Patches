package io.github.a092devs.nexfusion.morphe.youtube.layout.captions

import android.content.Context

import app.morphe.extension.shared.Logger
import app.morphe.extension.youtube.patches.CaptionsInPipPatch
import io.github.a092devs.nexfusion.FindMethodFunc
import io.github.a092devs.nexfusion.PatchExecutor
import io.github.a092devs.nexfusion.PatcherLog
import io.github.a092devs.nexfusion.hookMethod
import io.github.a092devs.nexfusion.morphe.Fingerprint
import io.github.a092devs.nexfusion.morphe.InstructionLocation.MatchAfterImmediately
import io.github.a092devs.nexfusion.morphe.Opcode
import io.github.a092devs.nexfusion.morphe.findMethodDirect
import io.github.a092devs.nexfusion.morphe.methodCall
import io.github.a092devs.nexfusion.morphe.parameters
import io.github.a092devs.nexfusion.morphe.shared.misc.settings.preference.PreferenceScreenPreference
import io.github.a092devs.nexfusion.morphe.shared.misc.settings.preference.SwitchPreference
import io.github.a092devs.nexfusion.morphe.youtube.misc.playservice.VersionCheck
import io.github.a092devs.nexfusion.morphe.youtube.misc.playservice.is_20_29_or_greater
import io.github.a092devs.nexfusion.morphe.youtube.misc.playservice.is_21_02_or_greater
import io.github.a092devs.nexfusion.morphe.youtube.misc.playservice.is_21_42_or_greater
import io.github.a092devs.nexfusion.morphe.youtube.misc.settings.PreferenceScreen
import io.github.a092devs.nexfusion.patch
import org.luckypray.dexkit.util.InstanceUtil
import org.luckypray.dexkit.wrap.DexField
import org.luckypray.dexkit.wrap.DexMethod
import java.lang.reflect.Field

// Port of upstream CaptionsInPipPatch.kt (upstream commit 6c817e7da). Upstream mutates the local
// register that carries the isPipActive value into the player state constructor. That is a
// bytecode edit, so the runtime port instead mutates the player state's isPipActive field while
// the captions overlay checks its visibility, which the overlay reads to decide if it is shown.

private const val CAPTIONS_OVERLAY_NAME = "player_overlay_caption"

/**
 * The anchors needed to hide the player state's picture-in-picture from the captions overlay and
 * to detect re-setting of the current caption track. Resolved once through DexKit by
 * [resolveCaptionsInPipAnchors] and converted to reflection members by
 * [resolveCaptionsInPipMembers].
 */
internal object CaptionsInPipAnchors {
    /** Smali type descriptor of the player state class. */
    @Volatile var stateTypeDescriptor: String? = null

    /** Ordered field names the state's toString() prints. */
    @Volatile var fieldNames: List<String>? = null

    /** Constructor call argument index of isPipActive, excluding the state instance itself. */
    @Volatile var pipArgIndex: Int = -1

    /** Field of the player state class holding whether picture-in-picture is active. */
    @Volatile var pipFieldDescriptor: String? = null

    /** Descriptor of the player state constructor. */
    @Volatile var stateCtorDescriptor: String? = null

    /** Smali type descriptor of the captions overlay implementation class. */
    @Volatile var captionsOverlayClassDescriptor: String? = null

    /** Interface method names the player overlay layouts call for a visibility check. */
    @Volatile var overlayCheckNames: Set<String>? = null

    /** 21.02+ only: field of the caption track event handler holding the current track. */
    @Volatile var currentTrackFieldDescriptor: String? = null

    /** 21.02+ only: descriptor of the method that sets the caption track. */
    @Volatile var setTrackMethodDescriptor: String? = null

    val isInitialized: Boolean
        get() = pipFieldDescriptor != null && stateTypeDescriptor != null && captionsOverlayClassDescriptor != null

    private fun getAppVersionCode(context: Context): Long = runCatching {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo(context.packageName, 0).versionCode.toLong()
    }.getOrDefault(0L)

    fun clear(context: Context) {
        stateTypeDescriptor = null
        fieldNames = null
        pipArgIndex = -1
        pipFieldDescriptor = null
        stateCtorDescriptor = null
        captionsOverlayClassDescriptor = null
        overlayCheckNames = emptySet()
        currentTrackFieldDescriptor = null
        setTrackMethodDescriptor = null
        context.getSharedPreferences("nexfusion_anchors_captions_pip", Context.MODE_PRIVATE).edit().clear().apply()
    }

    fun save(context: Context) {
        if (!isInitialized) return
        val currentVersion = getAppVersionCode(context)
        val prefs = context.getSharedPreferences("nexfusion_anchors_captions_pip", Context.MODE_PRIVATE)
        prefs.edit()
            .putLong("cached_version_code", currentVersion)
            .putString("stateTypeDescriptor", stateTypeDescriptor)
            .putString("fieldNames", fieldNames?.joinToString(","))
            .putInt("pipArgIndex", pipArgIndex)
            .putString("pipFieldDescriptor", pipFieldDescriptor)
            .putString("stateCtorDescriptor", stateCtorDescriptor)
            .putString("captionsOverlayClassDescriptor", captionsOverlayClassDescriptor)
            .putStringSet("overlayCheckNames", overlayCheckNames)
            .putString("currentTrackFieldDescriptor", currentTrackFieldDescriptor)
            .putString("setTrackMethodDescriptor", setTrackMethodDescriptor)
            .apply()
    }

    fun load(context: Context): Boolean {
        val prefs = context.getSharedPreferences("nexfusion_anchors_captions_pip", Context.MODE_PRIVATE)
        val currentVersion = getAppVersionCode(context)
        val cachedVersion = prefs.getLong("cached_version_code", -1L)
        if (cachedVersion != currentVersion || currentVersion == 0L) {
            clear(context)
            return false
        }
        val pip = prefs.getString("pipFieldDescriptor", null) ?: return false
        stateTypeDescriptor = prefs.getString("stateTypeDescriptor", null) ?: return false
        fieldNames = prefs.getString("fieldNames", "")?.split(",") ?: return false
        pipArgIndex = prefs.getInt("pipArgIndex", -1)
        pipFieldDescriptor = pip
        stateCtorDescriptor = prefs.getString("stateCtorDescriptor", null) ?: return false
        captionsOverlayClassDescriptor = prefs.getString("captionsOverlayClassDescriptor", null) ?: return false
        overlayCheckNames = prefs.getStringSet("overlayCheckNames", null) ?: emptySet()
        currentTrackFieldDescriptor = prefs.getString("currentTrackFieldDescriptor", null)
        setTrackMethodDescriptor = prefs.getString("setTrackMethodDescriptor", null)
        return true
    }
}

/**
 * Runtime state of the resolved pipeline, read from hook callbacks.
 */
internal object CaptionsInPipRuntime {
    @Volatile var pipArgIndex: Int = -1

    @Volatile var pipField: Field? = null

    /** Whether the fieldTen has been checked against the pip constructor argument. */
    @Volatile var fieldChecked = false

    /** If false, the positional field resolution failed and the field must not be mutated. */
    @Volatile var fieldVerified = false
}

/**
 * Resolves and populates [CaptionsInPipAnchors] within the DexKit query context.
 */
private val resolveCaptionsInPipAnchors: FindMethodFunc = findMethodDirect {
    // The state's toString() prints its fields as a ';'-separated name list.
    val stateToStringMethod = PlayerOverlayStateToStringFingerprint.match().method
    val stateTypeDescriptor = stateToStringMethod.declaredClass!!.descriptor
    val fieldNames = stateToStringMethod.instructions.firstOrNull {
        it.string?.contains("isPipActive") == true
    }?.string!!.split(';')
    // Argument position of isPipActive when creating the player state. The constructor call
    // includes the state instance itself in register 0, which filters out here.
    val pipArgIndex = fieldNames.indexOf("isPipActive")
    if (pipArgIndex < 0) {
        throw Exception("Unexpected player state fields: $fieldNames")
    }

    // Creates the player state and asks an overlay if it is shown. More than one overlay layout
    // does this. The matched instruction data is only inspected here; the visible behavior is
    // changed by mutating the state passed to the captions overlay's visibility check.
    val visibilityFingerprint = Fingerprint(
        filters = listOf(
            methodCall(definingClass = stateTypeDescriptor, name = "<init>"),
            methodCall(
                opcode = Opcode.INVOKE_INTERFACE,
                parameters = listOf(stateTypeDescriptor),
                returnType = "Z",
                location = MatchAfterImmediately(),
            ),
        ),
    )
    val visibilityMatches = visibilityFingerprint.matchAllOrNull()
        ?: throw Exception("Could not resolve player overlay visibility methods")

    // The captions overlay implementation is found by the class holding its name, since only it
    // returns the constant 'player_overlay_caption'.
    val captionsOverlayClassDescriptor =
        CaptionsOverlayNameFingerprint.match().method.declaredClass!!.descriptor

    // The overlay layouts call one interface method for the visibility check; the captions
    // overlay implementation carries a same-named override of that check.
    val overlayCheckNames = visibilityMatches.mapNotNull {
        it.instructionMatches[1].instruction.methodRef?.name
    }.toSet()

    // Resolve the state constructor and map the isPipActive argument to its field. Positionally
    // (like upstream does), assuming the constructor assigns the boolean parameters to fields in
    // parameter order, and verifying that correspondence at runtime.
    val stateCtorMatch = visibilityMatches.firstOrNull { match ->
        val params = match.instructionMatches.first().instruction.methodRef?.parameters ?: return@firstOrNull false
        params.size == fieldNames.size
    } ?: visibilityMatches.first()
    val stateCtorMethod = stateCtorMatch.instructionMatches.first().instruction.methodRef!!
    val ctorParams = stateCtorMethod.parameters
    if (ctorParams.size != fieldNames.size) {
        throw Exception("Unexpected player state constructor parameters: $ctorParams, fields: $fieldNames")
    }
    if (ctorParams[pipArgIndex] != "Z") {
        throw Exception("Unexpected player state isPipActive parameter type: ${ctorParams[pipArgIndex]}")
    }
    val pipBooleanOrdinal = fieldNames.subList(0, pipArgIndex).withIndex()
        .count { (index, _) -> ctorParams[index] == "Z" }
    var seenBooleans = 0
    var pipFieldDescriptor: String? = null
    for (instruction in stateCtorMethod.instructions) {
        if (Opcode.fromInt(instruction.opcode) == Opcode.IPUT_BOOLEAN) {
            if (seenBooleans == pipBooleanOrdinal) {
                pipFieldDescriptor = instruction.fieldRef?.descriptor
                break
            }
            seenBooleans++
        }
    }
    val pipFieldDescriptorResolved =
        pipFieldDescriptor ?: throw Exception("Could not resolve isPipActive field of the player state")

    // The current caption track can be set again when entering or leaving picture-in-picture,
    // which clears the caption on screen until the next one.
    var currentTrackFieldDescriptor: String? = null
    var setTrackMethodDescriptor: String? = null
    if (is_21_02_or_greater) {
        val trackTypeDescriptor = runCatching {
            CaptionTrackIsDisableOptionFingerprint.match().method.declaredClass!!.descriptor
        }.getOrNull()

        if (trackTypeDescriptor != null) {
            val trackEventMatch = runCatching {
                getCaptionTrackEventFingerprint(trackTypeDescriptor).match()
            }.getOrNull()

            if (trackEventMatch != null) {
                // The first filter match reads the current track field of the event handler.
                currentTrackFieldDescriptor =
                    trackEventMatch.instructionMatches.first().instruction.fieldRef!!.descriptor
                // The last filter match sets the new track.
                setTrackMethodDescriptor =
                    trackEventMatch.instructionMatches.last().instruction.methodRef!!.descriptor
            }
        }
    }

    CaptionsInPipAnchors.stateTypeDescriptor = stateTypeDescriptor
    CaptionsInPipAnchors.fieldNames = fieldNames
    CaptionsInPipAnchors.pipArgIndex = pipArgIndex
    CaptionsInPipAnchors.pipFieldDescriptor = pipFieldDescriptorResolved
    CaptionsInPipAnchors.stateCtorDescriptor = stateCtorMethod.descriptor
    CaptionsInPipAnchors.captionsOverlayClassDescriptor = captionsOverlayClassDescriptor
    CaptionsInPipAnchors.overlayCheckNames = overlayCheckNames
    CaptionsInPipAnchors.currentTrackFieldDescriptor = currentTrackFieldDescriptor
    CaptionsInPipAnchors.setTrackMethodDescriptor = setTrackMethodDescriptor

    stateToStringMethod
}

/**
 * Converts an "Lcom/example/Class;" smali descriptor to a binary class name.
 */
private fun classBinaryName(descriptor: String) =
    descriptor.removePrefix("L").removeSuffix(";").replace('/', '.')

/**
 * Converts the anchor descriptors into reflection members with the app class loader, and hooks
 * the captions overlay visibility check plus the caption track setter.
 */
private fun PatchExecutor.resolveCaptionsInPipMembers() {
    val anchors = CaptionsInPipAnchors
    val pipFieldDesc = anchors.pipFieldDescriptor ?: return
    val stateCtorDesc = anchors.stateCtorDescriptor ?: return
    val stateTypeDesc = anchors.stateTypeDescriptor ?: return
    val captionsOverlayClassDesc = anchors.captionsOverlayClassDescriptor ?: return

    CaptionsInPipRuntime.pipArgIndex = anchors.pipArgIndex
    CaptionsInPipRuntime.pipField = runCatching {
        InstanceUtil.getFieldInstance(classLoader, DexField(pipFieldDesc))
    }.getOrNull()?.also { it.isAccessible = true }
        ?: run {
            Logger.printException { "Could not resolve isPipActive field: $pipFieldDesc" }
            return
        }

    // Verify the resolved field matches the pip constructor argument once a state is created,
    // since the positional mapping relies on constructor field assignment order.
    DexMethod(stateCtorDesc).hookMethod {
        after { param ->
            if (!CaptionsInPipRuntime.fieldChecked) {
                CaptionsInPipRuntime.fieldChecked = true
                val expected = param.args.getOrNull(CaptionsInPipRuntime.pipArgIndex) as? Boolean
                val actual = runCatching {
                    CaptionsInPipRuntime.pipField?.getBoolean(param.thisObject)
                }.getOrNull()
                CaptionsInPipRuntime.fieldVerified = expected != null && actual == expected
                if (!CaptionsInPipRuntime.fieldVerified) {
                    Logger.printException {
                        "Player state isPipActive field verification failed"
                    }
                }
            }
        }
    }

    // The captions overlay implementation carries the visibility check override. Resolve it from
    // the loaded implementation class, constrained to the same-named interface method the overlay
    // layouts call.
    val stateClass = classLoader.loadClass(classBinaryName(stateTypeDesc))
    val captionsOverlayClass =
        classLoader.loadClass(classBinaryName(captionsOverlayClassDesc))
    val allMethods = (captionsOverlayClass.methods.toList() + captionsOverlayClass.declaredMethods.toList()).distinct()
    val visibleCandidates = allMethods.filter {
        it.parameterTypes.size == 1 && it.parameterTypes[0] == stateClass &&
                it.returnType == java.lang.Boolean.TYPE
    }
    val checkNames = anchors.overlayCheckNames ?: emptySet()
    val visibilityMethod = visibleCandidates.singleOrNull()
        ?: visibleCandidates.filter { it.name in checkNames }.singleOrNull()
        ?: run {
            val methodsInfo = allMethods.map { m -> "${m.name}(${m.parameterTypes.map { it.name }.joinToString(",")}): ${m.returnType.name}" }
            Logger.printException { "Could not resolve captions overlay visibility method on $captionsOverlayClass. stateClass=$stateClass, checkNames=$checkNames, visibleCandidates=$visibleCandidates, allMethods=$methodsInfo" }
            null
        } ?: return

    // Report the state's picture-in-picture as inactive while the captions overlay checks its
    // visibility, so hiding overlays in picture-in-picture does not hide the captions.
    visibilityMethod.hookMethod {
        before { param ->
            val runtime = CaptionsInPipRuntime
            if (!runtime.fieldVerified) {
                return@before
            }
            val state = param.args.firstOrNull() ?: return@before
            runCatching {
                val field = runtime.pipField ?: return@before
                if (field.getBoolean(state) &&
                    !CaptionsInPipPatch.isPipActive(CAPTIONS_OVERLAY_NAME)
                ) {
                    field.setBoolean(state, false)
                }
            }
        }
    }

    anchors.setTrackMethodDescriptor?.let { setTrackDescriptor ->
        val currentTrackField = runCatching {
            InstanceUtil.getFieldInstance(
                classLoader, DexField(anchors.currentTrackFieldDescriptor!!)
            )
        }.getOrNull()?.also { it.isAccessible = true }
        if (currentTrackField == null) {
            Logger.printException {
                "Could not resolve current caption track field: ${anchors.currentTrackFieldDescriptor}"
            }
            return
        }

        DexMethod(setTrackDescriptor).hookMethod {
            before { param ->
                if (CaptionsInPipPatch.isCurrentTrack(
                        param.args.firstOrNull(), currentTrackField.get(param.thisObject)
                    )
                ) {
                    // Do not set the same caption track again; the caption stays on screen.
                    param.result = Unit
                }
            }
        }
    }
}

val CaptionsInPip = patch(
    name = "Captions in picture-in-picture",
    description = "Adds an option to show captions in picture-in-picture mode.",
    use = false,
) {
    dependsOn(VersionCheck)

    if (is_21_42_or_greater) {
        // YouTube 21.42+ refactored caption overlay rendering away from player overlay interfaces
        // to Litho components. Deactivate on 21.42+ to prevent broken toggles and resolution errors.
        return@patch
    }

    // Caption settings. Used to organize all caption related settings together,
    // mirroring the upstream 'morphe_captions_screen' preference group.
    PreferenceScreen.PLAYER.addPreferences(
        PreferenceScreenPreference(
            key = "morphe_captions_screen",
            sorting = PreferenceScreenPreference.Sorting.UNSORTED,
            preferences = setOf(SwitchPreference("morphe_captions_in_pip")),
        )
    )

    if (!is_20_29_or_greater) {
        // TODO: Versions before 20.29 hide overlays using the player type
        //  (upstream getPlayerTypeOverlayVisibilityFingerprint). Not ported,
        //  as the target device runs YouTube >= 21.05.
        return@patch
    }

    runCatching {
        if (!CaptionsInPipAnchors.load(appContext)) {
            ::resolveCaptionsInPipAnchors.dexMethod
            CaptionsInPipAnchors.save(appContext)
        }
        try {
            resolveCaptionsInPipMembers()
        } catch (e: Throwable) {
            CaptionsInPipAnchors.clear(appContext)
            ::resolveCaptionsInPipAnchors.dexMethod
            CaptionsInPipAnchors.save(appContext)
            resolveCaptionsInPipMembers()
        }
    }.onFailure {
        PatcherLog.error(it) { "Captions in PIP initialization failed: ${it.message ?: it.javaClass.simpleName}" }
    }
}
