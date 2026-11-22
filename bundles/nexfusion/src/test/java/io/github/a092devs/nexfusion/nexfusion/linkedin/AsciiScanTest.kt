package io.github.a092devs.nexfusion.nexfusion.linkedin

import kotlin.test.Test
import kotlin.test.assertEquals

class AsciiScanTest {

    @Test
    fun `finds the marker anywhere in the payload`() {
        val payload = byteArrayOf(0x12, 0x08) +
            "urn:li:sponsoredContentV2:".toByteArray() +
            byteArrayOf(0x00)
        assertEquals(true, containsAscii(payload, "urn:li:sponsoredContentV2:"))
    }

    @Test
    fun `matches at the last possible offset`() {
        assertEquals(true, containsAscii("abcmarker".toByteArray(), "marker"))
    }

    @Test
    fun `does not match a prefix or suffix alone`() {
        assertEquals(false, containsAscii("marke".toByteArray(), "marker"))
        assertEquals(false, containsAscii("arker".toByteArray(), "marker"))
    }

    @Test
    fun `empty needle and empty haystack match nothing`() {
        assertEquals(false, containsAscii("abc".toByteArray(), ""))
        assertEquals(false, containsAscii(ByteArray(0), "abc"))
    }

    @Test
    fun `hasExactString matches exact protobuf strings`() {
        val strBytes = "Suggested".toByteArray()
        val payload = byteArrayOf(0x0a, strBytes.size.toByte()) + strBytes
        assertEquals(true, hasExactString(payload, "Suggested", "Disarankan"))
        assertEquals(false, hasExactString(payload, "Suggest", "Disarankan"))
        assertEquals(false, hasExactString(null, "Suggested"))
    }

    @Test
    fun `extractMediaItems extracts video with highest resolution`() {
        val vid360 = "https://dms.licdn.com/playlist/vid/v2/D5605AQH_vid/mp4-360p-30fp-crf28/0/123"
        val vid720 = "https://dms.licdn.com/playlist/vid/v2/D5605AQH_vid/mp4-720p-30fp-crf28/0/123"
        val scan = ProtoScanResult(listOf(vid360, vid720), emptyList())
        val items = extractMediaItems(scan, isProfileScreen = false)

        assertEquals(1, items.size)
        val item = items[0]
        assertEquals("D5605AQH_vid", item.assetId)
        assertEquals(true, item.isVideo)
        assertEquals(720, item.size)
        assertEquals(vid720, item.url)
    }

    @Test
    fun `extractMediaItems extracts structured image renditions`() {
        val root = "https://media.licdn.com/dms/image/v2/D5622AQG_photo/feedshare-shrink_800/"
        val imgRef = ImageRef(
            root = root,
            largestUrl = root + "large.jpg",
            largestWidth = 1920,
            smallestUrl = root + "small.jpg"
        )
        val scan = ProtoScanResult(emptyList(), listOf(imgRef))
        val items = extractMediaItems(scan, isProfileScreen = false)

        assertEquals(1, items.size)
        val item = items[0]
        assertEquals("D5622AQG_photo", item.assetId)
        assertEquals(false, item.isVideo)
        assertEquals(1920, item.size)
        assertEquals(root + "large.jpg", item.url)
        assertEquals(root + "small.jpg", item.thumbnailUrl)
    }

    @Test
    fun `extractMediaItems respects profileScreen flag`() {
        val profileRoot = "https://media.licdn.com/dms/image/v2/D5699AQG_user/profile-displayphoto-shrink_800/"
        val imgRef = ImageRef(
            root = profileRoot,
            largestUrl = profileRoot + "large.jpg",
            largestWidth = 800,
            smallestUrl = profileRoot + "small.jpg"
        )
        val scan = ProtoScanResult(emptyList(), listOf(imgRef))

        // When not profile screen, profile photos are ignored (belong to author avatar)
        val feedItems = extractMediaItems(scan, isProfileScreen = false)
        assertEquals(0, feedItems.size)

        // When profile screen, profile photo is extracted
        val profileItems = extractMediaItems(scan, isProfileScreen = true)
        assertEquals(1, profileItems.size)
        assertEquals("D5699AQG_user", profileItems[0].assetId)
    }
}
