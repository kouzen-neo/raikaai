package com.raikaai.app.web

import android.content.Context
import com.raikaai.app.util.KLog
import com.raikaai.app.util.NetworkUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.util.regex.Pattern

/**
 * Fetches a web page and extracts readable text for summarization.
 * Strategy: Jina AI Reader first (good article extraction), then direct
 * HTTP fetch with HTML stripping as fallback.
 */
object UrlSummarizer {
    data class PageContent(
        val title: String,
        val text: String,
        val url: String,
    )

    const val MAX_CHARS = 24_000

    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"

    /** Normalizes user input into a fetchable https URL, or null if invalid. */
    fun normalizeUrl(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return null
        val withScheme =
            when {
                trimmed.startsWith("http://", ignoreCase = true) -> trimmed
                trimmed.startsWith("https://", ignoreCase = true) -> trimmed
                else -> "https://$trimmed"
            }
        return try {
            val uri = android.net.Uri.parse(withScheme)
            val host = uri.host?.takeIf { it.contains('.') } ?: return null
            if (host.equals("localhost", ignoreCase = true)) return null
            withScheme
        } catch (_: Exception) {
            null
        }
    }

    suspend fun fetchReadableText(context: Context, rawUrl: String): PageContent? =
        withContext(Dispatchers.IO) {
            val url = normalizeUrl(rawUrl) ?: return@withContext null
            // SSRF guard: refuse non-public hosts before ANY external fetch (including Jina Reader).
            try {
                NetworkUtils.requirePublicHttpUrl(url)
            } catch (e: IllegalArgumentException) {
                KLog.w("UrlSummarizer", "Blocked non-public URL: ${e.message}")
                return@withContext null
            }
            val stripper = WebSearchManager.getInstance(context)
            // Attempt 1: Jina AI Reader
            try {
                val jinaUrl = "https://r.jina.ai/$url"
                val request =
                    Request.Builder()
                        .url(jinaUrl)
                        .header("User-Agent", USER_AGENT)
                        .header("Accept", "text/plain")
                        .build()
                val text: String =
                    NetworkUtils.HttpClient.searchClient.newCall(request).execute().use { response ->
                        if (response.isSuccessful) NetworkUtils.readBodyBounded(response.body, MAX_CHARS) else null
                    }.orEmpty()
                if (text.isNotBlank() && text.length > 200) {
                    return@withContext PageContent(title = url, text = text.trim(), url = url)
                }
            } catch (e: Exception) {
                KLog.e("UrlSummarizer", "Jina Reader failed for $url", e)
            }
            // Attempt 2: direct fetch + HTML strip, manual redirect hops
            // re-validated per hop (SSRF-safe).
            try {
                val rawHtml: String =
                    NetworkUtils.fetchBodyStringValidated(
                        url = url,
                        headers = mapOf(
                            "User-Agent" to USER_AGENT,
                            "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
                        ),
                        maxChars = MAX_CHARS + 8_000,
                    ) ?: return@withContext null
                if (rawHtml.isNotBlank()) {
                    val titleMatcher =
                        Pattern.compile("<title>([\\s\\S]*?)</title>", Pattern.CASE_INSENSITIVE)
                            .matcher(rawHtml)
                    val title =
                        if (titleMatcher.find()) stripper.stripHtml(titleMatcher.group(1).orEmpty()) else url
                    val bodyText = stripper.stripHtml(rawHtml).take(MAX_CHARS).trim()
                    if (bodyText.length > 200) {
                        return@withContext PageContent(title.ifBlank { url }, bodyText, url)
                    }
                }
            } catch (e: Exception) {
                KLog.e("UrlSummarizer", "Direct fetch failed for $url", e)
            }
            null
        }
}
