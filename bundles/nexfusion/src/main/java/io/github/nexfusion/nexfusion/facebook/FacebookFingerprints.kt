package io.github.nexfusion.nexfusion.facebook

import io.github.nexfusion.morphe.Opcode
import io.github.nexfusion.morphe.findClassDirect
import io.github.nexfusion.morphe.findMethodDirect
import io.github.nexfusion.morphe.findMethodListDirect
import io.github.nexfusion.morphe.opcodeEnum
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.query.enums.StringMatchType
import org.luckypray.dexkit.result.MethodData

// Anchors taken from SysAdminDoc/Hushfacebook (GPL-3.0):
//   patches/src/main/kotlin/app/morphe/patches/facebook/shared/FeedEdge.kt
//   patches/src/main/kotlin/app/morphe/patches/facebook/ads/sponsoredreels/ReelsAdPool.kt
//   patches/src/main/kotlin/app/morphe/patches/facebook/ads/sponsoredreels/ProfileReelAds.kt
//   patches/src/main/kotlin/app/morphe/patches/facebook/ads/sponsoredreels/Fingerprints.kt
//   patches/src/main/kotlin/app/morphe/patches/facebook/ads/telemetry/BlockAdTelemetryPatch.kt
// Upstream patches Facebook at build time; these finders resolve the same anchors at runtime.

/** Kept GraphQL model class: the news feed edge pushed into the feed's collection. */
private const val FEED_UNIT_EDGE = "com.facebook.graphql.model.GraphQLFeedUnitEdge"

/**
 * The funnel's known leading parameters: the collection builder first, then the edge. The third
 * parameter's class is renamed every build and is what broke the previous exact-signature match.
 */
private val FEED_FUNNEL_PARAMS = listOf(
    "com.google.common.collect.ImmutableList\$Builder",
    FEED_UNIT_EDGE,
)

/**
 * The single funnel every news feed edge passes through into the feed's collection.
 *
 * Through 582 the work is done by `addNewEdgeToCollection(ImmutableList$Builder,
 * GraphQLFeedUnitEdge, Object): boolean` itself. On 583 that method is a thin wrapper that hands
 * the edge, the collection and a category lookup to a static method of its own class, and the
 * feed's batch add calls that method directly, so the wrapper alone no longer sees every edge;
 * [feedFunnelBody] follows the wrapper to its body. The third parameter's class is renamed every
 * build, so the finder pins the name, the return type and the two kept parameter types. The
 * funnel may live in any dex entry.
 */
val feedFunnel = findMethodDirect {
    val matches = findMethod {
        matcher {
            name = "addNewEdgeToCollection"
            paramCount = 3
        }
    }.distinctBy { it.descriptor }.filter { method ->
        method.returnTypeName == "boolean" &&
            method.paramTypeNames.take(FEED_FUNNEL_PARAMS.size) == FEED_FUNNEL_PARAMS
    }
    check(matches.size == 1) {
        "expected exactly one addNewEdgeToCollection(ImmutableList\$Builder, GraphQLFeedUnitEdge, *)Z " +
            "feed funnel, found ${matches.size}"
    }
    feedFunnelBody(matches.single()) ?: matches.single()
}

/**
 * The static method of the wrapper's own class that is the real funnel, when [funnel] is a 583
 * wrapper: the wrapper delegates through exactly one static same-class call that takes the edge
 * and answers a boolean; its other calls (the category lookup, the void logging) answer something
 * else and are skipped. A funnel that does the work itself (through 582) or one whose shape is
 * anything else is left as the hook target on its own.
 */
private fun DexKitBridge.feedFunnelBody(funnel: MethodData): MethodData? {
    val delegate = runCatching { funnel.instructions }.getOrNull()
        ?.mapNotNull { instruction ->
            if (instruction.opcodeEnum != Opcode.INVOKE_STATIC &&
                instruction.opcodeEnum != Opcode.INVOKE_STATIC_RANGE
            ) {
                return@mapNotNull null
            }
            val ref = instruction.methodRef ?: return@mapNotNull null
            ref.takeIf {
                it.returnTypeName == "boolean" &&
                    it.declaredClassName == funnel.declaredClassName &&
                    it.paramTypeNames.any { type -> type == FEED_UNIT_EDGE }
            }
        }
        ?.distinctBy { it.name to it.paramTypeNames }
        ?.singleOrNull() ?: return null
    return findMethod {
        matcher {
            name = delegate.name
            paramTypes(*delegate.paramTypeNames.toTypedArray())
            returnType = delegate.returnTypeName
            declaredClass(funnel.declaredClassName)
        }
    }.distinctBy { it.descriptor }.singleOrNull()
}

/** Every method whose body holds exactly [marker]. */
private fun DexKitBridge.methodsHolding(marker: String): List<MethodData> =
    findMethod {
        matcher { usingStrings(listOf(marker), StringMatchType.Equals) }
    }.distinctBy { it.descriptor }

/**
 * Methods using all [strings], exact matches preferred over partial ones. Upstream's fingerprints
 * match `strings` by containment, so a renamed literal such as a "...V2" query still finds the
 * method; an exact match is tried first so a plain literal pins exactly the method itself.
 */
private fun DexKitBridge.methodsUsingAll(strings: List<String>): List<MethodData> {
    fun query(matchType: StringMatchType) = findMethod {
        matcher { usingStrings(strings, matchType) }
    }
    return query(StringMatchType.Equals).ifEmpty { query(StringMatchType.Contains) }
}

/**
 * The Reels and Watch ad pool's vends: the instance methods that answer an object and log
 * `-WVCDF-NO-AD` when there is no ad to give. No other class holds the literal.
 */
val reelsAdPoolVends = findMethodListDirect { methodsHolding("-WVCDF-NO-AD") }

/** The method that runs the Reels tab's video ads fetch: its idle state's own query. */
val reelsVideoAdsFetch = findMethodListDirect { methodsUsingAll(listOf("FBFetchReelsVideoAdsQuery")) }

/** The method that runs the in-stream (video) ad break fetch, which also logs its start. */
val instreamBreakFetch = findMethodListDirect {
    methodsUsingAll(listOf("InstreamExtendedBreakQuery", "Kicking off extended breaks fetch"))
}

/** The method that runs the profile reels ads fetch, a void instance method that returns early on its own. */
val profileReelsAdsFetch = findMethodListDirect { methodsUsingAll(listOf("ProfileReelsAsyncAdsQuery")) }

/**
 * The one void method each advertising-ID send marker picks, from upstream's AD_ID_SENDERS:
 * a void method loading exactly the string reports that advertising ID.
 */
val advertiserIdReportSender = findMethodListDirect { methodsHolding("ReportAdvertiserIDMutation") }
val launchCustomDataSender = findMethodListDirect { methodsHolding("fb4a_launch_custom_data") }
val privacySandboxRegisterSender = findMethodListDirect {
    methodsHolding("https://www.facebook.com/privacy_sandbox/mobile/register/source?tracking_data=")
}
val newInstallSender = findMethodListDirect { methodsHolding("app_new_install") }

// The client-side ad splices and the shared ad-break fetch, the other sponsored-reel anchors of
// upstream's sponsoredreels/Fingerprints.kt. Each splice is a void method that does nothing but
// insert an ad and tell the controller its data changed; upstream blocks all four, but its POE
// render task holds no string literal and cannot be anchored at runtime, so three resolve here.

/** The page-of-ads splice of the Reels and Watch item list. */
val insertAdsSplice = findMethodListDirect {
    methodsUsingAll(listOf("VideoHomeDataControllerImpl.maybeInsertAds"))
}

/** The insert of an ad fetched in response to what the viewer just did. */
val realtimeIntentAdInsert = findMethodListDirect {
    methodsUsingAll(listOf("VideoHomeDataControllerAdsUtil.maybeInsertFbShortsRealtimeIntentItem"))
}

/** The SFD ad insert task. */
val sfdAdInsert = findMethodListDirect {
    methodsUsingAll(listOf("VideoHomeDataControllerSfdAdsUtil"))
}

/** "Kicking off banner ads fetch", "Kicking off video ad fetch". */
private const val BANNER_FETCH_LOG = "Kicking off banner ads fetch"
private const val VIDEO_FETCH_LOG = "Kicking off video ad fetch"
private const val LISTENABLE_FUTURE = "com.google.common.util.concurrent.ListenableFuture"
private const val DEFERRED_CARD_STATE = "UnresolvedWithDeferredCardState"

/**
 * The shared ad-break fetch: void, and the only method logging both literals. It logs each one
 * right after starting that kind of query, so the last future-returning call before a literal is
 * the query helper the log reports.
 */
val adBreakFetch = findMethodDirect {
    val matches = findMethod {
        matcher { usingStrings(listOf(BANNER_FETCH_LOG, VIDEO_FETCH_LOG), StringMatchType.Contains) }
    }.filter { it.returnTypeName == "void" }.distinctBy { it.descriptor }
    check(matches.size == 1) { "expected exactly one ad-break fetch logging both literals, found ${matches.size}" }
    matches.single()
}

/** The banner query helper: all banner requests go through it. */
val adBreakBannerFetch = findMethodDirect {
    val instructions = adBreakFetch().instructions
    val bannerIndex = instructions.indexOfFirst { it.string == BANNER_FETCH_LOG }
    check(bannerIndex >= 0) { "\"$BANNER_FETCH_LOG\" is not in the ad-break fetch" }
    val ref = instructions.take(bannerIndex).mapNotNull { it.methodRef }
        .lastOrNull { it.returnTypeName == LISTENABLE_FUTURE }
        ?: throw IllegalStateException("the ad-break fetch makes no call returning a future before \"$BANNER_FETCH_LOG\"")
    val signatures = findMethod {
        matcher {
            name = ref.name
            paramTypes(*ref.paramTypeNames.toTypedArray())
            returnType = ref.returnTypeName
        }
    }
    val onClass = signatures.filter { it.declaredClassName == ref.declaredClassName }
    (if (onClass.size == 1) onClass else signatures).single()
}

/** Every future-returning method of the ad-break server API class: its video ad queries. */
val adBreakVideoFetches = findMethodListDirect {
    val instructions = adBreakFetch().instructions
    val videoIndex = instructions.indexOfFirst { it.string == VIDEO_FETCH_LOG }
    check(videoIndex >= 0) { "\"$VIDEO_FETCH_LOG\" is not in the ad-break fetch" }
    val ref = instructions.take(videoIndex).mapNotNull { it.methodRef }
        .lastOrNull { it.returnTypeName == LISTENABLE_FUTURE }
        ?: throw IllegalStateException("the ad-break fetch makes no call returning a future before \"$VIDEO_FETCH_LOG\"")
    val fetcher = getClassData(ref.declaredClassName)
        ?: throw IllegalStateException("the ad-break video fetcher class ${ref.declaredClassName} was not found")
    val fetches = fetcher.methods.filter { it.returnTypeName == LISTENABLE_FUTURE }
    check(fetches.isNotEmpty()) { "${fetcher.name} has no method returning a future" }
    fetches
}

/**
 * The Reels ads state whose ad-break lookup keeps retrying. The current builds name every state
 * from one method on the abstract base, by `instance-of`, so the state is the type tested just
 * before the literal; a build that names states any other way stops this finder rather than
 * letting it answer -1 for a sibling state too.
 */
/**
 * The four story-viewer bucket sources. Each takes the organic `ImmutableList` and returns it with
 * its ad buckets spliced in; returning the input is what Facebook itself does when a source has
 * nothing to add. Anchored on the unique literals upstream keeps.
 */
val inorganicClashStoryBuckets = findMethodListDirect { methodsHolding("handling_inorganic_clash") }
val adBucketDataSourceStoryBuckets = findMethodListDirect { methodsHolding("uninsertedMainAdsQueue") }
val adPaginatingStoryBuckets = findMethodListDirect {
    methodsHolding("AdPaginatingBucketStaticInsertionDataSource.getBuckets")
}
val storyViewerMidCardBuckets = findMethodListDirect {
    methodsHolding("StoryViewerMidCardDataSource.getBuckets")
}

/** The pre-EOF Reels row injector, which builds its own ShowcaseFeedUnit row. */
val preEofReelsInjectors = findMethodListDirect { methodsHolding("PreEofIfuSectionAdapter") }

/**
 * The methods that fill a post's three-dot menu. Every post menu goes through the base of the
 * video feed's menu helper; the override and the base share this signature, so all matching
 * bodies are returned and each is hooked after it has run. The first two arguments are the
 * Android menu and the view that opened it, which is where the downloader gets its Context.
 */
val postMenuFillers = findMethodListDirect {
    runCatching {
        val candidates = methodsUsingAll(listOf("VideoFeedStoryMenuHelper", "DOWNLOAD_VIDEO"))
            .ifEmpty { methodsHolding("DOWNLOAD_VIDEO") }
            .ifEmpty { methodsHolding("VideoFeedStoryMenuHelper") }
            .ifEmpty {
                findMethod {
                    matcher {
                        paramCount = 3
                        paramTypes = listOf("android.view.Menu", "android.view.View", null)
                        returnType = "void"
                        usingStrings(listOf("SAVE_VIDEO"), StringMatchType.Contains)
                    }
                }
            }
            .ifEmpty {
                findMethod {
                    matcher {
                        paramCount = 3
                        paramTypes = listOf("android.view.Menu", "android.view.View", null)
                        returnType = "void"
                        usingStrings(listOf("COPY_LINK"), StringMatchType.Contains)
                    }
                }
            }
            .ifEmpty {
                findMethod {
                    matcher {
                        paramCount = 3
                        paramTypes = listOf("android.view.Menu", "android.view.View", null)
                        returnType = "void"
                        usingStrings(listOf("SAVE_POST"), StringMatchType.Contains)
                    }
                }
            }

        val anchor = candidates.firstOrNull { method ->
            method.returnTypeName == "void" &&
                method.paramTypeNames.size >= 3 &&
                method.paramTypeNames[0] == "android.view.Menu" &&
                method.paramTypeNames[1] == "android.view.View" &&
                method.paramTypeNames.any { it.contains("PlayerOrigin") }
        } ?: candidates.firstOrNull { method ->
            method.returnTypeName == "void" &&
                method.paramTypeNames.size >= 3 &&
                method.paramTypeNames[0] == "android.view.Menu" &&
                method.paramTypeNames[1] == "android.view.View"
        }

        if (anchor == null) {
            candidates.filter { it.returnTypeName == "void" && it.paramTypeNames.size >= 3 && it.paramTypeNames[0] == "android.view.Menu" && it.paramTypeNames[1] == "android.view.View" }
        } else {
            candidates.filter { method ->
                method.returnTypeName == "void" &&
                    method.paramTypeNames == anchor.paramTypeNames
            }.distinctBy { it.descriptor }
        }
    }.getOrDefault(emptyList())
}

/** The GraphQL getters the post model exposes for the downloader's attachment walk. */
val storyAttachmentsGetter = findMethodDirect {
    findMethod {
        matcher {
            declaredClass("com.facebook.graphql.model.GraphQLStory", StringMatchType.Contains, false)
            usingNumbers(listOf("attachments".hashCode()))
        }
    }.filter {
        it.paramTypeNames.isEmpty() &&
            (it.returnTypeName.contains("ImmutableList") || it.returnTypeName.contains("List"))
    }.distinctBy { it.descriptor }.firstOrNull()
        ?: throw IllegalStateException("GraphQLStory's attachments getter was not found")
}

val storyAttachedStoryGetter = findMethodDirect {
    findMethod {
        matcher {
            declaredClass("com.facebook.graphql.model.GraphQLStory", StringMatchType.Contains, false)
            usingNumbers(listOf("attached_story".hashCode()))
        }
    }.filter {
        it.paramTypeNames.isEmpty() &&
            it.returnTypeName.contains("GraphQLStory")
    }.distinctBy { it.descriptor }.firstOrNull()
        ?: throw IllegalStateException("GraphQLStory's attached_story getter was not found")
}

val attachmentMediaGetter = findMethodDirect {
    findMethod {
        matcher {
            declaredClass(
                "com.facebook.graphql.model.GraphQLStoryAttachment",
                StringMatchType.Contains,
                false,
            )
            usingNumbers(listOf("media".hashCode()))
        }
    }.filter {
        it.paramTypeNames.isEmpty() &&
            it.returnTypeName.contains("GraphQLMedia")
    }.distinctBy { it.descriptor }.firstOrNull()
        ?: throw IllegalStateException("GraphQLStoryAttachment's media getter was not found")
}

val attachmentSubattachmentsGetter = findMethodDirect {
    findMethod {
        matcher {
            declaredClass(
                "com.facebook.graphql.model.GraphQLStoryAttachment",
                StringMatchType.Contains,
                false,
            )
            usingNumbers(listOf("subattachments".hashCode()))
        }
    }.filter {
        it.paramTypeNames.isEmpty() &&
            (it.returnTypeName.contains("ImmutableList") || it.returnTypeName.contains("List"))
    }.distinctBy { it.descriptor }.firstOrNull()
        ?: throw IllegalStateException("GraphQLStoryAttachment's subattachments getter was not found")
}

/**
 * The boolean reads of a photo's `can_viewer_download` flag. The viewer asks it when it builds
 * the photo menu and again when it decides whether to show the item; both are answered true while
 * the downloader is on, so the item appears even when the poster turned saving off.
 */
val canViewerDownloadGates = findMethodListDirect {
    runCatching {
        findMethod {
            matcher { usingNumbers(listOf("can_viewer_download".hashCode())) }
        }.filter { it.returnTypeName == "boolean" }.distinctBy { it.descriptor }
    }.getOrDefault(emptyList())
}

/** The photo viewer's own Save photo action, which the patch wraps with the downloader's. */
val savePhotoActions = findMethodListDirect {
    runCatching {
        findMethod {
            matcher { name = "newSavePhotoAction" }
        }.filter {
            it.paramTypeNames.size == 1 &&
                it.returnTypeName == "android.view.MenuItem\$OnMenuItemClickListener"
        }.distinctBy { it.descriptor }
    }.getOrDefault(emptyList())
}

val deferredCardState = findClassDirect {
    val nameMethod = findMethod {
        matcher {
            usingStrings(listOf(DEFERRED_CARD_STATE), StringMatchType.Equals)
            returnType = "java.lang.String"
            paramCount = 0
        }
    }.distinctBy { it.descriptor }.single()
    val instructions = nameMethod.instructions
    val literal = instructions.indexOfFirst { it.string == DEFERRED_CARD_STATE }
    check(literal >= 0) { "\"$DEFERRED_CARD_STATE\" is not in ${nameMethod.declaredClassName}->${nameMethod.name}" }
    (maxOf(0, literal - 3) until literal).reversed().firstNotNullOfOrNull { index ->
        instructions[index].takeIf { it.opcodeEnum == Opcode.INSTANCE_OF }?.classRef
    } ?: throw IllegalStateException("no instance-of tests \"$DEFERRED_CARD_STATE\" before its literal")
}

/**
 * The Reel "More" bottom sheet builder: void static method anchored on "fds_control_clear_mode",
 * which adds setting rows (including Clear mode) to the sheet's List<Any>.
 */
val reelMoreSheetBuilders = findMethodListDirect {
    runCatching {
        findMethod {
            matcher { usingStrings(listOf("fds_control_clear_mode"), StringMatchType.Contains) }
        }.distinctBy { it.descriptor }
            .ifEmpty {
                findMethod {
                    matcher { usingStrings(listOf("fds_control_save_video"), StringMatchType.Contains) }
                }.distinctBy { it.descriptor }
            }
            .ifEmpty {
                findMethod {
                    matcher { usingStrings(listOf("fds_control_remix_video"), StringMatchType.Contains) }
                }.distinctBy { it.descriptor }
            }
            .ifEmpty {
                findMethod {
                    matcher { usingStrings(listOf("clear_mode"), StringMatchType.Contains) }
                }.distinctBy { it.descriptor }
            }
    }.getOrDefault(emptyList())
}

/**
 * The constructors of VideoPlayerParams, which hold the videoId and the VideoDataSource.
 */
val videoPlayerParamsConstructors = findMethodListDirect {
    runCatching {
        findMethod {
            matcher {
                declaredClass("com.facebook.video.engine.api.VideoPlayerParams", StringMatchType.Equals, false)
                name = "<init>"
            }
        }.distinctBy { it.descriptor }
    }.getOrDefault(emptyList())
}

/**
 * The story viewer's capability class (X.CsG on 583), which evaluates
 * story capabilities like whether the story can be saved.
 */
val storyCapabilityConstructors = findMethodListDirect {
    runCatching {
        val callback = getClassData("Lcom/facebook/stories/viewer/ui/buckets/regular/topbar/menu/StoryViewerMoreButtonCallback;")
            ?: getClassData("com.facebook.stories.viewer.ui.buckets.regular.topbar.menu.StoryViewerMoreButtonCallback")
            ?: return@findMethodListDirect emptyList()
        val csGName = callback.methods.asSequence()
            .flatMap { it.instructions.asSequence() }
            .mapNotNull { it.fieldRef }
            .firstOrNull { it.typeName == "java.lang.Boolean" }
            ?.declaredClassName
            ?: return@findMethodListDirect emptyList()
        val clazz = findClass { matcher { className(csGName, StringMatchType.Equals) } }.firstOrNull()
            ?: return@findMethodListDirect emptyList()
        findMethod {
            matcher {
                declaredClass(clazz.name, StringMatchType.Equals, false)
                name = "<init>"
            }
        }.distinctBy { it.descriptor }
    }.getOrDefault(emptyList())
}

/**
 * The capability check method in CsG that computes whether the story can be saved.
 * StoryViewerMoreButtonCallback's 2-param ImmutableList.Builder method (A0C on 583)
 * calls it before adding the Save Story menu item.
 */
val storySaveCapabilityMethods = findMethodListDirect {
    runCatching {
        val callback = getClassData("Lcom/facebook/stories/viewer/ui/buckets/regular/topbar/menu/StoryViewerMoreButtonCallback;")
            ?: getClassData("com.facebook.stories.viewer.ui.buckets.regular.topbar.menu.StoryViewerMoreButtonCallback")
            ?: return@findMethodListDirect emptyList()
        val csGName = callback.methods.asSequence()
            .flatMap { it.instructions.asSequence() }
            .mapNotNull { it.fieldRef }
            .firstOrNull { it.typeName == "java.lang.Boolean" }
            ?.declaredClassName
            ?: return@findMethodListDirect emptyList()
        val clazz = findClass { matcher { className(csGName, StringMatchType.Equals) } }.firstOrNull()
            ?: return@findMethodListDirect emptyList()
        val builderMethod = callback.methods.firstOrNull { method ->
            method.paramTypeNames.size == 2 &&
                method.paramTypeNames[1] == "com.google.common.collect.ImmutableList\$Builder" &&
                method.instructions.any { it.methodRef?.declaredClassName == clazz.name }
        } ?: return@findMethodListDirect emptyList()
        val target = builderMethod.instructions.asSequence()
            .mapNotNull { it.methodRef }
            .firstOrNull { it.declaredClassName == clazz.name && it.returnTypeName == "boolean" && it.paramTypeNames.isEmpty() }
            ?: return@findMethodListDirect emptyList()
        findMethod {
            matcher {
                declaredClass(clazz.name, StringMatchType.Equals, false)
                name = target.name
                returnType = "boolean"
                paramCount = 0
            }
        }.distinctBy { it.descriptor }
    }.getOrDefault(emptyList())
}

/**
 * The story save action method called when the user taps Save in the story more menu.
 * Takes StoryCard as 1st parameter and references "PublishingTaskStatusUnknown" and "media_type".
 */
val storySaveActions = findMethodListDirect {
    runCatching {
        findMethod {
            matcher {
                usingStrings(listOf("PublishingTaskStatusUnknown"), StringMatchType.Contains)
            }
        }.filter { method ->
            method.returnTypeName == "void" &&
                method.paramTypeNames.size in 1..2 &&
                method.paramTypeNames.any { it.contains("StoryCard") }
        }.distinctBy { it.descriptor }
    }.getOrDefault(emptyList())
}

/**
 * The mutation that adds a search to search history.
 * Takes GraphSearchQuery, ... and boolean skipSave.
 */
val searchTypeaheadAddRecentSearchMethod = findMethodDirect {
    val matches = findMethod {
        matcher { usingStrings(listOf("FB4ASearchTypeaheadAddRecentSearch"), StringMatchType.Equals) }
    }.distinctBy { it.descriptor }
    check(matches.size == 1) { "expected 1 FB4ASearchTypeaheadAddRecentSearch method, found ${matches.size}" }
    matches.single()
}

/**
 * The Falco analytics event factory: private instance method reading PrivacyContext
 * and taking (Details, String eventName).
 */
val analyticsEventFactoryMethod = findMethodDirect {
    val matches = findMethod {
        matcher {
            usingFields {
                add { type = "com.facebook.logginginfra.falco.PrivacyContext" }
            }
            paramTypes = listOf(null, "java.lang.String")
        }
    }.distinctBy { it.descriptor }
    check(matches.size == 1) { "expected 1 analytics event factory method, found ${matches.size}" }
    matches.single()
}

/**
 * The Reels seen-state batch flush method, sending FbShortsSeenStateMutation.
 */
val reelsSeenStateMethod = findMethodDirect {
    val matches = findMethod {
        matcher { usingStrings(listOf("FbShortsSeenStateMutation"), StringMatchType.Equals) }
    }.filter { it.paramTypeNames.isEmpty() && it.returnTypeName == "void" }.distinctBy { it.descriptor }
    check(matches.size == 1) { "expected 1 FbShortsSeenStateMutation flush method, found ${matches.size}" }
    matches.single()
}

/**
 * The Story composer's music clip length calculation method.
 * Takes FbUserSession and composer state, holding 30000L and 15000L.
 */
val storyMusicClipLengthMethod = findMethodDirect {
    val matches = findMethod {
        matcher {
            returnType = "int"
            paramTypes = listOf("com.facebook.auth.usersession.FbUserSession", null)
        }
    }.filter { method ->
        val instructions = runCatching { method.instructions }.getOrNull() ?: return@filter false
        instructions.any { it.literal == 30000L } && instructions.any { it.literal == 15000L }
    }.distinctBy { it.descriptor }
    check(matches.size == 1) { "expected 1 story music clip length method, found ${matches.size}" }
    matches.single()
}

/**
 * Child getters of Bloks nodes on profiles.
 * Returns child lists on classes implementing Bloks node operations (getBoolean, getId, etc.).
 */
val bloksChildGetters = findMethodListDirect {
    findMethod {
        matcher {
            returnType = "java.util.List"
            paramTypes = listOf("int")
        }
    }.filter { method ->
        val owner = getClassData(method.declaredClassName) ?: return@filter false
        val mNames = owner.methods.map { it.name }.toSet()
        mNames.containsAll(listOf("getBoolean", "getId", "getInt", "getLong", "getString", "put"))
    }.distinctBy { it.descriptor }
}

/**
 * The Meta AI answer entity builder in search results.
 */
val searchMetaAiAnswerBuilder = findMethodDirect {
    val matches = findMethod {
        matcher {
            usingStrings(listOf("meta_ai_answer_entities"), StringMatchType.Equals)
            returnType = "void"
        }
    }.distinctBy { it.descriptor }
    check(matches.size == 1) { "expected 1 meta_ai_answer_entities method, found ${matches.size}" }
    matches.single()
}

/**
 * GenAiDeepDivePillPlugin methods: Ask Meta AI question pills under feed posts.
 */
val genAiDeepDivePillPluginMethods = findMethodListDirect {
    findMethod {
        matcher {
            declaredClass("com.facebook.feed.plugins.attachments.deepdivepill.impl.genai.GenAiDeepDivePillPlugin", StringMatchType.Equals, false)
        }
    }.distinctBy { it.descriptor }
}

/**
 * GenAiDeepDiveCtaPlugin methods: GenAI CTA chips under photo posts.
 */
val genAiDeepDiveCtaPluginMethods = findMethodListDirect {
    findMethod {
        matcher {
            declaredClass("com.facebook.feed.plugins.calltoaction.impl.genaideepdive.GenAiDeepDiveCtaPlugin", StringMatchType.Equals, false)
        }
    }.distinctBy { it.descriptor }
}

/**
 * InlineCommentSummaryWithGenAIPlugin methods: GenAI comment summaries under posts.
 */
val inlineCommentSummaryPluginMethods = findMethodListDirect {
    findMethod {
        matcher {
            declaredClass("com.facebook.feed.plugins.belowufifooter.impl.inlinecommentsummarywithgenai.InlineCommentSummaryWithGenAIPlugin", StringMatchType.Equals, false)
        }
    }.distinctBy { it.descriptor }
}


