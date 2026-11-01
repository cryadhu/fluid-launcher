package com.example.launcher.data

import android.content.Context
import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.net.HttpURLConnection
import java.net.URL
import java.io.File

data class Story(val title: String, val url: String, val source: String, val category: String, val published: String, val imageUrl: String? = null)
data class Rate(val pair: String, val value: Double, val date: String)
data class FeedData(val stories: List<Story> = emptyList(), val rates: List<Rate> = emptyList(), val fetchedAt: Long = 0, val error: String? = null)

interface NewsRepository { suspend fun topStories(categories: Set<NewsCategory>): NewsBatch }
interface MarketsRepository { suspend fun rates(): List<Rate> }

fun filterAppsLabel(label: String, query: String) = label.contains(query.trim(), ignoreCase = true)

internal fun getHttps(address: String): String {
    require(address.startsWith("https://"))
    val connection = URL(address).openConnection() as HttpURLConnection
    try {
        connection.connectTimeout = 12000
        connection.readTimeout = 12000
        connection.setRequestProperty("User-Agent", "cry/0.2 Android personal launcher")
        connection.setRequestProperty("Accept", "application/json, application/rss+xml, application/xml, text/xml")
        check(connection.responseCode in 200..299) { "Server returned ${connection.responseCode}" }
        return connection.inputStream.bufferedReader().use { it.readText() }
    } finally { connection.disconnect() }
}

class PublicNewsRepository : NewsRepository {
    override suspend fun topStories(categories: Set<NewsCategory>): NewsBatch = coroutineScope {
        val concurrency = Semaphore(3)
        val results = categories.sortedBy { it.ordinal }.map { category -> async(Dispatchers.IO) {
            concurrency.withPermit {
                val stories = try { parseRss(getHttps(category.feedUrl), category.name) }
                catch (error: Exception) {
                    if (error is CancellationException) throw error
                    if (category == NewsCategory.TECH) runCatching { parseHackerNews(getHttps("https://hn.algolia.com/api/v1/search?tags=front_page&hitsPerPage=8")) }.getOrDefault(emptyList()) else emptyList()
                }
                category to stories.take(8)
            }
        } }.awaitAll()
        val interleaved = (0 until 8).flatMap { index -> results.mapNotNull { it.second.getOrNull(index) } }.distinctBy { it.url }
        NewsBatch(interleaved, results.filter { it.second.isNotEmpty() }.map { it.first }.toSet(), results.filter { it.second.isEmpty() }.map { it.first }.toSet())
    }
}

internal fun parseHackerNews(raw: String): List<Story> {
    val hits = JSONObject(raw).getJSONArray("hits")
    return (0 until hits.length()).mapNotNull { i ->
        val item = hits.getJSONObject(i)
        val title = item.optString("title")
        val target = item.optString("url").takeIf { it.startsWith("https://") || it.startsWith("http://") }
            ?: "https://news.ycombinator.com/item?id=${item.getString("objectID")}" 
        if (title.isBlank()) null else Story(title, target, "Hacker News", "TECH", item.optString("created_at").take(10))
    }
}

internal fun parseRss(raw: String, category: String = "WORLD"): List<Story> {
    val parser = Xml.newPullParser()
    parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
    parser.setInput(raw.reader())
    val stories = mutableListOf<Story>()
    var inItem = false
    var title = ""; var link = ""; var date = ""; var image: String? = null
    while (parser.eventType != XmlPullParser.END_DOCUMENT) {
        if (parser.eventType == XmlPullParser.START_TAG) {
            if (parser.name == "item") { inItem = true; title = ""; link = ""; date = ""; image = null }
            else if (inItem) when (parser.name) {
                "media:content", "media:thumbnail", "enclosure" -> parser.getAttributeValue(null, "url")?.takeIf { it.startsWith("https://") && (parser.name != "enclosure" || parser.getAttributeValue(null, "type").orEmpty().startsWith("image/")) }?.let { image = it }
                "title" -> title = parser.nextText()
                "link" -> link = parser.nextText()
                "pubDate" -> date = parser.nextText()
            }
        } else if (parser.eventType == XmlPullParser.END_TAG && parser.name == "item") {
            if (title.isNotBlank() && link.startsWith("https://")) stories += Story(title, link, "The Guardian", category, date.take(22), image)
            inItem = false
        }
        parser.next()
    }
    return stories
}

class FrankfurterRepository : MarketsRepository {
    override suspend fun rates(): List<Rate> = withContext(Dispatchers.IO) {
        val json = JSONArray(getHttps("https://api.frankfurter.dev/v2/rates?base=USD&quotes=INR,EUR,GBP"))
        (0 until json.length()).map { i -> json.getJSONObject(i).let { Rate("USD / ${it.getString("quote")}", it.getDouble("rate"), it.getString("date")) } }
    }
}

class FeedRepository(context: Context) {
    private val cache = File(context.filesDir, "public_feed.json")
    suspend fun cached(): FeedData = withContext(Dispatchers.IO) {
        runCatching {
            val json = JSONObject(cache.readText())
            val stories = json.getJSONArray("stories")
            val rates = json.getJSONArray("rates")
            FeedData(
                (0 until stories.length()).map { stories.getJSONObject(it).let { s -> Story(s.getString("title"), s.getString("url"), s.getString("source"), s.getString("category"), s.getString("published"), s.optString("imageUrl").takeIf { it.startsWith("https://") }) } },
                (0 until rates.length()).map { rates.getJSONObject(it).let { r -> Rate(r.getString("pair"), r.getDouble("value"), r.getString("date")) } },
                json.getLong("fetchedAt")
            )
        }.getOrDefault(FeedData())
    }
    suspend fun refresh(old: FeedData, categories: Set<NewsCategory>, refreshRates: Boolean): FeedData = coroutineScope {
        val news = async { PublicNewsRepository().topStories(categories) }
        val money = async { if (refreshRates) runCatching { FrankfurterRepository().rates() } else Result.success(old.rates) }
        val n = news.await(); val m = money.await()
        val errors = listOfNotNull(if (n.failed.isNotEmpty()) "Couldn’t update ${n.failed.sortedBy { it.ordinal }.joinToString { it.label }}" else null, if (m.isFailure) "Exchange rates could not update" else null)
        val retained = old.stories.filter { NewsCategory.fromKey(it.category) !in n.updated }
        val data = FeedData((n.stories + retained).distinctBy { it.url }, m.getOrDefault(old.rates), if (n.updated.isNotEmpty() || refreshRates && m.isSuccess) System.currentTimeMillis() else old.fetchedAt, errors.takeIf { it.isNotEmpty() }?.joinToString(" · "))
        withContext(Dispatchers.IO) {
            val json = JSONObject().put("fetchedAt", data.fetchedAt)
                .put("stories", JSONArray(data.stories.map { JSONObject().put("title", it.title).put("url", it.url).put("source", it.source).put("category", it.category).put("published", it.published).put("imageUrl", it.imageUrl) }))
                .put("rates", JSONArray(data.rates.map { JSONObject().put("pair", it.pair).put("value", it.value).put("date", it.date) }))
            val temp = File(cache.parentFile, "public_feed.tmp")
            temp.writeText(json.toString())
            check(temp.renameTo(cache)) { "Cannot cache feed" }
        }
        data
    }
}
