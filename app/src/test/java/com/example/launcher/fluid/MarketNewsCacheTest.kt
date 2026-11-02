package com.example.launcher.fluid

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class MarketNewsCacheTest {
    @get:Rule val temp = TemporaryFolder()
    @Test fun persistsAcrossRecreationAndRefreshesAtTenMinutes() {
        var time = 1000L
        var calls = 0
        val fetch: (String) -> String = { "response-${++calls}" }
        val directory = temp.newFolder()
        assertEquals("response-1", MarketNewsCache(directory) { time }.get("stocks/1d", fetch))
        time += MARKET_NEWS_TTL - 1
        assertEquals("response-1", MarketNewsCache(directory) { time }.get("stocks/1d", fetch))
        assertEquals(1, calls)
        time++
        assertEquals("response-2", MarketNewsCache(directory) { time }.get("stocks/1d", fetch))
        assertEquals("response-3", MarketNewsCache(directory) { time }.get("stocks/1mo", fetch))
    }
    @Test fun failedRefreshPreservesSavedDataAndThrottlesRetries() {
        var time = 1000L
        var failures = 0
        val directory = temp.newFolder()
        val cache = MarketNewsCache(directory) { time }
        cache.get("news/sports") { "saved headlines" }
        time += MARKET_NEWS_TTL
        val fail: (String) -> String = { failures++; error("Offline") }
        assertEquals("saved headlines", cache.get("news/sports", fail))
        assertEquals("saved headlines", MarketNewsCache(directory) { time }.get("news/sports", fail))
        assertEquals(1, failures)
        assertFalse(marketNewsFresh(time, time - 1))
    }
    @Test fun manualRefreshBypassesFreshCacheAndUpdatesSavedData() {
        val directory=temp.newFolder()
        val cache=MarketNewsCache(directory) { 1000L }
        assertEquals("first",cache.get("news") { "first" })
        assertEquals("latest",cache.get("news", { "latest" },force=true))
        assertEquals("latest",MarketNewsCache(directory) { 1001L }.get("news") { error("Should use saved response") })
    }
}
