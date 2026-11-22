package io.github.a092devs.nexfusion.nexfusion.linkedin

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class LinkCleanerTest {

    @Test
    fun `removes tracking parameters`() {
        assertEquals(
            "https://www.linkedin.com/feed/",
            LinkCleaner.cleanUrl("https://www.linkedin.com/feed/?trk=public_post&lipi=abc"),
        )
    }

    @Test
    fun `keeps ordinary parameters in order`() {
        assertEquals(
            "https://www.linkedin.com/jobs/view/1?currentJobId=2&sortBy=DD",
            LinkCleaner.cleanUrl("https://www.linkedin.com/jobs/view/1?currentJobId=2&trk=x&sortBy=DD"),
        )
    }

    @Test
    fun `matches tracking keys case-insensitively`() {
        assertEquals(
            "https://www.linkedin.com/x",
            LinkCleaner.cleanUrl("https://www.linkedin.com/x?TRK=1"),
        )
    }

    @Test
    fun `keeps the fragment`() {
        assertEquals(
            "https://www.linkedin.com/x#comments",
            LinkCleaner.cleanUrl("https://www.linkedin.com/x?trk=1#comments"),
        )
    }

    @Test
    fun `cleans lnkd in links too`() {
        assertEquals("https://lnkd.in/abc", LinkCleaner.cleanUrl("https://lnkd.in/abc?trk=1"))
    }

    @Test
    fun `leaves other sites unchanged`() {
        val url = "https://example.com/?trk=1"
        assertSame(url, LinkCleaner.cleanUrl(url))
    }

    @Test
    fun `leaves links without a query unchanged`() {
        val url = "https://www.linkedin.com/feed/"
        assertSame(url, LinkCleaner.cleanUrl(url))
    }

    @Test
    fun `cleans every link in shared text`() {
        assertEquals(
            "look at https://www.linkedin.com/a and https://linkedin.com/b please",
            LinkCleaner.clean("look at https://www.linkedin.com/a?trk=1 and https://linkedin.com/b?trk=2 please"),
        )
    }

    @Test
    fun `returns the same instance when nothing changed`() {
        val text = "no links here"
        assertSame(text, LinkCleaner.clean(text))
    }

    @Test
    fun `returns null for null`() {
        assertNull(LinkCleaner.clean(null))
    }

    @Test
    fun `skips text without a scheme`() {
        val text = "linkedin.com/feed?trk=1"
        assertSame(text, LinkCleaner.clean(text))
    }
}
