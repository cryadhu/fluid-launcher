package com.example.launcher.data

import org.junit.Assert.*
import org.junit.Test

class FeedParsingTest {
    @Test fun searchIgnoresCaseAndOutsideWhitespace() {
        assertTrue(filterAppsLabel("Google Maps", " MAP "))
        assertFalse(filterAppsLabel("Calendar", "camera"))
        assertTrue(filterAppsLabel("Phone", ""))
    }
    @Test fun discussionStoriesUseTheirRealHackerNewsLink() {
        val stories = parseHackerNews("""{"hits":[{"title":"Ask HN: Question","objectID":"123","url":null,"created_at":"2026-10-23T10:00:00Z"}]}""")
        assertEquals("https://news.ycombinator.com/item?id=123", stories.single().url)
        assertEquals("2026-10-23", stories.single().published)
    }
    @Test fun unsafeSchemesNeverReachArticleLinks() {
        val stories = parseHackerNews("""{"hits":[{"title":"A headline","objectID":"456","url":"intent://untrusted"}]}""")
        assertEquals("https://news.ycombinator.com/item?id=456", stories.single().url)
    }
    @Test fun untitledStoriesAreDropped() {
        assertTrue(parseHackerNews("""{"hits":[{"title":"","objectID":"1","url":"https://example.com"}]}""").isEmpty())
    }
}
