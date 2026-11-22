package io.github.a092devs.nexfusion.nexfusion.instagram

import android.content.SharedPreferences
import io.github.a092devs.nexfusion.nexfusion.instagram.InstagramRequestFilter.Rule
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.net.URI

class InstagramPatchesTest {

    @Test
    fun testRequestRules() {
        fun rule(url: String) = InstagramRequestFilter.ruleFor(URI(url))

        assertEquals(Rule.ANALYTICS, rule("https://graph.instagram.com/logging_client_events"))
        assertEquals(Rule.STORY_SEEN, rule("https://i.instagram.com/api/v2/media/seen/?reel=1"))
        assertEquals(Rule.LIVE_VIEWER, rule("https://i.instagram.com/api/v1/live/123/heartbeat_and_get_viewer_count/"))
        assertEquals(Rule.REELS_ADS, rule("https://i.instagram.com/api/v1/feed/injected_reels_media/"))
        assertEquals(Rule.ADS, rule("https://i.instagram.com/api/v1/async_ads/"))
        assertEquals(Rule.SUGGESTED, rule("https://i.instagram.com/api/v1/discover/chaining/"))
        assertEquals(Rule.RECENT_SEARCHES, rule("https://i.instagram.com/api/v1/fbsearch/register_recent_search_click/"))
        assertEquals(Rule.REEL_WATCH_HISTORY, rule("https://i.instagram.com/api/v1/clips/write_seen_state/"))
        assertEquals(Rule.META_AI, rule("https://i.instagram.com/api/v1/meta_ai/prompt/"))
        assertEquals(Rule.META_AI, rule("https://i.instagram.com/api/v1/genai/chat/"))
        assertNull(rule("https://i.instagram.com/api/v1/feed/timeline/"))

        val adsUri = URI("https://i.instagram.com/api/v1/async_ads/")
        val reelsAdsUri = URI("https://i.instagram.com/api/v1/feed/injected_reels_media/")
        val recentSearchesUri = URI("https://i.instagram.com/api/v1/fbsearch/register_recent_search_click/")
        val reelWatchHistoryUri = URI("https://i.instagram.com/api/v1/clips/write_seen_state/")
        val metaAiUri = URI("https://i.instagram.com/api/v1/meta_ai/prompt/")
        InstagramRequestFilter.enable(Rule.ADS)
        InstagramRequestFilter.enable(Rule.REELS_ADS)
        InstagramRequestFilter.enable(Rule.RECENT_SEARCHES)
        InstagramRequestFilter.enable(Rule.REEL_WATCH_HISTORY)
        InstagramRequestFilter.enable(Rule.META_AI)
        assertTrue(InstagramRequestFilter.shouldBlock(adsUri))
        assertTrue(InstagramRequestFilter.shouldBlock(reelsAdsUri))
        assertTrue(InstagramRequestFilter.shouldBlock(recentSearchesUri))
        assertTrue(InstagramRequestFilter.shouldBlock(reelWatchHistoryUri))
        assertTrue(InstagramRequestFilter.shouldBlock(metaAiUri))
        assertFalse(InstagramRequestFilter.shouldBlock(URI("https://i.instagram.com/api/v1/feed/timeline/")))
    }

    @Test
    fun testSanitizeUrl() {
        assertEquals(
            "https://www.instagram.com/reel/abc/",
            InstagramLinks.sanitizeUrl("https://www.instagram.com/reel/abc/?igsh=MTc4MmM1YmI2Ng=="),
        )
        assertEquals(
            "https://www.instagram.com/p/abc/?img_index=2",
            InstagramLinks.sanitizeUrl("https://www.instagram.com/p/abc/?igsh=x&img_index=2"),
        )
        assertEquals(
            "https://example.com/?id=1#top",
            InstagramLinks.sanitizeUrl("https://example.com/?utm_source=ig&id=1&fbclid=x#top"),
        )
        assertEquals("https://example.com/a", InstagramLinks.sanitizeUrl("https://example.com/a"))
        assertEquals("not a url", InstagramLinks.sanitizeUrl("not a url"))
    }

    @Test
    fun testDashManifestAudioExtraction() {
        val sampleMpd = """
            <MPD xmlns="urn:mpeg:dash:schema:mpd:2011" minBufferTime="PT1.500S">
              <Period duration="PT0H0M28.028S">
                <AdaptationSet segmentAlignment="true" maxWidth="720" maxHeight="1280">
                  <Representation id="1" mimeType="video/mp4" codecs="avc1.64001F" bandwidth="1158670">
                    <BaseURL>https://video.xx.fbcdn.net/video.mp4</BaseURL>
                  </Representation>
                </AdaptationSet>
                <AdaptationSet segmentAlignment="true" mimeType="audio/mp4">
                  <Representation id="2" codecs="mp4a.40.2" bandwidth="131072">
                    <BaseURL>https://video.xx.fbcdn.net/audio_best.m4a?a=1&amp;b=2</BaseURL>
                  </Representation>
                  <Representation id="3" codecs="mp4a.40.5" bandwidth="64000">
                    <BaseURL>https://video.xx.fbcdn.net/audio_low.m4a</BaseURL>
                  </Representation>
                </AdaptationSet>
              </Period>
            </MPD>
        """.trimIndent()

        val audioUrl = io.github.a092devs.nexfusion.nexfusion.instagram.download.DashManifest.bestAudioUrl(sampleMpd)
        assertEquals("https://video.xx.fbcdn.net/audio_best.m4a?a=1&b=2", audioUrl)

        assertNull(io.github.a092devs.nexfusion.nexfusion.instagram.download.DashManifest.bestAudioUrl(null))
        assertNull(io.github.a092devs.nexfusion.nexfusion.instagram.download.DashManifest.bestAudioUrl(""))
        assertNull(io.github.a092devs.nexfusion.nexfusion.instagram.download.DashManifest.bestAudioUrl("<MPD></MPD>"))
    }

    @Test
    fun testDownloadFolderCustomization() {
        val storage = mutableMapOf<String, Any?>()
        val editor = object : SharedPreferences.Editor {
            override fun putString(key: String, value: String?): SharedPreferences.Editor {
                storage[key] = value
                return this
            }
            override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor {
                storage[key] = value
                return this
            }
            override fun remove(key: String): SharedPreferences.Editor {
                storage.remove(key)
                return this
            }
            override fun clear(): SharedPreferences.Editor { storage.clear(); return this }
            override fun commit() = true
            override fun apply() {}
            override fun putInt(k: String, v: Int) = this
            override fun putLong(k: String, v: Long) = this
            override fun putFloat(k: String, v: Float) = this
            override fun putStringSet(k: String, v: Set<String>?) = this
        }
        val prefs = object : SharedPreferences {
            override fun getAll(): Map<String, *> = storage
            override fun getString(key: String, defValue: String?): String? = storage[key] as? String ?: defValue
            override fun getBoolean(key: String, defValue: Boolean): Boolean = storage[key] as? Boolean ?: defValue
            override fun edit(): SharedPreferences.Editor = editor
            override fun getStringSet(k: String, d: Set<String>?) = d
            override fun getInt(k: String, d: Int) = d
            override fun getLong(k: String, d: Long) = d
            override fun getFloat(k: String, d: Float) = d
            override fun contains(k: String) = k in storage
            override fun registerOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) {}
            override fun unregisterOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        }

        val constructor = io.github.a092devs.nexfusion.nexfusion.instagram.download.DownloadSettings::class.java.getDeclaredConstructor(SharedPreferences::class.java)
        constructor.isAccessible = true
        val settings = constructor.newInstance(prefs)

        // Default folder
        assertEquals("NexFusion/Instagram", settings.customFolder)
        assertEquals("NexFusion/Instagram", settings.folder(null))
        assertEquals("NexFusion/Instagram", settings.folder("alice")) // perUsername is false by default

        // Enable per-username
        settings.perUsername = true
        assertEquals("NexFusion/Instagram/alice", settings.folder("alice"))

        // Set custom folder
        settings.customFolder = "MyDownloads/IG"
        assertEquals("MyDownloads/IG", settings.customFolder)
        assertEquals("MyDownloads/IG/bob", settings.folder("bob"))

        // Strip leading Download/ or slashes
        settings.customFolder = "/Downloads/CustomMedia/"
        assertEquals("CustomMedia", settings.customFolder)

        // Reset to default
        settings.customFolder = "NexFusion/Instagram"
        assertEquals("NexFusion/Instagram", settings.customFolder)
        assertFalse(prefs.contains("custom_download_folder"))
    }
}

