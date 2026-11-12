package io.github.nexfusion.morphe.youtube.layout.hide.ambientmode

import android.content.Context

import android.view.View
import app.morphe.extension.shared.settings.preference.SeekBarPreference
import app.morphe.extension.youtube.patches.AmbientModePatch as ExtensionAmbientModePatch
import app.morphe.extension.youtube.settings.Settings
import io.github.nexfusion.FindMethodFunc
import io.github.nexfusion.PatchExecutor
import io.github.nexfusion.PatcherLog
import io.github.nexfusion.hookMethod
import io.github.nexfusion.morphe.findMethodDirect
import io.github.nexfusion.morphe.findFieldFromToString
import io.github.nexfusion.morphe.shared.misc.settings.preference.NonInteractivePreference
import io.github.nexfusion.morphe.shared.misc.settings.preference.PreferenceScreenPreference
import io.github.nexfusion.morphe.shared.misc.settings.preference.SwitchPreference
import io.github.nexfusion.morphe.youtube.insertLiteralOverride
import io.github.nexfusion.morphe.youtube.misc.settings.PreferenceScreen
import io.github.nexfusion.patch
import org.luckypray.dexkit.util.InstanceUtil
import org.luckypray.dexkit.wrap.DexField
import org.luckypray.dexkit.wrap.DexMethod
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/**
 * CinematicContainerRenderer field 3 (presentation style) DYNAMIC_BLURRED:
 * the background takes the colors of the video.
 */
private const val PRESENTATION_STYLE_DYNAMIC_BLURRED = 5

/**
 * Default CinematicContainerRenderer proto, with presentation style DYNAMIC_BLURRED and
 * field 11 (ambient mode on by default) = true.
 */
private const val DEFAULT_CINEMATIC_CONTAINER_RENDERER =
    "EgsQ94CqmwMdAAAAABIGEPeAqpsDEgsQgICAmAMdAACAPxgFIpEBCICAgPgPEICAgPgPGg0IkE4QiCcdAACAPyAUJQAAIEAoATUA" +
            "AIA/PQAAgkNFmpmZP00AAMA/VQAAgD9YAWpSDQAAIEAVAAAgQB0AACBAJQAAIEAyPBIREPeAqpsDHQAAAAAI94CqmwMSERCA" +
            "gICYAx0AAIA/CICAgJgDCAIdAAAAPyUAAAA/LQAAgD81AACAPw=="

/**
 * Anchors pulled at patch time from the cinematic container (upstream commit 0eda16c28),
 * converted to reflection members before hooking the renderer state constructor.
 */
internal object CinematicContainerForceAnchors {
    @Volatile var rendererClassDescriptor: String? = null
    @Volatile var hasBitsFieldDescriptor: String? = null
    @Volatile var styleHasBitMask: Int = 0
    @Volatile var styleFieldDescriptor: String? = null
    @Volatile var configFieldDescriptor: String? = null
    @Volatile var enabledByDefaultFieldDescriptor: String? = null
    @Volatile var stateTypeDescriptor: String? = null
    @Volatile var waitingStateFieldDescriptor: String? = null
    @Volatile var loadedStateFieldDescriptor: String? = null

    val isInitialized: Boolean
        get() = rendererClassDescriptor != null

    private fun getAppVersionCode(context: Context): Long = runCatching {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo(context.packageName, 0).versionCode.toLong()
    }.getOrDefault(0L)

    fun clear(context: Context) {
        rendererClassDescriptor = null
        hasBitsFieldDescriptor = null
        styleHasBitMask = 0
        styleFieldDescriptor = null
        configFieldDescriptor = null
        enabledByDefaultFieldDescriptor = null
        stateTypeDescriptor = null
        waitingStateFieldDescriptor = null
        loadedStateFieldDescriptor = null
        context.getSharedPreferences("nexfusion_anchors_ambient", Context.MODE_PRIVATE).edit().clear().apply()
    }

    fun save(context: Context) {
        if (!isInitialized) return
        val currentVersion = getAppVersionCode(context)
        context.getSharedPreferences("nexfusion_anchors_ambient", Context.MODE_PRIVATE).edit()
            .putLong("cached_version_code", currentVersion)
            .putString("rendererClassDescriptor", rendererClassDescriptor)
            .putString("hasBitsFieldDescriptor", hasBitsFieldDescriptor)
            .putInt("styleHasBitMask", styleHasBitMask)
            .putString("styleFieldDescriptor", styleFieldDescriptor)
            .putString("configFieldDescriptor", configFieldDescriptor)
            .putString("enabledByDefaultFieldDescriptor", enabledByDefaultFieldDescriptor)
            .putString("stateTypeDescriptor", stateTypeDescriptor)
            .putString("waitingStateFieldDescriptor", waitingStateFieldDescriptor)
            .putString("loadedStateFieldDescriptor", loadedStateFieldDescriptor)
            .apply()
    }

    fun load(context: Context): Boolean {
        val prefs = context.getSharedPreferences("nexfusion_anchors_ambient", Context.MODE_PRIVATE)
        val currentVersion = getAppVersionCode(context)
        val cachedVersion = prefs.getLong("cached_version_code", -1L)
        if (cachedVersion != currentVersion || currentVersion == 0L) {
            clear(context)
            return false
        }
        rendererClassDescriptor = prefs.getString("rendererClassDescriptor", null) ?: return false
        hasBitsFieldDescriptor = prefs.getString("hasBitsFieldDescriptor", null) ?: return false
        styleHasBitMask = prefs.getInt("styleHasBitMask", 0)
        styleFieldDescriptor = prefs.getString("styleFieldDescriptor", null) ?: return false
        configFieldDescriptor = prefs.getString("configFieldDescriptor", null) ?: return false
        enabledByDefaultFieldDescriptor = prefs.getString("enabledByDefaultFieldDescriptor", null) ?: return false
        stateTypeDescriptor = prefs.getString("stateTypeDescriptor", null) ?: return false
        waitingStateFieldDescriptor = prefs.getString("waitingStateFieldDescriptor", null) ?: return false
        loadedStateFieldDescriptor = prefs.getString("loadedStateFieldDescriptor", null) ?: return false
        return true
    }
}

private val resolveCinematicContainerForceAnchors: FindMethodFunc = findMethodDirect {
    val rendererData = runCatching { CinematicContainerRendererFingerprint.match().method }.getOrThrow()
    val rendererType = rendererData.declaredClass!!.descriptor
    CinematicContainerForceAnchors.rendererClassDescriptor = rendererType

    val styleMatches = runCatching {
        (cinematicContainerPresentationStyleFingerprint(rendererType).matchAllOrNull()?.firstOrNull { it.instructionMatchesOrNull != null }
            ?: cinematicContainerPresentationStyleFingerprint(rendererType).match()).instructionMatches
    }.getOrThrow()
    CinematicContainerForceAnchors.hasBitsFieldDescriptor = styleMatches[0].instruction.fieldRef!!.descriptor
    CinematicContainerForceAnchors.styleHasBitMask = styleMatches[1].instruction.literal!!.toInt()
    CinematicContainerForceAnchors.styleFieldDescriptor = styleMatches[3].instruction.fieldRef!!.descriptor

    val configMatch = runCatching {
        (cinematicContainerConfigFingerprint(rendererType).matchAllOrNull()?.firstOrNull { it.instructionMatchesOrNull != null }
            ?: cinematicContainerConfigFingerprint(rendererType).match())
    }.getOrThrow()
    val configField = configMatch.instructionMatches.last().instruction.fieldRef!!
    val configDescriptor = configField.type.descriptor
    CinematicContainerForceAnchors.configFieldDescriptor = configField.descriptor

    val enabledByDefaultField = runCatching {
        (cinematicContainerEnabledByDefaultFingerprint(configDescriptor).matchAllOrNull()?.firstOrNull { it.instructionMatchesOrNull != null }
            ?: cinematicContainerEnabledByDefaultFingerprint(configDescriptor).match()).instructionMatches[3].instruction.fieldRef!!
    }.getOrThrow()
    CinematicContainerForceAnchors.enabledByDefaultFieldDescriptor = enabledByDefaultField.descriptor

    val withStateToString = runCatching {
        CinematicContainerRendererWithStateFingerprint.match().method
    }.getOrThrow()
    val stateType = withStateToString.findFieldFromToString("rendererLoadedState=").typeName
    val stateDescriptor = if (stateType.startsWith("L")) stateType else "L${stateType.replace('.', '/')};"
    CinematicContainerForceAnchors.stateTypeDescriptor = stateDescriptor

    val stateMatches = runCatching {
        (cinematicContainerRendererLoadedStateFingerprint(stateDescriptor).matchAllOrNull()?.firstOrNull { it.instructionMatchesOrNull != null }
            ?: cinematicContainerRendererLoadedStateFingerprint(stateDescriptor).match()).instructionMatches
    }.getOrThrow()
    CinematicContainerForceAnchors.waitingStateFieldDescriptor = stateMatches[1].instruction.fieldRef!!.descriptor
    CinematicContainerForceAnchors.loadedStateFieldDescriptor = stateMatches[3].instruction.fieldRef!!.descriptor

    rendererData
}

private fun classBinaryName(descriptor: String) =
    descriptor.removePrefix("L").removeSuffix(";").replace('/', '.')

private fun findFieldRecursive(classLoader: ClassLoader, descriptor: String): java.lang.reflect.Field {
    val dexField = DexField(descriptor)
    val className = dexField.className
    val fieldName = dexField.name
    var clazz: Class<*>? = runCatching { classLoader.loadClass(className) }.getOrNull()
    while (clazz != null && clazz != Any::class.java) {
        try {
            val field = clazz.getDeclaredField(fieldName)
            field.isAccessible = true
            return field
        } catch (_: NoSuchFieldException) {
            clazz = clazz.superclass
        }
    }
    return InstanceUtil.getFieldInstance(classLoader, dexField).apply { isAccessible = true }
}

private fun PatchExecutor.installForceAmbientModeRenderer() {
    val anchors = CinematicContainerForceAnchors

    val rendererClass = classLoader.loadClass(classBinaryName(anchors.rendererClassDescriptor!!))
    val hasBitsField = findFieldRecursive(classLoader, anchors.hasBitsFieldDescriptor!!)
    val styleField = findFieldRecursive(classLoader, anchors.styleFieldDescriptor!!)
    val configField = findFieldRecursive(classLoader, anchors.configFieldDescriptor!!)
    val enabledByDefaultField = findFieldRecursive(classLoader, anchors.enabledByDefaultFieldDescriptor!!)

    val defaultRenderer = rendererClass.declaredFields.firstOrNull {
        Modifier.isStatic(it.modifiers) && it.type == rendererClass
    }?.apply { isAccessible = true }?.get(null)

    val parseFromSingleArg = runCatching {
        rendererClass.declaredMethods.firstOrNull {
            Modifier.isStatic(it.modifiers) && it.name == "parseFrom" &&
                it.parameterTypes.size == 1 && it.parameterTypes[0] == ByteArray::class.java
        }?.apply { isAccessible = true }
    }.getOrNull()

    val parseFromTwoArg = runCatching {
        var cls: Class<*>? = rendererClass
        var method: Method? = null
        while (cls != null && cls != Any::class.java && method == null) {
            method = cls.declaredMethods.firstOrNull {
                Modifier.isStatic(it.modifiers) && it.name == "parseFrom" &&
                    it.parameterTypes.size == 2 && it.parameterTypes[1] == ByteArray::class.java
            }
            cls = cls.superclass
        }
        method?.apply { isAccessible = true }
    }.getOrNull()

    val stateClass = classLoader.loadClass(classBinaryName(anchors.stateTypeDescriptor!!))
    val waitingState = findFieldRecursive(classLoader, anchors.waitingStateFieldDescriptor!!)
        .get(null)!!
    val loadedState = findFieldRecursive(classLoader, anchors.loadedStateFieldDescriptor!!)
        .get(null)!!

    // Runtime side note: the class of the wrapped renderer differs per host flavor, so both
    // methods are found from the argument's runtime class instead of being field constants.
    val rendererWithStateClass = classLoader.loadClass(
        classBinaryName(CinematicContainerRendererWithStateFingerprint.dexMethod.declaredClassName)
    )
    val constructor = rendererWithStateClass.declaredConstructors.first {
        it.parameterTypes.size == 2 && it.parameterTypes[0] == stateClass
    }
    val optionalClass = constructor.parameterTypes[1]
    val optionalPresentMethod = optionalClass.getMethod("isPresent")
    val optionalGetMethod = optionalClass.getMethod("get")
    val optionalOfMethod = optionalClass.getMethod("of", Object::class.java)
    val optionalEmptyMethod = optionalClass.getMethod("empty")

    constructor.hookMethod {
        before { param ->
            // Mutating the arguments replicates upstream's p1/p2 reassignment at the very
            // start of the constructor, before the constructor stores them.
            runCatching {
                val state = param.args[0]
                val optionalArgument = param.args[1] ?: return@runCatching

                // patch_forceAmbientModeRenderer: only forced while ambient mode is not disabled
                // in the settings and the state is still waiting for a /next response.
                // Ambient mode is disabled: clear renderer so 21.43+ (which removed the feature flags) stays off
                if (!ExtensionAmbientModePatch.disableAmbientMode(true)) {
                    param.args[1] = optionalEmptyMethod.invoke(null)
                    return@runCatching
                }

                // patch_forceAmbientModeRenderer: only forced while ambient mode is not disabled
                // in the settings and the state is still waiting for a /next response.
                if (state === waitingState) {
                    var renderer = if (optionalPresentMethod.invoke(optionalArgument) as Boolean) {
                        optionalGetMethod.invoke(optionalArgument)
                    } else {
                        // No /next response yet: fall back on the default renderer.
                        val data =
                            android.util.Base64.decode(DEFAULT_CINEMATIC_CONTAINER_RENDERER, android.util.Base64.DEFAULT)
                        runCatching {
                            when {
                                parseFromSingleArg != null -> parseFromSingleArg.invoke(null, data)
                                parseFromTwoArg != null && defaultRenderer != null ->
                                    parseFromTwoArg.invoke(null, defaultRenderer, data)
                                else -> defaultRenderer
                            }
                        }.getOrNull() ?: defaultRenderer
                    }

                    // Force presentation style DYNAMIC_BLURRED, and mark the corresponding
                    // has-bit so the style survives serialization.
                    hasBitsField.setInt(
                        renderer, hasBitsField.getInt(renderer) or anchors.styleHasBitMask
                    )
                    styleField.setInt(renderer, PRESENTATION_STYLE_DYNAMIC_BLURRED)

                    // Only the default: the flyout menu toggle is saved separately and still applies.
                    val config = configField.get(renderer)
                    if (config != null) {
                        enabledByDefaultField.setBoolean(config, true)
                    }

                    param.args[1] = optionalOfMethod.invoke(null, renderer)
                }

                // patch_getAmbientModeRendererState: any present renderer (possibly the forced
                // one just created above) reports as loaded.
                if (optionalPresentMethod.invoke(param.args[1]) as Boolean) {
                    param.args[0] = loadedState
                }
            }
        }
    }
}

/**
 * Adds options to bypass power saving restrictions for Ambient mode and disable it entirely
 * or in fullscreen.
 *
 * Ported from Morphe v1.45.0 ambientModePatch and v1.48.0 (upstream commit 0eda16c28):
 *  - "disable Ambient mode" forces the ambient experiment flags (45376186, 45372814) to
 *    follow the Morphe setting,
 *  - "Bypass Ambient mode restrictions" makes power save mode reports false;
 *  - "Disable Ambient mode in fullscreen" restores an opaque background after the player view
 *    lays out, reverting YouTube's fullscreen dim;
 *  - the /next response renderer is always forced to DYNAMIC_BLURRED and enabled by default,
 *    unless ambient mode is disabled in the Morphe settings.
 */
val AmbientMode = patch(
    name = "Ambient mode",
    description = "Forces Ambient mode on and adds options to bypass power saving restrictions " +
            "for Ambient mode and disable it entirely or in fullscreen.",
) {
    PreferenceScreen.PLAYER.addPreferences(
        PreferenceScreenPreference(
            key = "morphe_ambient_mode_screen",
            sorting = PreferenceScreenPreference.Sorting.UNSORTED,
            preferences = setOf(
                SwitchPreference("morphe_enhanced_ambient_mode", summary = true),
                SwitchPreference("morphe_enhanced_ambient_mode_readable_text", summary = true),
                SwitchPreference("morphe_enhanced_ambient_mode_dim_while_scrolling", summary = true),
                NonInteractivePreference(
                    key = "morphe_enhanced_ambient_mode_dim_duration",
                    tag = SeekBarPreference::class.java,
                    selectable = true,
                ),
                SwitchPreference("morphe_bypass_ambient_mode_restrictions", summary = true),
                SwitchPreference("morphe_disable_ambient_mode"),
                SwitchPreference("morphe_disable_fullscreen_ambient_mode"),
            ),
        ),
    )

    // Force ambient mode, unless it is disabled in the Morphe settings.
    // The flyout menu toggle of the player still turns it on and off.
    // 21.30+ inlines the flag lookups and must patch 4 places for each flag; each flag is on
    // if ambient mode is not disabled.
    insertLiteralOverride(45376186L) { _ -> ExtensionAmbientModePatch.disableAmbientMode(true) }
    insertLiteralOverride(45372814L) { _ -> ExtensionAmbientModePatch.disableAmbientMode(true) }

    // The /next response can have no CinematicContainerRenderer, a static color style
    // (the background does not change with the video, issue #3783) or ambient mode off by
    // default. Override what the server decides.
    runCatching {
        if (!CinematicContainerForceAnchors.load(appContext)) {
            ::resolveCinematicContainerForceAnchors.dexMethod
            CinematicContainerForceAnchors.save(appContext)
        }
        try {
            installForceAmbientModeRenderer()
        } catch (e: Throwable) {
            // Stale or invalid cached anchors: clear cache, re-resolve fresh, and retry once
            CinematicContainerForceAnchors.clear(appContext)
            ::resolveCinematicContainerForceAnchors.dexMethod
            CinematicContainerForceAnchors.save(appContext)
            installForceAmbientModeRenderer()
        }
    }.onFailure {
        PatcherLog.info(it) { "Force ambient mode hook skipped: ${it.message ?: it.javaClass.simpleName}" }
    }

    // Bypass Ambient mode restrictions: while the setting is on, pretend the device is not in
    // power save mode (power save is exactly what gates Ambient mode off by default).
    runCatching {
        DexMethod("Landroid/os/PowerManager;->isPowerSaveMode()Z").hookMethod {
            before { param ->
                if (Settings.BYPASS_AMBIENT_MODE_RESTRICTIONS.get()) {
                    param.result = false
                }
            }
        }
    }

    // Disable the fullscreen dim by restoring an opaque background after the player lays out.
    runCatching {
        SetFullScreenBackgroundColorFingerprint.hookMethod {
            after { param ->
                val view = param.thisObject as? View ?: return@after
                val color = ExtensionAmbientModePatch.getFullScreenBackgroundColor(0)
                if (color != 0) {
                    view.setBackgroundColor(color)
                }
            }
        }
    }
}
