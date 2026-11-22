package io.github.a092devs.nexfusion.nexfusion.instagram

import io.github.a092devs.nexfusion.nexfusion.instagram.download.DashManifest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class DashManifestTest {
    @Test
    fun `picks the highest bandwidth AAC-LC audio track`() {
        val manifest = """
            <MPD xmlns="urn:mpeg:dash:schema:mpd:2011">
              <Period>
                <AdaptationSet contentType="video" mimeType="video/mp4">
                  <Representation id="v1" bandwidth="900000" codecs="avc1.4d401f" mimeType="video/mp4">
                    <BaseURL>https://cdn.example.test/video.mp4</BaseURL>
                  </Representation>
                </AdaptationSet>
                <AdaptationSet contentType="audio" mimeType="audio/mp4">
                  <Representation id="a1" bandwidth="64000" codecs="mp4a.40.5" mimeType="audio/mp4">
                    <BaseURL>https://cdn.example.test/audio-he-aac.mp4</BaseURL>
                  </Representation>
                  <Representation id="a2" bandwidth="128000" codecs="mp4a.40.2" mimeType="audio/mp4">
                    <BaseURL>https://cdn.example.test/audio-aac.mp4?bytestart=0&amp;byteend=42</BaseURL>
                  </Representation>
                </AdaptationSet>
                <AdaptationSet contentType="audio" mimeType="audio/mp4">
                  <Representation id="a3" bandwidth="96000" codecs="mp4a.40.2" mimeType="audio/mp4">
                    <BaseURL>https://cdn.example.test/audio-aac-low.mp4</BaseURL>
                  </Representation>
                </AdaptationSet>
              </Period>
            </MPD>
        """.trimIndent()

        assertEquals(
            "https://cdn.example.test/audio-aac.mp4?bytestart=0&byteend=42",
            DashManifest.bestAudioUrl(manifest),
        )
    }

    @Test
    fun `ignores audio representations without a BaseURL`() {
        val manifest = """
            <MPD>
              <AdaptationSet contentType="audio">
                <Representation bandwidth="128000" codecs="mp4a.40.2" mimeType="audio/mp4"/>
              </AdaptationSet>
            </MPD>
        """.trimIndent()

        assertNull(DashManifest.bestAudioUrl(manifest))
    }

    @Test
    fun `returns null for blank manifests`() {
        assertNull(DashManifest.bestAudioUrl(null))
        assertNull(DashManifest.bestAudioUrl(""))
        assertNull(DashManifest.bestAudioUrl("   "))
    }
}
