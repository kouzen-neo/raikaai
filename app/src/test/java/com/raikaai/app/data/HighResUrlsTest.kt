package com.raikaai.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HighResUrlsTest {
    private val hash = "50f690abf27b9a167736ba1ddd3095d24639a60b"

    @Test
    fun thumbnailExpandsToSampleOriginalsThenThumbnail() {
        val urls = RoleplayRepository.highResUrls("https://safebooru.org/thumbnails/4619/thumbnail_$hash.jpg")
        assertEquals("https://safebooru.org/samples/4619/sample_$hash.jpg", urls.first())
        assertEquals("https://safebooru.org/thumbnails/4619/thumbnail_$hash.jpg", urls.last())
        assertTrue(urls.contains("https://safebooru.org/images/4619/$hash.png"))
    }

    @Test
    fun sampleUrlsAreRecognisedToo() {
        val urls = RoleplayRepository.highResUrls("https://safebooru.org/samples/3072/sample_$hash.jpg")
        assertEquals("https://safebooru.org/samples/3072/sample_$hash.jpg", urls.first())
    }

    @Test
    fun otherHostsAndBlankStayAsIs() {
        assertEquals(listOf("https://example.com/a.png"), RoleplayRepository.highResUrls("https://example.com/a.png"))
        assertTrue(RoleplayRepository.highResUrls("").isEmpty())
        assertTrue(RoleplayRepository.highResUrls(null).isEmpty())
    }
}
