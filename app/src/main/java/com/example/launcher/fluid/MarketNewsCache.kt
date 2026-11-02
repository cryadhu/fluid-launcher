package com.example.launcher.fluid

import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

internal const val MARKET_NEWS_TTL = 10 * 60 * 1000L
internal fun marketNewsFresh(savedAt: Long, now: Long) = now >= savedAt && now - savedAt < MARKET_NEWS_TTL

/** Persistent response cache, including a retry cooldown and last-good offline data. */
internal class MarketNewsCache(private val directory: File, private val now: () -> Long = System::currentTimeMillis) {
    private val locks = java.util.concurrent.ConcurrentHashMap<String, Any>()
    fun get(address: String, fetch: (String) -> String): String = get(address,fetch,false)
    fun get(address: String, fetch: (String) -> String, force: Boolean): String = synchronized(locks.getOrPut(address) { Any() }) {
        directory.mkdirs()
        val key = MessageDigest.getInstance("SHA-256").digest(address.toByteArray()).joinToString("") { "%02x".format(it) }
        val file = File(directory, "$key.json")
        val old = runCatching { JSONObject(file.readText()) }.getOrNull()
        val data = old?.optString("data")?.takeIf { it.isNotEmpty() }
        val time = now()
        if (!force && old != null && marketNewsFresh(old.optLong("attempt"), time)) return@synchronized data ?: error("Waiting for the next update")
        try {
            val result = fetch(address)
            file.writeText(JSONObject().put("attempt", time).put("data", result).toString())
            result
        } catch (e: Exception) {
            if(e is java.util.concurrent.CancellationException) throw e
            file.writeText(JSONObject().put("attempt", time).put("data", data.orEmpty()).toString())
            data ?: throw e
        }
    }
}
