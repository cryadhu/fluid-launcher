package com.example.launcher.data

enum class NewsCategory(val label: String, val section: String, val glyph: String) {
    WORLD("World", "world", "browser"),
    POLITICS("Politics", "politics", "news"),
    SPORTS("Sports", "sport", "game"),
    MOVIES("Movies", "film", "video"),
    MUSIC("Music", "music", "music"),
    TECH("Technology", "technology", "code"),
    BUSINESS("Business", "business", "wallet"),
    SCIENCE("Science", "science", "app"),
    LIFESTYLE("Lifestyle", "lifeandstyle", "health");

    val feedUrl get() = "https://www.theguardian.com/$section/rss"
    companion object {
        val all: Set<NewsCategory> = entries.toSet()
        fun fromKey(key: String): NewsCategory? = entries.firstOrNull { it.name == key }
    }
}

fun selectedStories(stories: List<Story>, selected: Set<NewsCategory>): List<Story> = stories.filter { NewsCategory.fromKey(it.category) in selected }
data class NewsBatch(val stories: List<Story>, val updated: Set<NewsCategory>, val failed: Set<NewsCategory>)

fun selectedTopStories(stories: List<Story>, selected: Set<NewsCategory>): List<Story> = selectedStories(stories, selected).take(20)
