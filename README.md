<div align="center">
  <img src="assets/icon.png" width="128" height="128" alt="Nexfusion-Patches Logo" />
  <h1>Nexfusion-Patches</h1>
  <p><strong>Modular runtime bytecode patch bundles (.nxb) for NexFusion and LSPosed.</strong></p>
  <p>
    <a href="https://github.com/a092devs/Nexfusion-Patches/releases"><img src="https://img.shields.io/github/v/release/a092devs/Nexfusion-Patches?style=flat-square&color=0284C7&label=Release" alt="Latest Release" /></a>
    <img src="https://img.shields.io/badge/Format-.nxb%20(Signed%20APK)-6366F1?style=flat-square" alt="Format: .nxb" />
    <img src="https://img.shields.io/badge/LSPosed-API%20101-blue?style=flat-square" alt="LSPosed API 101" />
    <img src="https://img.shields.io/badge/Targets-22%20Applications-10B981?style=flat-square" alt="Targets: 22 Applications" />
    <img src="https://img.shields.io/badge/Icon-Hardware%20Silicon%20Matrix-0284C7?style=flat-square" alt="Hardware Silicon Matrix" />
    <a href="LICENSE"><img src="https://img.shields.io/badge/License-GPL--3.0-orange?style=flat-square" alt="GPL-3.0" /></a>
  </p>
  <p>
    <a href="#-getting-started">🚀 Getting Started</a> •
    <a href="#-core-architecture--design">⚡ Architecture</a> •
    <a href="#-bundle-structure--contract">📦 Bundle Contract</a> •
    <a href="#-curated-patch-bundles--supported-applications">🧩 Supported Apps</a> •
    <a href="#-building-from-source">🛠️ Building</a>
  </p>
</div>

---

## 📖 Overview

**Nexfusion-Patches** provides modular runtime bytecode patches packaged into `.nxb` bundles for the [NexFusion](https://github.com/a092devs/NexFusion) root + LSPosed manager ecosystem.

Unlike traditional patching tools that decompile, modify, re-sign, and reinstall target APKs, **NexFusion patches execute entirely in-memory at process startup**:

- 🔒 **Zero APK Tampering:** Target applications remain official, unaltered on disk, and preserve their authentic Play Store signatures and Play Protect integrity.
- ⚡ **Dynamic DexKit Interception:** Obfuscated classes, methods, and fields are resolved at process startup via bytecode pattern analysis and string fingerprinting.
- 🛡️ **Fail-Safe Isolation:** Hooks execute with comprehensive `runCatching` safeguards, ensuring target apps remain stable and functional even if non-critical symbols change across updates.
- 📦 **Containerized `.nxb` Bundles:** Patches are packaged into signed `.nxb` APK containers loaded directly into process memory without requiring APK installation.

> [!IMPORTANT]
> - **Runtime-Only Architecture:** Patches run purely in-memory via LSPosed (API 101). Target APK files are never modified, re-signed, or repackaged.
> - **Trusted Sources:** Bytecode executes within target application processes. Always install patch bundles from trusted developers and verified repositories.
> - **Integrity Guarantee:** Banking, payment, and security-critical applications are deliberately excluded from patch targets.

---

## 🚀 Getting Started

1. **📥 Install NexFusion:** Download and install the manager from [NexFusion Releases](https://github.com/a092devs/NexFusion/releases).
2. **🛡️ Configure LSPosed:** Enable the **NexFusion** module in LSPosed and add your desired target applications to its scope.
3. **📦 Install Patch Bundles:** Open NexFusion → **Patches** tab. The official companion repository (`a092devs/Nexfusion-Patches`) is pre-configured; tap **Install** on `NexFusion Patches`.
4. **⚙️ Toggle Desired Patches:** Open the **Home** tab, tap any target application card, and enable your preferred runtime hooks.
5. **🔄 Apply & Restart:** Tap **Force Stop** or use the 1-tap **App Info** shortcut to restart the target application and let LSPosed attach the updated bytecode hooks.

---

## ⚡ Core Architecture & Design

<details>
<summary><b>1. 🔍 Feature Detection Over Hardcoded Offsets</b></summary>
<br>

Patches locate target bytecode dynamically using DexKit pattern analysis, opcodes, and string references rather than fragile hardcoded class names or method offsets. This ensures robust resilience across minor and major application updates.
</details>

<details>
<summary><b>2. 🛡️ Fault-Tolerant Hook Lifecycle</b></summary>
<br>

Every hook registration is isolated within safe runtime execution boundaries. If an obfuscated symbol cannot be resolved due to an unexpected upstream UI rewrite, only the specific affected feature degrades gracefully — preventing application crashes.
</details>

<details>
<summary><b>3. 🎯 Truthful Application Targets</b></summary>
<br>

Bundle entries explicitly declare verified application version ranges (`AppPatchInfo.targets`). NexFusion uses these declarations to drive honest compatibility badges and update indicators in the manager UI.
</details>

---

## 📦 Bundle Structure & Contract

<details>
<summary><b>View Bundle Specification & Manifest Contract</b></summary>
<br>

A `.nxb` file is a signed APK container loaded directly into process memory by NexFusion. The manager inspects the following `meta-data` entries from the bundle's `AndroidManifest.xml`:

| `meta-data` Key | Value Type | Description |
|---|---|---|
| `nexfusion.bundle.entry` | FQCN Class | Class implementing `io.github.nexfusion.PatchBundle` (entry point) |
| `nexfusion.bundle.apiVersion` | Integer | Patcher API version the bundle was compiled against (e.g. `1`) |
| `nexfusion.bundle.author` | String | Bundle author displayed in the NexFusion manager |
| `nexfusion.bundle.description` | `@string` | String resource describing the bundle's capabilities |
| `nexfusion.bundle.compatibilitySeries` | String | Compatible manager compatibility series (e.g. `3.6`) |
| `nexfusion.bundle.minManagerVersion` | String | Minimum required manager version (e.g. `3.6.0`) |
| `nexfusion.bundle.maxManagerVersion` | String | Maximum supported manager version (or empty for forward compatibility) |

### Manifest Contract & Resource Isolation
- **Entry Point:** The entry class implements `io.github.nexfusion.PatchBundle`, returning the list of supported applications (`AppPatchInfo`) and their verified versions.
- **Resource Partitioning:** Custom UI resources are assigned dedicated package IDs (`0x02`–`0x7e`, never the application ID `0x7f`) to prevent resource collision when multiple bundles hook into the same process.

</details>

---

## 🧩 Curated Patch Bundles & Supported Applications

### Curated Distributions

- **`nexfusion`:** Streaming core patches for YouTube, YouTube Music, and Google Photos cloud storage spoofing.
- **`nexfusion` (`NexFusion Patches`):** Unified community runtime distribution covering all **22 supported application targets** in a single `.nxb` package.

---

### Supported Applications Matrix

<details>
<summary><b>🎥 Video, Audio & Streaming (7 Targets)</b> — <i>YouTube, YouTube Music, Kick, Twitch, Crunchyroll, MX Player, Stremio</i></summary>
<br>

| Application | Package Identifier | Key Patch Capabilities |
|---|---|---|
| **YouTube** | `com.google.android.youtube` | Remove video and feed ads, SponsorBlock integration, background playback, link sanitization, custom playback speeds & quality selector, swipe volume/brightness controls, layout component filtering, DeArrow title/thumbnail replacement, Litho node bypass, Shorts resuming, Ambient mode, Miniplayer, PiP seek buttons, disable playlist autoplay, channel search fingerprint fix, guarded navigation bar hook against abstract methods. |
| **YouTube Music** | `com.google.android.apps.youtube.music` | Background audio playback, audio ad removal, exclusive audio stream playback, hide upgrade buttons, suppress promotional banners. |
| **Kick** | `com.kick.mobile` | Live stream & VOD ad blocking, background audio playback, custom chat emotes (7TV, BTTV, FFZ), telemetry suppression, AMOLED black theme. |
| **Twitch** | `tv.twitch.android.app` | Stream and display ad blocking, 7TV / BTTV / FFZ third-party chat emotes, background audio playback, telemetry suppression, AMOLED black theme. |
| **Crunchyroll** | `com.crunchyroll.crunchyroid` | Native Picture-in-Picture mode with contextual Skip Intro and Skip Credits controls; remove titles from Continue Watching feed. Verified targets: 3.126.0, 3.117.0. |
| **MX Player** | `com.mxtech.videoplayer.ad` | Remove startup splash ads, Monetize-v2 ad engine, Google AdMob, InMobi, and banner ad containers; instant splash (bypass UMP consent dialogs and telemetry); clean offline local mode (collapse online streaming bottom navigation bar); clean layout (hide WhatsApp status tile, cloud recycle bin upsells, and promo banners); sidebar clutter cleanup; bypass Smart Enhance intro dialog; suppress 2x SpeedUp HUD overlay. |
| **Stremio** | `com.stremio.one` | Auto & manual intro/credits skipping using embedded chapters and stream metadata; auto-pick best stream (resolves 4K/cached stream on tap); hide episode spoilers (synopses & thumbnails); avoid SDH subtitles; audio & subtitle track language memory; smoother playback buffer expansion (120s); seamless binge watching; external player progress & subtitle hand-off (MX Player, VLC, Vimu). |

</details>

<details>
<summary><b>💬 Social Networks & Messaging (8 Targets)</b> — <i>Telegram, Telegram (Web), Instagram, Facebook, X (Twitter), Reddit, Discord, LinkedIn</i></summary>
<br>

| Application | Package Identifier | Key Patch Capabilities |
|---|---|---|
| **Telegram** | `org.telegram.messenger` | Unlock local Premium features (status badges, emoji reactions, up to 6 multi-accounts); bypass channel restrictions & copy/forward protection; ghost typing mode (hide typing & recording status); anti-disappearing media (prevent timer destruction); anti-screenshot secret chat alerts; remove channel sponsored messages, search sponsored accounts, and video ads; hide Story bar, avatar story rings, and Post Story button; hide proxy sponsored channels; keep deleted messages locally; exact unrounded member/subscriber/view counts; faster file downloads (512 KB chunks with 8 concurrent connections); block in-app APK update prompts; disable analytics, crash logs, and call diagnostics; open links in default system browser; strip link tracking parameters (`utm_*`, etc.); auto-reveal spoiler text/media; mask phone number; disable double-tap reaction trigger; auto-close keyboard on scroll; hide Premium, gifts, and Stars upsell prompts; hide greeting stickers; hide floating translate bar; filter blocked users in groups; play voice/video notes one at a time; volume keys only change volume without unmuting video; disable pull-to-archive; disable pull-up to next channel; disable chat swipe actions; disable draft link previews; render system fonts instead of bundled Roboto; pure black AMOLED (#000000) dark theme. Verified targets: 13.1.1 (Play Store), 13.1.0, 12.10.6. |
| **Telegram (Web)** | `org.telegram.messenger.web` | Identical full-suite runtime patches as official Telegram for web-distributed standalone builds. Verified targets: 13.1.1, 13.1.0, 12.10.6. |
| **Instagram** | `com.instagram.android` | Media downloader (posts, multi-photo carousels, Reels, Stories, Highlights, profile pictures) with custom save directory, per-username subfolders, and timestamp naming; sponsored post, Reels, and Story ad removal; anonymous Story and Live stream viewing; tracking link sanitizer; ghost mode; optimized non-blocking layout listener for fluid typing and instant keyboard pop-up. Verified targets: 454.0.0.54.77, 453.0.0.57.79, 453.0.0.55.79, 452.1.0.53.64, 452.0.0.48.64, 451.0.0.53.80, 450.0.0.46.79, 447.0.0.55.81. |
| **Facebook** | `com.facebook.katana` | Media downloader for photos, videos, Reels, and Stories with DASH stream and audio extraction; hide sponsored posts, Reels, Stories, and floating comment CTA pills; screenshot restriction bypass; telemetry blocking. Verified targets: 586.0.0.52.53, 585.0.0.47.54, 584.0.0.51.56, 583.0.0.39.55, 582.0.0.50.54, 581.0.0.45.54. |
| **X (Twitter)** | `com.twitter.android` | Promoted post and ad removal; video and GIF download unlock; sanitize share links (strip tracking query parameters); disable timeline auto-refresh; bypass Compose sensitive media blur interstitials without tap-to-reveal; outdated-app warning and forced in-app update block; telemetry and analytics blackholing; Premium upsell removal; tracking link sanitizer. Verified targets: 12.38.1-prod.01, 12.38.0-prod.01, 12.37.2-prod.01, 12.36.1-prod.01, 12.35.0-prod.01. |
| **Reddit** | `com.reddit.frontpage` | Unlock local Premium UI perks (custom app icons, avatar styling, premium badges); block AutoMod comments at top of threads; feed and comment ad removal; start as guest mode; sanitize sharing URLs; suppress screenshot banner; transparent navigation bar; colorize comment indent lines; custom feed sorting; Redgifs audio unmuting. Verified targets: 2026.43.1, 2026.43.0, 2026.42.0, 2026.41.0, 2026.38.0, 2026.37.0, 2026.36.0, 2026.24.0, 2026.14.0. |
| **Discord** | `com.discord` | Background audio playback (voice messages & media continue playing when switching apps); passkey & security key clarification hint; suppress crash telemetry, analytics, touch logging, install attribution, and advertising identifiers; native phone font support. Verified targets: 351.11, 350.8, 349.9, 348.10. |
| **LinkedIn** | `com.linkedin.android` | Anonymous profile viewing (browse profiles without alerting users in 'Who viewed your profile'); 1-tap media downloader for photos, multi-image carousels, and progressive MP4 videos with download overlay button; sanitize copy and share links; bypass `safety/go` interstitial redirects directly; hide suggested posts (`Suggested`, `Disarankan`); hide promoted jobs (`Promoted`, `Dipromosikan`); hide Premium upsell banners in navigation panel and SDUI feeds; feed filters (celebrations, job cards, reposts, videos, reaction counts, new posts pill); Material You dynamic wallpaper palette (Android 12+); suppress tracking telemetry. Universal compatibility across all LinkedIn versions (e.g. 4.1.1267, 4.1.1266, 4.1.1264, 4.1.1255+). |

</details>

<details>
<summary><b>🛠️ System, Productivity & Utilities (6 Targets)</b> — <i>Google Photos, Google Maps, Gboard, Gmail, Google App (Discover), Proton VPN</i></summary>
<br>

| Application | Package Identifier | Key Patch Capabilities |
|---|---|---|
| **Google Photos** | `com.google.android.apps.photos` | Device spoofing for unlimited original-quality Google Photos cloud storage backup, DCIM folder backup control. |
| **Google Maps** | `com.google.android.apps.maps` | Sponsored pin and search ad suppression, high refresh rate unlock (90/120 Hz), telemetry blocking, hide AI clutter, AMOLED black theme. |
| **Gboard** | `com.google.android.inputmethod.latin` | Rounded keycap styling and expressive UI borders; Google Sans Flex typography & GM3 Expressive styling; proactive post-correction undo controls; Quick & Magic Emoji (Emojify); expanded AI writing tools (proofread across apps, streaming UI, inline replace button); spellchecker chips UI; scan text OCR; screenshot chips & clipboard paste suggestions; spacebar cursor trackpad gesture control. Verified targets: 18.5.6, 18.5.4, 18.5.3, 18.4.1. |
| **Gmail** | `com.google.android.gm` | Suppress sponsored promotional and advertising cards in personal inboxes. |
| **Google App (Discover)** | `com.google.android.googlequicksearchbox` | Filter sponsored cards, shopping ads, and promotional content in the Discover feed. |
| **Proton VPN** | `ch.protonvpn.android` | Suppress upgrade prompts, unlock NetShield UI toggles, disable telemetry and observability background workers. |

</details>

---

## 🛠️ Building from Source

<details>
<summary><b>View Build Instructions & Bundle Assembly</b></summary>
<br>

### Prerequisites
- JDK 21 (Temurin or OpenJDK) or JDK 25 LTS
- Android SDK with API 37 platform tools (configured via `ANDROID_HOME` or `local.properties`)

### Build Commands
```bash
# Clone the repository
git clone https://github.com/a092devs/Nexfusion-Patches.git
cd Nexfusion-Patches

# Run unit tests across all bundles
./gradlew testDebugUnitTest

# Assemble all .nxb release bundles
./gradlew assembleBundle

# Assemble a specific bundle (e.g. NexFusion)
./gradlew :bundles:nexfusion:assembleBundle
```

The compiled bundles will be generated in `bundles/<bundle-name>/build/outputs/bundle/*.nxb`.

</details>

---

## 🌐 Discovery & Release Pipeline

<details>
<summary><b>View Discovery Specification & CI/CD Pipeline</b></summary>
<br>

NexFusion discovers bundles through **patch sources**. A source can be configured as:
- **GitHub Release Source:** A repository (`owner/repo`) whose latest release provides compiled `*.nxb` assets.
- **Direct Feed Source:** A JSON endpoint declaring bundle versions, download URLs, and metadata:
  ```json
  {
    "version": "4.2.4",
    "download_url": "https://github.com/a092devs/Nexfusion-Patches/releases/download/4.2.4/nexfusion-4.2.4.nxb",
    "description": "Unified community runtime patch bundle"
  }
  ```

Automated releases are published via [`.github/workflows/release.yml`](.github/workflows/release.yml) upon verified version declarations in `CHANGELOG.md`.

</details>

---

## 🤝 Contributing

Contributions, bug reports, and pull requests are welcome!
- Ensure all unit tests pass prior to opening pull requests: `./gradlew testDebugUnitTest`
- All contributions must adhere to the runtime-only architecture (in-memory execution via LSPosed API 101, zero APK modification).
- Ensure target application stability by wrapping non-essential hooks in `runCatching` safeguards.

---

## 📜 Credits & Acknowledgments

<details>
<summary><b>View Upstream & Open-Source Acknowledgments</b></summary>
<br>

Nexfusion-Patches is developed and maintained by **a092devs**. We gratefully acknowledge the open-source projects, reverse engineers, and library authors whose research and tools empower our patch ecosystem:

| Project | Contribution |
|---|---|
| **[LSPosed](https://github.com/LSPosed/LSPosed)** | Core ART runtime hooking framework and official `libxposed` (API 101) |
| **[DexKit](https://luckypray.org/DexKit/en/)** | High-performance Dex bytecode pattern matching and opcode resolution |
| **[Morphe](https://morphe.software)** | Bytecode runtime hook patterns, fingerprint specifications, and shared utilities |
| **[ReVanced](https://revanced.app)** | Reverse engineering research, feature specifications, and community contributions |
| **[SysAdminDoc](https://github.com/SysAdminDoc)** | Stealth, ad-blocking, and media extraction hook specifications |
| **[Piko](https://github.com/crimera/piko)** | Runtime hook logic and downloader patterns |
| **[VenusIsJaded](https://github.com/VenusIsJaded/Venus-Patches)** | Privacy target tables and font styling |
| **[heyymichii](https://github.com/heyymichii/michii-patches)** | Share-link cleaning, direct navigation, and tracking removal |
| **[ak800i](https://github.com/ak800i/mixplorer-patches-for-morphe)** | Scoped-storage file sharing fix for Android 11+ |
| **[jkennethcarino](https://github.com/jkennethcarino/adobo)** | Screenshot-banner suppression, link sanitization, and UI hooks |
| **[gnadgnaoh](https://github.com/gnadgnaoh/NexFusion-XES)** | Inbox promotion and Discover feed ad filtering |

</details>

---

## ⚖️ License

Nexfusion-Patches is open-source software developed by **a092devs** and licensed under the **GNU General Public License v3.0** — see [LICENSE](LICENSE). Third-party libraries, ported feature specifications, and upstream dependencies retain their respective licenses and attribution.
