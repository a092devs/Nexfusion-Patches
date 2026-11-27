package io.github.a092devs.nexfusion.nexfusion.reddit

import app.morphe.extension.reddit.patches.DisableBottomBarAutoHidePatch
import app.morphe.extension.reddit.patches.DisableRefreshOnBackPatch
import app.morphe.extension.reddit.patches.EnableDebuggingPatch
import app.morphe.extension.reddit.patches.FullScreenReplyPatch
import app.morphe.extension.reddit.patches.HideAskButtonPatch
import app.morphe.extension.reddit.patches.HidePeopleAlsoSearchForSectionPatch
import app.morphe.extension.reddit.patches.KeepMediaViewerInSubredditPatch
import app.morphe.extension.reddit.patches.PrivateBrowsingModePatch
import app.morphe.extension.reddit.patches.ShowViewCountPatch
import app.morphe.extension.reddit.patches.SwipeUpForCommentsPatch
import com.reddit.domain.model.media.MediaContext
import io.github.a092devs.nexfusion.compat.XC_MethodHook
import java.lang.reflect.Member
import io.github.a092devs.nexfusion.compat.XposedBridge
import io.github.a092devs.nexfusion.morphe.AccessFlags
import io.github.a092devs.nexfusion.morphe.Fingerprint
import io.github.a092devs.nexfusion.morphe.InstructionLocation.MatchAfterImmediately
import io.github.a092devs.nexfusion.morphe.InstructionLocation.MatchAfterWithin
import io.github.a092devs.nexfusion.morphe.Opcode
import io.github.a092devs.nexfusion.morphe.checkCast
import io.github.a092devs.nexfusion.morphe.fieldAccess
import io.github.a092devs.nexfusion.morphe.findMethodDirect
import io.github.a092devs.nexfusion.morphe.methodCall
import io.github.a092devs.nexfusion.morphe.newInstance
import io.github.a092devs.nexfusion.morphe.opcode
import io.github.a092devs.nexfusion.morphe.string
import io.github.a092devs.nexfusion.patch
import io.github.a092devs.nexfusion.scopedHook
import java.lang.reflect.Method

// region Fingerprints

private const val EXPERIMENT_VARIANT_CLASS = "Lcom/reddit/common/experiments/ExperimentVariant;"
private const val MEDIA_CONTEXT_CLASS = "Lcom/reddit/domain/model/media/MediaContext;"

internal object FeatureFlagFingerprint : Fingerprint(
    returnType = "Z",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    parameters = listOf("Ljava/lang/String;", "Z", "Z", "Z"),
    filters = listOf(
        methodCall(
            definingClass = "Lcom/reddit/experiments/",
            returnType = EXPERIMENT_VARIANT_CLASS,
        ),
        methodCall(
            smali = "$EXPERIMENT_VARIANT_CLASS->getName()Ljava/lang/String;",
        ),
    ),
)

private object FeatureFlagLegacyClassFingerprint : Fingerprint(
    returnType = "V",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    parameters = listOf("Ljava/lang/String;"),
    filters = listOf(
        string("experiment_name"),
        string("max_length"),
    ),
)

internal object FeatureFlagLegacyFingerprint : Fingerprint(
    classFingerprint = FeatureFlagLegacyClassFingerprint,
    returnType = "Z",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    parameters = listOf("Ljava/lang/String;", "Z"),
)

internal object NavBarStateChangeFingerprint : Fingerprint(
    definingClass = "Lcom/reddit/launch/bottomnav/BottomNavScreen;",
    returnType = "V",
    parameters = listOf("F"),
    filters = listOf(
        methodCall(
            definingClass = "Lcom/reddit/launch/bottomnav/BottomNavScreen\$onNavBarStateChange\$1",
            name = "init",
        ),
    ),
)

internal object IncognitoRevampEnabledFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "Z",
    parameters = listOf(),
    filters = listOf(
        checkCast("Lcom/reddit/incognito/IncognitoRevampM1Variant;"),
        fieldAccess(definingClass = "Lcom/reddit/incognito/IncognitoRevampM1Variant;", name = "CONTROL"),
        opcode(Opcode.IF_EQ),
    ),
)

internal object HomeBackPressedFingerprint : Fingerprint(
    name = "invoke",
    returnType = "Ljava/lang/Object;",
    parameters = listOf(),
    filters = listOf(
        methodCall(
            definingClass = "Lcom/reddit/feedslegacy/switcher/impl/homepager/compose/HomePagerScreen;",
            returnType = "Lcom/reddit/screen/BaseScreen;",
            parameters = listOf(),
        ),
        methodCall(
            returnType = "Z",
            parameters = listOf(),
            location = MatchAfterWithin(20),
        ),
        opcode(Opcode.MOVE_RESULT, location = MatchAfterImmediately()),
        newInstance("Lcom/reddit/feedslegacy/switcher/impl/exitapp/ExitAppToastSharedPreferences\$toastSeenTotalCount\$1;"),
        opcode(Opcode.IF_EQZ, location = MatchAfterWithin(5)),
        string("onBackPressedHandler"),
        newInstance("Lcom/reddit/feedslegacy/switcher/impl/exitapp/RedditExitAppOnDoubleBackClickDelegate\$handleAppExit\$1;"),
    ),
)

internal object SearchRowMapperFingerprint : Fingerprint(
    filters = listOf(
        string("SearchRowComponentHeaderPresentation"),
        string("SearchRowOverflowHeaderPresentation"),
        string("SearchQuerySuggestion"),
        methodCall(
            opcode = Opcode.INVOKE_STATIC,
            parameters = listOf("Ljava/lang/Object;", "Ljava/lang/Object;"),
            returnType = "Z",
            location = MatchAfterWithin(5),
        ),
        opcode(Opcode.MOVE_RESULT, MatchAfterImmediately()),
    ),
)

internal object VerticalChainingFingerprint : Fingerprint(
    name = "invoke",
    returnType = "Ljava/lang/Object;",
    parameters = listOf(),
    filters = listOf(
        fieldAccess(
            definingClass = "Lcom/reddit/fullbleedplayer/FbpRedesignM3Variant;",
            name = "VERTICAL_CHAINING",
        ),
        methodCall(
            smali = "Ljava/lang/Boolean;->valueOf(Z)Ljava/lang/Boolean;",
            location = MatchAfterWithin(5),
        ),
    ),
)

internal object VerticalChainingLegacyFingerprint : Fingerprint(
    name = "init",
    returnType = "V",
    filters = listOf(
        methodCall(returnType = "Z", parameters = listOf()),
        methodCall(
            smali = "Lcom/reddit/domain/model/media/MediaContext;->isImage()Z",
            location = MatchAfterWithin(10),
        ),
        newInstance("Lcom/reddit/fullbleedplayer/data/viewstateproducers/PagerStateProducer\$1;"),
    ),
)

private fun recommendedPostsRequestFilters(endpointClass: String) = listOf(
    methodCall(definingClass = MEDIA_CONTEXT_CLASS, name = "getSeedSubredditIds"),
    methodCall(definingClass = MEDIA_CONTEXT_CLASS, name = "getListingType"),
    fieldAccess(definingClass = endpointClass, name = "COMMUNITY"),
    methodCall(definingClass = "Lcom/reddit/domain/model/listing/Listing;", name = "init"),
)

internal object RecommendedMediaRequestFingerprint : Fingerprint(
    returnType = "Ljava/lang/Object;",
    filters = recommendedPostsRequestFilters("Lcom/reddit/type/MediaFeedEndpoint;"),
)

internal object RecommendedVideosRequestFingerprint : Fingerprint(
    returnType = "Ljava/lang/Object;",
    filters = recommendedPostsRequestFilters("Lcom/reddit/type/VideoFeedEndpoint;"),
)

internal val homeBackCurrentScreenMethodRef = findMethodDirect {
    HomeBackPressedFingerprint.instructionMatches[0].instruction.methodRef!!
}

internal val homeBackRefreshCheckMethodRef = findMethodDirect {
    HomeBackPressedFingerprint.instructionMatches[1].instruction.methodRef!!
}

internal val verticalChainingLegacyMethodRef = findMethodDirect {
    VerticalChainingLegacyFingerprint.instructionMatches.first().instruction.methodRef!!
}

internal val mediaReqGetListingTypeRef = findMethodDirect {
    RecommendedMediaRequestFingerprint.instructionMatches[1].instruction.methodRef!!
}

internal val mediaReqListingCtorRef = findMethodDirect {
    RecommendedMediaRequestFingerprint.instructionMatches[3].instruction.methodRef!!
}

internal val videoReqGetListingTypeRef = findMethodDirect {
    RecommendedVideosRequestFingerprint.instructionMatches[1].instruction.methodRef!!
}

internal val videoReqListingCtorRef = findMethodDirect {
    RecommendedVideosRequestFingerprint.instructionMatches[3].instruction.methodRef!!
}

// endregion

// region Shared FeatureFlag hook

private object RedditFeatureFlagBridge {
    @Volatile
    private var hooked = false
    @Volatile
    var enableFullScreenReply = false
    @Volatile
    var enableHideAskButton = false
    @Volatile
    var enableShowViewCount = false
    @Volatile
    var enableDebugging = false

    fun ensureHooked(target: Member?) {
        if (hooked || target == null) return
        synchronized(this) {
            if (hooked) return
            XposedBridge.hookMethod(
                target,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val name = param.args.getOrNull(0) as? String ?: return
                        var value = param.result as? Boolean ?: return
                        if (enableFullScreenReply) {
                            value = FullScreenReplyPatch.useFullScreenReply(name, value)
                        }
                        if (enableHideAskButton) {
                            value = HideAskButtonPatch.hideAskButton(name, value)
                        }
                        if (enableShowViewCount) {
                            value = ShowViewCountPatch.showViewCount(name, value)
                        }
                        if (enableDebugging) {
                            value = EnableDebuggingPatch.isBooleanExperimentEnabled(name, value)
                        }
                        param.result = value
                    }
                },
            )
            hooked = true
        }
    }
}

// endregion

// region Patches

val PortedDisableBottomBarAutoHide = patch(
    name = "Disable bottom bar auto-hide",
    description = "Adds an option to keep the bottom navigation bar visible while scrolling.",
    use = false,
) {
    val member = NavBarStateChangeFingerprint.memberOrNull ?: return@patch
    XposedBridge.hookMethod(
        member,
        object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val fraction = param.args.getOrNull(0) as? Float ?: return
                param.args[0] = DisableBottomBarAutoHidePatch.getNavBarShownFraction(fraction)
            }
        },
    )
}

val PortedPrivateBrowsingMode = patch(
    name = "Private browsing mode",
    description = "Adds an option to choose whether private browsing uses Anonymous Browsing or Incognito.",
    use = false,
) {
    val member = IncognitoRevampEnabledFingerprint.memberOrNull ?: return@patch
    XposedBridge.hookMethod(
        member,
        object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val enabled = param.result as? Boolean ?: return
                param.result = PrivateBrowsingModePatch.isIncognitoEnabled(enabled)
            }
        },
    )
}

val PortedDisableRefreshOnBack = patch(
    name = "Disable refresh on back to exit",
    description = "Adds an option to exit the app with back on the home feed without first scrolling to the top and reloading.",
    use = false,
) {
    val backMember = HomeBackPressedFingerprint.memberOrNull ?: return@patch
    val getCurrentScreen = runCatching { ::homeBackCurrentScreenMethodRef.member }.getOrNull() ?: return@patch
    val refreshCheck = runCatching { ::homeBackRefreshCheckMethodRef.method }.getOrNull() ?: return@patch
    val hookedClasses = java.util.Collections.synchronizedSet(HashSet<Class<*>>())
    XposedBridge.hookMethod(
        backMember,
        scopedHook(getCurrentScreen) {
            after { param ->
                val screen = param.result ?: return@after
                val screenCls = screen.javaClass
                if (!hookedClasses.add(screenCls)) return@after
                runCatching {
                    val concrete = screenCls.getMethod(refreshCheck.name)
                    XposedBridge.hookMethod(
                        concrete,
                        object : XC_MethodHook() {
                            override fun afterHookedMethod(p: MethodHookParam) {
                                val shouldRefresh = p.result as? Boolean ?: return
                                p.result = DisableRefreshOnBackPatch.refreshOnBack(shouldRefresh)
                            }
                        },
                    )
                }
            }
        },
    )
}

val PortedHidePeopleAlsoSearchFor = patch(
    name = "Hide People also search for section",
    description = "Adds an option to hide the 'People also search for' section from search results.",
    use = false,
) {
    // Note: Do not hook Intrinsics.areEqual at runtime (causes infinite recursion inside Xposed dispatch).
    // If SearchRowMapperFingerprint is needed, filter the mapped SearchRow result in afterHookedMethod.
    val mapper = SearchRowMapperFingerprint.memberOrNull ?: return@patch
    XposedBridge.hookMethod(
        mapper,
        object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                if (!HidePeopleAlsoSearchForSectionPatch.isQuerySuggestion(true)) {
                    // Check if any list/children field on the mapped row represents query suggestions.
                    val res = param.result ?: return
                    if (res.toString().contains("SearchQuerySuggestion")) {
                        param.result = null
                    }
                }
            }
        },
    )
}

val PortedSwipeUpForComments = patch(
    name = "Swipe up for comments",
    description = "Allows swiping up on media in the media viewer to open comments instead of the next video.",
    use = false,
) {
    val verticalMember = VerticalChainingFingerprint.memberOrNull
    if (verticalMember != null) {
        XposedBridge.hookMethod(
            verticalMember,
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val allow = param.result as? Boolean ?: return
                    param.result = SwipeUpForCommentsPatch.allowVerticalChaining(allow)
                }
            },
        )
    } else if (VerticalChainingLegacyFingerprint.memberOrNull != null) {
        val legacyGetter = ::verticalChainingLegacyMethodRef.member
        XposedBridge.hookMethod(
            legacyGetter,
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val allow = param.result as? Boolean ?: return
                    param.result = SwipeUpForCommentsPatch.allowVerticalChaining(allow)
                }
            },
        )
    }
}

val PortedKeepMediaViewerInSubreddit = patch(
    name = "Keep media viewer in subreddit",
    description = "Keeps the media viewer within the current subreddit when swiping between posts.",
    use = false,
) {
    RecommendedMediaRequestFingerprint.memberOrNull?.let { reqMember ->
        val getListingType = ::mediaReqGetListingTypeRef.member
        val listingCtor = ::mediaReqListingCtorRef.member
        XposedBridge.hookMethod(
            reqMember,
            scopedHook(getListingType) {
                after { param ->
                    val ctx = param.thisObject as? MediaContext ?: return@after
                    param.result = KeepMediaViewerInSubredditPatch.getListingType(ctx)
                }
            },
        )
        XposedBridge.hookMethod(
            reqMember,
            scopedHook(listingCtor) {
                before { param ->
                    @Suppress("UNCHECKED_CAST")
                    val posts = param.args.getOrNull(0) as? List<Any?> ?: return@before
                    val after = param.args.getOrNull(1) as? String
                    param.args[1] = KeepMediaViewerInSubredditPatch.getAfter(posts, after)
                    param.args[0] = KeepMediaViewerInSubredditPatch.filterPosts(posts)
                }
            },
        )
    }
    RecommendedVideosRequestFingerprint.memberOrNull?.let { reqMember ->
        val getListingType = ::videoReqGetListingTypeRef.member
        val listingCtor = ::videoReqListingCtorRef.member
        XposedBridge.hookMethod(
            reqMember,
            scopedHook(getListingType) {
                after { param ->
                    val ctx = param.thisObject as? MediaContext ?: return@after
                    param.result = KeepMediaViewerInSubredditPatch.getListingType(ctx)
                }
            },
        )
        XposedBridge.hookMethod(
            reqMember,
            scopedHook(listingCtor) {
                before { param ->
                    @Suppress("UNCHECKED_CAST")
                    val posts = param.args.getOrNull(0) as? List<Any?> ?: return@before
                    val after = param.args.getOrNull(1) as? String
                    param.args[1] = KeepMediaViewerInSubredditPatch.getAfter(posts, after)
                    param.args[0] = KeepMediaViewerInSubredditPatch.filterPosts(posts)
                }
            },
        )
    }
}

val PortedFullScreenReply = patch(
    name = "Full-screen reply",
    description = "Opens comment replies in the full-screen editor instead of the inline composer.",
    use = false,
) {
    RedditFeatureFlagBridge.enableFullScreenReply = true
    RedditFeatureFlagBridge.ensureHooked(FeatureFlagFingerprint.memberOrNull ?: FeatureFlagLegacyFingerprint.memberOrNull)
}

val PortedHideAskButton = patch(
    name = "Hide Ask button",
    description = "Adds an option to hide the Ask AI button from the toolbar.",
    use = false,
) {
    RedditFeatureFlagBridge.enableHideAskButton = true
    RedditFeatureFlagBridge.ensureHooked(FeatureFlagFingerprint.memberOrNull ?: FeatureFlagLegacyFingerprint.memberOrNull)
}

val PortedShowViewCount = patch(
    name = "Show view count",
    description = "Adds an option to show post view counts.",
    use = false,
) {
    RedditFeatureFlagBridge.enableShowViewCount = true
    RedditFeatureFlagBridge.ensureHooked(FeatureFlagFingerprint.memberOrNull ?: FeatureFlagLegacyFingerprint.memberOrNull)
}

val PortedEnableDebugging = patch(
    name = "Enable debugging",
    description = "Enables debug logging for Reddit patches.",
    use = false,
) {
    RedditFeatureFlagBridge.enableDebugging = true
    RedditFeatureFlagBridge.ensureHooked(FeatureFlagFingerprint.memberOrNull ?: FeatureFlagLegacyFingerprint.memberOrNull)
}

// endregion
