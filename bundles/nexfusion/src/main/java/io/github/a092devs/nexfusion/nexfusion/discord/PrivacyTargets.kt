package io.github.a092devs.nexfusion.nexfusion.discord

// Native privacy entry points ported from VenusIsJaded/Venus-Patches (GPL-3.0),
// patches/src/main/kotlin/app/venus/patches/PrivacyPatches.kt (Discord 348.10 - Stable).
//
// Venus rewrites these methods with an early-return prefix at build time. This module installs the
// same stubs at runtime: the hook callback replaces the method body with the same result, so the
// original code never runs. Entries are grouped like upstream; the jank-stats block follows the
// 351.11 layout, where the screen-context calls moved from JankStatsModule to JankScreenContext
// and FrameMetricsAggregator.enableTracking gained the Window parameter.
//
// Two upstream targets are not ported because their names are obfuscated and version-specific:
//   - Lio/sentry/android/core/c1;->b(Context, l0, b4)V          (Sentry's disk cache; covered by
//     the named SentryInitProvider.onCreate stub, which stops Sentry from starting at all)
//   - Lpe/a;->a(Context)AdvertisingIdClient$Info                (Discord's repackaged copy of
//     Google's AdvertisingIdClient; the named AppsFlyer AFb1qSDK target is ported)

/** What a stubbed entry point returns, mirroring Venus's `NativePrivacy.Result`. */
internal enum class PrivacyResult {
    VOID,
    TRUE,
    FALSE,
    NULL,
    SELF,
    EMPTY_STRING,
    PROMISE_NULL,
    PROMISE_TRUE,
    PROMISE_FALSE,
    PROMISE_EMPTY_ARRAY,
    NO_AD_ID,
    AF_SUCCESS,
    EMPTY_MAP,
    PROFILE_START,
    AD_ID_MAP,
    CALLBACK_NULL,
    CALLBACK_FALSE,
    CALLBACK_EMPTY_STRING,
    BOXED_FALSE,
    THROWABLE_STRING,
}

/**
 * One native method to stub.
 *
 * @param owner Class descriptor, like `Lcom/discord/crash_reporting/CrashReportingModule;`.
 * @param parameters Parameter descriptors. `J` and `D` count as one register but are single entries.
 * @param returns Return descriptor, like `V` or `Ljava/lang/String;`.
 */
internal class PrivacyTarget(
    val owner: String,
    val name: String,
    val parameters: List<String>,
    val returns: String,
    val result: PrivacyResult,
) {
    /** JVM method descriptor, like `Lcom/appsflyer/internal/AFa1uSDK;->start(Landroid/content/Context;)V`. */
    val descriptor: String
        get() = "$owner->$name(${parameters.joinToString(separator = "")})$returns"
}

internal object PrivacyTargets {

    val crash = listOf(
        PrivacyTarget("Lcom/discord/crash_reporting/CrashReportingModule;", "initializeManager", listOf(), "V", PrivacyResult.VOID),
        PrivacyTarget("Lcom/discord/crash_reporting/CrashReportingModule;", "getDidCrashDuringPreviousExecution", listOf("Lcom/facebook/react/bridge/Callback;"), "V", PrivacyResult.CALLBACK_FALSE),
        PrivacyTarget("Lcom/discord/crash_reporting/CrashReportingModule;", "getLastCrashReport", listOf("Lcom/facebook/react/bridge/Callback;"), "V", PrivacyResult.CALLBACK_NULL),
        PrivacyTarget("Lcom/discord/crash_reporting/CrashReportingModule;", "getSystemLog", listOf("Lcom/facebook/react/bridge/Callback;"), "V", PrivacyResult.CALLBACK_EMPTY_STRING),
        PrivacyTarget("Lcom/discord/crash_reporting/CrashReporting;", "isCrashedLastRun", listOf(), "Ljava/lang/Boolean;", PrivacyResult.BOXED_FALSE),
        PrivacyTarget("Lcom/discord/crash_reporting/system_logs/SystemLogUtils;", "initSystemLogCapture", listOf("Landroid/content/Context;"), "V", PrivacyResult.VOID),
        PrivacyTarget("Lcom/discord/crash_reporting/system_logs/SystemLogCapture;", "startThread", listOf("Landroid/content/Context;"), "V", PrivacyResult.VOID),
        PrivacyTarget("Lcom/discord/crash_reporting/CrashPersistence;", "getLastCrashInfo", listOf(), "Lcom/discord/crash_reporting/CrashPersistence\$LastCrashInfo;", PrivacyResult.NULL),
        PrivacyTarget("Lcom/discord/crash_reporting/CrashPersistence;", "setLastCrashInfo", listOf("Lcom/discord/crash_reporting/CrashPersistence\$LastCrashInfo;"), "V", PrivacyResult.VOID),
        PrivacyTarget("Lcom/discord/crash_reporting/WebrtcCrashReporting;", "reportWebrtcException", listOf("Ljava/lang/Throwable;"), "Ljava/lang/String;", PrivacyResult.THROWABLE_STRING),
        PrivacyTarget("Lcom/discord/crash_reporting/CrashReporting;", "addBreadcrumb", listOf("Ljava/lang/String;", "Ljava/util/Map;", "Ljava/lang/String;", "Lcom/discord/crash_reporting/CrashReporting\$BreadcrumbLevel;", "Z"), "V", PrivacyResult.VOID),
        PrivacyTarget("Lcom/discord/crash_reporting/CrashReporting;", "addBreadcrumbBatchBinary", listOf("Ljava/nio/ByteBuffer;"), "V", PrivacyResult.VOID),
        PrivacyTarget("Lcom/discord/crash_reporting/CrashReporting;", "captureException", listOf("Ljava/lang/Throwable;", "Z"), "V", PrivacyResult.VOID),
        PrivacyTarget("Lcom/discord/crash_reporting/CrashReporting;", "captureMessage", listOf("Ljava/lang/String;", "Ljava/lang/Exception;"), "V", PrivacyResult.VOID),
        PrivacyTarget("Lcom/discord/crash_reporting/CrashReporting;", "captureMessage", listOf("Ljava/lang/String;", "Ljava/lang/String;", "Lcom/discord/crash_reporting/CrashReporting\$ErrorLevel;"), "V", PrivacyResult.VOID),
        PrivacyTarget("Lcom/discord/crash_reporting/CrashReporting;", "libdiscoreAddBreadcrumb", listOf("Ljava/lang/String;", "Ljava/lang/String;", "Ljava/lang/String;"), "V", PrivacyResult.VOID),
        PrivacyTarget("Lcom/discord/crash_reporting/CrashReporting;", "isDisabled", listOf(), "Z", PrivacyResult.TRUE),
        PrivacyTarget("Lio/sentry/android/core/SentryInitProvider;", "onCreate", listOf(), "Z", PrivacyResult.TRUE),
        PrivacyTarget("Lcom/discord/crash_reporting/NativeCrashReporting\$Companion;", "initNative", listOf("Z"), "V", PrivacyResult.VOID),
        PrivacyTarget("Lio/sentry/react/RNSentryModule;", "initNativeSdk", listOf("Lcom/facebook/react/bridge/ReadableMap;", "Lcom/facebook/react/bridge/Promise;"), "V", PrivacyResult.PROMISE_FALSE),
        PrivacyTarget("Lio/sentry/react/RNSentryModule;", "captureEnvelope", listOf("Ljava/lang/String;", "Lcom/facebook/react/bridge/ReadableMap;", "Lcom/facebook/react/bridge/Promise;"), "V", PrivacyResult.PROMISE_TRUE),
        PrivacyTarget("Lio/sentry/react/RNSentryModule;", "captureReplay", listOf("Z", "Lcom/facebook/react/bridge/Promise;"), "V", PrivacyResult.PROMISE_NULL),
        PrivacyTarget("Lio/sentry/react/RNSentryModule;", "captureScreenshot", listOf("Lcom/facebook/react/bridge/Promise;"), "V", PrivacyResult.PROMISE_NULL),
        PrivacyTarget("Lio/sentry/react/RNSentryModule;", "fetchViewHierarchy", listOf("Lcom/facebook/react/bridge/Promise;"), "V", PrivacyResult.PROMISE_NULL),
        PrivacyTarget("Lio/sentry/react/RNSentryModule;", "fetchNativeDeviceContexts", listOf("Lcom/facebook/react/bridge/Promise;"), "V", PrivacyResult.PROMISE_NULL),
        PrivacyTarget("Lio/sentry/react/RNSentryModule;", "fetchNativeAppStart", listOf("Lcom/facebook/react/bridge/Promise;"), "V", PrivacyResult.PROMISE_NULL),
        PrivacyTarget("Lio/sentry/react/RNSentryModule;", "fetchNativeFrames", listOf("Lcom/facebook/react/bridge/Promise;"), "V", PrivacyResult.PROMISE_NULL),
        PrivacyTarget("Lio/sentry/react/RNSentryModule;", "fetchNativeLogAttributes", listOf("Lcom/facebook/react/bridge/Promise;"), "V", PrivacyResult.PROMISE_NULL),
        PrivacyTarget("Lio/sentry/react/RNSentryModule;", "initNativeReactNavigationNewFrameTracking", listOf("Lcom/facebook/react/bridge/Promise;"), "V", PrivacyResult.PROMISE_NULL),
        PrivacyTarget("Lio/sentry/react/RNSentryModule;", "addBreadcrumb", listOf("Lcom/facebook/react/bridge/ReadableMap;"), "V", PrivacyResult.VOID),
        PrivacyTarget("Lio/sentry/react/RNSentryModule;", "setContext", listOf("Ljava/lang/String;", "Lcom/facebook/react/bridge/ReadableMap;"), "V", PrivacyResult.VOID),
        PrivacyTarget("Lio/sentry/react/RNSentryModule;", "setExtra", listOf("Ljava/lang/String;", "Ljava/lang/String;"), "V", PrivacyResult.VOID),
        PrivacyTarget("Lio/sentry/react/RNSentryModule;", "setTag", listOf("Ljava/lang/String;", "Ljava/lang/String;"), "V", PrivacyResult.VOID),
        PrivacyTarget("Lio/sentry/react/RNSentryModule;", "setUser", listOf("Lcom/facebook/react/bridge/ReadableMap;", "Lcom/facebook/react/bridge/ReadableMap;"), "V", PrivacyResult.VOID),
        PrivacyTarget("Lio/sentry/react/RNSentryModule;", "enableNativeFramesTracking", listOf(), "V", PrivacyResult.VOID),
        PrivacyTarget("Lio/sentry/react/RNSentryModule;", "startProfiling", listOf("Z"), "Lcom/facebook/react/bridge/WritableMap;", PrivacyResult.PROFILE_START),
        PrivacyTarget("Lio/sentry/react/RNSentryModule;", "stopProfiling", listOf(), "Lcom/facebook/react/bridge/WritableMap;", PrivacyResult.EMPTY_MAP),
        PrivacyTarget("Lio/sentry/react/RNSentryModule;", "getCurrentReplayId", listOf(), "Ljava/lang/String;", PrivacyResult.NULL),
        PrivacyTarget("Lio/sentry/react/RNSentryModule;", "setActiveSpanId", listOf("Ljava/lang/String;"), "Z", PrivacyResult.FALSE),
        PrivacyTarget("Lcom/discord/crash_reporting/PerformanceTracing;", "start", listOf(), "V", PrivacyResult.VOID),
        PrivacyTarget("Lcom/discord/crash_reporting/CrashReporting;", "setTag", listOf("Lcom/discord/crash_reporting/CrashReporting\$CrashReportingReady;", "Ljava/lang/String;", "Ljava/lang/String;"), "V", PrivacyResult.VOID),
        PrivacyTarget("Lcom/discord/crash_reporting/CrashPersistence;", "markCrashHandled", listOf("Ljava/lang/String;"), "V", PrivacyResult.VOID),
    )

    val telemetry = listOf(
        PrivacyTarget("Lcom/discord/metric_monitor/MonitoringAgent;", "increment", listOf("Lcom/discord/metric_monitor/MetricEvent;"), "V", PrivacyResult.VOID),
        PrivacyTarget("Lcom/discord/metric_monitor/MonitoringAgent;", "setMetricLogger\$metric_monitor_release", listOf("Lkotlin/jvm/functions/Function1;"), "V", PrivacyResult.VOID),
        PrivacyTarget("Lcom/discord/analytics/touch/TouchEventAnalyticsModule;", "enableTouchLogging", listOf(), "V", PrivacyResult.VOID),
        PrivacyTarget("Lcom/discord/analytics/touch/TouchEventAnalyticsModule;", "disableTouchLogging", listOf(), "V", PrivacyResult.VOID),
        PrivacyTarget("Lcom/discord/analytics/touch/TouchEventAnalyticsModule;", "onEventRecognized", listOf("Lcom/discord/analytics/touch/TouchEventDetails;"), "V", PrivacyResult.VOID),
        PrivacyTarget("Lcom/discord/analytics/touch/TouchLogger;", "enable", listOf("Landroid/app/Activity;"), "V", PrivacyResult.VOID),
        PrivacyTarget("Lcom/discord/analytics/touch/TouchLogger;", "disable", listOf(), "V", PrivacyResult.VOID),
        PrivacyTarget("Lcom/discord/analytics/touch/TouchLogger;", "disable", listOf("Landroid/app/Activity;"), "V", PrivacyResult.VOID),
        PrivacyTarget("Lcom/discord/analytics/touch/TouchLogger;", "clearListeners", listOf(), "V", PrivacyResult.VOID),
        PrivacyTarget("Lcom/discord/analytics/touch/TouchLogger;", "handleTouchEvent", listOf("Landroid/view/MotionEvent;", "Landroid/view/ViewGroup;", "Ljava/lang/String;"), "V", PrivacyResult.VOID),
        PrivacyTarget("Lcom/discord/analytics/touch/TouchLogger;", "registerListener", listOf("Lcom/discord/analytics/touch/OnEventRecognizedListener;"), "V", PrivacyResult.VOID),
        PrivacyTarget("Lcom/discord/analytics/touch/TouchLogger;", "unregisterListener", listOf("Lcom/discord/analytics/touch/OnEventRecognizedListener;"), "V", PrivacyResult.VOID),
        PrivacyTarget("Lcom/discord/analytics/touch/TouchLogger;", "notifyListeners\$analytics_release", listOf("Lcom/discord/analytics/touch/TouchEventDetails;"), "V", PrivacyResult.VOID),
        PrivacyTarget("Lcom/discord/crash_reporting/TelemetryRing;", "init", listOf("Landroid/content/Context;", "Lcom/discord/crash_reporting/TelemetryRingTypes\$Budget;"), "V", PrivacyResult.VOID),
        PrivacyTarget("Lcom/discord/crash_reporting/TelemetryRing;", "append", listOf("Ljava/lang/String;", "J", "Ljava/lang/String;", "Ljava/util/Map;", "Ljava/util/List;"), "V", PrivacyResult.VOID),
        PrivacyTarget("Lcom/discord/crash_reporting/TelemetryRing;", "enqueueWrite", listOf("Lcom/discord/crash_reporting/TelemetryRingSqliteStore\$EntryPayload;"), "V", PrivacyResult.VOID),
        PrivacyTarget("Lcom/discord/crash_reporting/TelemetryRingModule;", "append", listOf("Ljava/lang/String;", "D", "Ljava/lang/String;", "Lcom/facebook/react/bridge/ReadableMap;", "Lcom/facebook/react/bridge/ReadableArray;"), "V", PrivacyResult.VOID),
        // Jank stats: per-frame timings for every screen, named and saved to disk, then uploaded as
        // ANDROID_JANK_STATS / ANDROID_JANK_SESSION events. Nothing in the app shows or uses them.
        PrivacyTarget("Lcom/discord/jank_stats/JankStatsAggregator;", "initialize", listOf("Landroid/view/Window;"), "V", PrivacyResult.VOID),
        PrivacyTarget("Lcom/discord/jank_stats/JankStatsAggregator;", "enableTracking", listOf(), "V", PrivacyResult.VOID),
        PrivacyTarget("Lcom/discord/jank_stats/JankStatsAggregator;", "disableTracking", listOf(), "V", PrivacyResult.VOID),
        PrivacyTarget("Lcom/discord/jank_stats/JankStatsAggregator;", "issueJankReport", listOf("Ljava/lang/String;"), "Lcom/discord/jank_stats/JankReport;", PrivacyResult.NULL),
        PrivacyTarget("Lcom/discord/jank_stats/JankStatsAggregator;", "setJankHeuristicMultiplier", listOf("F"), "V", PrivacyResult.VOID),
        PrivacyTarget("Lcom/discord/jank_stats/FrameMetricsAggregator;", "bindTo", listOf("Landroid/view/Window;"), "V", PrivacyResult.VOID),
        PrivacyTarget("Lcom/discord/jank_stats/FrameMetricsAggregator;", "enableTracking", listOf("Landroid/view/Window;"), "V", PrivacyResult.VOID),
        PrivacyTarget("Lcom/discord/jank_stats/FrameMetricsAggregator;", "disableTracking", listOf(), "V", PrivacyResult.VOID),
        PrivacyTarget("Lcom/discord/jank_stats/FrameMetricsAggregator;", "closeReportWindow", listOf(), "V", PrivacyResult.VOID),
        PrivacyTarget("Lcom/discord/jank_stats/FrameMetricsAggregator;", "issueReport", listOf(), "Lcom/discord/jank_stats/FrameMetricsReport;", PrivacyResult.NULL),
        // Discord's JankStatsManager skips the upload when the report is null.
        PrivacyTarget("Lcom/discord/jank_stats/JankStatsModule;", "requestReport", listOf(), "Lcom/facebook/react/bridge/WritableMap;", PrivacyResult.NULL),
        PrivacyTarget("Lcom/discord/jank_stats/JankStatsModule;", "startTracking", listOf(), "V", PrivacyResult.VOID),
        PrivacyTarget("Lcom/discord/jank_stats/JankStatsModule;", "stopTracking", listOf(), "V", PrivacyResult.VOID),
        PrivacyTarget("Lcom/discord/jank_stats/JankStatsModule;", "setJankHeuristicMultiplier", listOf("D"), "V", PrivacyResult.VOID),
        // JankSessionManager stops when there are no pending sessions.
        PrivacyTarget("Lcom/discord/jank_stats/JankSessionModule;", "getPendingReports", listOf("Lcom/facebook/react/bridge/Promise;"), "V", PrivacyResult.PROMISE_EMPTY_ARRAY),
        // JankSessionRecorder spawns a background thread and opens two SharedPreferences stores on
        // startup, and JankScreenContext locks segmentLock on every screen change.
        PrivacyTarget("Lcom/discord/jank_stats/JankSessionRecorder;", "init", listOf("Landroid/content/Context;"), "V", PrivacyResult.VOID),
        PrivacyTarget("Lcom/discord/jank_stats/JankSessionRecorder;", "recordFrame", listOf("Z", "J"), "V", PrivacyResult.VOID),
        PrivacyTarget("Lcom/discord/jank_stats/JankSessionRecorder;", "openSegment", listOf("Ljava/lang/String;", "Ljava/lang/String;"), "V", PrivacyResult.VOID),
        PrivacyTarget("Lcom/discord/jank_stats/JankSessionRecorder;", "openSegmentKeepingLabel", listOf("Ljava/lang/String;"), "V", PrivacyResult.VOID),
        PrivacyTarget("Lcom/discord/jank_stats/JankSessionRecorder;", "nameOpenSegment", listOf("Ljava/lang/String;"), "V", PrivacyResult.VOID),
        PrivacyTarget("Lcom/discord/jank_stats/JankSessionRecorder;", "renameAndSettleOpenSegment", listOf("Ljava/lang/String;"), "V", PrivacyResult.VOID),
        PrivacyTarget("Lcom/discord/jank_stats/JankSessionRecorder;", "settleOpenSegment", listOf(), "V", PrivacyResult.VOID),
        PrivacyTarget("Lcom/discord/jank_stats/JankSessionRecorder;", "onTrackingDisabled", listOf(), "V", PrivacyResult.VOID),
        PrivacyTarget("Lcom/discord/jank_stats/JankSessionRecorder;", "acknowledge", listOf("Ljava/util/Set;"), "V", PrivacyResult.VOID),
        PrivacyTarget("Lcom/discord/jank_stats/JankSessionRecorder;", "hydrateLaunchId", listOf("Ljava/lang/String;"), "V", PrivacyResult.VOID),
        PrivacyTarget("Lcom/discord/jank_stats/JankSessionRecorder;", "pendingRecords", listOf("Lkotlin/jvm/functions/Function1;"), "V", PrivacyResult.VOID),
        PrivacyTarget("Lcom/discord/jank_stats/JankSessionModule;", "hydrateLaunchId", listOf("Ljava/lang/String;"), "V", PrivacyResult.VOID),
        PrivacyTarget("Lcom/discord/jank_stats/JankSessionModule;", "ackReports", listOf("Lcom/facebook/react/bridge/ReadableArray;"), "V", PrivacyResult.VOID),
        PrivacyTarget("Lcom/discord/jank_stats/JankScreenContext;", "beginScreenTransition", listOf(), "V", PrivacyResult.VOID),
        PrivacyTarget("Lcom/discord/jank_stats/JankScreenContext;", "nameCurrentScreen", listOf("Ljava/lang/String;", "Ljava/util/Set;"), "V", PrivacyResult.VOID),
        PrivacyTarget("Lcom/discord/jank_stats/JankScreenContext;", "setScreenContext", listOf("Ljava/lang/String;", "Ljava/lang/String;"), "V", PrivacyResult.VOID),
        PrivacyTarget("Lcom/discord/jank_stats/JankScreenContext;", "settleCurrentScreen", listOf(), "V", PrivacyResult.VOID),
        PrivacyTarget("Lcom/discord/jank_stats/JankScreenContext;", "onScreenBecameTopMost", listOf("Ljava/lang/String;"), "V", PrivacyResult.VOID),
        PrivacyTarget("Lcom/discord/jank_stats/JankScreenContext;", "onScreenDidAppear", listOf("Ljava/lang/String;"), "V", PrivacyResult.VOID),
        // Native startup markers and ART profile telemetry only feed TTI analytics.
        PrivacyTarget("Lcom/discord/tti_manager/react/ReactMarkerListener;", "start", listOf(), "V", PrivacyResult.VOID),
        PrivacyTarget("Lcom/discord/tti_manager/TTIMetrics;", "record", listOf("Ljava/lang/String;", "J", "Ljava/lang/String;", "Z"), "V", PrivacyResult.VOID),
        PrivacyTarget("Lcom/discord/tti_manager/TTIModule;", "getAndroidArtProfileTelemetry", listOf("Lcom/facebook/react/bridge/Promise;"), "V", PrivacyResult.PROMISE_NULL),
        PrivacyTarget("Lcom/discord/tti_manager/TTIManagerModule;", "getAllNativeTimestamps", listOf("Lcom/facebook/react/bridge/Promise;"), "V", PrivacyResult.PROMISE_EMPTY_ARRAY),
        PrivacyTarget("Lcom/discord/tti_manager/TTIManagerModule;", "trackTTILogged", listOf(), "V", PrivacyResult.VOID),
        PrivacyTarget("Lcom/discord/tti_manager/TTIManagerModule;", "initialize", listOf(), "V", PrivacyResult.VOID),
        PrivacyTarget("Lcom/discord/tti_manager/TTIManagerModule;", "invalidate", listOf(), "V", PrivacyResult.VOID),
        PrivacyTarget("Lcom/discord/tti_manager/TTIManagerModule;", "logToDevice", listOf("Ljava/lang/String;"), "V", PrivacyResult.VOID),
    )

    val advertising = listOf(
        PrivacyTarget("Lcom/discord/ads/AdsModule;", "getGoogleAdvertisingId", listOf("Lcom/facebook/react/bridge/Promise;"), "V", PrivacyResult.AD_ID_MAP),
        PrivacyTarget("Lcom/appsflyer/internal/AFb1qSDK;", "AFAdRevenueData", listOf("Landroid/content/Context;"), "Lcom/appsflyer/internal/AFb1qSDK\$AFa1uSDK;", PrivacyResult.NO_AD_ID),
    )

    val attribution = listOf(
        PrivacyTarget("Lcom/discord/analytics/InstallReferrerModule;", "get", listOf("Lcom/facebook/react/bridge/Promise;"), "V", PrivacyResult.PROMISE_NULL),
        PrivacyTarget("Lcom/appsflyer/internal/AFa1uSDK;", "init", listOf("Ljava/lang/String;", "Lcom/appsflyer/AppsFlyerConversionListener;", "Landroid/content/Context;"), "Lcom/appsflyer/AppsFlyerLib;", PrivacyResult.SELF),
        PrivacyTarget("Lcom/appsflyer/internal/AFa1uSDK;", "isStopped", listOf(), "Z", PrivacyResult.TRUE),
        PrivacyTarget("Lcom/appsflyer/internal/AFa1uSDK;", "start", listOf("Landroid/content/Context;"), "V", PrivacyResult.VOID),
        PrivacyTarget("Lcom/appsflyer/internal/AFa1uSDK;", "start", listOf("Landroid/content/Context;", "Ljava/lang/String;"), "V", PrivacyResult.VOID),
        PrivacyTarget("Lcom/appsflyer/internal/AFa1uSDK;", "start", listOf("Landroid/content/Context;", "Ljava/lang/String;", "Lcom/appsflyer/attribution/AppsFlyerRequestListener;"), "V", PrivacyResult.AF_SUCCESS),
        PrivacyTarget("Lcom/appsflyer/internal/AFa1uSDK;", "logEvent", listOf("Landroid/content/Context;", "Ljava/lang/String;", "Ljava/util/Map;"), "V", PrivacyResult.VOID),
        PrivacyTarget("Lcom/appsflyer/internal/AFa1uSDK;", "logEvent", listOf("Landroid/content/Context;", "Ljava/lang/String;", "Ljava/util/Map;", "Lcom/appsflyer/attribution/AppsFlyerRequestListener;"), "V", PrivacyResult.AF_SUCCESS),
        PrivacyTarget("Lcom/appsflyer/internal/AFa1uSDK;", "logSession", listOf("Landroid/content/Context;"), "V", PrivacyResult.VOID),
        PrivacyTarget("Lcom/appsflyer/internal/AFa1uSDK;", "logLocation", listOf("Landroid/content/Context;", "D", "D"), "V", PrivacyResult.VOID),
        PrivacyTarget("Lcom/appsflyer/internal/AFa1uSDK;", "logAdRevenue", listOf("Lcom/appsflyer/AFAdRevenueData;", "Ljava/util/Map;"), "V", PrivacyResult.VOID),
        PrivacyTarget("Lcom/appsflyer/internal/AFa1uSDK;", "setCustomerIdAndLogSession", listOf("Ljava/lang/String;", "Landroid/content/Context;"), "V", PrivacyResult.VOID),
        PrivacyTarget("Lcom/appsflyer/internal/AFa1uSDK;", "updateServerUninstallToken", listOf("Landroid/content/Context;", "Ljava/lang/String;"), "V", PrivacyResult.VOID),
        PrivacyTarget("Lcom/appsflyer/internal/AFa1uSDK;", "performOnAppAttribution", listOf("Landroid/content/Context;", "Ljava/net/URI;"), "V", PrivacyResult.VOID),
        PrivacyTarget("Lcom/appsflyer/internal/AFa1uSDK;", "performOnDeepLinking", listOf("Landroid/content/Intent;", "Landroid/content/Context;"), "V", PrivacyResult.VOID),
        PrivacyTarget("Lcom/appsflyer/internal/AFa1uSDK;", "setAdditionalData", listOf("Ljava/util/Map;"), "V", PrivacyResult.VOID),
        PrivacyTarget("Lcom/appsflyer/internal/AFa1uSDK;", "setAndroidIdData", listOf("Ljava/lang/String;"), "V", PrivacyResult.VOID),
        PrivacyTarget("Lcom/appsflyer/internal/AFa1uSDK;", "setImeiData", listOf("Ljava/lang/String;"), "V", PrivacyResult.VOID),
        PrivacyTarget("Lcom/appsflyer/internal/AFa1uSDK;", "setOaidData", listOf("Ljava/lang/String;"), "V", PrivacyResult.VOID),
        PrivacyTarget("Lcom/appsflyer/internal/AFa1uSDK;", "setInstallId", listOf("Ljava/lang/String;"), "V", PrivacyResult.VOID),
        PrivacyTarget("Lcom/appsflyer/internal/AFa1uSDK;", "setCustomerUserId", listOf("Ljava/lang/String;"), "V", PrivacyResult.VOID),
        PrivacyTarget("Lcom/appsflyer/internal/AFa1uSDK;", "setPhoneNumber", listOf("Ljava/lang/String;"), "V", PrivacyResult.VOID),
        PrivacyTarget("Lcom/appsflyer/internal/AFa1uSDK;", "setUserEmails", listOf("Lcom/appsflyer/AppsFlyerProperties\$EmailsCryptType;", "[Ljava/lang/String;"), "V", PrivacyResult.VOID),
        PrivacyTarget("Lcom/appsflyer/internal/AFa1uSDK;", "setUserEmails", listOf("[Ljava/lang/String;"), "V", PrivacyResult.VOID),
        PrivacyTarget("Lcom/appsflyer/internal/AFa1uSDK;", "setPartnerData", listOf("Ljava/lang/String;", "Ljava/util/Map;"), "V", PrivacyResult.VOID),
        PrivacyTarget("Lcom/appsflyer/internal/AFa1uSDK;", "getAppsFlyerUID", listOf("Landroid/content/Context;"), "Ljava/lang/String;", PrivacyResult.EMPTY_STRING),
        PrivacyTarget("Lcom/appsflyer/internal/AFa1uSDK;", "getAttributionId", listOf("Landroid/content/Context;"), "Ljava/lang/String;", PrivacyResult.EMPTY_STRING),
    )
}
