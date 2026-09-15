package com.lumen.launcher.data

import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.net.HttpURLConnection
import java.net.URL

class NewsRepository {

    suspend fun load(topics: Collection<NewsTopic>): List<NewsItem> = withContext(Dispatchers.IO) {
        if (topics.isEmpty()) return@withContext emptyList()
        coroutineScope {
            topics.map { topic -> async { loadTopic(topic) } }.awaitAll().flatten()
        }
            .distinctBy { it.url.ifBlank { it.title.lowercase() } }
            .sortedByDescending { it.imageUrl.isNotBlank() }
            .take(40)
    }

    private suspend fun loadTopic(topic: NewsTopic): List<NewsItem> = coroutineScope {
        val batches = topic.feeds.take(2).map { url ->
            async {
                val xml = get(url) ?: return@async emptyList()
                parse(xml, topic)
            }
        }
        batches.awaitAll().flatten()
    }

    private fun get(url: String): String? {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 7000
            readTimeout = 7000
            requestMethod = "GET"
            instanceFollowRedirects = true
            setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/124.0.0.0 Mobile Safari/537.36 Lumen/1.0"
            )
            setRequestProperty("Accept", "application/rss+xml, application/atom+xml, application/xml, text/xml, */*")
        }
        return try {
            if (connection.responseCode !in 200..299) null
            else connection.inputStream.bufferedReader().use { it.readText() }
        } catch (_: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }

    private fun parse(xml: String, topic: NewsTopic): List<NewsItem> {
        val parser = Xml.newPullParser()
        parser.setInput(xml.reader())
        val items = mutableListOf<NewsItem>()
        var event = parser.eventType
        var inEntry = false
        var title = ""
        var link = ""
        var source = ""
        var published = ""
        var image = ""
        var description = ""
        while (event != XmlPullParser.END_DOCUMENT) {
            val name = parser.name?.substringAfter(':')?.lowercase().orEmpty()
            when (event) {
                XmlPullParser.START_TAG -> when (name) {
                    "item", "entry" -> {
                        inEntry = true
                        title = ""
                        link = ""
                        source = ""
                        published = ""
                        image = ""
                        description = ""
                    }
                    "title" -> if (inEntry) title = text(parser)
                    "link" -> if (inEntry) {
                        val href = parser.getAttributeValue(null, "href").orEmpty()
                        val body = text(parser)
                        link = body.ifBlank { href }
                    }
                    "source" -> if (inEntry) source = text(parser)
                    "pubdate", "published", "updated", "date" -> if (inEntry && published.isBlank()) published = text(parser)
                    "description", "summary", "encoded" -> if (inEntry) {
                        val html = text(parser)
                        description = html
                        if (image.isBlank()) image = firstImage(html)
                    }
                    "content", "thumbnail" -> if (inEntry) {
                        val url = parser.getAttributeValue(null, "url")
                            ?: parser.getAttributeValue(null, "href")
                        val medium = parser.getAttributeValue(null, "medium").orEmpty()
                        val type = parser.getAttributeValue(null, "type").orEmpty()
                        val looksImage = !url.isNullOrBlank() && (
                            name == "thumbnail" ||
                                medium == "image" ||
                                type.startsWith("image") ||
                                url.orEmpty().hasImageExt()
                            )
                        if (looksImage && image.isBlank()) {
                            image = url.orEmpty()
                        } else if (name == "content") {
                            val html = text(parser)
                            if (html.isNotBlank()) {
                                description = html
                                if (image.isBlank()) image = firstImage(html)
                            }
                        }
                    }
                    "enclosure" -> if (inEntry && image.isBlank()) {
                        val url = parser.getAttributeValue(null, "url").orEmpty()
                        val type = parser.getAttributeValue(null, "type").orEmpty()
                        if (url.isNotBlank() && (type.startsWith("image") || url.hasImageExt())) image = url
                    }
                    "image", "icon" -> if (inEntry && image.isBlank()) {
                        val url = parser.getAttributeValue(null, "href")
                            ?: parser.getAttributeValue(null, "url")
                        if (!url.isNullOrBlank()) image = url else {
                            val body = text(parser)
                            if (body.startsWith("http")) image = body
                        }
                    }
                }
                XmlPullParser.END_TAG -> if ((name == "item" || name == "entry") && inEntry) {
                    inEntry = false
                    val cleanTitle = strip(title).substringBeforeLast(" - ").ifBlank { strip(title) }
                    val inferredSource = source.ifBlank {
                        strip(title).substringAfterLast(" - ", "").ifBlank { topic.title }
                    }
                    if (image.isBlank()) image = firstImage(description)
                    if (cleanTitle.isNotBlank() && link.isNotBlank()) {
                        items += NewsItem(
                            title = cleanTitle,
                            source = strip(inferredSource),
                            url = link.trim(),
                            published = published.trim(),
                            imageUrl = normalizeImage(image),
                            topic = topic
                        )
                    }
                }
            }
            event = parser.next()
        }
        return items
    }

    private fun text(parser: XmlPullParser): String {
        return runCatching { parser.nextText().trim() }.getOrDefault("")
    }

    private fun firstImage(html: String): String {
        if (html.isBlank()) return ""
        val match = IMG.find(html) ?: return ""
        return unescape(match.groupValues[1])
    }

    private fun strip(raw: String): String = TAGS.replace(unescape(raw), "").trim()

    private fun unescape(raw: String): String = raw
        .replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&nbsp;", " ")

    private fun normalizeImage(url: String): String {
        val trimmed = url.trim()
        if (trimmed.isBlank()) return ""
        val https = when {
            trimmed.startsWith("//") -> "https:$trimmed"
            trimmed.startsWith("http://") -> "https://${trimmed.removePrefix("http://")}"
            else -> trimmed
        }
        return unescape(https)
    }

    private fun String.hasImageExt(): Boolean {
        val lower = lowercase()
        return listOf(".jpg", ".jpeg", ".png", ".webp", ".gif").any { lower.contains(it) }
    }

    private companion object {
        val IMG = Regex("""<img[^>]+src\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
        val TAGS = Regex("<[^>]+>")
    }
}
