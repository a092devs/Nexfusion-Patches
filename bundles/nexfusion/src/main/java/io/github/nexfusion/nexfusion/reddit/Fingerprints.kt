package io.github.nexfusion.nexfusion.reddit

import io.github.nexfusion.RequireAppVersion
import io.github.nexfusion.morphe.AccessFlags
import io.github.nexfusion.morphe.Fingerprint
import io.github.nexfusion.morphe.InstructionLocation.MatchAfterImmediately
import io.github.nexfusion.morphe.InstructionLocation.MatchAfterWithin
import io.github.nexfusion.morphe.Opcode
import io.github.nexfusion.morphe.fieldAccess
import io.github.nexfusion.morphe.findFieldDirect
import io.github.nexfusion.morphe.findMethodDirect
import io.github.nexfusion.morphe.literal
import io.github.nexfusion.morphe.methodCall
import io.github.nexfusion.morphe.newInstance
import io.github.nexfusion.morphe.string

// Ported from jkennethcarino/adobo (GPL-3.0),
// patches/src/main/kotlin/dev/jkcarino/adobo/patches/reddit/ (Reddit 2026.38.0).
//
// Adobo resolves these matchers against the exact APK at build time and rewrites the bytecode.
// The same matchers run here as DexKit queries at patch time on the device, and the patch files
// in this package install runtime hooks with the same results.

/** Class anchor for Reddit's `Link` model, matched by its `toString()` prefix. */
internal object LinkToStringFingerprint : Fingerprint(
    returnType = "Ljava/lang/String;",
    parameters = listOf(),
    filters = listOf(
        string("Link(id="),
    )
)

private object AccountPreferencesToStringFingerprint : Fingerprint(
    returnType = "Ljava/lang/String;",
    parameters = listOf(),
    filters = listOf(
        string("AccountPreferences(over18="),
    )
)

private object AccountToStringFingerprint : Fingerprint(
    returnType = "Ljava/lang/String;",
    parameters = listOf(),
    filters = listOf(
        string("Account(id="),
        string(", outboundClickTracking="),
    )
)

/** Redirect / click-tracking getters behind out.reddit.com, stubbed to open links directly. */
internal object GetAllowClickTrackingFingerprint : Fingerprint(
    classFingerprint = AccountPreferencesToStringFingerprint,
    name = "getAllowClickTracking",
    returnType = "Z",
)

internal object GetOutboundClickTrackingFingerprint : Fingerprint(
    classFingerprint = AccountToStringFingerprint,
    name = "getOutboundClickTracking",
    returnType = "Z",
)

internal object GetOutboundLinkFingerprint : Fingerprint(
    classFingerprint = LinkToStringFingerprint,
    name = "getOutboundLink",
    returnType = "L",
    parameters = listOf(),
)

/** Score / view-count getters on the `Link` model. */
internal object GetScoreFingerprint : Fingerprint(
    classFingerprint = LinkToStringFingerprint,
    name = "getScore",
    returnType = "I",
    parameters = listOf(),
)

internal object GetHideScoreFingerprint : Fingerprint(
    classFingerprint = LinkToStringFingerprint,
    name = "getHideScore",
    returnType = "Z",
    parameters = listOf(),
)

internal object GetViewCountFingerprint : Fingerprint(
    classFingerprint = LinkToStringFingerprint,
    name = "getViewCount",
    returnType = "Ljava/lang/Long;",
    parameters = listOf(),
)

/** The small dispatcher that creates and emits an `OnAutoRefreshFeed` event. */
internal object OnAutoRefreshFeedFingerprint : Fingerprint(
    name = "invoke",
    filters = listOf(
        newInstance(type = "Lcom/reddit/feeds/ui/events/OnAutoRefreshFeed;"),
        methodCall(
            definingClass = "Lcom/reddit/feeds/ui/events/OnAutoRefreshFeed;",
            name = "<init>",
            location = MatchAfterImmediately(),
        ),
        methodCall(
            name = "onEvent",
            location = MatchAfterImmediately(),
        ),
    )
)

/**
 * The dispatcher that decides an [com.reddit.screen.AppResumeAction;] on app resume; upstream
 * returns early from it so `REDIRECT_TO_HOME` never runs. Upstream also has a fallback that
 * splices a return into an `onResume` override; a runtime hook cannot splice instructions
 * mid-method, so only the dispatcher is ported and a miss is logged instead.
 */
internal object AppResumeRedirectFingerprint : Fingerprint(
    returnType = "V",
    parameters = listOf("L"),
    filters = listOf(
        fieldAccess(
            definingClass = "Lcom/reddit/screen/AppResumeAction;",
            name = "REDIRECT_TO_HOME",
        ),
        literal(0x10008000),
    )
)

/** Feed post header cell metadata, containing the post ID and media URL. */
internal object MetadataCellToStringFingerprint : Fingerprint(
    name = "toString",
    returnType = "Ljava/lang/String;",
    parameters = listOf(),
    filters = listOf(
        string("MetadataCellFragment(id="),
        string(", mediaPath="),
    ),
)

/** Feed video element cell, containing the linkId and Reddit default video URL. */
internal object VideoElementToStringFingerprint : Fingerprint(
    name = "toString",
    returnType = "Ljava/lang/String;",
    parameters = listOf(),
    filters = listOf(
        string("VideoElement(linkId="),
        string(", defaultUrl="),
    ),
)

/** Video player URLs, containing defaultUrl as first argument. */
internal object VideoUrlsToStringFingerprint : Fingerprint(
    name = "toString",
    returnType = "Ljava/lang/String;",
    parameters = listOf(),
    filters = listOf(
        string("VideoUrls(defaultUrl="),
    ),
)

/** Media3 DataSpec constructor, created for every URL the video player loads. */
internal object DataSpecToStringFingerprint : Fingerprint(
    name = "toString",
    returnType = "Ljava/lang/String;",
    filters = listOf(
        string("DataSpec["),
    ),
)

/** Domain VideoUrls model of feed videos. */
internal object DomainVideoUrlsToStringFingerprint : Fingerprint(
    definingClass = "Lcom/reddit/domain/model/VideoUrls;",
    name = "toString",
    returnType = "Ljava/lang/String;",
)

// The fingerprints below are the Morphe Reddit anchors, moved here when Reddit became a single
// NexFusion app. They are kept identical to the upstream 2026.41.0 anchors so the port can be
// diffed against `patches/src/main/kotlin/app/morphe/patches/reddit/`.

/** The listing constructor that fills the children, after and before fields. */
internal object ListingFingerprint : Fingerprint(
    definingClass = "Lcom/reddit/domain/model/listing/Listing;",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.CONSTRUCTOR),
    filters = listOf(
        fieldAccess(
            opcode = Opcode.IPUT_OBJECT,
            smali = "Lcom/reddit/domain/model/listing/Listing;->children:Ljava/util/List;",
        ),
        fieldAccess(
            opcode = Opcode.IPUT_OBJECT,
            smali = "Lcom/reddit/domain/model/listing/Listing;->after:Ljava/lang/String;",
        ),
        fieldAccess(
            opcode = Opcode.IPUT_OBJECT,
            smali = "Lcom/reddit/domain/model/listing/Listing;->before:Ljava/lang/String;",
        ),
    ),
)

/** SubmittedListing was removed in 2026.16.0. */
@RequireAppVersion(maxVersion = "2026.16.0")
internal object SubmittedListingFingerprint : Fingerprint(
    definingClass = "Lcom/reddit/domain/model/listing/SubmittedListing;",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.CONSTRUCTOR),
    filters = listOf(
        fieldAccess(
            opcode = Opcode.IPUT_OBJECT,
            smali = "Lcom/reddit/domain/model/listing/SubmittedListing;->children:Ljava/util/List;",
        ),
        fieldAccess(
            opcode = Opcode.IPUT_OBJECT,
            smali = "Lcom/reddit/domain/model/listing/SubmittedListing;->videoUploads:Ljava/util/List;",
        ),
        fieldAccess(
            opcode = Opcode.IPUT_OBJECT,
            smali = "Lcom/reddit/domain/model/listing/SubmittedListing;->after:Ljava/lang/String;",
        ),
    ),
)

private object AdPostSectionToStringFingerprint : Fingerprint(
    name = "toString",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "Ljava/lang/String;",
    parameters = listOf(),
    filters = listOf(
        string("AdPostSection(linkId="),
    ),
)

internal object AdPostSectionConstructorFingerprint : Fingerprint(
    classFingerprint = AdPostSectionToStringFingerprint,
    name = "<init>",
    returnType = "V",
)

@RequireAppVersion("2026.04.0")
internal object CommentsAdStateToStringFingerprint : Fingerprint(
    name = "toString",
    returnType = "Ljava/lang/String;",
    filters = listOf(
        string("CommentsAdState(conversationAdViewState="),
        string(", adsLoadCompleted="),
        fieldAccess(opcode = Opcode.IGET_BOOLEAN),
    ),
)

@RequireAppVersion("2026.04.0")
internal object CommentsAdStateConstructorFingerprint : Fingerprint(
    classFingerprint = CommentsAdStateToStringFingerprint,
    name = "<init>",
    returnType = "V",
)

@get:RequireAppVersion("2026.04.0")
internal val adsLoadCompletedField = findFieldDirect {
    CommentsAdStateToStringFingerprint.instructionMatches.last().instruction.fieldRef!!
}

internal object CommentsViewModelAdLoaderFingerprint : Fingerprint(
    definingClass = "Lcom/reddit/comments/presentation/CommentsViewModel;",
    returnType = "V",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    parameters = listOf("Z", "L", "I"),
    filters = listOf(
        methodCall(
            opcode = Opcode.INVOKE_DIRECT,
            name = "<init>",
            parameters = listOf("Z", "I"),
            returnType = "V",
        ),
    ),
)

internal object ImmutableListBuilderFingerprint : Fingerprint(
    name = "<clinit>",
    returnType = "V",
    parameters = listOf(),
    filters = listOf(
        methodCall(
            opcode = Opcode.INVOKE_STATIC,
            definingClass = "Lcom/reddit/accessibility/AutoplayVideoPreviewsOption;",
            name = "getEntries",
        ),
        methodCall(
            opcode = Opcode.INVOKE_STATIC,
            parameters = listOf("Ljava/lang/Iterable;"),
        ),
    ),
)

internal val ImmutableListBuilderReference = findMethodDirect {
    ImmutableListBuilderFingerprint.instructionMatches
        .last().instruction.methodRef!!
}

/** FrontpageApplication's onboarding gate, hooked by Start as guest. */
internal object FrontPageApplicationHasFinishedOnboardingFingerprint : Fingerprint(
    definingClass = "Lcom/reddit/frontpage/FrontpageApplication;",
    filters = listOf(
        methodCall(smali = "Lcom/reddit/session/Session;->isLoggedIn()Z"),
        methodCall(smali = "Lcom/reddit/session/Session;->isIncognito()Z"),
        methodCall(
            opcode = Opcode.INVOKE_VIRTUAL,
            returnType = "Z",
            parameters = listOf(),
            location = MatchAfterWithin(10),
        ),
    ),
)

internal val FrontPageApplicationHasFinishedOnboarding = findMethodDirect {
    FrontPageApplicationHasFinishedOnboardingFingerprint.instructionMatches.last().instruction.methodRef!!
}

/** The formatter that appends Reddit's tracking query parameters to a shared link. */
internal object ShareLinkFormatterFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC, AccessFlags.FINAL),
    returnType = "Ljava/lang/String;",
    parameters = listOf("Ljava/lang/String;", "Ljava/util/Map;"),
    filters = listOf(
        methodCall(smali = "Landroid/net/Uri${'$'}Builder;->clearQuery()Landroid/net/Uri${'$'}Builder;"),
    ),
)

/**
 * Video download, which is enqueued with the download manager.
 */
internal object DownloadVideoFingerprint : Fingerprint(
    returnType = "V",
    filters = listOf(
        methodCall(
            opcode = Opcode.INVOKE_DIRECT,
            definingClass = "Landroid/app/DownloadManager${'$'}Request;",
            name = "<init>"
        ),
        string("DownloadManager_not_available"),
    ),
)

/** Edge-to-edge setup method that sets status and navigation bar color and contrast enforcement. */
val EdgeToEdgeSetUpMethod = findMethodDirect {
    findMethod {
        matcher {
            addInvoke {
                name("setNavigationBarContrastEnforced")
            }
        }
    }.first()
}

/** Comment indentation Compose drawing lambda that uses alpha 0.4f (0x3ecccccd). */
val CommentIndentMethod = findMethodDirect {
    findMethod {
        matcher {
            name = "invoke"
            returnType = "java.lang.Object"
            paramTypes("java.lang.Object")
            usingNumbers(listOf(0x3ecccccd))
        }
    }.first()
}

/** Compose DrawScope.drawLine static method called inside CommentIndentMethod. */
val CommentIndentDrawLineMethod = findMethodDirect {
    CommentIndentMethod(this).invokes.first {
        it.paramTypes.size >= 8 && it.returnTypeName == "void"
    }
}

/** Search answers preview section fingerprints from adobo v1.7.0. */
private object SearchAnswersPreviewStreamingSectionFingerprint : Fingerprint(
    returnType = "Ljava/lang/String;",
    parameters = listOf(),
    filters = listOf(
        string("SearchAnswersPreviewStreamingSection(viewState="),
    ),
)

private object SearchAnswersPreviewSectionFingerprint : Fingerprint(
    filters = listOf(
        string("SearchAnswersPreviewSection(viewState="),
    ),
)

internal object AnswersPreviewStreamingSectionFingerprint : Fingerprint(
    classFingerprint = SearchAnswersPreviewStreamingSectionFingerprint,
    returnType = "V",
    filters = listOf(
        literal(-0x5ccf1694),
    ),
)

internal object AnswersPreviewSectionFingerprint : Fingerprint(
    classFingerprint = SearchAnswersPreviewSectionFingerprint,
    returnType = "V",
    filters = listOf(
        literal(-0xce97450),
    ),
)


