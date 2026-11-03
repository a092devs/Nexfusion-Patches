# Changelog

All notable changes to the NexFusion patch bundles are documented here. Releases are dispatched
automatically on every push to `main` for the version of the first `X.Y.Z` section below when that
version has no release yet. The section matching the release tag becomes the release notes,
falling back to generated commit notes when no section exists. Android CI
validates changes; the dedicated release workflow builds and publishes release
bundles after CI succeeds.

## 4.1.0

- `NexFusion` 4.1.0:
  * CI & Release Infrastructure:
    - Added `beta` branch validation to Android CI workflows.
    - Gated automated release dispatching strictly to the `main` branch to guarantee production release stability.
  * Ecosystem Alignment & Compatibility:
    - Synchronized patch bundle versioning to 4.1.0 with the NexFusion 4.1.0 manager ecosystem.
    - Verified full runtime cross-compatibility, dynamic DexKit hook resolution, and resource injection integrity across all 20 supported target applications.

## 4.0.0

- `NexFusion` 4.0.0:
  * Inaugural Nexfusion-Patches Rebranding & Package Migration:
    - Renamed bundle architecture to Nexfusion-Patches across all patch definitions, extension bindings, and build configurations.
    - Fully migrated package namespaces, extension loaders, and bytecode references to `io.github.nexfusion.*`.
  * Reddit Quality of Life:
    - Transparent Navigation Bar: Enforced fully transparent system navigation bar across all Reddit screens by hooking Reddit's EdgeToEdge setup, activity window focus changes, and platform Window APIs with contrast enforcement disabled.
    - Colorize Comment Indent Lines: Added color-coded indentation guidelines per nesting depth, cycling through 8 distinct theme hues with full opacity in Jetpack Compose drawScope.
  * Facebook Media Downloader Hardening:
    - Fixed an issue where the download patch failed to apply due to strict multi-hook assertions and rigid menu filler signatures.
    - Added resilient multi-anchor fingerprint resolution for post overflow menus, VideoPlayerParams constructors, and can_viewer_download gates.
    - Wrapped individual capability initializers in isolated error boundaries, ensuring download features apply reliably across Facebook 581 through 585+.
  * Instagram Interaction Safeguards:
    - Expanded "Turn off double tap to like" patch to cover both Feed (`onDoubleTapMedia`) and Reels (`ClipsItemGestureDetector_onDoubleTap`) gesture listeners.
    - Defaulted patch to enabled to reliably prevent accidental likes while scrolling feeds and viewing reels.
  * YouTube Settings & About Section:
    - Updated About section inside Morphe settings to display NexFusion, release version 4.0.0, and official NexFusion repository links.
    - Preserved Morphe settings menus, preferences hierarchy, and headers intact.
  * Issue Template Refinement & Housekeeping:
    - Rectified duplicated bundle examples and updated version references to 4.0.0.
    - Cleaned up issue templates, grammar, and navigation instructions across repositories.
  * Universal Compatibility & Integration:
    - Full bidirectional compatibility with the NexFusion manager ecosystem.
    - Preserved all runtime patch features, dynamic hook registries, and resource injection tables across 20 supported applications.
  * Zero-Trace Architecture & Optimization:
    - Cleaned up build artifacts, legacy tracking branches, and outdated namespace bindings.

## 3.6.3


- `NexFusion` 3.6.3:
  * Universal Cross-Compatibility System:
    - Removed rigid manager version locks, enabling cross-compatibility across all manager versions.
    - Future bundle updates and downgrades can be directly installed without requiring manager updates.
  * Instagram:
    - Added `Turn off HDR brightness boosts` patch: clamps HDR headroom on SurfaceView/Window and neutralizes synthetic HDR tone mapping in software video decoders.
  * Facebook:
    - Added `Hide suggested groups` patch: suppresses Suggested for you group cards and recommendation units in feeds and the Groups tab.
  * YouTube / YouTube Music:
    - Synchronized core extensions: adjusted snackbar queue timeouts, added Watch Later button settings, fixed Explore shelf visibility, and improved Shorts hold-to-speed handling.
  * Release Synchronization:
    - Synchronized bundle release metadata with NexFusion 3.6.3.

## 3.6.2

- `NexFusion` 3.6.2:
  * Gboard Emoji Fast Access Row Bug Fix:
    - Fixed an issue where the "Emoji fast access row" setting in Gboard settings remained enabled after being disabled.
    - Fixed on-keyboard "Remove row" action being ignored by honoring persisted user choices and preventing dynamic preference hooks from overriding explicitly saved settings.
  * Decommissioned Spotify Support:
    - Removed Spotify from the patch suite and bundle manifests.
  * Minimum Manager Version:
    - Upgraded minimum manager version requirement to `3.6.2`.

## 3.6.1

- `NexFusion` 3.6.1:
  * Google Maps Support (`com.google.android.apps.maps`):
    - Added `Hide ads` patch: suppresses promoted map pins, sponsored search result rows, navigation ad labels, and ad impression beacons.
    - Added `High refresh rate` patch: unhooks artificial 60 Hz window caps on WindowManager layout parameters and unlocks 90 Hz / 120 Hz display refresh rates and map rendering.
    - Added `Disable telemetry` patch: blocks Clearcut logging, Firebase analytics, Play services compliance check-ins, and performance monitoring transmitters.
    - Added `Hide clutter & AI summaries` patch: disables Ask Maps in navigation and search, and suppresses AI place summaries and promotional prompts.
    - Added `AMOLED black theme` patch: provides pure AMOLED black background styling for dark mode interface elements.
  * Kick Support (`com.kick.mobile`):
    - Added comprehensive patch suite including telemetry suppression, background audio playback, live/VOD ad bypass, custom third-party emote integration, and AMOLED black theme.
  * Twitch Support (`tv.twitch.android.app`):
    - Added client and display ad blocking, 7TV/BTTV/FFZ third-party chat emotes, background audio playback, telemetry suppression, and AMOLED black theme.
  * Gboard Keyboard Auto-Collapse Fix:
    - Resolved keyboard auto-collapse when sending messages in WhatsApp and other messaging applications by decoupling dictation cancellation from input finish views.
  * X (Twitter) Ad Filtering Hardening:
    - Added JSON timeline entry and module item layer ad filtering for robust sponsored content suppression across 12.39+ builds.
  * Decommissioned MiXplorer from default bundles and catalog.
  * Upgraded minimum manager version requirement to `3.6.1`.

## 3.6.0

- `NexFusion` 3.6.0:
  * Universal Dynamic Patch Architecture:
    - Transitioned all supported application targets to universal dynamic bytecode matching, aligning the entire patch catalog with the version-agnostic architecture of Google Photos, Gmail, Google Discover, and LinkedIn.
    - Removed rigid target version restrictions across YouTube, YT Music, Crunchyroll, Discord, Facebook, Instagram, Gboard, Proton VPN, Reddit, X (Twitter), Telegram, and Telegram Web.
    - Patches now hook dynamically at runtime across app updates without triggering version gating or false incompatibility warnings.
    - Upgraded bundle compatibility series to `3.6` and minimum manager version to `3.6.0`.

## 3.5.11

- `NexFusion` 3.5.11:
  * X (Twitter) 12.38.1 Compatibility & Crash Resolution:
    - Resolved `ClassCastException` on application launch caused by obfuscated empty presenter shift in X 12.38+ (`com.x.urt.g0.a`).
    - Enforced runtime presenter interface assignability verification (`presenterInterface.isAssignableFrom(cls)`) and method return type validation in the timeline presenter funnel hook, guaranteeing hook stability.
    - Added dynamic fallback resolution across common timeline presenter suffixes for future version resilience.
    - Added promoted-item probe coverage for X 12.38+ obfuscated models (`k1`, `q1`, `e2`, `z1`, `a2`) and updated unit tests.
    - Registered downloadable video metadata flag descriptor (`com.x.models.t4.g`).
  * YouTube Ambient Mode Resilience:
    - Verified dynamic recursive superclass field resolution (`findFieldRecursive`) for `CinematicContainerForceAnchors` and silent background diagnostics, ensuring zero on-screen toast errors on modern builds (e.g. 21.43+).

## 3.5.10

- `NexFusion` 3.5.10:
  * Crunchyroll Support (`com.crunchyroll.crunchyroid`):
    - Added `Enable Picture-in-Picture` patch: unlocks native PiP video player capability, registers action manager, adds seamless PiP entry on user leave hint, and connects contextual Skip Intro, Skip Credits, Skip Preview, and Skip Recap controls directly into PiP playback actions.
    - Added `Remove from Continue Watching` patch: injects a "Remove" option into feed item overflow menus with dynamic label and constructor reflection across obfuscated builds, persists hidden playhead timestamps in preferences, and filters dismissed titles from the home feed.
    - Added verified compatibility targets for Crunchyroll 3.126.0 and 3.117.0.
  * YouTube Ambient Mode Resilience:
    - Implemented recursive superclass field resolution (`findFieldRecursive`) for `CinematicContainerForceAnchors`, ensuring obfuscated proto hierarchy fields resolve reliably on YouTube 21.43+.
    - Replaced hard failure logging with graceful silent fallback and background diagnostics, eliminating unhandled field lookup toasts.
  * Facebook 585 Compatibility:
    - Added target support for Facebook `585.0.0.47.54` with verified dynamic Redex and GraphQL query hooks.
  * Gboard 18.5.6 Compatibility:
    - Added target support for Gboard `18.5.6.993608717-release-arm64-v8a` supporting Google Sans Flex typography, proactive undo controls, and quick & magic emoji (Emojify).
  * LinkedIn Universal Compatibility:
    - Made LinkedIn target support universal across all releases (including `4.1.1267+`), aligning with the universal patch architecture of Google Photos, Gmail, and Google Discover.
  * X (Twitter) 12.38.1 Compatibility:
    - Added target support for X `12.38.1-prod.01` with verified media download and subscription check hooks.

## 3.5.9

- `NexFusion` 3.5.9:
  * Discord 351.11 - Stable:
    - Remapped the jank-stats privacy entries that moved from `JankStatsModule` to `JankScreenContext`, followed `FrameMetricsAggregator.enableTracking(Window)`, and updated `JankSessionRecorder` to `nameOpenSegment`; all 130 privacy descriptors and the ReactFontManager phone-font anchor now resolve on 351.11.
    - Added `351.11 - Stable` to the declared targets.
  * Instagram 453.0.0.57.79:
    - Verified the download menu, carousel/story anchors, media getters, Tigon request filter and kept class names against the installed build.
    - Added `453.0.0.57.79` to the declared targets.
  * Reddit 2026.43.1:
    - Verified the feed, ad, media, sharing and onboarding fingerprints against the installed build.
    - Added `2026.43.1` to the declared targets.
  * Tests:
    - Added a binary-backed Discord privacy-target test and refreshed the supported-version matrix.

## 3.5.8

- `NexFusion` 3.5.8:
  * YouTube Patch Resilience & Dynamic Version Handling:
    - Updated `AppVersion.parse` to sanitize leading prefixes and architecture/build variant tags (e.g., `v21.43.523`, `21.43.523 (arm64-v8a)`), preventing modern YouTube builds from falsely degrading to legacy fallback hooks.
    - Converted version comparison delegates to fail-safe default values, preventing early evaluation uninitialized property exceptions.
    - Updated `playerParameterBuilderClass` and `playerParameterBuilderFingerprint` to robustly select the primary overload on YouTube 21.42+ targets.
    - Version-Aware Anchor Caching: Stored version codes with cached anchor preferences in Ambient Mode, Skip Live Shorts, and Captions in PIP, automatically invalidating stale descriptors across YouTube updates and eliminating missing field toasts.
    - Toast Suppression & Safe Degradation: Replaced user-facing exception toasts with silent background diagnostic logs and automatic runtime cache re-resolution.
    - Streamlined `FormatStreamModelToStringFingerprint` with string token matching and guarded optional info card layout hooks.
  * Test Suite Integration: Added YouTube verification targets to automated test suites across all supported releases.

## 3.5.7

- `NexFusion` 3.5.7:
  * LinkedIn Media Downloader: Implemented full SDUI media extraction and lifecycle hooks (`ComponentTransformer.doTransform` and `SduiFragment`). Adds download buttons to full-screen photo posts, video posts, multi-photo carousels, profile pictures, and profile banners with single-tap direct download and multi-media chooser dialogs saving to `Pictures/LinkedIn` and `Movies/LinkedIn`.

## 3.5.6

- `NexFusion` 3.5.6:
  * YouTube Recommended Version: Fixed target metadata experimental flags so YouTube 21.43.523 is correctly prioritized as the primary recommended version across all patcher surfaces.
  * X (Twitter) 12.38.0-prod.01 Compatibility: Added target support for 12.38.0-prod.01 with enhanced constructor-level blur interception on `MediaVisibilityResults`.

## 3.5.5

- `NexFusion` 3.5.5:
  * YouTube Navigation Guard: Guarded navigation bar hook against abstract class methods on YouTube 21.43.523.
  * LinkedIn Feature Expansion & SDUI Filtering:
    - Added "Download media": Adds a 1-tap download button to full-screen photo and video viewer presenters, supporting high-resolution MP4 streams and full-resolution vector/bitmap renditions via system DownloadManager.
    - Added "Hide suggested posts": Removes suggested posts outside network from feed via protobuf exact string inspection (`Suggested`, `Disarankan`).
    - Added "Hide promoted jobs": Filters promoted job cards from Jobs tab and search results (`Promoted`, `Dipromosikan`).
    - Added "Hide Premium upsells": Filters premium promotional banners, AI upsell cards, and Me panel upsell slot content.
    - Added "Feed filters": Configurable suppression of celebration updates, job posts, reposts, videos, reaction counts, and the new posts pill.
    - Added "Material You theme": Dynamic wallpaper palette mapping for LinkedIn blue accents and surface containers on Android 12+.
    - Target compatibility updated to include LinkedIn 4.1.1266.
  * X (Twitter) Sensitive Media Compose Interstitial Bypass: Added reflection hook on `MediaVisibilityResults.getBlurImageInterstitial` to reveal sensitive media directly across Compose timelines without requiring manual tap-to-reveal.
  * Gboard Typography & Control Enhancements:
    - Added "Google Sans Flex typography": Enables Google Sans Flex keycap styling and GM3 expressive design flags.
    - Added "Undo action controls": Adds proactive 1-tap Undo buttons in suggestion strip post-correction and autocorrection.
    - Added "Quick & Magic Emoji (Emojify)": Unlocks fast-access emoji toolbar and Gemini-powered Magic Emoji suggestion button.
    - Enhanced AI writing tools and spell checker chips with replace button integration and stable entrance controls.
  * Telegram Play Store Build Support: Added official target support for Telegram and Telegram (Web) 13.1.1.
  * Target & Catalog Synchronization: Synchronized recommended targets and compatibility definitions across all supported apps.

## 3.5.4

- `NexFusion` 3.5.4:
  * Instagram `453.0.0.55.79` Compatibility: Verified and added official target support for Instagram `453.0.0.55.79`. Confirmed download sheet menus, story header binders, position fields, and DASH manifest finders.
  * Reddit 2026.43.0 Target Alignment: Added `2026.43.0` target declaration to NexFusion patch metadata, synchronizing bundle support with `MorpheTargets.REDDIT` while preserving `2026.42.0` compatibility.
  * X (Twitter) 12.37.2-prod.01 Catalog Sync: Validated timeline ad suppression and promoted content filtering for X `12.37.2-prod.01`.
  * Target Inventory Synchronization: Synchronized target application inventories and recommendation lists across manager and bundle declarations for all 17 supported applications.

## 3.5.3

- `NexFusion` 3.5.3:
  * Channel Search Fingerprint Fix: Fixed runtime error when applying the "Channel search" patch. Corrected navigation endpoint field discovery in `BrowseFragmentOnCreateViewFingerprint` to accurately target the fragment's `NavigationEndpoint` instance rather than subsequent view theme models.
  * Resilient Setter Resolution: Resolved `BrowseFragmentSetterMethod` matching failure by dynamically locating the endpoint assignment instruction and adding safe fallback handling.
  * Search Submit Matching: Disambiguated `SearchSubmitFingerprint` DexKit query with `@StrictQuery` and invocation filtering (`toByteArray`), avoiding query result overflow across YouTube versions.

## 3.5.2

- `NexFusion` 3.5.2:
  * Channel Search: Fully ported runtime channel search into NexFusion patch bundle. Adds dedicated search button beside YouTube's global search on channel pages with search hint reflection and channel-scoped result filtering. Compatible with YouTube 21.42.243 through 21.43.523+.
  * DeArrow Preference Categories & Titles: Fixed duplicate/repetitive option titles in YouTube > Settings > Morphe settings > DeArrow. All 5 thumbnail preferences (Home, Subscriptions, You, Player, Search) now have distinct titles and are cleanly grouped under categorized headers with proper About preference linking.
  * Submodule Sync: Updated `morphe-patches` with toolbar button injection for runtime channel search integration.

## 3.5.1

- `NexFusion` 3.5.1:
  * YouTube 21.43.523 Compatibility: Added target support for YouTube `21.43.523` (experimental) with backwards compatibility across all supported YouTube targets.
  * Reddit 2026.43.0 Compatibility: Added target support for Reddit `2026.43.0`.
  * Ambient Mode 21.43+: Handled removal of server feature flags in 21.43+ by cleanly returning empty renderer when ambient mode is disabled in settings.
  * Navigation Bar 21.43+: Added support for resource-styled navigation tabs across all tabs, adapted Create button enum mapping, and fixed bottom room padding for fixed bars and comment replies.
  * Miniplayer: Ported updated resume check and command matching for recent YouTube targets.
  * Search Comments: Ported comments search filter with search button in engagement panel header.
  * Download Post Images: Added support for downloading images and GIFs from channel and feed community posts.
  * Channel Search: Added support for searching channel community posts alongside video uploads.
  * Upstream Sync: Synchronized extensions and library with upstream `dev` branch.

## 3.5.0

- `NexFusion` 3.5.0:
  * X (Twitter) Compatibility: Added official support for X `12.37.2-prod.01`.
  * Dynamic Ad Suppression: Updated timeline presenter funnel matching with resilient reflection to prevent breakage across obfuscated navigation parameter classes.
  * Empty Presenter: Added support for `com.x.urt.b0.a` with fallback to `com.x.urt.f0.a`.
  * Promoted Item Probes: Added promoted post (`l1.e`), promoted RTB (`r1.j`), and item variant (`f2.h`) probes alongside legacy models.
  * Video Downloads: Added Premium check hooks for `com.x.subscriptions.r`, `isDownloadable` overrides for video models `v4.j`, `p4.g`, `s4.g`, and updated video tab download handler.
  * Update Blocker: Added in-app update prompt blocker for `com.x.android.appupdate.l.a`. 
  * Bundle Compatibility: Updated compatibility series and minManagerVersion to 3.5 to align with NexFusion 3.5.0 manager.
  * Gboard Screenshot Chips: Enabled `enable_screenshot_in_clipboard`, `enable_auto_paste_chips`, and `writing_tools_v2_enable_screenshot_context` with forced overrides so recent screenshots reliably show in the suggestion strip.
  * Gboard Suggestion Strip & Preferences: SharedPreferences hook now preserves existing user preferences instead of overwriting them on startup, preventing preference resets on force-stop. Proactive suggestion strip close button disabled by default to keep the suggestion row persistently accessible.

## 3.4.10

- `NexFusion` 3.4.10:
  * YouTube 21.42.243 Compatibility: Resolved startup crashes and restored stability across patches on YouTube 21.42.243.
  * Swipe Controls: Attached gesture overlay host to MainActivity lifecycle, restoring brightness, volume, and playback speed gestures.
  * Seekbar & Gestures: Restored tap-to-seek, slide-to-seek, and skip live Shorts on 21.42+.
  * Navigation Bar: Streamlined bottom room, navigation padding, and keyboard inset hooks to prevent layout crashes.
  * Ambient Mode: Disambiguated candidate matching, normalized class descriptors for reflection, and added resilient Protobuf Lite parser resolution with default instance fallback.
  * Captions: Guarded captions in picture-in-picture hooks and preferences on 21.42+ where caption rendering transitioned to Litho components.
  * Vertical Scroll: Restored scoped SwipeRefreshLayout vertical scroll checks.
  * VersionCheck Lifecycle: Enforced VersionCheck dependency across feature overrides and debugging patches to eliminate lifecycle initialization errors.

## 3.4.9

- `NexFusion` 3.4.9:
  * Telegram Hotfix: Scoped HushTelegram feature suite strictly to official Telegram clients (Google Play & Web, 13.1.0 and 12.10.6), removing third-party fork targets.

## 3.4.8

- `NexFusion` 3.4.8:
  * Telegram Support: Ported comprehensive feature suite from `SysAdminDoc/HushTelegram` supporting official Telegram (Google Play & Web, 13.1.0 and 12.10.6).
  * Features Included:
    - Hide Ads & Sponsored Content: Suppresses channel sponsored messages (`MessagesController.getSponsoredMessages`), video player ads (`VideoAds.load`), channel recommendations, and network ad requests.
    - Hide Stories: Suppresses story fetching and indicators (`StoriesController.loadAllStories`, `hasStories`).
    - Hide Sponsored Proxy Channel: Hides sponsored proxy promotion channels from the chat list and folders.
    - Keep Deleted Messages: Prevents deletion of received messages on device when sender deletes them (`MessagesStorage.markMessagesAsDeleted`).
    - Exact Numbers: Shows full unrounded member, subscriber, view, and reaction counts (e.g. 12,345 instead of 12.3K).
    - Faster Downloads: Unlocks 512 KB chunks and 8 concurrent parallel connections (`FileLoadOperation.updateParams`).
    - Disable Update Checks: Suppresses standalone update checks and in-app update prompts.
    - Disable Analytics & Diagnostics: Blocks telemetry, crash reporting, and VoIP call diagnostic uploads.
    - Open Links Externally: Opens web links directly in external default browser (`Browser.openUrl`).
    - Strip Link Tracking: Sanitizes links by stripping `utm_*`, `gclid`, `fbclid`, `igshid`, and `si` tracking tags.
    - Reveal Spoilers: Automatically reveals covered spoiler text, photos, and videos.
    - Hide Phone Number: Masks phone number with dots in Settings and user profile screens.
    - Disable Double-Tap Reactions: Stops accidental message double-tap quick reactions.
    - Hide Keyboard on Scroll: Closes on-screen keyboard when scrolling chats.
    - Hide Commerce & Upsells: Hides Premium, Stars, Gifts, and Wallet sales entry points.
    - Hide Greeting Stickers: Hides empty-chat greeting sticker suggestions.
    - Hide Translate Bar: Hides the floating translation banner in foreign-language chats.
    - Hide Blocked Users in Groups: Leaves messages from blocked users out of group chats.
    - Play Voice Messages One at a Time: Prevents consecutive voice/video message autoplay.
    - Keep Videos Muted on Volume Keys: Adjusts system volume without unmuting video playback.
    - Disable Archive Pull Down: Disables pull-down on chat list to reveal archive.
    - Disable Channel Pull Up: Disables pull-up at bottom of channel to jump to next channel.
    - Disable Chat Swipe Actions: Disables sideways swipe gestures on chat list rows.
    - Disable Draft Link Previews: Stops automatic link preview requests while drafting.
    - Use System Font: Renders text using phone system font instead of bundled Roboto files.
    - AMOLED Black Dark Theme: Replaces dark background surfaces with pure `#000000` for OLED screens.

## 3.4.7

- `NexFusion` 3.4.7:
  * Gboard 18.5.x Support: Added official target support for Gboard `18.5.4.993608717-release-arm64-v8a` and `18.5.3.993608717-release-arm64-v8a`.
  * Feature Port from PixelBoard (v1.3.0 by Akshay Kadam):
    - Extended Clipboard History Retention Slider: Configures Gboard's native `LabelSummarySliderPreference` in clipboard settings with discrete duration choices (1 hour, 24 hours, 7 days, 30 days, Never). Includes clamping protection against Material Slider `value > valueTo` crashes.
    - Gemini Rambler Dictation Prompt Fix: Restores verbatim spoken transcripts into the committed text span and resets agentic intent (intent 0) whenever Rambler misclassifies speech as an AI prompt instruction, ensuring speech-to-text always types what was spoken.
    - Signature Verification Bypass: Added `Signature verification bypass` patch targeting `sfr` (18.5.3/18.5.4), `ryi` (18.4.1), `rvl` (18.3.1), and `rpv` (18.0.3) to allow offline speech and AI models to load cleanly.
    - Rambler Selectors & Mic Activation: Updated capability selectors for Gboard 18.5.x (`nca`, `mwg`), hooked toolbar mic activation (`mjz.B`), and suppressed selection reversion (`fde.hT`).
    - AI Writing Tools All Languages: Unlocked all languages and locales for Gemini AI writing tools and Proofread (`*` language tags allowlist).
    - Speech Pack Download Loop Guard: Added Language Download Guard preventing infinite download request retry loops in speech services.
    - Samsung One UI & Vivo Clipboard Sync: Hooked foreground IME focus and `ClipboardDataHandler` (`frc.ft` / `fml.ft`) to guarantee primary clip capture.

## 3.4.6

- `NexFusion` 3.4.6:
  * Meta AI Removal: Completely removed Meta AI integration across Facebook and Instagram:
    - Facebook: Suppressed search Meta AI answer cards (`meta_ai_answer_entities`), Ask Meta AI question pills under feed posts (`GenAiDeepDivePillPlugin`), GenAI photo CTA chips (`GenAiDeepDiveCtaPlugin`), GenAI comment summaries (`InlineCommentSummaryWithGenAIPlugin`), and Meta AI feed unit edges.
    - Instagram: Intercepted and blocked low-level MobileConfig flags (`0x81067100111e9fL`, `0x81040c0006109dL`, `0x81040a001b1093L`, `0x81168700017258L`, `0x8110c200115a92L`), redirected all Meta AI / GenAI network endpoints (`/meta_ai`, `/genai`, `/aistudio`, `/muse`, `/hatch`), and added runtime view hiders for all Meta AI buttons, search pills, rings, badges, and composer items.
  * Facebook Hush Features: Ported `Don't save search history` (stops search history tracking and keeps visited profiles/pages out of search history via `FB4ASearchTypeaheadAddRecentSearch` and Falco privacy analytics suppression), `Don't send reel watch history` (suppresses `FbShortsSeenStateMutation` GraphQL batches), `Longer music on photo stories` (allows photo story music clips to play for up to 30 seconds instead of 15), and `Hide profile notes` (hides profile picture note bubbles) from Hushfacebook.
  * Instagram Hush Features: Ported `Don't save recent searches` (intercepts `/fbsearch/register_recent_search_click/` via Tigon), `Don't send reel watch history` (intercepts `/clips/write_seen_state/` via Tigon), and `Hide Follow on feed posts` (removes Follow and Subscribe buttons from feed post headers) from HushGram.
  * Compatibility Targets: Verified support for Facebook 584 (`com.facebook.katana-584.0.0.51.56`) and Instagram 452 (`com.instagram.android-452.1.0.53.64`).

## 3.4.5

- `NexFusion` 3.4.5:
  * YouTube Patch Fixes: Resolved failing patches on YouTube 21.42.243. Added `@StrictQuery` filtering to `ReelPlaybackRepeatFingerprint` in `SkipLiveShortsPatch` to isolate the exact reel repeat handler, and added resilient fallback for caption track event handling in `CaptionsInPipPatch` for 21.42+ where track event routing was refactored.
  * Classic YouTube 5-Tab Navigation: Fully restored the classic 5-tab bottom navigation (`Home`, `Shorts`, `+`, `Subscriptions`, `You`) via dynamic runtime injection, ensuring the Subscriptions tab is present and accessible. Removed redundant top category filter chips ("Podcasts", "Live", "Music", etc.) from the Home feed header.
  * Seekbar Tap-to-Seek & Slide-to-Seek: Added `Enable tap to seek`, `Enable slide to seek`, and `Disable precise seeking gesture` patches to restore classic tap-anywhere seekbar navigation and prevent unwanted fullscreen precise-seeking drawer expansion.
  * Compatibility Targets: Added support for Facebook 584 (`584.0.0.51.56`) with verified feed and reels ad-blocking finders.
  * Codebase Maintenance: Cleaned up temporary test probes and investigation files, ensuring clean repository trees and reliable release bundle generation.

## 3.4.4

- `NexFusion` 3.4.4:
  * Unified Patch Bundle: Merged YouTube, YouTube Music, and Google Photos directly into NexFusion, retiring the separate `bundles/nexfusion` bundle to consolidate all 15 supported applications into a single, unified patch bundle (`nexfusion-3.4.4.nxb`).
  * Classic YouTube Navigation: Fully restored the classic 5 bottom tabs layout (`Home`, `Shorts`, `+`, `Subscriptions`, `You`) and phone header chips ("All", "Podcasts", "Gaming", etc.) by purging Automotive Guide spoofing from `NavigationBarPatch`.
  * YouTube SponsorBlock: Restored `PlayerTypeFingerprint` and `ReelWatchPagerFingerprint` in `PlayerTypeHookPatch`, ensuring player types are tracked in real-time, `playerState().isPlayerTypeNoneOrHidden()` evaluates accurately, and automated segment skipping operates seamlessly.
  * YouTube Settings & About Screen: Updated branding to "NexFusion", updated repository link to `https://github.com/a092devs/NexFusion`, developer attribution to `@a092devs`, and Telegram to `https://t.me/a092devs`. Removed Discord, Donate, and Morphe API fetch buttons.
  * Verified App Targets: YouTube `21.42.243`, `21.41.525`, `21.40.161`, `21.39.522`; YouTube Music `9.33.00`; Instagram `452.1.0.53.64`; LinkedIn `4.1.1264`; Facebook `583.0.0.39.55`; Discord `350.8`; Proton VPN `5.20.98.0`; X `12.36.1-prod.01`; Reddit `2026.42.0`. All unit and compatibility tests pass.

## 3.4.3

- `NexFusion` 1.0.15:
  * YouTube Client Identity & Layout: Completely eliminated internal `isAutomotive` context overrides and InnerTube `osName: "Android Automotive"` endpoint spoofing from `HideAdsPatch` and `AdsFilter`. YouTube operates strictly as a standard Android phone client, fully restoring the native portrait video player, classic 5-tab bottom navigation (`Home`, `Shorts`, `+`, `Subscriptions`, `You`), and eliminating tablet/dashboard split mode.
  * YouTube Watch History: Fully restored user Watch History shelf (`library_recent_shelf` / `FEhistory`) on the "You" tab and continuous playback tracking by serving standard Android InnerTube browse and player payloads.
  * Preferences Defaults Migration: Added one-time migration (`nexfusion_defaults_migrated_v4`) in `Settings.java` resetting stale stored preferences on existing installs (`swap_create_with_notifications` -> false, `hide_horizontal_shelves` -> false, `hide_get_premium` -> true, `hide_youtube_premium_menu` -> true, `hide_shorts` -> false, `hide_subscriptions` -> false, `hide_create` -> false).
  * SponsorBlock Controls: Re-enabled standard controls overlay bindings (`size_adjustable_youtube_controls_overlay`), allowing SponsorBlock skip buttons, segment markers, and automated skip logic to seamlessly render on the phone player layout.
  * Settings Branding & Links: Updated Morphe About dialog branding to "NexFusion", updated links to developer GitHub repository (`https://github.com/a092devs/NexFusion`), developer profile (`https://github.com/a092devs`), and Telegram (`https://t.me/a092devs`), and removed legacy third-party donation links.
- `NexFusion` 3.4.3:
  * Developer Attribution: Cleaned legacy developer references across Spotify and media session modules, aligning attribution with `a092devs`.

## 3.4.2

- `NexFusion` 1.0.14:
  * YouTube Layout & Navigation: Restored classic 5 bottom tabs layout (Home, Shorts, Subscriptions, Create/Notifications, You) by defaulting `morphe_swap_create_with_notifications_button` to `false`, preventing unintended Android Automotive Guide client spoofing.
  * YouTube Premium Promos: Defaulted `morphe_hide_get_premium_button` and `morphe_hide_youtube_premium_menu` to `true`, cleanly hiding "Get Premium" promotions from the You tab page header (`SPunlimited`) and navigation menus.
  * YouTube Watch History: Restored Watch History shelf on the "You" tab by setting `morphe_hide_horizontal_shelves` default to `false` and preserving `FEhistory` shelf visibility.
  * YouTube 21.42+ Feature Flag Override: Added `experimentalIndexedBooleanFeatureFlagFingerprint` (`(I, J, Z) -> Z`) support to `FeatureOverride` and `EnableDebuggingPatch`, ensuring all runtime boolean feature flag overrides take effect on YouTube 21.42+.
  * Extension Class Loading: Switched to framework inflation entry-point hooks (`LayoutInflater.createView`, `DrawableInflater.inflateFromClass`, and `Fragment.instantiate`) for module UI classes, preserving the host ART ClassLoader hierarchy without parent delegation interference.
- `NexFusion` 3.4.2:
  * Instagram: Added target `452.1.0.53.64` to `Instagram` targets in `NexFusionPatches.kt`, resolving status from "Untested" to "Compatible".
  * LinkedIn: Added target `4.1.1264` to `LinkedIn` targets in `NexFusionPatches.kt`, resolving status from "Untested" to "Compatible".

## 3.4.1

- `NexFusion` 1.0.13:
  * YouTube SponsorBlock: Resolved critical playback time tracking failure on YouTube 21.42+. Fixed `PlayerControllerSetTimeReferenceFingerprint` opcode filter mismatch to accurately bind the `arcb.<init>` constructor hook, restoring real-time playback position ticks to `videoTimeHooks` and re-enabling automated SponsorBlock segment skipping.
- `NexFusion` 3.4.1:
  * Instagram: Verified compatibility with `com.instagram.android` 452.1.0.53.64 (reels, stories, media download hooks pass with zero errors).
  * LinkedIn: Verified target class and method references (`NavigateToUrlActionViewData`, `RichTextUtils.getLinkShimmingLink`, `MetricQueue.queueMetric`, `ConversationListItemTransformer`, `sendTypingIndicator`, `updateConversationReadStatus`, `ClickActions`, `ComponentTransformer`, `CollectionTemplate`) on `com.linkedin.android` 4.1.1264.
  * Comprehensive Suite Verification: Confirmed 100% test pass rate across YouTube, Spotify, Reddit, Facebook, Discord, Mixplorer, ProtonVPN, and Twitter target binaries.

## 3.4.0

- `NexFusion` 3.4.0:
  * Settings UI: Fixed `sortPreferenceGroups` NullPointerException when encountering null-key preference groups or null items, adding full null-safety with collation fallback and automated regression tests.
  * Patch Set Curation: Completely removed discontinued patches (`Kleinanzeigen`, `Untappd`, and `InterPals`) along with their exclusive resources, tests, and build definitions.
  * NexFusion Modernization: Relocated remaining `2eno` extension packages (googleads, shared, spotify) to `io.github.nexfusion.nexfusion.extension`, unified runtime logger tag to NexFusion, and preserved upstream attribution.
  * Reddit: Added `RedgifsAudioPatch` for unmuting embedded Redgifs audio streams on Reddit 2026.42.0+.
  * Upstream Feature Ports: Integrated Picture-in-Picture captions toggle, Skip Live Shorts filter, Ambient Mode override controls, and Video Delay adjustments.
  * Build & CI: Submodule pointers and public fork references updated to ensure fully reproducible, verified CI builds.
- Build: Removed the duplicated local `build-logic` copy and pinned the
  `nexfusion.bundle` plugin to the version published by NexFusion.
- CI: Kept validation in the Android workflow and restored a dedicated release
  workflow for reproducible release builds and publishing.
- Security: Added repository secret scanning and documented handling for
  client-side API keys and release signing identity.

## 3.3.10

- `NexFusion` 3.3.10:
  * Gboard: Removed the forced `show_suggestion_strip` override. The suggestion-strip toggle is a genuine user preference again and no longer resets on every Gboard start; the writing-tools patch only seeds the keys it actually needs. If the strip is currently forced on, turn it off once after updating — the choice now persists.
  * Reddit: Added support for Reddit 2026.42.0 and verified every Reddit fingerprint against it (release of October 16, 2026). Target list now includes 2026.42.0, 2026.41.0, 2026.38.0 and older builds.
  * Spotify: Ported Femify's "Show unplayable songs" patch. It forces `show_unavailable_tracks` on every freshly built settings state, so tracks that cannot be played (wrong market, removed, …) stay visible in collections instead of being hidden. Anchors verified against Spotify 9.1.90.2270 and 9.1.92.3058.
  * Spotify: The "songs skip after about 9 seconds" behavior is a server-side account restriction. Femify's updated source contains the same attribute overrides as NexFusion and no client-side fix, and ReVanced's removed "Spoof client" patch cannot be ported to runtime hooks. `Unlock Spotify Premium` stays opt-in, and its description now warns about the 9-second cutoff.

## 3.3.9

- `NexFusion` 1.0.12:
  * YouTube: Added `is_21.38_or_greater` and `is_21.40_or_greater` version flags for YouTube 21.38+ and 21.40+ compatibility.
  * YouTube: Added `readsResourcesWithParser` support to `ElementProtoParserHookPatch` for YouTube 21.40+ inline resource handling (template/JS module bytes).
  * YouTube: Made `timeMethod` hook optional in `VideoInformationPatch` so the patch still applies even when the fingerprint doesn't match on YouTube 21.42+, preventing cascade failures to SponsorBlock and other dependent patches.
  * YouTube: Added `name` parameters to all YouTube dependency patches (`PlayerResponseMethodHook`, `VideoId`, `VideoInformationPatch`, `PlayerTypeHook`, `LegacyPlayerControls`, `PlayerControlsOverlayVisibility`, `conversionContextPatch`, `textComponentPatch`) for clearer patch status reporting.
- `NexFusion` 1.0.11:
  * YouTube: Fixed `FixVerticalScrollPatch` fingerprint for YouTube 21.42+ by resolving direct `canScrollVertically` call on obfuscated `SwipeRefreshLayout` and adding resilient catch handler to prevent cascading `LithoFilter` hook initialization failure.
  * YouTube: Updated `experimentFlagUtilFingerprint` and flag accessor fingerprints for YouTube 21.42+ three-parameter method shape (`listOf("I", "J", ...)`).
  * YouTube: Added 21.42+ compatibility check in `VersionCheckPatch`.
  * YouTube: Constrained bottom navigation bar IME padding growth.
- `NexFusion` 3.3.9:
  * Instagram: Redesigned Downloader UI to match Facebook's seamless experience with direct 1-tap rows for Reel, Audio, Photo, and Album downloads.
  * Gboard: Fixed AI Writing Tools (`jarvis`) toolbar access point ordering and Phenotype flag integration so writing tools appear directly on the keyboard toolbar.
  * App Compatibility: Added support and verified hooks for Discord 350.8, LinkedIn 4.1.1263, Proton VPN 5.20.98.0, and X 12.36.1-prod.01.
  * Proton VPN: Added suppression for telemetry and background workers.

## 3.3.8

- `NexFusion` 3.3.8:
  * Facebook: Full media download implementation for Facebook 583.0.0.39.55:
    - Photo Viewer: Unlocked native "Save to phone" action with high-resolution JPEG extraction.
    - Reels: Integrated native one-tap "Download reel" and "Download audio" controls directly into the Reel controls sheet using authentic Facebook FDS download glyphs (`X.1Uf.A8L`), supporting progressive and DASH track extraction.
    - Stories: Unlocked native story save capability across all stories.
    - Post Menus: Added theme-aware media download actions to post overflow sheets.
    - Privacy: Sponsored feed story filtering via server value, sponsored reel suppression, and ad telemetry blocking.
  * Reddit: Consolidated Reddit into NexFusion. Added "Hide Reddit ads", "Sanitize Reddit sharing links" and "Start Reddit as guest" alongside existing Reddit patches, removing dual-bundle duplication.
  * Instagram: Added support for Instagram 452.0.0.48.64 and verified sheet-menu anchors. Added DASH manifest test suite.
  * Discord: Added support for Discord 349.9; all 135 privacy targets resolve against the installed APK.
- `NexFusion` 1.0.10:
  * Reddit: Removed from this bundle; all Reddit patches now live exclusively in NexFusion.

## 3.3.3

- `NexFusion` 3.3.3:
  * Instagram: Fixed the remaining Instagram 452 runtime failures. The sheet-menu row adder now accepts 451's five-parameter shape and 452's four-parameter shape, and the carousel position fingerprint follows 452's `List.get` decode. Download options install in the reels and feed sheets again, and carousel downloads use the item on screen.
  * Instagram: Fingerprint tests now assert the sheet-menu row adder and carousel position field for every APK in `binaries/`.

## 3.3.2

- `NexFusion` 3.3.2:
  * Instagram: Verified every patch anchor against Instagram 452.0.0.48.64. The story menu row and highlight menu builder now resolve their classes from the menus that build them, so 452's extra row parameters and wrapper builder no longer break downloads.
  * Instagram: Fingerprint coverage now runs against every APK in `binaries/` and also asserts the Tigon, story viewer and in-app browser anchors.
  * Gboard: Restored the Material 3 "Advanced settings" menu on Gboard 18.4.1. The hook follows the `PreferenceHeaderFragment` hierarchy instead of the removed obfuscated method names, injects the entry at the bottom of settings, and cleans up the "Rate us" / "Help & feedback" rows again.

## 3.3.1

- `NexFusion` 3.3.1:
  * Spotify: Added "Unlock Spotify Premium" patch, ported from `yobreehs/yfimeF`/Femify: account attribute overrides unlock on-demand playback, unforced shuffle, unlimited skips and the Premium settings UI, and restore the popular tracks on artist pages. Every hook installs independently, so a moved anchor on a new Spotify version only disables that block. Ships disabled because Spotify restricts accounts that use modified clients (issue #3); enable it in NexFusion explicitly.
  * Spotify: "Hide ad sections" now also removes the embedded brand-ad card from the now playing view feed.
  * Spotify: Added fingerprints for the product state, artist query parameters, Google Assistant context and now playing feed, validated against Spotify 9.1.90.2270 and 9.1.92.3058.
  * NOTICE: Added attribution for the ReVanced/Femify premium material.

## 3.3.0

- `NexFusion` 3.3.0:
  * Gmail: Added "Hide ads" patch to remove sponsored conversation rows between emails in Promotions and Social tabs (ported from `MrxSiN/GmailHideAds` and `gnadgnaoh/NexFusion-XES`).
  * Google App: Added "Filter Discover ads" patch to filter promoted ad cards from the Google Discover feed (ported from `hxreborn/discover-ads-filter` and `gnadgnaoh/NexFusion-XES`).
  * NOTICE: Added full attribution and license credits for ported material.

## 3.2.9

- `NexFusion` 3.2.9:
  * Gboard: Fixed visual issue in landscape mode by suppressing internal dogfood feedback buttons ("Help improve Rambler by reporting quality issues or bugs") and voice donation / dictation promo nag banners ("Help improve voice typing?").
  * Gboard: Added `FlagOverride.forceFalse()` support and enforced clean toolbar layout without promo banner clipping.

## 3.2.8

- `NexFusion` 3.2.8:
  * Spotify: Reverted "Unlock Spotify Premium" patch.

## 3.2.7

- `NexFusion` 3.2.7:
  * Spotify: Added "Unlock Spotify Premium" patch (reverted in 3.2.8).

## 3.2.6

- `NexFusion` & `NexFusion` 3.2.6:
  * YouTube: Ported latest fixes and updates from Morphe `dev` branch:
    - **Miniplayer**: Watch transition animation fix (prevents feed from jumping or rendering black when expanding/collapsing player) and "Play next/last in queue" from feeds fix.
    - **Video Quality**: Added initial video quality and SABR stream constraint overrides to prevent video quality from dropping during playback.
    - **Navigation Bar**: Added translucent navigation bar overrides to fix raised navigation bar and empty bottom gap.
    - **Status Bar**: Added `Hide StatusBar` patch with explicit *"Fix status bar covering the top bars"* layout setting.
    - **Video Codecs**: Modernized codec settings to unified `VideoCodecListPreference` (combining AVC / VP9) with Force HDR support.
    - **Visual/Theme**: Light theme readability fixes for comments button page indicator dots and metadata panel text contrast.
  * Submodules: Pinned `morphe-patches` and `morphe-patches-library` to latest remote heads.

## 3.2.5

- `NexFusion` 3.2.5:
  * Gboard: Ported all features from PixelBoard (by Akshay Kadam):
    - **In-Gboard "Advanced settings" Screen**: Full Material 3 dialog integrated directly into Gboard settings (`PreferenceHeaderFragment`) with independent toggles for AI Writing Tools, Support All Keyboards, Enable Advanced Voice Typing, and Material 3 Rounded Keys, plus quick restart button (`↻`) and Akshay Kadam attribution.
    - **Preference Bloat Cleanup**: Automatically stripped "Rate Gboard" and "Help & feedback" from settings screens.
    - **Gemini Rambler Natural Voice Typing**: Real-time speech cleanup (filters fillers/stutters like "um", "ah", "like"), thought completion (on-the-fly corrections), context auto-punctuation and capitalization, and seamless multilingual fluidity.
    - **Direct Boot & Battery Lifecycle Readiness**: Device-Protected Storage (DE) support ensures voice typing is immediately active upon boot without manual restarts (#4), and speech recognition bindings (`GoogleAsrService`) are cleanly released when the keyboard is hidden to eliminate idle battery drain (#3).
    - **Clipboard & UI Enhancements**: Samsung/Vivo clipboard sync (#17) and spellchecker inline chips UI.
  * Gboard: fixed crash in `ModuleManager` on Gboard 18.4.1 beta caused by invalid wildcard `*` language tags, restoring rounded keycaps (Belka UI) and AI Writing Tools.
  * Gboard: decoupled rounded keycaps and AI writing tools preference overrides so each feature can be toggled independently.
  * Gboard: added real-time Phenotype flag override reporting to logcat.

## 3.2.4

- `NexFusion` 3.2.4:
  * Gboard: added support for Gboard 18.4.1 Release (`odx`/`ody` obfuscated phenotype flag getters).
  * Gboard: added "Emogen (GenAI Emoji Maker)" patch to unlock generative AI emoji maker access point and endpoints.
  * Gboard: added "Sign-to-Text (Signboard ASL)" patch for camera-based American Sign Language translation.
  * Gboard: added "Manual Incognito mode toggle" patch to easily toggle incognito typing directly from toolbar access points.
  * Gboard: added "Smart auto-correction & fast learning" patch with primary-key revert on backspace, two-stage decoding, and optimized user history learning rates.
  * Gboard: added "Candidate strip emoji vibrancy" patch ensuring candidate strip emoji glyphs render at full opacity (alpha 255) instead of dull translucent styles.
  * Gboard: expanded "Quick insert" with dynamic multi-view, contacts, clipboard query results, and local image suggestion flags.
  * Gboard: expanded "Screenshot suggestion chips" with extended clipboard history and direct quick paste flags.
  * Gboard: expanded "AI Writing Tools" with staging server endpoints for writing helper and Imagen.

## 3.2.3

- `NexFusion` 3.2.3:
  * Instagram: added system File Manager folder picker (`ACTION_OPEN_DOCUMENT_TREE`) with fallback to direct path editing and reset to default options.
  * Instagram: ported "Hide Follow on stories" from HushGram (#237), keeping stories from accounts you don't follow clean.
  * Reddit: fixed crash when opening notifications and notifications inbox caused by unboxing and null children lists.
  * Reddit: ported Redgifs audio playback and HD resolution patches from Morphe, restoring high-quality sound for Redgifs media.
  * Compatibility: fully tested and validated against Instagram 451.0.0.53.80 and Reddit 2026.41.0.

## 3.2.2

- `NexFusion` 3.2.2:
  * Patch-only release within series `3.2`, manager unchanged at `3.2.0`.
  * CI fix: `InstalledAppAuditTest` no longer fails when `docs/installed-app-patch-audit.md` is absent from the published repo (the file is kept locally).
  * Manager fix: update-check refresh now cancels any in-progress job and debounces rapid taps, so the UI stays fluid.

## 3.2.0

- `NexFusion` 3.2.0:
  * Renamed the custom `a092devs` bundle to `NexFusion Patches` (`nexfusion.nxb`, `io.github.nexfusion.patches`).
  * Initiated coordinated versioning series `3.2` matching NexFusion manager `3.2.0`.
  * Added compatibility series (`3.2`) and minimum manager version (`3.2.0`) metadata for semantic compatibility validation in the manager app.
  * Preserved full support for all existing patch collections and ported features:
    - Instagram (`com.instagram.android` 451.0.0.53.80, 450.0.0.46.79, 447.0.0.55.81): ad & suggestion blocking, ad injector guard, in-app download dialog with custom folders, per-username subfolders, timestamp filenames, DASH audio extraction, ghost story/live viewing, story auto-advance hold, typing status suppression, double-tap like suppression.
    - Spotify (`com.spotify.music`): audio ad muting, ad view & upsell banner suppression, Premium tab hiding, launcher widgets fix, link sanitizer, Material You theme, custom rounded corners.
    - Reddit (`com.reddit.frontpage`): screenshot banner disable, direct links, home-feed auto-refresh disable, transparent navigation bar, upvote score hiding, post view count hiding.
    - Discord (`com.discord` 348.10): crash reporting, telemetry and touch logging, install attribution, advertising identifiers blocking, and system typeface font forcing.
    - X / Twitter (`com.twitter.android` 12.35.0-prod.01): promoted post removal, video downloads unlock, update screen block, telemetry blackholing, premium upsell removal.
    - Gboard, LinkedIn, MiXplorer, and Proton VPN ports maintained with full functionality.

## 2.13.0


- `a092devs` 1.8.2:
  * Instagram (`com.instagram.android` 451.0.0.53.80): added custom download folder configuration,
    allowing users to set any custom base folder path (or reset to `NexFusion/Instagram`).
  * Instagram: redesigned the Download Settings dialog into a smooth, in-place update interface,
    eliminating the flickering dismiss-and-reopen behavior on option toggles.
  * Instagram: added new patches ported from HushGram:
    - `Stop story auto-advance`: keeps stories on screen without advancing automatically until tapped or swiped.
    - `Hide typing status`: suppresses typing indicator packets so other users do not see when you are typing in chats.
    - `Turn off double tap to like`: stops double tapping posts, reels, and comments from liking them, preventing accidental likes while scrolling.
  * Instagram: expanded suggested content filter rules to intercept additional recommendation endpoints.

## 2.12.0

- `a092devs` 1.8.1:
  * Instagram (`com.instagram.android` 451.0.0.53.80): added "Download audio" option to the download
    dialog for reels and videos, saving the audio track as an `.m4a` file directly into
    `Download/NexFusion/Instagram` using DASH manifest stream extraction with native media muxer fallback.
  * Instagram: default download folder path under Downloads is now `Download/NexFusion/Instagram`
    on device storage (with optional poster subfolder when per-username folders are enabled).

## 2.11.0

- `a092devs` 1.8.0:
  * Instagram (`com.instagram.android` 451.0.0.53.80): `Hide ads` and `Hide reels ads` now stop the
    ads that kept showing between reels and in stories. Instagram builds some sponsored items itself
    and inserts them into the feed, where the request filter can never see them; both patches now
    guard that insert (the "ad injector" Piko answers false: the method holding both
    "cross_surface_duplicate_ad" and "Is ad pod", `LX/05gS.A02` on 451) and it reports that nothing
    went in, so no gap is left behind.
  * Instagram: `Hide reels ads` previously blocked nothing, as `/feed/injected_reels_media/` was
    matched by the ADS rule which the patch never enables. The endpoint now matches the REELS_ADS
    rule, `Hide ads` enables both rules, and the filter test pins the mapping.
  * Instagram: the download options have settings now, reachable from the new "Download settings" row
    of the download dialog: the save location (Download, or Pictures and Movies for the gallery), a
    folder per username, and timestamp names (`yyyyMMdd_HHmmss`). They live in the app's own
    preferences and are read once at the start of a download, so nothing polls and nothing reaches
    back to the manager. The poster's username comes from the media's `user` and the user's
    `username` getters pinned by the hash of each key (`Media.A3R` and `User.A8D` on 451); a build
    that renames them falls back to the plain folder and downloads keep working.
  * Spotify (`com.spotify.music`): new app entry carrying the Spotify patches merged from the `2eno`
    bundle — ad muting and hiding (audio, sections, views, player video, playlist, popups and
    context-menu upsells), Premium tab hiding, third-party launcher widgets fix, sharing link
    sanitizer, Dynamic Material You theme and custom rounded corners.
- The `2eno` bundle is merged into `a092devs` and is not published anymore; its extension code is
  vendored in-tree (the `2eno-patches` submodule and its Dependabot updates are gone). Attribution
  is retained in the `a092devs` NOTICE.

## 2.10.0

- The `piko` bundle is removed: it carried only the X hooks for `12.19.1-release.0`, which no longer
  match the maintained X builds, and its Instagram side had already moved to `a092devs` in 2.6.0.
  X coverage is superseded by the kveld9 port in `a092devs` 1.7.0 (`12.35.0-prod.01`). The Piko
  NOTICE is retained verbatim as
  `bundles/a092devs/src/main/java/io/github/nexfusion/a092devs/NOTICE-piko` (the `a092devs` NOTICE
  now points there), so the upstream attribution requirement survives the removal.
- Docs updated for the removal: the README bundle list, the DEVELOPMENT module tree and the
  installed-app audit now credit `a092devs` for Instagram and X instead of the removed bundle.

## 2.9.0

- `a092devs` 1.7.0:
  * X (`com.twitter.android` 12.35.0-prod.01): new app entry with nine runtime ports of
    `kveld9/kveld-extra-morphe-patches` — `Remove Ads`, `Unlock Video Downloads`,
    `Block Update Screen`, `Block Telemetry & Trackers`, `Remove Premium Upsell`,
    `Control Video Auto Scroll`, `DM Debloat`, `Pause Search Suggestions` and
    `Show Sensitive Media`. Per-patch defaults mirror upstream (first five enabled, last four off).
    The upstream manifest and resource edits (permission strips, component disables, Sentry metadata
    removal) have no runtime equivalent and are not ported.
  * NOTICE: adds the `kveld9/kveld-extra-morphe-patches` (GPL-3.0) section covering the X port.

## 2.8.1

- `a092devs` 1.6.1:
  * JioHotstar (`in.startv.hotstar`): the 2.8.0 runtime port has been withdrawn — the patches did not take effect when applied on the device, so the whole port is removed from the bundle: the patch and fingerprint sources, the tests, the app entry and the `durgesh0505/chiggi_morphe_patches` NOTICE section. The 2.8.0 release notes keep the record of what was attempted.

## 2.8.0

- `a092devs` 1.6.0:
  * Facebook (`com.facebook.katana`): the feed funnel now survives the 583 wrapper/body split. The finder pins the two kept parameter types instead of the renamed third one, and follows the wrapper to the static body it delegates to, so "Hide sponsored posts" no longer fails at every app start with 583 installed — which was the source of the recurring "Error while applying the following patches" toast. The hook reads the edge from whichever argument holds it.
  * JioHotstar (`in.startv.hotstar`): new runtime ports of `durgesh0505/chiggi_morphe_patches` for the phone build (26.09.28.2, keeping the upstream TV build 26.06.22.3 as a target) — premium UI unlock (subscribed state, download buttons, playback nudge suppression), video ad removal (SSAI tag cleared in the AdMetadata constructor so the player requests a clean stream, plus the home-feed banner render stopped), screenshot restriction removal, all-codecs/4K checks and the Dolby Vision/HDR capability check forced on, and the device-attestation blob forced to report a clean device (anchored by its 34-String constructor, which survives the class renames; the blob's own flags were verified against the phone build's parameter names). Every finder carries upstream's access flags and was verified against all thirteen dex entries of the phone build. Not ported, with reasons in the bundle sources: Firebase analytics flags (read before runtime hooks exist), the proxy/VPN verdict bypass (renamed lambda anchors), the device-id spoof and the security-state self-report (renamed anchors), and manifest/resource edits.
  * NOTICE: adds the `durgesh0505/chiggi_morphe_patches` entry (GPL-3.0, adapting Paresh-Maheshwari's patches).

## 2.7.0

- `a092devs` 1.5.0:
  * Facebook (`com.facebook.katana`): the feed funnel, the Reels and Watch ad-pool vends, the ad fetches and the telemetry senders are now resolved at runtime with DexKit instead of hooking names that do not exist in release builds. Sponsored reels also cancel the ad-break queries with a failed future, block the anchorable client-side splices, and stop the deferred-card ad-break poller.
  * Instagram (`com.instagram.android`): the patches now ride on the Tigon request filter and real framework anchors; the direct-message stubs, the log-only patches and the old download stub are gone. The open-links-externally patch anchors on Instagram's own in-app browser, keeps Instagram-family pages and ad links in the app, follows redirect shims and strips only the click id. The download options of the post, sheet, story, highlight and profile-picture menus are restored from this repository's Piko bundle and registered.
  * NOTICE: adds the piko entry (GPL-3.0, NOTICE retained) and corrects the Hushfacebook and HushGram entries.
- `2eno`: repaired the Spotify hide-ad-views patch against 9.1.90.2270, which previously hooked names absent from the release build and silently did nothing.

## 2.6.0

- `a092devs` 1.4.0:
  * Integrated comprehensive Instagram (`com.instagram.android`) patch suite ported from `SysAdminDoc/HushGram`, targeting `451.0.0.53.80`, `450.0.0.46.79`, and `447.0.0.55.81`:
    - Hide ads: suppresses sponsored posts, feed ad units (`INTENT_AWARE_AD_PIVOT`, `STAND_ALONE_MULTI_AD_PIVOT`), injected reels, and profile ads.
    - Hide suggested posts & reels: filters explore chaining, suggestions in feed, and recommended accounts.
    - Ghost mode: anonymous story viewing (blocks `media/seen/` receipts), anonymous live watching (blocks heartbeat presence pings), anonymous DMs (blocks seen/read receipts), and disables typing indicators.
    - Privacy: removes `FLAG_SECURE` for screenshots and screen recordings in disappearing chats, blocks screenshot detection callbacks and observers, sanitizes tracking tags (`igsh`, `utm_*`, `fbclid`) from share URLs, bypasses in-app browser to open links externally, and disables analytics/telemetry.
    - Download any media without watermarks, ask before like, ask before refresh, and unlocked developer options.
  * Expanded Facebook (`com.facebook.katana`) patches ported from `SysAdminDoc/Hushfacebook` targeting `582.0.0.50.54` and `581.0.0.45.54`:
    - Hide sponsored posts, feed ads, comment sheet ad pills, sponsored reels, and marketplace ads.
    - Block ad telemetry, background ad prefetch jobs, and advertising ID tracking sinks.
    - Bypass in-app browser to open links in default system browser, and download media.
- `piko`:
  * Migrated Instagram patches into `a092devs.nxb` to focus solely on X (Twitter).
- Repository maintenance:
  * Removed untracked/redundant `reports/` directory from commit tree and updated `.gitignore`.

## 2.5.0

- `a092devs` 1.4.0:
  * Expanded Proton VPN (`ch.protonvpn.android`) runtime patches ported from `hxreborn/morphe-patches` and `hoo-dles/morphe-patches`:
    - Remove server change delay: eliminates cooldown wait periods between server switching on free plans (`AppConfigResponse`).
    - Unlock advanced settings & NetShield: unlocks NetShield ad/tracker blocking, split tunneling (app & IP routing), custom DNS, and allow LAN connections.
    - Suppress upgrade promotions: eliminates onboarding upgrade dialogs, promotional alerts, and account upgrade banners.
    - Updated verified compatibility targets to include `5.20.74.0`, `5.20.57.0`, and `5.20.39.0` alongside `5.19.x`.

## 2.4.0

- `nexfusion` 1.0.7: ported four runtime player and layout patches from Morphe/yollAmeF:
  * Ambient Mode: options to bypass power-saving restrictions and disable Ambient mode dimming in fullscreen.
  * Miniplayer: modern miniplayer flag overrides, horizontal drag support, and offscreen repositioning hooks.
  * Picture-in-picture seek buttons: rewind and forward (±5s) controls injected directly into Android Picture-in-Picture window actions.
  * Disable playlist autoplay: suppresses automatic queue advancement to the next playlist video.
- `a092devs` 1.3.0:
  * Proton VPN (`ch.protonvpn.android`): runtime patches ported from Paresh-Patches to disable telemetry/observability workers, suppress upgrade onboarding dialogs, and unlock NetShield UI feature availability.
  * Gboard (`com.google.android.inputmethod.latin`): expanded with dynamic Phenotype flag class detection and SharedPreferences hook for Belka rounded keycaps, Gemini AI writing tools, Scan Text OCR, floating screenshot chips, Emoji Palette V2, Smart Compose predictions, and spellchecker suggestion chips from PixelBoard.
- `2eno`: Spotify runtime updates for APS banner handling, upsell suppression, and fingerprint stability.

## 2.3.0

- `nexfusion`: version-gated fingerprint updates for YouTube 21.41 (background playback settings,
  player type target and the shorts-resume constructor split).
- `a092devs`: Discord "Use your phone's font" option (ReactFontManager system-typeface hook ported
  from Venus Patches; off by default) and seven Reddit runtime ports from Adobo — screenshot-banner
  disable, external links without `out.reddit.com`, home-feed auto-refresh disable, home-screen
  redirect disable, transparent navigation bar, upvote-score hiding and post view-count hiding.
  The Reddit matchers were verified against 2026.41.0 fingerprints; score/view-count/nav-bar and
  auto-refresh ports are opt-in, and the score/view-count ports stub getters only (upstream also
  rewrites toString fields, which runtime hooks cannot).
- `a092devs` 1.2.0: three new app tables ported as runtime hooks. Gboard 18.4.1 gets 13 flag
  patches (cursor trackpad, quick insert, emoji size, access points menu redesign, Bluetooth
  microphone, close proactive suggestions, access point count, clipboard character limit, flow
  mode, advanced voice typing, grammar checker, inline suggestions, key shapes) from
  jasonwu1994/Gboard-patches; all of them share one flag-getter hook, and the access point count
  and advanced voice patches are off by default. Facebook 582 gets allow-screenshots and
  block-screenshot-detection from SysAdminDoc/Hushfacebook. LinkedIn gets hide sponsored
  messages, ghost mode typing indicator and read receipts, disable double-tap like and hide feed
  ads from heyymichii/michii-patches. The parts that need patch-time resources are not ported
  (Gboard OCR plumbing and AI writing tools, LinkedIn DownloadMedia, the Facebook feed patches);
  each bundle's NOTICE lists the ported material per source.
- Repository tooling: release notes now come from this changelog, and a push to `main` dispatches
  the release for the first version section automatically.

## 2.1.0 and earlier

See the GitHub Releases page for earlier release notes.
