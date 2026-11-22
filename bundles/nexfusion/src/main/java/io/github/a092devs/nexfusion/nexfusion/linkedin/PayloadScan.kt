package io.github.a092devs.nexfusion.nexfusion.linkedin

import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

// Kept in its own file so unit tests can load it without the patcher runtime, like LinkCleaner.

/** Byte-level ASCII search, the check upstream's ProtoScanner.containsAscii does on the payload. */
internal fun containsAscii(haystack: ByteArray, needle: String): Boolean {
    if (needle.isEmpty() || haystack.size < needle.length) return false
    outer@ for (start in 0..haystack.size - needle.length) {
        for (offset in needle.indices) {
            if (haystack[start + offset] != needle[offset].code.toByte()) continue@outer
        }
        return true
    }
    return false
}

/**
 * Checks whether any length-delimited string in the serialized protobuf matches any of [values] exactly.
 */
internal fun hasExactString(data: ByteArray?, vararg values: String): Boolean {
    if (data == null || values.isEmpty()) return false
    val strings = scanStrings(data)
    for (s in strings) {
        for (v in values) {
            if (s == v) return true
        }
    }
    return false
}

internal data class ImageRef(
    val root: String,
    val largestUrl: String,
    val largestWidth: Int,
    val smallestUrl: String
)

internal class ProtoScanResult(
    val strings: List<String>,
    val images: List<ImageRef>
)

internal data class MediaItem(
    val assetId: String,
    val isVideo: Boolean,
    val url: String,
    val size: Int,
    var thumbnailUrl: String? = null
) {
    fun label(): String = if (isVideo) "Video ${size}p" else "Photo ${size}px"
}

private const val MAX_PROTO_DEPTH = 48
private const val IMAGE_ROOT_PREFIX = "https://media.licdn.com/dms/image/"

internal fun scanProto(data: ByteArray): ProtoScanResult {
    val strings = mutableListOf<String>()
    val images = mutableListOf<ImageRef>()
    parseProtoMessage(data, 0, data.size, 0, strings, images)
    return ProtoScanResult(strings, images)
}

internal fun scanStrings(data: ByteArray): List<String> = scanProto(data).strings

internal fun extractMediaItems(scan: ProtoScanResult, isProfileScreen: Boolean): List<MediaItem> {
    val videos = LinkedHashMap<String, MediaItem>()
    for (s in scan.strings) {
        val start = s.indexOf("https://dms.licdn.com/playlist/vid/")
        if (start < 0) continue
        val url = s.substring(start)
        val rem = if (url.startsWith("https://dms.licdn.com/playlist/vid/v2/")) {
            url.substring("https://dms.licdn.com/playlist/vid/v2/".length)
        } else {
            url.substring("https://dms.licdn.com/playlist/vid/".length)
        }
        val asset = rem.substringBefore('/')
        val mp4Idx = url.indexOf("/mp4-")
        if (asset.isEmpty() || mp4Idx < 0) continue
        val heightStr = url.substring(mp4Idx + "/mp4-".length).takeWhile { it.isDigit() }
        val height = heightStr.toIntOrNull() ?: continue
        val current = videos[asset]
        if (current == null || height > current.size) {
            videos[asset] = MediaItem(asset, isVideo = true, url = url, size = height)
        }
    }

    val images = LinkedHashMap<String, MediaItem>()
    val thumbnails = LinkedHashMap<String, String>()
    for (img in scan.images) {
        val prefix = if (img.root.startsWith("https://media.licdn.com/dms/image/v2/")) {
            "https://media.licdn.com/dms/image/v2/"
        } else if (img.root.startsWith("https://media.licdn.com/dms/image/")) {
            "https://media.licdn.com/dms/image/"
        } else continue

        val afterPrefix = img.root.substring(prefix.length)
        val asset = afterPrefix.substringBefore('/')
        if (asset.isEmpty()) continue
        val kind = afterPrefix.substringAfter('/', "")

        if (kind.startsWith("videocover")) {
            thumbnails[asset] = img.smallestUrl
        } else if (isProfileScreen) {
            if (kind.startsWith("profile-displayphoto") || kind.startsWith("profile-displaybackgroundimage")) {
                val current = images[asset]
                if (current == null || img.largestWidth > current.size) {
                    images[asset] = MediaItem(asset, isVideo = false, url = img.largestUrl, size = img.largestWidth, thumbnailUrl = img.smallestUrl)
                }
            }
        } else {
            if (kind.startsWith("feedshare-") || kind.startsWith("image-shrink_") || kind.startsWith("shrink_") ||
                (!kind.startsWith("videocover") && !kind.startsWith("profile-") && !kind.startsWith("company-logo"))) {
                val current = images[asset]
                if (current == null || img.largestWidth > current.size) {
                    images[asset] = MediaItem(asset, isVideo = false, url = img.largestUrl, size = img.largestWidth, thumbnailUrl = img.smallestUrl)
                }
            }
        }
    }

    // Fallback: scan strings for direct image URLs if no structured renditions matched
    if (images.isEmpty()) {
        for (s in scan.strings) {
            if (!s.startsWith("https://media.licdn.com/dms/image/")) continue
            val afterPrefix = if (s.startsWith("https://media.licdn.com/dms/image/v2/")) {
                s.substring("https://media.licdn.com/dms/image/v2/".length)
            } else {
                s.substring("https://media.licdn.com/dms/image/".length)
            }
            val asset = afterPrefix.substringBefore('/')
            if (asset.isEmpty() || asset.length < 5) continue
            val kind = afterPrefix.substringAfter('/', "")
            if (kind.startsWith("videocover")) continue
            if (!isProfileScreen && (kind.startsWith("profile-") || kind.startsWith("company-logo"))) continue
            if (!images.containsKey(asset)) {
                images[asset] = MediaItem(asset, isVideo = false, url = s, size = 1080)
            }
        }
    }

    for (video in videos.values) {
        video.thumbnailUrl = thumbnails[video.assetId]
    }

    val result = ArrayList<MediaItem>()
    result.addAll(videos.values)
    result.addAll(images.values)
    return result
}

private class MessageNode {
    var field1String: String? = null
    var field1Varint: Long = -1L
    var field3String: String? = null
    val field2Messages = mutableListOf<MessageNode>()
}

private fun parseProtoMessage(
    b: ByteArray,
    start: Int,
    end: Int,
    depth: Int,
    outStrings: MutableList<String>,
    outImages: MutableList<ImageRef>
): MessageNode? {
    if (depth > MAX_PROTO_DEPTH) return null
    val message = MessageNode()
    val foundStrings = mutableListOf<String>()
    val foundImages = mutableListOf<ImageRef>()
    var p = start
    while (p < end) {
        val tagResult = readVarint(b, p, end) ?: return null
        val tag = tagResult[0]
        p = tagResult[1].toInt()
        val field = (tag ushr 3).toInt()
        val wireType = (tag and 7L).toInt()
        if (field <= 0) return null

        when (wireType) {
            0 -> {
                val v = readVarint(b, p, end) ?: return null
                p = v[1].toInt()
                if (field == 1) message.field1Varint = v[0]
            }
            1 -> p += 8
            5 -> p += 4
            2 -> {
                val lenResult = readVarint(b, p, end) ?: return null
                val len = lenResult[0]
                p = lenResult[1].toInt()
                if (len < 0 || len > end - p) return null
                val subEnd = p + len.toInt()
                val s = printableUtf8(b, p, subEnd)
                if (s != null) {
                    foundStrings.add(s)
                    if (field == 1) message.field1String = s
                    if (field == 3) message.field3String = s
                } else {
                    var sub = parseProtoMessage(b, p, subEnd, depth + 1, foundStrings, foundImages)
                    if (sub == null && subEnd > p + 1 && b[p] == 0.toByte()) {
                        sub = parseProtoMessage(b, p + 1, subEnd, depth + 1, foundStrings, foundImages)
                    }
                    if (sub != null) {
                        if (field == 2) message.field2Messages.add(sub)
                    } else {
                        salvageStrings(b, p, subEnd, foundStrings)
                    }
                }
                p = subEnd
            }
            else -> return null
        }
        if (p > end) return null
    }
    outStrings.addAll(foundStrings)
    outImages.addAll(foundImages)
    val image = asImage(message)
    if (image != null) outImages.add(image)
    return message
}

private fun asImage(m: MessageNode): ImageRef? {
    val root = m.field1String ?: return null
    if (!root.startsWith(IMAGE_ROOT_PREFIX)) return null
    var largest: String? = null
    var smallest: String? = null
    var largestWidth = -1L
    var smallestWidth = Long.MAX_VALUE
    for (r in m.field2Messages) {
        val suffix = r.field3String ?: continue
        val width = r.field1Varint
        if (width <= 0) continue
        if (width > largestWidth) {
            largestWidth = width
            largest = suffix
        }
        if (width < smallestWidth) {
            smallestWidth = width
            smallest = suffix
        }
    }
    if (largest == null) return null
    return ImageRef(
        root = root,
        largestUrl = root + largest,
        largestWidth = largestWidth.toInt(),
        smallestUrl = root + (smallest ?: largest)
    )
}

private fun salvageStrings(b: ByteArray, start: Int, end: Int, out: MutableList<String>) {
    var i = start
    while (i < end - 2) {
        val tag = b[i].toInt() and 0xff
        if ((tag and 7) == 2 && (tag ushr 3) > 0) {
            val lenResult = readVarint(b, i + 1, end)
            if (lenResult != null && lenResult[0] >= 1 && lenResult[0] <= end - lenResult[1]) {
                val strStart = lenResult[1].toInt()
                val strEnd = strStart + lenResult[0].toInt()
                val s = printableUtf8(b, strStart, strEnd)
                if (s != null) {
                    out.add(s)
                    i = strEnd
                    continue
                }
            }
        }
        i++
    }
}

private fun readVarint(b: ByteArray, start: Int, end: Int): LongArray? {
    var p = start
    var result = 0L
    var shift = 0
    while (shift < 64) {
        if (p >= end) return null
        val cur = b[p++]
        result = result or ((cur.toLong() and 0x7fL) shl shift)
        if ((cur.toInt() and 0x80) == 0) {
            return longArrayOf(result, p.toLong())
        }
        shift += 7
    }
    return null
}

private fun printableUtf8(b: ByteArray, start: Int, end: Int): String? {
    if (end <= start) return null
    for (i in start until end) {
        val c = b[i].toInt() and 0xff
        if (c < 0x20 || c == 0x7f) return null
    }
    val decoder = StandardCharsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
    return try {
        decoder.decode(ByteBuffer.wrap(b, start, end - start)).toString()
    } catch (_: Exception) {
        null
    }
}
