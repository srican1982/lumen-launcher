package com.lumen.launcher.data

import android.content.pm.ApplicationInfo

object AppCategorizer {

    private val social = listOf(
        "whatsapp", "telegram", "instagram", "facebook", "messenger", "twitter",
        "tiktok", "snapchat", "discord", "reddit", "signal", "imo", "viber", "line"
    )
    private val work = listOf(
        "gmail", "outlook", "office", "teams", "slack", "zoom", "meet", "docs",
        "sheets", "notion", "trello", "jira", "calendar", "mail", "linkedin"
    )
    private val entertainment = listOf(
        "youtube", "netflix", "spotify", "twitch", "primevideo", "disney",
        "hulu", "kindle", "podcast", "music", "plex", "vlc"
    )
    private val finance = listOf(
        "bank", "wallet", "paypal", "venmo", "cashapp", "wise", "revolut",
        "chase", "wellsfargo", "capitalone", "coinbase", "binance", "pay"
    )
    private val food = listOf(
        "uber.eats", "ubereats", "doordash", "grubhub", "postmates", "deliveroo",
        "swiggy", "zomato", "starbucks", "mcdonalds", "chipotle"
    )
    private val shopping = listOf(
        "amazon", "ebay", "etsy", "walmart", "target", "shopify", "aliexpress",
        "bestbuy", "costco", "ikea"
    )
    private val travel = listOf(
        "maps", "uber", "lyft", "airbnb", "booking", "expedia", "airline",
        "delta", "united", "tripadvisor", "waze"
    )
    private val utilities = listOf(
        "settings", "clock", "calculator", "files", "filemanager", "gallery",
        "photos", "camera", "weather", "flashlight", "keep", "notes", "drive"
    )

    fun categorize(packageName: String, label: String, category: Int): AppCategory {
        when (category) {
            ApplicationInfo.CATEGORY_SOCIAL -> return AppCategory.Social
            ApplicationInfo.CATEGORY_PRODUCTIVITY -> return AppCategory.Work
            ApplicationInfo.CATEGORY_GAME -> return AppCategory.Games
            ApplicationInfo.CATEGORY_VIDEO,
            ApplicationInfo.CATEGORY_AUDIO,
            ApplicationInfo.CATEGORY_IMAGE -> return AppCategory.Entertainment
            ApplicationInfo.CATEGORY_MAPS -> return AppCategory.Travel
            ApplicationInfo.CATEGORY_NEWS -> return AppCategory.Utilities
        }
        val haystack = "$packageName $label".lowercase()
        return when {
            matches(haystack, social) -> AppCategory.Social
            matches(haystack, work) -> AppCategory.Work
            matches(haystack, entertainment) -> AppCategory.Entertainment
            matches(haystack, finance) -> AppCategory.Finance
            matches(haystack, food) -> AppCategory.Food
            matches(haystack, shopping) -> AppCategory.Shopping
            matches(haystack, travel) -> AppCategory.Travel
            matches(haystack, utilities) -> AppCategory.Utilities
            else -> AppCategory.Utilities
        }
    }

    private fun matches(haystack: String, needles: List<String>): Boolean {
        return needles.any { haystack.contains(it) }
    }
}
