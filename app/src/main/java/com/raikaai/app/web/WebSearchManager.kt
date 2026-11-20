package com.raikaai.app.web

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.raikaai.app.util.KLog
import com.raikaai.app.util.NetworkUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern

data class WebSearchItem(
    val title: String,
    val snippet: String,
    val url: String,
)

data class WebSearchResult(
    val query: String,
    val items: List<WebSearchItem>,
    val promptContext: String,
)

class WebSearchManager private constructor(private val context: Context) {
    companion object {
        @Volatile
        private var instance: WebSearchManager? = null

        fun getInstance(context: Context): WebSearchManager =
            instance ?: synchronized(this) {
                instance ?: WebSearchManager(context.applicationContext).also { instance = it }
            }

        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"

        private const val CACHE_TTL_MS = 20 * 60 * 1000L // 20 minutes
    }

    private data class CacheEntry(
        val result: WebSearchResult,
        val timestamp: Long = System.currentTimeMillis(),
    )

    private val searchCache = ConcurrentHashMap<String, CacheEntry>()
    private val gson = Gson()
    private val client = NetworkUtils.HttpClient.searchClient

    suspend fun search(query: String, lang: String = "id", maxResults: Int = 4): WebSearchResult? =
        withContext(Dispatchers.IO) {
            val cleanQuery = query.trim()
                .removePrefix("/search").removePrefix("/Search")
                .removePrefix("/web").removePrefix("/Web")
                .trim()
            if (cleanQuery.isBlank()) return@withContext null

            val searchLang = if (lang.startsWith("en", ignoreCase = true)) "en" else "id"
            val cacheKey = "$searchLang:${cleanQuery.lowercase()}"

            // 1. Check in-memory TTL Cache (0 ms instant response)
            searchCache[cacheKey]?.let { entry ->
                if (System.currentTimeMillis() - entry.timestamp < CACHE_TTL_MS) {
                    KLog.d("WebSearch", "Cache HIT (0 ms) for query: $cleanQuery (lang=$searchLang)")
                    return@withContext entry.result
                } else {
                    searchCache.remove(cacheKey)
                }
            }

            KLog.d("WebSearch", "Executing fast parallel search for: $cleanQuery (lang=$searchLang)")

            // 2. Direct URL fetch if query is a web link
            if (cleanQuery.startsWith("http://") || cleanQuery.startsWith("https://")) {
                val direct = fetchUrlContent(cleanQuery)
                if (direct != null) {
                    val result = WebSearchResult(
                        query = cleanQuery,
                        items = listOf(direct),
                        promptContext = buildPromptContext(cleanQuery, listOf(direct), searchLang),
                    )
                    searchCache[cacheKey] = CacheEntry(result)
                    return@withContext result
                }
            }

            val combinedItems = mutableListOf<WebSearchItem>()

            // 3. Parallel Provider Execution via Coroutines (Google News RSS + Wikipedia)
            coroutineScope {
                val gNewsDeferred = async(Dispatchers.IO) {
                    runCatching { searchGoogleNewsRss(cleanQuery, searchLang, 3) }.getOrDefault(emptyList())
                }
                val wikiDeferred = async(Dispatchers.IO) {
                    runCatching { searchWikipedia(cleanQuery, searchLang, 2) }.getOrDefault(emptyList())
                }
                val ddgDeferred = async(Dispatchers.IO) {
                    runCatching { searchDuckDuckGoLite(cleanQuery, searchLang, 3) }.getOrDefault(emptyList())
                }

                val gNews = gNewsDeferred.await()
                val wiki = wikiDeferred.await()
                val ddg = ddgDeferred.await()

                // Wikipedia + News first (highest signal), then general web
                // results so non-news queries are covered too.
                combinedItems.addAll(wiki.take(2))
                combinedItems.addAll(gNews.take(2))
                combinedItems.addAll(ddg.take(2))

                // Cross-language fallback
                if (combinedItems.isEmpty()) {
                    val fallbackLang = if (searchLang == "id") "en" else "id"
                    val fallbackWiki = runCatching { searchWikipedia(cleanQuery, fallbackLang, 2) }.getOrDefault(emptyList())
                    combinedItems.addAll(fallbackWiki)
                }
            }

            if (combinedItems.isNotEmpty()) {
                // Harness-style: read the top pages and keep the passages that
                // actually answer the query, instead of title-only snippets.
                val finalItems = enrichTopResults(cleanQuery, combinedItems.distinctBy { it.url }.take(maxResults))
                val compressedPrompt = buildPromptContext(cleanQuery, finalItems, searchLang)
                val result = WebSearchResult(
                    query = cleanQuery,
                    items = finalItems,
                    promptContext = compressedPrompt,
                )
                searchCache[cacheKey] = CacheEntry(result)
                KLog.d("WebSearch", "Parallel search retrieved ${finalItems.size} results for: $cleanQuery (cached)")
                return@withContext result
            }

            KLog.w("WebSearch", "All search providers exhausted with no results for: $cleanQuery")
            null
        }

    private fun searchGoogleNewsRss(query: String, lang: String = "id", maxResults: Int = 4): List<WebSearchItem> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val hl = if (lang == "id") "id" else "en-US"
        val gl = if (lang == "id") "ID" else "US"
        val ceid = if (lang == "id") "ID:id" else "US:en"
        val url = "https://news.google.com/rss/search?q=$encoded&hl=$hl&gl=$gl&ceid=$ceid"

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .build()

        val xmlStr: String = client.newCall(request).execute().use { response ->
            if (response.isSuccessful) response.body?.string() else null
        } ?: return emptyList()

        val results = mutableListOf<WebSearchItem>()
        try {
            val itemPattern = Pattern.compile("<item>([\\s\\S]*?)<\\/item>", Pattern.CASE_INSENSITIVE)
            val titlePattern = Pattern.compile("<title>([\\s\\S]*?)<\\/title>", Pattern.CASE_INSENSITIVE)
            val linkPattern = Pattern.compile("<link>([\\s\\S]*?)<\\/link>", Pattern.CASE_INSENSITIVE)
            val pubDatePattern = Pattern.compile("<pubDate>([\\s\\S]*?)<\\/pubDate>", Pattern.CASE_INSENSITIVE)
            val descPattern = Pattern.compile("<description>([\\s\\S]*?)<\\/description>", Pattern.CASE_INSENSITIVE)

            val itemMatcher = itemPattern.matcher(xmlStr)
            while (itemMatcher.find() && results.size < maxResults) {
                val itemBlock = itemMatcher.group(1).orEmpty()
                val tMatcher = titlePattern.matcher(itemBlock)
                val lMatcher = linkPattern.matcher(itemBlock)
                val pMatcher = pubDatePattern.matcher(itemBlock)
                val dMatcher = descPattern.matcher(itemBlock)

                val rawTitle = if (tMatcher.find()) tMatcher.group(1).orEmpty() else ""
                val rawLink = if (lMatcher.find()) lMatcher.group(1).orEmpty() else ""
                val rawPubDate = if (pMatcher.find()) pMatcher.group(1).orEmpty() else ""
                val rawDesc = if (dMatcher.find()) dMatcher.group(1).orEmpty() else ""

                val cleanTitle = stripHtml(rawTitle).trim()
                val cleanSnippet = stripHtml(rawDesc).trim().ifBlank {
                    if (rawPubDate.isNotBlank()) "Published: $rawPubDate" else ""
                }

                if (cleanTitle.isNotBlank()) {
                    results.add(
                        WebSearchItem(
                            title = cleanTitle,
                            snippet = "$cleanTitle. $cleanSnippet".take(220),
                            url = rawLink.trim().ifBlank { "https://news.google.com" },
                        )
                    )
                }
            }
        } catch (e: Exception) {
            KLog.w("WebSearch", "Failed to parse Google News RSS: ${e.message}")
        }
        return results
    }

    private fun searchDuckDuckGoLite(query: String, lang: String = "id", maxResults: Int = 4): List<WebSearchItem> {
        val formBody = FormBody.Builder()
            .add("q", query)
            .build()

        val acceptLang = if (lang == "en") "en-US,en;q=0.9" else "id-ID,id;q=0.9,en-US;q=0.8,en;q=0.7"
        val request = Request.Builder()
            .url("https://lite.duckduckgo.com/lite/")
            .post(formBody)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .header("Accept-Language", acceptLang)
            .build()

        val html: String = client.newCall(request).execute().use { response ->
            if (response.isSuccessful) response.body?.string() else null
        } ?: return emptyList()

        val linkPattern = Pattern.compile(
            "<a[^>]*class=['\"]result-link['\"][^>]*href=['\"]([^'\"]+)['\"][^>]*>([\\s\\S]*?)<\\/a>|<a[^>]*href=['\"]([^'\"]+)['\"][^>]*class=['\"]result-link['\"][^>]*>([\\s\\S]*?)<\\/a>",
            Pattern.CASE_INSENSITIVE,
        )
        val snippetPattern = Pattern.compile(
            "<td[^>]*class=['\"]result-snippet['\"][^>]*>([\\s\\S]*?)<\\/td>",
            Pattern.CASE_INSENSITIVE,
        )

        // Collect link hits with their positions, then pair each link with
        // the snippet that follows it (before the next result). The old code
        // paired two independently collected lists by index, silently dropping
        // or mismatching results whenever the counts differed.
        class LinkHit(val url: String, val title: String, val start: Int, val end: Int)
        val hits = mutableListOf<LinkHit>()
        val linkMatcher = linkPattern.matcher(html)
        while (linkMatcher.find() && hits.size < maxResults) {
            val url = linkMatcher.group(1) ?: linkMatcher.group(3) ?: ""
            val rawTitle = linkMatcher.group(2) ?: linkMatcher.group(4) ?: ""
            val title = stripHtml(rawTitle).trim()
            if (url.startsWith("http") && title.isNotBlank()) {
                hits.add(LinkHit(url, title, linkMatcher.start(), linkMatcher.end()))
            }
        }

        val results = mutableListOf<WebSearchItem>()
        for ((i, hit) in hits.withIndex()) {
            val regionEnd = if (i + 1 < hits.size) hits[i + 1].start else html.length
            val snippetMatcher = snippetPattern.matcher(html)
            snippetMatcher.region(hit.end, regionEnd)
            val snippet = if (snippetMatcher.find()) {
                stripHtml(snippetMatcher.group(1).orEmpty()).trim()
            } else {
                ""
            }
            results.add(
                WebSearchItem(
                    title = hit.title,
                    // Keep the result even when the page omits a snippet.
                    snippet = snippet.ifBlank { hit.title }.take(450),
                    url = hit.url,
                )
            )
        }
        return results
    }

    private fun searchWikipedia(query: String, lang: String, maxResults: Int): List<WebSearchItem> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val searchUrl = "https://$lang.wikipedia.org/w/api.php?action=query&list=search&srsearch=$encoded&utf8=&format=json"
        val request = Request.Builder()
            .url(searchUrl)
            .header("User-Agent", USER_AGENT)
            .build()

        val jsonStr: String = client.newCall(request).execute().use { response ->
            if (response.isSuccessful) response.body?.string() else null
        } ?: return emptyList()

        val root = gson.fromJson(jsonStr, JsonObject::class.java)
        val queryObj = root?.getAsJsonObject("query") ?: return emptyList()
        val searchArr = queryObj.getAsJsonArray("search") ?: return emptyList()
        if (searchArr.isEmpty) return emptyList()

        val pageTitles = mutableListOf<String>()
        val snippetMap = mutableMapOf<String, String>()
        for (i in 0 until minOf(searchArr.size(), maxResults)) {
            val item = searchArr.get(i).asJsonObject
            val title = item.get("title")?.asString.orEmpty()
            val snippet = stripHtml(item.get("snippet")?.asString.orEmpty()).trim()
            if (title.isNotBlank()) {
                pageTitles.add(title)
                snippetMap[title] = snippet
            }
        }

        // Fetch authoritative full-paragraph intro extracts
        val extractsMap = mutableMapOf<String, String>()
        try {
            val titlesEncoded = pageTitles.joinToString("|") { URLEncoder.encode(it, "UTF-8") }
            val extractUrl = "https://$lang.wikipedia.org/w/api.php?action=query&prop=extracts&exintro=1&explaintext=1&titles=$titlesEncoded&format=json"
            val eReq = Request.Builder().url(extractUrl).header("User-Agent", USER_AGENT).build()
            val eJson: String = client.newCall(eReq).execute().use { eRes ->
                if (eRes.isSuccessful) eRes.body?.string() else null
            }.orEmpty()
            val eRoot = gson.fromJson(eJson, JsonObject::class.java)
            val pages = eRoot?.getAsJsonObject("query")?.getAsJsonObject("pages")
            pages?.keySet()?.forEach { pid ->
                val p = pages.getAsJsonObject(pid)
                val pTitle = p?.get("title")?.asString.orEmpty()
                val pExtract = p?.get("extract")?.asString.orEmpty().trim()
                if (pTitle.isNotBlank() && pExtract.isNotBlank()) {
                    extractsMap[pTitle] = pExtract.replace("\n", " ").trim()
                }
            }
        } catch (e: Exception) {
            KLog.e("WebSearch", "Failed to parse Wikipedia extracts", e)
        }

        val results = mutableListOf<WebSearchItem>()
        for (title in pageTitles) {
            val fullExtract = extractsMap[title]
            val snippet = fullExtract ?: snippetMap[title].orEmpty()
            if (snippet.isNotBlank()) {
                val pageUrl = "https://$lang.wikipedia.org/wiki/${URLEncoder.encode(title.replace(" ", "_"), "UTF-8")}"
                results.add(
                    WebSearchItem(
                        title = title,
                        snippet = snippet.take(280),
                        url = pageUrl,
                    )
                )
            }
        }
        return results
    }

    private fun fetchUrlContent(targetUrl: String): WebSearchItem? {
        // SSRF guard: user-supplied URL must be publicly routable before any
        // fetch (Jina attempt or direct). Throws on violation -> caught below.
        try {
            com.raikaai.app.util.NetworkUtils.requirePublicHttpUrl(targetUrl)
        } catch (e: IllegalArgumentException) {
            KLog.w("WebSearch", "Blocked non-public URL: ${e.message}")
            return null
        }
        // Attempt 1: Jina AI Reader
        try {
            val jinaUrl = "https://r.jina.ai/$targetUrl"
            val request = Request.Builder()
                .url(jinaUrl)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "text/plain")
                .build()
            val text: String = client.newCall(request).execute().use { response ->
                if (response.isSuccessful) response.body?.string() else null
            }.orEmpty().take(900)
            if (text.isNotBlank()) {
                return WebSearchItem(
                    title = "Web Page: $targetUrl",
                    snippet = text,
                    url = targetUrl,
                )
            }
        } catch (e: Exception) {
            KLog.e("WebSearch", "Jina AI Reader failed for $targetUrl", e)
        }

        // Attempt 2: Direct HTTP fetch with HTML stripping, manual
        // redirect hops re-validated per hop (SSRF-safe).
        try {
            val rawHtml: String =
                com.raikaai.app.util.NetworkUtils.fetchBodyStringValidated(
                    url = targetUrl,
                    headers = mapOf(
                        "User-Agent" to USER_AGENT,
                        "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
                    ),
                    maxChars = 60_000,
                ) ?: return null
            if (rawHtml.isNotBlank()) {
                val titleMatcher = Pattern.compile("<title>([\\s\\S]*?)</title>", Pattern.CASE_INSENSITIVE).matcher(rawHtml)
                val title = if (titleMatcher.find()) stripHtml(titleMatcher.group(1).orEmpty()) else targetUrl
                val bodyText = stripHtml(rawHtml).take(900)
                if (bodyText.isNotBlank()) {
                    return WebSearchItem(
                        title = title.ifBlank { targetUrl },
                        snippet = bodyText,
                        url = targetUrl,
                    )
                }
            }
        } catch (e: Exception) {
            KLog.e("WebSearch", "Direct HTTP fetch failed for $targetUrl", e)
        }

        return null
    }

    /**
     * Comprehensive HTML entity unescaping and whitespace cleaner.
     */
    fun stripHtml(html: String): String {
        var text = html.replace(Regex("<[^>]*>"), " ")
        text = text
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#039;", "'")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            .replace("&nbsp;", " ")
            .replace("&mdash;", "—")
            .replace("&ndash;", "–")
            .replace("&hellip;", "…")
            .replace("&copy;", "©")
            .replace("&reg;", "®")
            .replace("&trade;", "™")

        // Unescape numeric decimal entities (e.g. &#8217;)
        text = Regex("&#(\\d+);").replace(text) { match ->
            try {
                match.groupValues[1].toInt().toChar().toString()
            } catch (_: Exception) {
                match.value
            }
        }
        // Unescape numeric hex entities (e.g. &#x2019;)
        text = Regex("&#[xX]([0-9a-fA-F]+);").replace(text) { match ->
            try {
                match.groupValues[1].toInt(16).toChar().toString()
            } catch (_: Exception) {
                match.value
            }
        }

        return text
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    /**
     * Compresses and deduplicates search results to save 30%-50% context window tokens.
     */
    fun buildPromptContext(query: String, items: List<WebSearchItem>, lang: String = "en"): String {
        val sb = StringBuilder()
        val seenSentences = mutableSetOf<String>()
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())

        sb.append("\n\n[WEB SEARCH]\nQuery: \"$query\" · Today: $today\n\n")
        items.forEachIndexed { index, item ->
            val excerpt = deduplicateAndCompressSnippet(item.snippet, seenSentences)
            val host = runCatching { java.net.URI(item.url).host.orEmpty().removePrefix("www.") }.getOrDefault("")
            sb.append("[${index + 1}] ${item.title}").append(if (host.isNotBlank()) " ($host)" else "").append("\n")
            sb.append(excerpt).append("\n\n")
        }
        sb.append(
            """
            [HOW TO ANSWER]
            - Answer the question directly in the user's language. Start with the answer itself in one or two sentences.
            - Add only the details that matter (short bullets are fine). No introduction, no restating the question.
            - Cite inline as [1], [2] right after the fact it supports. Do not list the sources at the end; the app shows them.
            - Prefer the newest information. If the sources disagree or do not answer the question, say so in one sentence.
            - Never mention "search results" or "according to the sources".
            """.trimIndent(),
        )
        return sb.toString().trimEnd()
    }

    /**
     * Fetches the top results whose snippet is too thin (news RSS often has
     * only a headline) and replaces it with the page passages most relevant
     * to [query]. Direct, SSRF-checked fetch; each page has a short timeout
     * and failures keep the original snippet.
     */
    private suspend fun enrichTopResults(
        query: String,
        items: List<WebSearchItem>,
    ): List<WebSearchItem> =
        coroutineScope {
            items.mapIndexed { i, item ->
                async(Dispatchers.IO) {
                    if (i >= 3 || item.snippet.length >= 400) return@async item
                    val host = runCatching { java.net.URI(item.url).host.orEmpty() }.getOrDefault("")
                    // Google News links are JS redirects; Wikipedia already has a good extract.
                    if (host.contains("news.google.") || host.contains("wikipedia.org")) return@async item
                    val html =
                        kotlinx.coroutines.withTimeoutOrNull(5_000) {
                            runCatching {
                                NetworkUtils.fetchBodyStringValidated(
                                    url = item.url,
                                    headers = mapOf("User-Agent" to USER_AGENT, "Accept" to "text/html,application/xhtml+xml"),
                                    maxChars = 300_000,
                                )
                            }.getOrNull()
                        } ?: return@async item
                    val passage = bestPassages(query, stripHtml(html), 700)
                    if (passage.length > item.snippet.length) item.copy(snippet = passage) else item
                }
            }.map { it.await() }
        }

    private fun deduplicateAndCompressSnippet(snippet: String, seenSentences: MutableSet<String>): String {
        val sentences = snippet.split(Regex("(?<=[.!?])\\s+"))
        val kept = mutableListOf<String>()

        for (s in sentences) {
            val normalized = s.trim().lowercase()
            if (normalized.length < 15) continue
            // Check if similar sentence already recorded
            val isDuplicate = seenSentences.any { seen ->
                seen.contains(normalized) || normalized.contains(seen)
            }
            if (!isDuplicate) {
                seenSentences.add(normalized)
                kept.add(s.trim())
            }
        }

        val result = if (kept.isNotEmpty()) kept.joinToString(" ") else snippet.trim()
        return result.take(700)
    }

    fun clearCache() {
        searchCache.clear()
    }
}

/**
 * Picks the sentences of [text] that share the most terms with [query]
 * (kept in page order) up to [maxChars]. Pure function; unit-tested.
 */
internal fun bestPassages(
    query: String,
    text: String,
    maxChars: Int,
): String {
    val terms =
        query.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.length >= 3 }.toSet()
    val sentences =
        text.split(Regex("(?<=[.!?])\\s+|\\n+"))
            .map { it.trim() }
            .filter { it.length in 40..600 }
    if (sentences.isEmpty()) return ""
    val scored =
        sentences.mapIndexed { i, sentence ->
            val lower = sentence.lowercase()
            Triple(i, sentence, terms.count { lower.contains(it) })
        }
    val picked = mutableListOf<Triple<Int, String, Int>>()
    var used = 0
    for (c in scored.filter { it.third > 0 }.sortedByDescending { it.third }) {
        if (used + c.second.length > maxChars) continue
        picked.add(c)
        used += c.second.length + 1
    }
    if (picked.isEmpty()) return sentences.first().take(maxChars)
    return picked.sortedBy { it.first }.joinToString(" ") { it.second }
}
