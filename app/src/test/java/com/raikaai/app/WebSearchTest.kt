package com.raikaai.app

import com.raikaai.app.web.WebSearchItem
import com.raikaai.app.web.WebSourceCitation
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.regex.Pattern

class WebSearchTest {
    @Test
    fun testDuckDuckGoLiteParsing() {
        val sampleHtml = """
            <tr>
                <td valign="top">1.&nbsp;</td>
                <td>
                  <a rel="nofollow" href="https://www.flashscore.co.id/sepak-bola/inggris/" class='result-link'>Hasil Liga Inggris, Skor Langsung Sepak Bola Inggris</a>
                </td>
            </tr>
            <tr>
                <td>&nbsp;&nbsp;&nbsp;</td>
                <td class='result-snippet'>
                  <b>Skor</b> langsung sepak bola di Flashscore.co.id - <b>Inggris</b>, Hasil <b>liga</b> <b>Inggris</b>. Livescore sepak bola yang dapat disesuaikan.
                </td>
            </tr>
            <tr>
                <td valign="top">2.&nbsp;</td>
                <td>
                  <a class="result-link" href="https://www.bola.net/hasil-pertandingan/liga-inggris.html">Hasil Liga Inggris 2026: Skor Terbaru - Bola.net</a>
                </td>
            </tr>
            <tr>
                <td>&nbsp;&nbsp;&nbsp;</td>
                <td class="result-snippet">
                  Hasil <b>pertandingan</b> <b>Liga</b> <b>Inggris</b> musim 2026/2027 lengkap dengan <b>skor</b> akhir.
                </td>
            </tr>
        """.trimIndent()

        val linkPattern = Pattern.compile(
            "<a[^>]*class=['\"]result-link['\"][^>]*href=['\"]([^'\"]+)['\"][^>]*>([\\s\\S]*?)<\\/a>|<a[^>]*href=['\"]([^'\"]+)['\"][^>]*class=['\"]result-link['\"][^>]*>([\\s\\S]*?)<\\/a>",
            Pattern.CASE_INSENSITIVE
        )
        val snippetPattern = Pattern.compile(
            "<td[^>]*class=['\"]result-snippet['\"][^>]*>([\\s\\S]*?)<\\/td>",
            Pattern.CASE_INSENSITIVE
        )

        val linkMatcher = linkPattern.matcher(sampleHtml)
        val links = mutableListOf<Pair<String, String>>()
        while (linkMatcher.find()) {
            val url = linkMatcher.group(1) ?: linkMatcher.group(3) ?: ""
            val title = linkMatcher.group(2) ?: linkMatcher.group(4) ?: ""
            if (url.isNotBlank()) {
                links.add(Pair(url, title.replace(Regex("<[^>]+>"), "").trim()))
            }
        }

        val snipMatcher = snippetPattern.matcher(sampleHtml)
        val snippets = mutableListOf<String>()
        while (snipMatcher.find()) {
            val snippet = snipMatcher.group(1).orEmpty().replace(Regex("<[^>]+>"), "").trim()
            if (snippet.isNotBlank()) {
                snippets.add(snippet)
            }
        }

        assertEquals(2, links.size)
        assertEquals(2, snippets.size)
        assertTrue(links[0].first.startsWith("https://www.flashscore.co.id"))
        assertTrue(links[1].first.startsWith("https://www.bola.net"))
    }

    @Test
    fun testGoogleNewsRssXmlParsing() {
        val sampleXml = """
            <rss version="2.0">
              <channel>
                <title>Google News</title>
                <item>
                  <title>Presiden Prabowo Subianto Lantik Menteri Kabinet Merah Putih</title>
                  <link>https://news.google.com/articles/123</link>
                  <pubDate>Mon, 21 Oct 2024 10:00:00 GMT</pubDate>
                  <description>Presiden Prabowo Subianto resmi melantik jajaran menteri kabinet di Istana Negara.</description>
                </item>
                <item>
                  <title>Hasil Liga Inggris 2026: Arsenal Menang Telak</title>
                  <link>https://news.google.com/articles/456</link>
                  <pubDate>Sun, 23 Aug 2026 21:00:00 GMT</pubDate>
                  <description>Arsenal berhasil meraih kemenangan telak dengan skor 3-0.</description>
                </item>
              </channel>
            </rss>
        """.trimIndent()

        val itemPattern = Pattern.compile("<item>([\\s\\S]*?)<\\/item>", Pattern.CASE_INSENSITIVE)
        val titlePattern = Pattern.compile("<title>([\\s\\S]*?)<\\/title>", Pattern.CASE_INSENSITIVE)
        val linkPattern = Pattern.compile("<link>([\\s\\S]*?)<\\/link>", Pattern.CASE_INSENSITIVE)
        val descPattern = Pattern.compile("<description>([\\s\\S]*?)<\\/description>", Pattern.CASE_INSENSITIVE)

        val itemMatcher = itemPattern.matcher(sampleXml)
        val items = mutableListOf<Triple<String, String, String>>()

        while (itemMatcher.find()) {
            val itemBlock = itemMatcher.group(1).orEmpty()
            val tMatcher = titlePattern.matcher(itemBlock)
            val lMatcher = linkPattern.matcher(itemBlock)
            val dMatcher = descPattern.matcher(itemBlock)

            val title = if (tMatcher.find()) tMatcher.group(1).orEmpty().trim() else ""
            val link = if (lMatcher.find()) lMatcher.group(1).orEmpty().trim() else ""
            val desc = if (dMatcher.find()) dMatcher.group(1).orEmpty().trim() else ""

            if (title.isNotBlank()) {
                items.add(Triple(title, link, desc))
            }
        }

        assertEquals(2, items.size)
        assertTrue(items[0].first.contains("Prabowo Subianto"))
        assertTrue(items[1].first.contains("Arsenal"))
    }

    @Test
    fun testWebSourceCitationSerializationAndParsing() {
        val citation = WebSourceCitation(
            title = "Berita Terkini Kompas",
            url = "https://www.kompas.com/read/2026/08/25/berita",
            snippet = "Ringkasan berita aktual hari ini.",
            index = 1,
        )

        assertEquals("kompas.com", citation.domain)

        val json = citation.toJson()
        assertTrue(json.contains("web_source"))
        assertTrue(json.contains("kompas.com"))

        val parsed = WebSourceCitation.parse(json)
        assertNotNull(parsed)
        assertEquals("Berita Terkini Kompas", parsed?.title)
        assertEquals("https://www.kompas.com/read/2026/08/25/berita", parsed?.url)
        assertEquals("kompas.com", parsed?.domain)
        assertEquals(1, parsed?.index)
    }

    @Test
    fun testWebSourceCitationLegacyCompatibility() {
        val legacy = "Web: Presiden Indonesia Terkini"
        val parsed = WebSourceCitation.parse(legacy, 2)
        assertNotNull(parsed)
        assertEquals("Presiden Indonesia Terkini", parsed?.title)
        assertTrue(parsed?.url?.contains("google.com") == true)
        assertEquals(2, parsed?.index)
    }

    @Test
    fun testWebSourceCitationDirectUrl() {
        val url = "https://en.wikipedia.org/wiki/Artificial_intelligence"
        val parsed = WebSourceCitation.parse(url, 3)
        assertNotNull(parsed)
        assertEquals(url, parsed?.url)
        assertEquals("en.wikipedia.org", parsed?.domain)
        assertEquals(3, parsed?.index)
    }

    @Test
    fun testWebSourceCitationEncodesFallbackSearchUrl() {
        val raw = """{"title":"Hello World & More","url":""}"""
        val parsed = WebSourceCitation.parse(raw)
        assertNotNull(parsed)
        assertEquals("https://google.com/search?q=Hello+World+%26+More", parsed?.url)
    }

    @Test
    fun testReadBodyBoundedStopsAtLimit() {
        val huge = "x".repeat(50_000)
        val body = huge.toResponseBody(null)
        val capped = com.raikaai.app.util.NetworkUtils.readBodyBounded(body, 128)
        assertEquals(128, capped?.length)
    }
}
