package io.github.a092devs.nexfusion.nexfusion.instagram.download

import java.util.regex.Pattern

/**
 * Parses audio URLs from Instagram's DASH manifests (`video_dash_manifest`).
 */
internal object DashManifest {
    private val ADAPTATION_SET = Pattern.compile("<AdaptationSet\\b([^>]*)>(.*?)</AdaptationSet>", Pattern.DOTALL)
    private val REPRESENTATION = Pattern.compile("<Representation\\b([^>]*?)(?:/>|>(.*?)</Representation>)", Pattern.DOTALL)
    private val ATTRIBUTE = Pattern.compile("([\\w:]+)\\s*=\\s*\"([^\"]*)\"")
    private val BASE_URL = Pattern.compile("<BaseURL[^>]*>(.*?)</BaseURL>", Pattern.DOTALL)

    class AudioTrack(val codecs: String, val bandwidth: Long, val url: String)

    /**
     * Finds the best audio track URL in [manifest], preferring AAC-LC with highest bandwidth.
     */
    fun bestAudioUrl(manifest: String?): String? {
        if (manifest.isNullOrBlank()) return null
        val tracks = mutableListOf<AudioTrack>()
        try {
            val sets = ADAPTATION_SET.matcher(manifest)
            while (sets.find()) {
                val setAttrs = sets.group(1).orEmpty()
                val isAudioSet = setAttrs.contains("audio", ignoreCase = true)
                val reps = REPRESENTATION.matcher(sets.group(2).orEmpty())
                while (reps.find()) {
                    val repAttrs = reps.group(1).orEmpty()
                    val body = reps.group(2).orEmpty()
                    val isAudio = isAudioSet || repAttrs.contains("audio", ignoreCase = true) || repAttrs.contains("mp4a", ignoreCase = true)
                    if (!isAudio) continue

                    val baseMatch = BASE_URL.matcher(body)
                    if (!baseMatch.find()) continue
                    val rawUrl = baseMatch.group(1)?.trim() ?: continue
                    val url = unescape(rawUrl)
                    if (!url.startsWith("https://", ignoreCase = true)) continue

                    val codecs = attribute(repAttrs, "codecs") ?: attribute(setAttrs, "codecs") ?: ""
                    val bandwidth = attribute(repAttrs, "bandwidth")?.toLongOrNull() ?: 0L
                    tracks += AudioTrack(codecs, bandwidth, url)
                }
            }
        } catch (_: Throwable) {
            // Return best track parsed so far
        }
        if (tracks.isEmpty()) return null

        val sorted = tracks.sortedWith(
            compareByDescending<AudioTrack> { rankCodec(it.codecs) }
                .thenByDescending { it.bandwidth }
        )
        return sorted.firstOrNull()?.url
    }

    private fun rankCodec(codecs: String): Int = when {
        codecs.contains("mp4a.40.2") -> 3
        codecs.contains("mp4a.40.5") || codecs.contains("mp4a.40.29") -> 2
        codecs.startsWith("mp4a") -> 1
        else -> 0
    }

    private fun attribute(attributes: String, name: String): String? {
        val matcher = ATTRIBUTE.matcher(attributes)
        while (matcher.find()) {
            if (matcher.group(1) == name) return unescape(matcher.group(2).orEmpty())
        }
        return null
    }

    private fun unescape(text: String): String = text
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&apos;", "'")
        .replace("&amp;", "&")
}
