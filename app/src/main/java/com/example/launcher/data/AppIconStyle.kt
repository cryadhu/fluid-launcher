package com.example.launcher.data

enum class AppAccent { AMBER, MINT, CORAL, SKY }
enum class AppCategory { GAME, AUDIO, VIDEO, IMAGE, SOCIAL, NEWS, MAPS, PRODUCTIVITY, OTHER }
data class AppIconStyle(val glyph: String, val accent: AppAccent)

/** Bundled utility glyphs and fallback artwork when an installed icon cannot be loaded. */
fun appIconStyle(packageName: String, label: String, category: AppCategory = AppCategory.OTHER): AppIconStyle {
    val packageId = packageName.lowercase(java.util.Locale.ROOT)
    val words = Regex("[\\p{L}\\p{N}]+").findAll("$packageId ${label.lowercase(java.util.Locale.ROOT)}").map { it.value }.toSet()
    fun matches(vararg names: String) = names.any { if ('.' in it) packageId.contains(it) else it in words }
    fun icon(name: String, accent: AppAccent) = AppIconStyle(name, accent)
    return when {
        matches("settings", "systemui", "preferences") -> icon("settings", AppAccent.SKY)
        matches("calendar", "agenda", "calendars") -> icon("calendar", AppAccent.AMBER)
        matches("clock", "deskclock", "alarm", "stopwatch", "timer") -> icon("clock", AppAccent.MINT)
        matches("camera", "opencamera", "gcam") -> icon("camera", AppAccent.CORAL)
        matches("photos", "photo", "gallery", "gallery3d", "pictures", "snapseed", "lightroom", "image") -> icon("photos", AppAccent.AMBER)
        matches("com.google.android.gm", "gmail", "email", "mail", "outlook", "protonmail", "k9") -> icon("mail", AppAccent.SKY)
        matches("contacts", "contact", "people", "addressbook") -> icon("contacts", AppAccent.MINT)
        matches("dialer", "phone", "dialerapp", "truecaller", "telecom") -> icon("phone", AppAccent.MINT)
        matches("messages", "messaging", "sms", "mms", "securesms", "signal", "whatsapp", "telegram", "messenger", "slack", "discord", "teams") -> icon("messages", AppAccent.AMBER)
        matches("chrome", "browser", "firefox", "fenix", "brave", "opera", "edge", "vivaldi", "duckduckgo", "sbrowser") -> icon("browser", AppAccent.SKY)
        matches("maps", "map", "navigation", "waze", "osmand", "citymapper") -> icon("maps", AppAccent.CORAL)
        matches("music", "spotify", "audio", "podcast", "podcasts", "audible", "soundcloud", "ytmusic") -> icon("music", AppAccent.MINT)
        matches("youtube", "video", "videos", "netflix", "vlc", "primevideo", "disney", "hotstar", "twitch") -> icon("video", AppAccent.CORAL)
        matches("keep", "notes", "note", "notion", "obsidian", "evernote", "joplin", "onenote") -> icon("notes", AppAccent.CORAL)
        matches("tasks", "task", "todo", "todoist", "ticktick", "reminders", "trello", "asana") -> icon("tasks", AppAccent.AMBER)
        matches("drive", "dropbox", "onedrive", "cloud", "nextcloud") -> icon("cloud", AppAccent.SKY)
        matches("files", "file", "documentsui", "filemanager", "downloads", "explorer", "solidexplorer") -> icon("files", AppAccent.AMBER)
        matches("wallet", "pay", "gpay", "paypal", "phonepe", "paytm", "bank", "banking", "finance", "money", "bankofamerica", "hdfc", "icici", "sbi", "axis") -> icon("wallet", AppAccent.MINT)
        matches("calculator", "calc", "calculator2") -> icon("calculator", AppAccent.SKY)
        matches("weather", "forecast", "accuweather") -> icon("weather", AppAccent.SKY)
        matches("health", "fit", "fitness", "strava", "workout", "garmin", "fitbit", "samsunghealth") -> icon("health", AppAccent.CORAL)
        matches("book", "books", "kindle", "reader", "readera", "kobo") -> icon("book", AppAccent.AMBER)
        matches("news", "bbc", "guardian", "reuters", "economist", "feedly", "inoreader") -> icon("news", AppAccent.SKY)
        matches("instagram", "facebook", "twitter", "reddit", "linkedin", "threads", "snapchat", "mastodon", "bluesky") -> icon("social", AppAccent.CORAL)
        matches("store", "vending", "market", "shopping", "amazon", "flipkart", "myntra", "ebay", "etsy") -> icon("shopping", AppAccent.AMBER)
        matches("uber", "ola", "lyft", "transport", "transit", "rail", "train", "irctc") -> icon("transport", AppAccent.MINT)
        matches("travel", "booking", "airbnb", "trip", "trips", "flights", "expedia") -> icon("travel", AppAccent.SKY)
        matches("food", "zomato", "swiggy", "doordash", "ubereats", "restaurant") -> icon("food", AppAccent.CORAL)
        matches("game", "games", "gaming", "steam", "xbox", "playstation") -> icon("game", AppAccent.CORAL)
        matches("security", "authenticator", "password", "bitwarden", "1password", "lastpass", "vpn", "protonvpn") -> icon("shield", AppAccent.MINT)
        matches("translate", "translation", "language", "duolingo") -> icon("translate", AppAccent.MINT)
        matches("docs", "sheets", "slides", "word", "excel", "powerpoint", "office", "acrobat", "pdf") -> icon("document", AppAccent.SKY)
        matches("termux", "terminal", "code", "github", "gitlab", "developer") -> icon("code", AppAccent.MINT)
        matches("voicesearch", "voice", "assistant", "googlequicksearchbox") -> icon("mic", AppAccent.AMBER)
        else -> when (category) {
            AppCategory.GAME -> icon("game", AppAccent.CORAL)
            AppCategory.AUDIO -> icon("music", AppAccent.MINT)
            AppCategory.VIDEO -> icon("video", AppAccent.CORAL)
            AppCategory.IMAGE -> icon("photos", AppAccent.AMBER)
            AppCategory.SOCIAL -> icon("social", AppAccent.CORAL)
            AppCategory.NEWS -> icon("news", AppAccent.SKY)
            AppCategory.MAPS -> icon("maps", AppAccent.CORAL)
            AppCategory.PRODUCTIVITY -> icon("notes", AppAccent.MINT)
            AppCategory.OTHER -> icon("app", AppAccent.entries[Math.floorMod(packageId.hashCode(), AppAccent.entries.size)])
        }
    }
}
