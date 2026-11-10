package io.github.nexfusion.nexfusion.linkedin

import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Resources
import android.content.res.TypedArray
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import io.github.nexfusion.compat.XC_MethodHook
import io.github.nexfusion.compat.XC_MethodHook.MethodHookParam
import io.github.nexfusion.compat.XposedBridge
import io.github.nexfusion.IHook
import io.github.nexfusion.PatcherLog
import io.github.nexfusion.hookMethod
import io.github.nexfusion.patch
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap

// Ported from heyymichii/michii-patches (GPL-3.0):
// - extensions/extension/src/main/java/app/linkedin/extension/MessagingPatch.java
// - extensions/extension/src/main/java/app/linkedin/extension/DoubleTapPatch.java
// - extensions/extension/src/main/java/app/linkedin/extension/HideAdsPatch.java and SduiComponentFilter.java
// - extensions/extension/src/main/java/app/linkedin/extension/MaterialYou.java
// - patches/src/main/kotlin/app/linkedin/patches/{messaging,feed,ads,theme}/

val HideSponsoredMessages = patch(
    name = "Hide sponsored messages",
    description = "Removes sponsored InMail conversations from the messaging list.",
) {
    try {
        val input = classLoader.loadClass(
            "com.linkedin.android.messaging.conversationlist.ConversationListItemTransformer\$TransformerInput",
        )
        val constructor = input.declaredConstructors.firstOrNull { candidate ->
            val types = candidate.parameterTypes
            types.size == 5 &&
                types[0] == List::class.java &&
                types[1] == List::class.java &&
                types[2] == ArrayList::class.java &&
                types[3] == Boolean::class.javaPrimitiveType
        } ?: throw NoSuchMethodException("TransformerInput(List, List, ArrayList, boolean, Urn)")
        XposedBridge.hookMethod(
            constructor,
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val conversations = param.args.getOrNull(0) as? List<*> ?: return
                    param.args[0] = conversations.filterNot(::isSponsoredConversation)
                }
            },
        )
    } catch (t: Throwable) {
        PatcherLog.info(t) { "NexFusion: LinkedIn conversation list target not found" }
    }
}

val GhostNoTypingIndicator = patch(
    name = "Ghost mode: hide typing indicator",
    description = "Stops LinkedIn from sending the \"typing...\" state to the other person.",
) {
    try {
        val store = classLoader.loadClass(
            "com.linkedin.android.messenger.data.networking.impl.ConversationWriteNetworkStoreImpl",
        )
        val success = classLoader.loadClass("com.linkedin.android.architecture.data.Resource")
            .getMethod("success", Any::class.java)
        val targets = store.declaredMethods.filter {
            it.name == "sendTypingIndicator" && it.returnType == Any::class.java
        }
        if (targets.isEmpty()) throw NoSuchMethodException("sendTypingIndicator(...)")
        val hook = object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                param.result = success.invoke(null, null as Any?)
            }
        }
        targets.forEach { XposedBridge.hookMethod(it, hook) }
    } catch (t: Throwable) {
        PatcherLog.info(t) { "NexFusion: LinkedIn typing indicator target not found" }
    }
}

val GhostNoReadReceipts = patch(
    name = "Ghost mode: no read receipts",
    description = "Keeps conversations unread on the other side while you are chatting.",
) {
    try {
        val impl = classLoader.loadClass("com.linkedin.android.messaging.sdk.MessagingSdkWriteFlowFeatureImpl")
        val targets = impl.declaredMethods.filter {
            it.name == "updateConversationReadStatus" &&
                it.parameterTypes.size == 2 &&
                it.parameterTypes[0] == List::class.java &&
                it.parameterTypes[1] == Boolean::class.javaPrimitiveType
        }
        if (targets.isEmpty()) throw NoSuchMethodException("updateConversationReadStatus(List, boolean)")
        val liveDataClass = classLoader.loadClass("androidx.lifecycle.MutableLiveData")
        val hook = object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                if (param.args.getOrNull(1) != true) return
                if (!calledFromChatList()) return
                param.result = liveDataClass.getDeclaredConstructor().newInstance()
            }
        }
        targets.forEach { XposedBridge.hookMethod(it, hook) }
    } catch (t: Throwable) {
        PatcherLog.info(t) { "NexFusion: LinkedIn read status target not found" }
    }
}

val DisableDoubleTapLike = patch(
    name = "Disable double-tap like",
    description = "Stops a double tap on a feed post from liking it.",
) {
    try {
        val clickActions = classLoader.loadClass("com.linkedin.sdui.viewdata.action.ClickActions")
        val targets = clickActions.declaredConstructors.filter { candidate ->
            val types = candidate.parameterTypes
            types.size >= 3 &&
                (0..2).all { types[it].name == "com.linkedin.sdui.viewdata.action.ActionListViewData" }
        }
        if (targets.isEmpty()) throw NoSuchMethodException("ClickActions(ActionListViewData x3)")
        val hook = object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                param.args[2] = null
            }
        }
        targets.forEach { XposedBridge.hookMethod(it, hook) }
    } catch (t: Throwable) {
        PatcherLog.info(t) { "NexFusion: LinkedIn click actions target not found" }
    }
}

val HideFeedAds = patch(
    name = "Hide feed ads",
    description = "Removes promoted posts from the feed, in the server-driven and legacy lists.",
) {
    hookSduiTransformer { component -> isSponsoredComponent(component) }

    // Legacy feed: the model filter rebuilds the collection through copyWithNewElements.
    try {
        val template = classLoader.loadClass("com.linkedin.android.pegasus.gen.collection.CollectionTemplate")
        val targets = template.declaredMethods.filter {
            it.name == "copyWithNewElements" && it.parameterTypes.contentEquals(arrayOf(List::class.java))
        }
        if (targets.isEmpty()) throw NoSuchMethodException("CollectionTemplate.copyWithNewElements(List)")
        val hook = object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val updates = param.args.getOrNull(0) as? List<*> ?: return
                param.args[0] = filterSponsoredUpdates(updates)
            }
        }
        targets.forEach { XposedBridge.hookMethod(it, hook) }
    } catch (t: Throwable) {
        PatcherLog.info(t) { "NexFusion: LinkedIn collection template target not found" }
    }
}

val HideSuggestedPosts = patch(
    name = "Hide suggested posts",
    description = "Removes \"Suggested\" posts from outside your network from the feed.",
) {
    hookSduiTransformer { component -> isSuggestedComponent(component) }
}

val HidePromotedJobs = patch(
    name = "Hide promoted jobs",
    description = "Removes promoted job listings from the Jobs tab and job search.",
) {
    hookSduiTransformer { component -> isPromotedJobComponent(component) }
}

val HidePremiumUpsells = patch(
    name = "Hide Premium upsells",
    description = "Removes Premium and AI upsell cards and banners, including on profiles and the Me panel.",
) {
    hookSduiTransformer { component -> isPremiumUpsellComponent(component) }

    try {
        val navPanelTransformer = classLoader.loadClass("com.linkedin.android.home.navpanel.HomeNavPanelTransformer")
        val targets = navPanelTransformer.declaredMethods.filter {
            it.name == "toPremiumViewData" && it.parameterTypes.size == 1
        }
        val hook = object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                param.args[0] = null
            }
        }
        targets.forEach { XposedBridge.hookMethod(it, hook) }
    } catch (t: Throwable) {
        PatcherLog.info(t) { "NexFusion: LinkedIn HomeNavPanelTransformer.toPremiumViewData target not found" }
    }
}

val ExpandPosts = patch(
    name = "Expand posts",
    description = "Shows posts fully expanded by default without having to tap '...see more'.",
) {
    registerSduiTransformer { component, viewName, payload ->
        if (viewName == "feed-commentary" && payload != null) {
            expandCommentary(component, payload)
        } else null
    }
}

val FeedFilters = patch(
    name = "Feed filters",
    description = "Filters celebrations, job cards, reposts, videos, reaction counts, AI coach prompts, puzzle games, recommendations, and new posts pills.",
    use = false,
) {
    hookSduiTransformer { component -> isFeedFilteredComponent(component) }
}

val PitchBlackTheme = patch(
    name = "Pitch black theme",
    description = "Replaces the dark mode gray backgrounds with pure AMOLED pitch black (#000000).",
    use = false,
) {
    val colors = LinkedInPitchBlackColors()

    PorterDuffColorFilter::class.java.getConstructor(Int::class.javaPrimitiveType, PorterDuff.Mode::class.java).hookMethod {
        before { it.args[0] = colors.map(it.args[0] as Int) }
    }
    ColorStateList::class.java.getMethod("getColorForState", IntArray::class.java, Int::class.javaPrimitiveType).hookMethod {
        after { param ->
            param.result = colors.map(param.result as Int)
        }
    }
    Color::class.java.getMethod("parseColor", String::class.java).hookMethod {
        after { it.result = colors.map(it.result as Int) }
    }
    Paint::class.java.getMethod("setColor", Int::class.javaPrimitiveType).hookMethod {
        before { it.args[0] = colors.map(it.args[0] as Int) }
    }
    Resources::class.java.getMethod("getColor", Int::class.javaPrimitiveType, Resources.Theme::class.java).hookMethod {
        after { it.result = colors.map(it.result as Int) }
    }
    TypedArray::class.java.getMethod("getColor", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType).hookMethod {
        after { it.result = colors.map(it.result as Int) }
    }
}

val MaterialYouTheme = patch(
    name = "Material You theme",
    description = "Applies dynamic wallpaper colors to LinkedIn accents and card backgrounds on Android 12+.",
    use = false,
) {
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
        val colors = LinkedInMaterialYouColors(appContext)

        PorterDuffColorFilter::class.java.getConstructor(Int::class.javaPrimitiveType, PorterDuff.Mode::class.java).hookMethod {
            before { it.args[0] = colors.map(it.args[0] as Int) }
        }
        ColorStateList::class.java.getMethod("getColorForState", IntArray::class.java, Int::class.javaPrimitiveType).hookMethod {
            after { param ->
                param.result = colors.map(param.result as Int)
            }
        }
        Color::class.java.getMethod("parseColor", String::class.java).hookMethod {
            after { it.result = colors.map(it.result as Int) }
        }
        Paint::class.java.getMethod("setColor", Int::class.javaPrimitiveType).hookMethod {
            before { it.args[0] = colors.map(it.args[0] as Int) }
        }
        Resources::class.java.getMethod("getColor", Int::class.javaPrimitiveType, Resources.Theme::class.java).hookMethod {
            after { it.result = colors.map(it.result as Int) }
        }
        TypedArray::class.java.getMethod("getColor", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType).hookMethod {
            after { it.result = colors.map(it.result as Int) }
        }
    }
}

private val sduiPredicates = mutableListOf<(Any) -> Boolean>()
private val sduiTransformers = mutableListOf<(Any, String, ByteArray?) -> Any?>()
private var sduiTransformerHookInstalled = false

/** Hooks SDUI ComponentTransformer.doTransform to filter components matching [predicate]. */
private fun IHook.hookSduiTransformer(predicate: (Any) -> Boolean) {
    synchronized(sduiPredicates) {
        sduiPredicates.add(predicate)
    }
    ensureSduiTransformerHook()
}

private fun IHook.registerSduiTransformer(transformer: (Any, String, ByteArray?) -> Any?) {
    synchronized(sduiTransformers) {
        sduiTransformers.add(transformer)
    }
    ensureSduiTransformerHook()
}

private fun IHook.ensureSduiTransformerHook() {
    if (sduiTransformerHookInstalled) return
    sduiTransformerHookInstalled = true
    try {
        val transformer = classLoader.loadClass("com.linkedin.sdui.transformer.impl.ComponentTransformer")
        val targets = transformer.declaredMethods.filter {
            it.name == "doTransform" &&
                it.parameterTypes.firstOrNull()?.name == "proto.sdui.components.core.Component"
        }
        if (targets.isEmpty()) throw NoSuchMethodException("ComponentTransformer.doTransform(Component, ...)")
        val hook = object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val component = param.args.getOrNull(0) ?: return
                val predicates = synchronized(sduiPredicates) { sduiPredicates.toList() }
                for (p in predicates) {
                    if (p(component)) {
                        param.result = null
                        return
                    }
                }

                val viewTracking = if (callOrNull(component, "hasViewTracking") == true) {
                    callOrNull(component, "getViewTracking")
                } else null
                val viewName = callOrNull(viewTracking, "getViewName") as? String ?: ""
                val payload = callOrNull(component, "toByteArray") as? ByteArray

                if (payload != null) {
                    if (viewName == "feed-full-update") {
                        PostMenuCache.remember(payload)
                    } else if (viewName == "feed-control-menu-page") {
                        val injected = PostMenuInjector.inject(component, payload)
                        if (injected !== component) {
                            param.args[0] = injected
                        }
                    }
                }

                val transformers = synchronized(sduiTransformers) { sduiTransformers.toList() }
                for (t in transformers) {
                    val currentComp = param.args.getOrNull(0) ?: component
                    val currentPayload = callOrNull(currentComp, "toByteArray") as? ByteArray
                    val transformed = t(currentComp, viewName, currentPayload)
                    if (transformed != null) {
                        param.args[0] = transformed
                    }
                }
            }
        }
        targets.forEach { XposedBridge.hookMethod(it, hook) }
    } catch (t: Throwable) {
        PatcherLog.info(t) { "NexFusion: LinkedIn SDUI transformer target not found" }
    }
}

/** Sponsored conversation list items, the same two markers upstream's isSponsored checks. */
private fun isSponsoredConversation(conversation: Any?): Boolean {
    if (conversation == null) return false
    val contentMetadata = fieldValue(fieldValue(conversation, "entityData"), "contentMetadata")
    if (fieldValue(contentMetadata, "hasConversationAdContentValue") == true) return true
    val latestMessage = fieldValue(conversation, "latestMessage") ?: return false
    return runCatching {
        val loader = latestMessage.javaClass.classLoader ?: return false
        val messageItemKt = loader.loadClass("com.linkedin.android.messenger.data.extensions.MessageItemKt")
        val isSpInMail = messageItemKt.getMethod("isSpInMail", latestMessage.javaClass)
        isSpInMail.invoke(null, latestMessage) == true
    }.getOrDefault(false)
}

/** Automatic read marks only come from the open chat screen, like upstream's stack check. */
private fun calledFromChatList(): Boolean =
    Throwable().stackTrace.any { it.className.startsWith("com.linkedin.android.messaging.messagelist.") }

/** A feed-full-update component that is an ad: sponsored tracking metadata or the payload marker. */
private fun isSponsoredComponent(component: Any): Boolean {
    return try {
        val viewTracking = if (callOrNull(component, "hasViewTracking") == true) {
            callOrNull(component, "getViewTracking")
        } else {
            null
        }
        callOrNull(viewTracking, "getViewName") == "feed-full-update" &&
            (hasSponsoredTracking(viewTracking) || payloadContains(component, SPONSORED_CONTENT_MARKER))
    } catch (_: Throwable) {
        false
    }
}

private fun isSuggestedComponent(component: Any): Boolean {
    return try {
        val viewTracking = if (callOrNull(component, "hasViewTracking") == true) {
            callOrNull(component, "getViewTracking")
        } else {
            null
        }
        if (callOrNull(viewTracking, "getViewName") != "feed-full-update") return false
        val payload = callOrNull(component, "toByteArray") as? ByteArray ?: return false
        hasExactString(payload, "Suggested", "Disarankan")
    } catch (_: Throwable) {
        false
    }
}

private fun isPromotedJobComponent(component: Any): Boolean {
    return try {
        val viewTracking = if (callOrNull(component, "hasViewTracking") == true) {
            callOrNull(component, "getViewTracking")
        } else {
            null
        }
        if (callOrNull(viewTracking, "getViewName") != "job-card") return false
        val payload = callOrNull(component, "toByteArray") as? ByteArray ?: return false
        hasExactString(payload, "Promoted", "Dipromosikan")
    } catch (_: Throwable) {
        false
    }
}

private fun isPremiumUpsellComponent(component: Any): Boolean {
    return try {
        val viewTracking = if (callOrNull(component, "hasViewTracking") == true) {
            callOrNull(component, "getViewTracking")
        } else {
            null
        }
        val viewName = callOrNull(viewTracking, "getViewName") as? String ?: ""
        if (viewName.contains("upsell")) return true
        if (viewName == "profile-card-promo" && payloadContains(component, "premium-upsell-card")) return true
        false
    } catch (_: Throwable) {
        false
    }
}

private val FILTERED_SDUI_VIEWS = setOf(
    "feed-reaction-count",
    "feed-comment-count",
    "feed-repost-count",
    "comment-reaction-count",
    "feed-new-update-pill",
    "feed-new-updates-pill",
    "feed-pill-container",
    "feed-see-translation",
)

private fun isFeedFilteredComponent(component: Any): Boolean {
    return try {
        val viewTracking = if (callOrNull(component, "hasViewTracking") == true) {
            callOrNull(component, "getViewTracking")
        } else {
            null
        }
        val viewName = callOrNull(viewTracking, "getViewName") as? String ?: ""
        if (viewName in FILTERED_SDUI_VIEWS) return true
        if (isAiView(viewName) || isGameView(viewName) || isRecommendedForYou(viewName)) return true
        if (viewName == "feed-full-update") {
            val payload = callOrNull(component, "toByteArray") as? ByteArray ?: return false
            if (containsAscii(payload, "feed-celebrations-update-detail") ||
                containsAscii(payload, "feed-job-card-entity") ||
                containsAscii(payload, "feed-original-share-description") ||
                containsAscii(payload, "large-single-video-view") ||
                containsAscii(payload, "feed-games-") ||
                containsAscii(payload, "feed-coach")
            ) {
                return true
            }
        }
        false
    } catch (_: Throwable) {
        false
    }
}

private fun isAiView(viewName: String): Boolean =
    viewName.startsWith("feed-coach") || viewName.startsWith("feed-ai-") ||
        viewName.contains("-ai-") || viewName.endsWith("-ai") || viewName.contains("coach-prompt")

private fun isGameView(viewName: String): Boolean =
    viewName.startsWith("feed-games-") || viewName.startsWith("game-") ||
        viewName.contains("crossclimb") || viewName.contains("tango") ||
        viewName.contains("queens") || viewName.contains("pinpoint") ||
        viewName.contains("puzzle")

private fun isRecommendedForYou(viewName: String): Boolean =
    viewName.startsWith("feed-recommended-for-you") || viewName.startsWith("feed-interest-recommendation")

private fun expandCommentary(component: Any, payload: ByteArray): Any? {
    return runCatching {
        val expand = ProtoEditor.nested(payload, 35, 2, 12, 2) ?: return null
        val fields = ProtoEditor.parse(payload) ?: return null
        val trigger = ProtoField(25, 2, ProtoEditor.write(listOf(
            ProtoField(1, 2, expand),
            ProtoEditor.messageField(13)
        )))
        val updated = fields.toMutableList().apply { add(trigger) }
        val newBytes = ProtoEditor.write(updated)
        val parseFrom = component.javaClass.getMethod("parseFrom", ByteArray::class.java)
        parseFrom.invoke(null, newBytes)
    }.getOrNull()
}

private class LinkedInPitchBlackColors {
    fun map(color: Int): Int {
        val rgb = color and 0xFFFFFF
        val alpha = color and -0x1000000
        val mappedRgb = when (rgb) {
            0x1B1F23 -> 0x000000 // AMOLED_CANVAS
            0x1D2226 -> 0x0D0D0D // AMOLED_CARD
            0x293037, 0x293138 -> 0x1A1A1A // AMOLED_TINT
            else -> rgb
        }
        return alpha or mappedRgb
    }
}

/** viewTracking.metadata[] contains a TrackingMetadata whose sponsoredData is set. */
private fun hasSponsoredTracking(viewTracking: Any?): Boolean {
    val metadata = callOrNull(viewTracking, "getMetadataList") as? List<*> ?: return false
    return metadata.any { item -> callOrNull(item, "hasSponsoredData") == true }
}

/** A feed Update is an ad when update.metadata.trackingData.sponsoredTracking is set. */
private fun filterSponsoredUpdates(updates: List<*>): List<*> = try {
    updates.filterNot { update ->
        val trackingData = fieldValue(fieldValue(update, "metadata"), "trackingData")
        fieldValue(trackingData, "sponsoredTracking") != null
    }
} catch (_: Throwable) {
    updates
}

private const val SPONSORED_CONTENT_MARKER = "urn:li:sponsoredContentV2:"

/** Scans the serialized protobuf for the marker, like upstream's ProtoScanner.containsAscii. */
private fun payloadContains(component: Any, marker: String): Boolean {
    val payload = callOrNull(component, "toByteArray") as? ByteArray ?: return false
    return containsAscii(payload, marker)
}

private val fieldCache = ConcurrentHashMap<Class<*>, Map<String, Field>>()
private val methodCache = ConcurrentHashMap<Class<*>, Map<String, Method>>()

/** Reads a field by name, searching superclasses like upstream's Reflect.get. */
private fun fieldValue(target: Any?, name: String): Any? {
    if (target == null) return null
    val fields = fieldCache.computeIfAbsent(target.javaClass) { cls ->
        buildMap {
            var current: Class<*>? = cls
            while (current != null && current != Any::class.java) {
                for (field in current.declaredFields) {
                    if (!containsKey(field.name)) {
                        runCatching { field.isAccessible = true }
                        put(field.name, field)
                    }
                }
                current = current.superclass
            }
        }
    }
    val field = fields[name] ?: return null
    return runCatching { field.get(target) }.getOrNull()
}

/** Invokes a public no-arg method; the call/string/is helpers of upstream's Proto. */
private fun callOrNull(target: Any?, name: String): Any? {
    if (target == null) return null
    val method = methodCache.computeIfAbsent(target.javaClass) { cls ->
        cls.methods.filter { it.parameterTypes.isEmpty() }.associateBy { it.name }
    }[name] ?: return null
    return runCatching { method.invoke(target) }.getOrNull()
}

/** Maps LinkedIn's blue palette and Mercado background surfaces to Android 12+ wallpaper dynamic colors. */
private class LinkedInMaterialYouColors(context: Context) {
    private fun Context.systemColor(name: String, fallback: Int): Int = try {
        val id = resources.getIdentifier(name, "color", "android")
        if (id != 0) getColor(id) else fallback
    } catch (_: Exception) {
        fallback
    }

    private val accentShades = mapOf(
        0xF6FBFF to "system_accent1_10",
        0xE8F3FF to "system_accent1_50",
        0xD0E8FF to "system_accent1_100",
        0xD2E9FF to "system_accent1_100",
        0xA8D4FF to "system_accent1_200",
        0xAAD6FF to "system_accent1_200",
        0x70B5F9 to "system_accent1_200",
        0x71B7FB to "system_accent1_200",
        0x378FE9 to "system_accent1_400",
        0x0A66C2 to "system_accent1_600",
        0x004182 to "system_accent1_700",
        0x09223B to "system_accent1_800",
        0x0A2037 to "system_accent1_900",
    )

    private val backgroundShades = mapOf(
        0x1B1F23 to "system_neutral2_800",
        0x1D2226 to "system_neutral2_800",
        0x293037 to "system_neutral2_700",
        0x293138 to "system_neutral2_700",
        0xF4F2EE to "system_neutral2_50",
        0xF3F2EF to "system_neutral2_50",
    )

    private val resolvedColors = buildMap {
        for ((rgb, name) in accentShades) {
            put(rgb, context.systemColor(name, rgb or -0x1000000))
        }
        for ((rgb, name) in backgroundShades) {
            put(rgb, context.systemColor(name, rgb or -0x1000000))
        }
    }

    fun map(color: Int): Int {
        val rgb = color and 0xFFFFFF
        val mapped = resolvedColors[rgb] ?: return color
        return (color and -0x1000000) or (mapped and 0xFFFFFF)
    }
}

