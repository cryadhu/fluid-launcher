package com.example.launcher.data

import org.junit.Assert.*
import org.junit.Test

class NewsSelectionTest {
    @Test fun topStoriesCapsAfterFilteringWithoutChangingOrder() {
        val input = (1..50).map { Story("Story $it", "https://example.com/$it", "Source", if (it % 2 == 0) "SPORTS" else "MUSIC", "") }
        val result = selectedTopStories(input, setOf(NewsCategory.SPORTS))
        assertEquals(20, result.size)
        assertEquals((2..40 step 2).map { "Story $it" }, result.map { it.title })
        assertTrue(selectedTopStories(input, emptySet()).isEmpty())
    }
    private val stories = NewsCategory.entries.map { Story(it.label, "https://example.com/${it.name}", "Source", it.name, "") }
    @Test fun defaultsIncludeEveryTopic() { assertEquals(stories, selectedStories(stories, NewsCategory.all)) }
    @Test fun selectedTopicsExcludeOtherHeadlines() {
        assertEquals(listOf("SPORTS", "MOVIES"), selectedStories(stories, setOf(NewsCategory.SPORTS, NewsCategory.MOVIES)).map { it.category })
    }
    @Test fun clearingTopicsDoesNotFallBackToAll() { assertTrue(selectedStories(stories, emptySet()).isEmpty()) }
    @Test fun unknownCachedTopicsDoNotLeakIntoSelection() {
        assertTrue(selectedStories(listOf(Story("Old", "https://example.com", "", "UNKNOWN", "")), NewsCategory.all).isEmpty())
    }
}
