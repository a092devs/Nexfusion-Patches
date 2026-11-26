package io.github.a092devs.nexfusion.nexfusion.reddit

import android.graphics.Canvas
import android.view.View
import app.morphe.extension.reddit.patches.GlassNavigationBarPatch as ExtGlassNav
import app.morphe.extension.reddit.settings.Settings
import app.morphe.extension.reddit.utils.ComposeUtils
import io.github.a092devs.nexfusion.PatcherLog
import io.github.a092devs.nexfusion.compat.XC_MethodHook
import io.github.a092devs.nexfusion.compat.XC_MethodReplacement
import io.github.a092devs.nexfusion.compat.XposedBridge
import io.github.a092devs.nexfusion.patch
import io.github.a092devs.nexfusion.scopedHook
import java.lang.reflect.Field
import java.lang.reflect.Member
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.IdentityHashMap

private class ModifierAdapter(var raw: Any) : ComposeUtils.Modifier {
    companion object {
        lateinit var bridge: FastComposeBridge
    }

    override fun patch_drawWithContent(draw: Function1<*, *>): Any =
        bridge.drawWithContentMethod.invoke(null, raw, bridge.wrapDrawFunction(draw))!!

    override fun patch_offset(x: Float, y: Float): Any =
        bridge.offsetMethod.invoke(null, raw, x, y)!!

    override fun patch_padding(start: Float, top: Float, end: Float, bottom: Float): Any =
        bridge.paddingMethod.invoke(null, raw, start, top, end, bottom)!!

    override fun patch_windowInsetsPadding(insets: ComposeUtils.WindowInsets): Any = raw
}

private fun unwrapModifier(obj: Any): Any =
    if (obj is ModifierAdapter) obj.raw else obj

private class ComposeCanvasAdapter : ComposeUtils.ComposeCanvas {
    lateinit var raw: Any

    override fun patch_getNativeCanvas(): Canvas =
        ModifierAdapter.bridge.internalCanvasField.get(raw) as Canvas

    override fun patch_setNativeCanvas(canvas: Canvas) {
        ModifierAdapter.bridge.internalCanvasField.set(raw, canvas)
    }
}

private class ContentDrawScopeAdapter : ComposeUtils.ContentDrawScope {
    lateinit var raw: Any
    private val canvasAdapter = ComposeCanvasAdapter()

    override fun patch_getCanvas(): ComposeUtils.ComposeCanvas {
        val b = ModifierAdapter.bridge
        val drawContext = b.drawContextMethod.invoke(raw)!!
        canvasAdapter.raw = b.drawContextCanvasMethod.invoke(drawContext)!!
        return canvasAdapter
    }

    override fun patch_getSize(): Long =
        ModifierAdapter.bridge.drawScopeSizeMethod.invoke(raw) as Long

    override fun patch_drawContent() {
        ModifierAdapter.bridge.drawContentMethod.invoke(raw)
    }

    override fun patch_drawRoundRect(color: Long, topLeft: Long, size: Long, cornerRadius: Long) {
        ModifierAdapter.bridge.drawRoundRectMethod.invoke(
            null,
            raw,
            color,
            topLeft,
            size,
            cornerRadius,
            null,
            0f,
            0xf0,
        )
    }
}

private class FastComposeBridge(
    val drawWithContentMethod: Method,
    val offsetMethod: Method,
    val paddingMethod: Method,
    val drawContentMethod: Method,
    val drawContextMethod: Method,
    val drawContextCanvasMethod: Method,
    val internalCanvasField: Field,
    val drawScopeSizeMethod: Method,
    val drawRoundRectMethod: Method,
    private val backgroundMethod: Method,
    private val railBackgroundMethod: Method,
) {
    private val appFunction1Class: Class<*> = drawWithContentMethod.parameterTypes[1]
    private val drawProxyCache = IdentityHashMap<Function1<*, *>, Any>()
    private val scopeThreadLocal = ThreadLocal.withInitial { ContentDrawScopeAdapter() }

    init {
        drawWithContentMethod.isAccessible = true
        offsetMethod.isAccessible = true
        paddingMethod.isAccessible = true
        drawContentMethod.isAccessible = true
        drawContextMethod.isAccessible = true
        drawContextCanvasMethod.isAccessible = true
        drawScopeSizeMethod.isAccessible = true
        drawRoundRectMethod.isAccessible = true
        backgroundMethod.isAccessible = true
        railBackgroundMethod.isAccessible = true
        ModifierAdapter.bridge = this
    }

    fun wrapModifier(rawModifier: Any): ComposeUtils.Modifier =
        if (rawModifier is ComposeUtils.Modifier) rawModifier else ModifierAdapter(rawModifier)

    fun wrapDrawFunction(fn: Function1<*, *>): Any {
        synchronized(drawProxyCache) {
            drawProxyCache[fn]?.let { return it }
        }
        @Suppress("UNCHECKED_CAST")
        val typedFn = fn as Function1<Any?, Any?>
        val proxy = Proxy.newProxyInstance(
            appFunction1Class.classLoader,
            arrayOf(appFunction1Class),
        ) { p, method, args ->
            when (method.name) {
                "invoke" -> {
                    val rawScope = args?.getOrNull(0) ?: return@newProxyInstance null
                    if (rawScope is ComposeUtils.ContentDrawScope) {
                        typedFn.invoke(rawScope)
                    } else {
                        val adapter = scopeThreadLocal.get()!!
                        val prev = if (adapter::class.java != Any::class.java) null else null
                        adapter.raw = rawScope
                        typedFn.invoke(adapter)
                    }
                }
                "hashCode" -> System.identityHashCode(p)
                "equals" -> p === args?.getOrNull(0)
                "toString" -> "NexFusionDrawProxy"
                else -> null
            }
        }
        synchronized(drawProxyCache) {
            drawProxyCache[fn] = proxy
        }
        return proxy
    }

    fun wrapNeutralColors(rawColors: Any): ExtGlassNav.NeutralColors {
        return object : ExtGlassNav.NeutralColors {
            override fun patch_getBackground(): Long = backgroundMethod.invoke(rawColors) as Long
            override fun patch_getRailBackground(): Long = railBackgroundMethod.invoke(rawColors) as Long
        }
    }
}

val GlassNavigationBar = patch(
    name = "Glass navigation bar",
    description = "Shows the Reddit bottom navigation bar, or tablet side rail, as a floating translucent glass capsule.",
    use = false,
) {
    val bottomNavBarMember = BottomNavBarFingerprint.memberOrNull
    if (bottomNavBarMember == null) {
        PatcherLog.info { "NexFusion: GlassNavigationBar BottomNavBarFingerprint not found; skipping" }
        return@patch
    }

    val drawWithContentMethod = ::drawWithContentMethodRef.method
    val surfaceMember = ::surfaceMethodRef.member

    val offsetMethod = OffsetFingerprint.memberOrNull as? Method ?: return@patch
    val paddingMethod = PaddingFingerprint.memberOrNull as? Method ?: return@patch
    val drawContentMethod = DrawContentFingerprint.memberOrNull as? Method ?: return@patch
    val drawRoundRectMethod = DrawRoundRectFingerprint.memberOrNull as? Method ?: return@patch
    val drawScopeSizeMethod = ::drawScopeSizeMethodRef.method
    val drawContextMethod = ::drawContextMethodRef.method
    val drawContextCanvasMethod = ::drawContextCanvasMethodRef.method
    val internalCanvasField = ::internalCanvasFieldRef.field.apply { isAccessible = true }

    val backdropMember: Member
    val backgroundMethod: Method
    val modernBackdrop = BottomNavBackdropFingerprint.memberOrNull
    if (modernBackdrop != null) {
        backdropMember = modernBackdrop
        backgroundMethod = ::modernBackgroundMethodRef.method
    } else {
        backdropMember = BottomNavBackdropLegacyFingerprint.memberOrNull ?: return@patch
        backgroundMethod = ::legacyBackgroundMethodRef.method
    }

    val railBgMember = RailBackgroundFingerprint.memberOrNull
    val railBackgroundMethod = if (railBgMember != null) {
        ::railBackgroundMethodRef.method
    } else {
        backgroundMethod
    }

    val bridge = FastComposeBridge(
        drawWithContentMethod = drawWithContentMethod,
        offsetMethod = offsetMethod,
        paddingMethod = paddingMethod,
        drawContentMethod = drawContentMethod,
        drawContextMethod = drawContextMethod,
        drawContextCanvasMethod = drawContextCanvasMethod,
        internalCanvasField = internalCanvasField,
        drawScopeSizeMethod = drawScopeSizeMethod,
        drawRoundRectMethod = drawRoundRectMethod,
        backgroundMethod = backgroundMethod,
        railBackgroundMethod = railBackgroundMethod,
    )

    // Search button (2026.47.0+)
    val navScreenStateCtor = NavScreenContentStateConstructorFingerprint.memberOrNull
    val homeTopBarMember = HomeTopBarFingerprint.memberOrNull
    val screenAttach = ScreenAttachFingerprint.memberOrNull
    val screenDetach = ScreenDetachFingerprint.memberOrNull
    val searchButtonSupported = navScreenStateCtor != null &&
        homeTopBarMember != null &&
        screenAttach != null &&
        screenDetach != null
    if (searchButtonSupported) {
        runCatching {
            val method = ExtGlassNav::class.java.getDeclaredMethod("isSearchButtonSupported")
            XposedBridge.hookMethod(method, XC_MethodReplacement.returnConstant(true))
        }
    }

    // Side rail
    SideNavRailContentFingerprint.memberOrNull?.let { sideRailMember ->
        val dpCompareMember = ::sideRailDpCompareMethodRef.member
        val railModifierThenMember = ::sideRailModifierThenMethodRef.member
        val labelCtor = ::sideRailLabelCtorRef.member

        XposedBridge.hookMethod(
            sideRailMember,
            scopedHook(dpCompareMember) {
                before { param ->
                    ExtGlassNav.setRailHeight(param.args[0] as Float)
                }
            },
        )
        XposedBridge.hookMethod(
            sideRailMember,
            scopedHook(railModifierThenMember) {
                before { param ->
                    val rawMod = param.args[1] ?: return@before
                    param.args[1] = unwrapModifier(ExtGlassNav.getRailModifier(bridge.wrapModifier(rawMod)))
                }
            },
        )
        XposedBridge.hookMethod(
            labelCtor,
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    param.args[1] = ExtGlassNav.showLabel(param.args[1] as Boolean) as Any
                }
            },
        )
    }

    if (railBgMember != null) {
        val inRailBg = ThreadLocal.withInitial { false }
        XposedBridge.hookMethod(
            railBgMember,
            scopedHook(railBackgroundMethod) {
                after { param ->
                    if (inRailBg.get() == true) return@after
                    val rawColors = param.thisObject ?: return@after
                    inRailBg.set(true)
                    try {
                        param.result = ExtGlassNav.getRailBackground(bridge.wrapNeutralColors(rawColors))
                    } finally {
                        inRailBg.set(false)
                    }
                }
            },
        )
    }

    // Bottom navigation bar backdrop color
    XposedBridge.hookMethod(
        backdropMember,
        scopedHook(backgroundMethod) {
            after { param ->
                param.result = ExtGlassNav.getBarColor(param.result as Long)
            }
        },
    )

    // Bottom navigation bar Surface + drawWithContent
    XposedBridge.hookMethod(
        bottomNavBarMember,
        scopedHook(drawWithContentMethod) {
            before { param ->
                val barDraw = ExtGlassNav.getBarDraw(null) ?: return@before
                param.args[1] = bridge.wrapDrawFunction(barDraw)
            }
        },
    )
    XposedBridge.hookMethod(
        bottomNavBarMember,
        scopedHook(surfaceMember) {
            before { param ->
                val rawMod = param.args[0] ?: return@before
                param.args[0] = unwrapModifier(ExtGlassNav.getBarModifier(bridge.wrapModifier(rawMod)))
                param.args[2] = ExtGlassNav.getBarElevation(param.args[2] as Float)
                param.args[3] = ExtGlassNav.getBarColor(param.args[3] as Long)
            }
        },
    )

    // BottomNavStackHost content modifier (frosted blur)
    val modifierCompanion = runCatching {
        ::modifierCompanionFieldRef.field.apply { isAccessible = true }.get(null)
    }.getOrNull()
    BottomNavStackHostFingerprint.memberOrNull?.let { hostCaller ->
        val navStackHostMember = ::navStackHostMethodRef.member
        XposedBridge.hookMethod(
            hostCaller,
            scopedHook(navStackHostMember) {
                before { param ->
                    var flags = param.args[6] as Int
                    if ((flags and 0x1) != 0 && modifierCompanion != null) {
                        param.args[1] = modifierCompanion
                        flags = flags and -0x2
                        param.args[6] = flags
                    }
                    val rawMod = param.args[1] ?: return@before
                    param.args[1] = unwrapModifier(ExtGlassNav.getContentModifier(bridge.wrapModifier(rawMod)))
                }
            },
        )
    }

    // Bar placement (slide + lift) - only hook if lift/blur/search button needs offset tracking
    val needsPlacementHook = Settings.GLASS_NAVIGATION_BAR.get() && (
        Settings.GLASS_NAVIGATION_BAR_POSITION.get() > 0 ||
            Settings.GLASS_NAVIGATION_BAR_STYLE.get() == ExtGlassNav.GlassStyle.FROSTED ||
            (searchButtonSupported && Settings.GLASS_NAVIGATION_BAR_SEARCH_BUTTON.get())
        )
    if (needsPlacementHook) {
        BottomNavBarPlacementFingerprint.memberOrNull?.let { placementMember ->
            val barHeightField = ::barHeightFieldRef.field.apply { isAccessible = true }
            val roundToIntMember = ::roundToIntMethodRef.member
            val currentThis = ThreadLocal<Any?>()
            XposedBridge.hookMethod(
                placementMember,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        currentThis.set(param.thisObject)
                    }

                    override fun afterHookedMethod(param: MethodHookParam) {
                        currentThis.remove()
                    }
                },
            )
            XposedBridge.hookMethod(
                placementMember,
                scopedHook(roundToIntMember) {
                    before { param ->
                        val outer = currentThis.get() ?: return@before
                        if (!barHeightField.declaringClass.isInstance(outer)) return@before
                        val barHeight = barHeightField.getInt(outer).toFloat()
                        param.args[0] = ExtGlassNav.getBarSlide(param.args[0] as Float, barHeight)
                    }
                },
            )
        }
    }

    // Static bottom navigation bar modifier (Inbox / You tabs)
    StaticBottomNavBarFingerprint.memberOrNull?.let { staticBarMember ->
        val modifierMember = ::staticBarModifierMethodRef.member
        XposedBridge.hookMethod(
            staticBarMember,
            scopedHook(modifierMember) {
                before { param ->
                    val rawMod = param.args[1] ?: return@before
                    param.args[1] = unwrapModifier(ExtGlassNav.getStaticBarModifier(bridge.wrapModifier(rawMod)))
                }
            },
        )
    }

    // Create button offset (2026.45.0+)
    CreateButtonOffsetFingerprint.memberOrNull?.let { createBtnMember ->
        val mathMaxMember = ::createBtnMathMaxMethodRef.member
        XposedBridge.hookMethod(
            createBtnMember,
            scopedHook(mathMaxMember) {
                before { param ->
                    val barHeight = (param.args[0] as Int).toFloat()
                    param.args[0] = Math.round(ExtGlassNav.getCreateButtonOffset(barHeight))
                }
            },
        )
    }

    // Search button hooks (2026.47.0+)
    if (searchButtonSupported) {
        XposedBridge.hookMethod(
            navScreenStateCtor!!,
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    ExtGlassNav.setShowBottomNav(param.args[0] as Boolean)
                    param.args[1] = ExtGlassNav.padContentForBottomNav(param.args[1] as Boolean)
                }
            },
        )
        XposedBridge.hookMethod(
            screenAttach!!,
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    ExtGlassNav.onScreenAttached(param.thisObject, param.args[0] as? View)
                }
            },
        )
        XposedBridge.hookMethod(
            screenDetach!!,
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    ExtGlassNav.onScreenDetached(param.thisObject, param.args[0] as? View)
                }
            },
        )
        val menuInvoke = ::homeMenuInvokeMethodRef.member
        val searchFieldMethod = ::homeSearchFieldMethodRef.method
        val avatarMember = ::homeAvatarMethodRef.member
        val modifierClass = drawWithContentMethod.parameterTypes[0]
        XposedBridge.hookMethod(
            homeTopBarMember!!,
            scopedHook(menuInvoke) {
                before { param ->
                    val rawMod = param.args.getOrNull(0) ?: return@before
                    if (modifierClass.isInstance(rawMod)) {
                        param.args[0] = unwrapModifier(ExtGlassNav.getTopBarButtonModifier(bridge.wrapModifier(rawMod)))
                    }
                }
            },
        )
        val fieldParams = searchFieldMethod.parameterTypes
        val clickIdx = fieldParams.indexOfFirst { it == Function0::class.java }
        val modIdx = fieldParams.indexOfFirst { it == modifierClass }
        XposedBridge.hookMethod(
            homeTopBarMember,
            scopedHook(searchFieldMethod) {
                before { param ->
                    if (clickIdx >= 0) {
                        val fn = param.args[clickIdx] as? () -> Any?
                        if (fn != null) {
                            param.args[clickIdx] = ExtGlassNav.getSearchFieldClick(fn)
                        }
                    }
                    if (modIdx >= 0) {
                        val rawMod = param.args[modIdx]
                        if (rawMod != null) {
                            param.args[modIdx] = unwrapModifier(ExtGlassNav.getSearchFieldModifier(bridge.wrapModifier(rawMod)))
                        }
                    }
                }
            },
        )
        XposedBridge.hookMethod(
            homeTopBarMember,
            scopedHook(avatarMember) {
                before { param ->
                    val rawMod = param.args[2] ?: return@before
                    param.args[2] = unwrapModifier(ExtGlassNav.getTopBarButtonModifier(bridge.wrapModifier(rawMod)))
                }
            },
        )
    }
}
