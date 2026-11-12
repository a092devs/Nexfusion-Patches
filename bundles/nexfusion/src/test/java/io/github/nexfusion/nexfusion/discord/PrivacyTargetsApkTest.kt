package io.github.nexfusion.nexfusion.discord

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.luckypray.dexkit.DexKitBridge
import java.io.File

/**
 * Checks the hard-coded privacy descriptors against the Discord APKs in `binaries/`.
 *
 * The privacy patches do not use fingerprints: each entry point is resolved by its exact JVM
 * descriptor at runtime, and a rename silently skips that hook. This test makes such a rename
 * visible before a release. It skips when no Discord APK is present, like the Instagram checks.
 */
class PrivacyTargetsApkTest {

    companion object {
        init {
            try {
                System.loadLibrary("dexkit")
            } catch (_: UnsatisfiedLinkError) {
                System.loadLibrary("libdexkit")
            }
        }

        private val knownRemovedIn353 = setOf(
            "Lcom/discord/crash_reporting/CrashReporting;->isCrashedLastRun()Ljava/lang/Boolean;",
            "Lcom/discord/crash_reporting/system_logs/SystemLogUtils;->initSystemLogCapture(Landroid/content/Context;)V",
            "Lcom/discord/crash_reporting/system_logs/SystemLogCapture;->startThread(Landroid/content/Context;)V",
            "Lcom/discord/crash_reporting/CrashPersistence;->getLastCrashInfo()Lcom/discord/crash_reporting/CrashPersistence\$LastCrashInfo;",
            "Lcom/discord/crash_reporting/CrashPersistence;->setLastCrashInfo(Lcom/discord/crash_reporting/CrashPersistence\$LastCrashInfo;)V",
            "Lcom/discord/crash_reporting/CrashReporting;->addBreadcrumb(Ljava/lang/String;Ljava/util/Map;Ljava/lang/String;Lcom/discord/crash_reporting/CrashReporting\$BreadcrumbLevel;Z)V",
            "Lcom/discord/crash_reporting/CrashReporting;->captureException(Ljava/lang/Throwable;Z)V",
            "Lcom/discord/crash_reporting/CrashReporting;->captureMessage(Ljava/lang/String;Ljava/lang/Exception;)V",
            "Lcom/discord/crash_reporting/CrashReporting;->captureMessage(Ljava/lang/String;Ljava/lang/String;Lcom/discord/crash_reporting/CrashReporting\$ErrorLevel;)V",
            "Lcom/discord/crash_reporting/CrashReporting;->isDisabled()Z",
            "Lcom/discord/crash_reporting/PerformanceTracing;->start()V",
            "Lcom/discord/crash_reporting/CrashReporting;->setTag(Lcom/discord/crash_reporting/CrashReporting\$CrashReportingReady;Ljava/lang/String;Ljava/lang/String;)V",
            "Lcom/discord/crash_reporting/CrashPersistence;->markCrashHandled(Ljava/lang/String;)V",
            "Lcom/discord/metric_monitor/MonitoringAgent;->increment(Lcom/discord/metric_monitor/MetricEvent;)V",
            "Lcom/discord/metric_monitor/MonitoringAgent;->setMetricLogger\$metric_monitor_release(Lkotlin/jvm/functions/Function1;)V",
            "Lcom/discord/analytics/touch/TouchEventAnalyticsModule;->onEventRecognized(Lcom/discord/analytics/touch/TouchEventDetails;)V",
            "Lcom/discord/analytics/touch/TouchLogger;->enable(Landroid/app/Activity;)V",
            "Lcom/discord/analytics/touch/TouchLogger;->disable()V",
            "Lcom/discord/analytics/touch/TouchLogger;->disable(Landroid/app/Activity;)V",
            "Lcom/discord/analytics/touch/TouchLogger;->clearListeners()V",
            "Lcom/discord/analytics/touch/TouchLogger;->handleTouchEvent(Landroid/view/MotionEvent;Landroid/view/ViewGroup;Ljava/lang/String;)V",
            "Lcom/discord/analytics/touch/TouchLogger;->registerListener(Lcom/discord/analytics/touch/OnEventRecognizedListener;)V",
            "Lcom/discord/analytics/touch/TouchLogger;->unregisterListener(Lcom/discord/analytics/touch/OnEventRecognizedListener;)V",
            "Lcom/discord/analytics/touch/TouchLogger;->notifyListeners\$analytics_release(Lcom/discord/analytics/touch/TouchEventDetails;)V",
            "Lcom/discord/crash_reporting/TelemetryRing;->init(Landroid/content/Context;Lcom/discord/crash_reporting/TelemetryRingTypes\$Budget;)V",
            "Lcom/discord/crash_reporting/TelemetryRing;->append(Ljava/lang/String;JLjava/lang/String;Ljava/util/Map;Ljava/util/List;)V",
            "Lcom/discord/crash_reporting/TelemetryRing;->enqueueWrite(Lcom/discord/crash_reporting/TelemetryRingSqliteStore\$EntryPayload;)V",
            "Lcom/discord/jank_stats/JankStatsAggregator;->initialize(Landroid/view/Window;)V",
            "Lcom/discord/jank_stats/JankStatsAggregator;->enableTracking()V",
            "Lcom/discord/jank_stats/JankStatsAggregator;->disableTracking()V",
            "Lcom/discord/jank_stats/JankStatsAggregator;->issueJankReport(Ljava/lang/String;)Lcom/discord/jank_stats/JankReport;",
            "Lcom/discord/jank_stats/JankStatsAggregator;->setJankHeuristicMultiplier(F)V",
            "Lcom/discord/jank_stats/FrameMetricsAggregator;->bindTo(Landroid/view/Window;)V",
            "Lcom/discord/jank_stats/FrameMetricsAggregator;->enableTracking(Landroid/view/Window;)V",
            "Lcom/discord/jank_stats/FrameMetricsAggregator;->disableTracking()V",
            "Lcom/discord/jank_stats/FrameMetricsAggregator;->closeReportWindow()V",
            "Lcom/discord/jank_stats/FrameMetricsAggregator;->issueReport()Lcom/discord/jank_stats/FrameMetricsReport;",
            "Lcom/discord/jank_stats/JankSessionRecorder;->init(Landroid/content/Context;)V",
            "Lcom/discord/jank_stats/JankSessionRecorder;->recordFrame(ZJ)V",
            "Lcom/discord/jank_stats/JankSessionRecorder;->openSegment(Ljava/lang/String;Ljava/lang/String;)V",
            "Lcom/discord/jank_stats/JankSessionRecorder;->openSegmentKeepingLabel(Ljava/lang/String;)V",
            "Lcom/discord/jank_stats/JankSessionRecorder;->nameOpenSegment(Ljava/lang/String;)V",
            "Lcom/discord/jank_stats/JankSessionRecorder;->renameAndSettleOpenSegment(Ljava/lang/String;)V",
            "Lcom/discord/jank_stats/JankSessionRecorder;->settleOpenSegment()V",
            "Lcom/discord/jank_stats/JankSessionRecorder;->onTrackingDisabled()V",
            "Lcom/discord/jank_stats/JankSessionRecorder;->acknowledge(Ljava/util/Set;)V",
            "Lcom/discord/jank_stats/JankSessionRecorder;->hydrateLaunchId(Ljava/lang/String;)V",
            "Lcom/discord/jank_stats/JankSessionRecorder;->pendingRecords(Lkotlin/jvm/functions/Function1;)V",
            "Lcom/discord/jank_stats/JankScreenContext;->beginScreenTransition()V",
            "Lcom/discord/jank_stats/JankScreenContext;->nameCurrentScreen(Ljava/lang/String;Ljava/util/Set;)V",
            "Lcom/discord/jank_stats/JankScreenContext;->setScreenContext(Ljava/lang/String;Ljava/lang/String;)V",
            "Lcom/discord/jank_stats/JankScreenContext;->settleCurrentScreen()V",
            "Lcom/discord/jank_stats/JankScreenContext;->onScreenBecameTopMost(Ljava/lang/String;)V",
            "Lcom/discord/jank_stats/JankScreenContext;->onScreenDidAppear(Ljava/lang/String;)V",
            "Lcom/discord/tti_manager/react/ReactMarkerListener;->start()V",
            "Lcom/discord/tti_manager/TTIMetrics;->record(Ljava/lang/String;JLjava/lang/String;Z)V",
        )
    }

    @Test
    fun `privacy targets exist in the Discord APK`() {
        val apks = File("binaries")
            .listFiles { file -> file.isFile && file.name.startsWith("com.discord-") && file.name.endsWith(".apk") }
            ?.sortedBy { it.name }
            .orEmpty()
        if (apks.isEmpty()) {
            println("No Discord APK found, skipping")
            return
        }

        val targets = listOf(
            PrivacyTargets.crash,
            PrivacyTargets.telemetry,
            PrivacyTargets.advertising,
            PrivacyTargets.attribution,
        ).flatten()

        for (apk in apks) {
            println("=== ${apk.name} ===")
            // ApkContext's version parser only accepts numeric versions; Discord reports
            // "351.11 - Stable", so open DexKit directly for this APK.
            DexKitBridge.create(apk.absolutePath).use { dexkit ->
                val missing = targets.filter { target ->
                    dexkit.findMethod { matcher { descriptor(target.descriptor) } }.isEmpty()
                }

                println("Verified ${targets.size - missing.size}/${targets.size} privacy targets")
                if (missing.isNotEmpty()) {
                    println("Missing privacy targets (${missing.size}):")
                    missing.forEach { println("  - ${it.descriptor}") }
                }

                // Discord 353.11+ retired 56 internal legacy analytics/jank/telemetry classes.
                // Runtime hooks ignore missing targets gracefully, but verify that no unexpected targets are missing.
                val expectedMissing = if (apk.name.contains("353.")) knownRemovedIn353 else emptySet()
                val unexpectedMissing = missing.map { it.descriptor }.toSet() - expectedMissing
                assertTrue(
                    unexpectedMissing.isEmpty(),
                    "${apk.name}: ${unexpectedMissing.size} unexpected privacy targets missing: $unexpectedMissing",
                )

                // The phone-font patch resolves these class names directly on supported versions.
                if (dexkit.findClass { matcher { className("com.facebook.react.common.assets.ReactFontManager") } }.isNotEmpty()) {
                    assertTrue(
                        dexkit.findMethod {
                            matcher {
                                declaredClass("com.facebook.react.common.assets.ReactFontManager")
                                name = "getTypeface"
                                paramTypes("java.lang.String", "com.facebook.react.common.assets.ReactFontManager\$TypefaceStyle", "android.content.res.AssetManager")
                            }
                        }.isNotEmpty(),
                        "${apk.name}: ReactFontManager.getTypeface not found",
                    )
                    assertTrue(
                        dexkit.findMethod {
                            matcher {
                                declaredClass("com.facebook.react.common.assets.ReactFontManager\$TypefaceStyle")
                                name = "getNearestStyle"
                                paramCount = 0
                            }
                        }.isNotEmpty(),
                        "${apk.name}: TypefaceStyle.getNearestStyle not found",
                    )
                } else {
                    println("${apk.name} does not contain ReactFontManager; UsePhoneFont will be skipped gracefully on this version.")
                }
                assertTrue(
                    dexkit.findClass {
                        matcher { className("com.facebook.react.bridge.Arguments") }
                    }.isNotEmpty(),
                    "${apk.name}: React Arguments not found",
                )
            }
        }
    }
}
