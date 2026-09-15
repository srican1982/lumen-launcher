package com.lumen.launcher.search

import com.lumen.launcher.data.AppCategory
import com.lumen.launcher.data.AppInfo
import com.lumen.launcher.data.SpaceKind

data class TaskIntent(
    val id: String,
    val title: String,
    val subtitle: String,
    val keywords: List<String>,
    val packageHints: List<String>,
    val category: AppCategory? = null,
    val space: SpaceKind? = null
)

object IntentIndex {

    val all = listOf(
        TaskIntent(
            id = "food",
            title = "Get food",
            subtitle = "Order or find something to eat",
            keywords = listOf("food", "hungry", "eat", "lunch", "dinner", "breakfast", "i want food", "order food"),
            packageHints = listOf("doordash", "ubereats", "uber.eats", "grubhub", "swiggy", "zomato", "deliveroo", "starbucks"),
            category = AppCategory.Food
        ),
        TaskIntent(
            id = "pay",
            title = "Pay a bill",
            subtitle = "Banking and cards",
            keywords = listOf(
                "pay", "bill", "credit card", "i need to pay", "pay my credit",
                "banking", "banking apps", "bank apps", "finance apps", "finance folder"
            ),
            packageHints = listOf("chase", "bank", "bofa", "wellsfargo", "capitalone", "wallet"),
            category = AppCategory.Finance
        ),
        TaskIntent(
            id = "send_money",
            title = "Send money",
            subtitle = "PayPal, Zelle, wallets",
            keywords = listOf("send money", "zelle", "venmo", "transfer", "pay pal"),
            packageHints = listOf("paypal", "venmo", "cashapp", "wise", "zelle", "revolut"),
            category = AppCategory.Finance
        ),
        TaskIntent(
            id = "invest",
            title = "Check investments",
            subtitle = "Brokerage and markets",
            keywords = listOf("invest", "stocks", "fidelity", "portfolio"),
            packageHints = listOf("fidelity", "robinhood", "coinbase", "etrade", "vanguard"),
            category = AppCategory.Finance
        ),
        TaskIntent(
            id = "photo_edit",
            title = "Edit photos",
            subtitle = "Photo editors on this phone",
            keywords = listOf("edit photo", "editing photos", "photo editor", "retouch", "used yesterday for editing photos"),
            packageHints = listOf("snapseed", "lightroom", "picsart", "vsco", "photoshop", "photos", "gallery"),
            category = AppCategory.Entertainment
        ),
        TaskIntent(
            id = "video_edit",
            title = "Edit video",
            subtitle = "Video editors on this phone",
            keywords = listOf("edit video", "editing videos", "capcut", "premiere"),
            packageHints = listOf("capcut", "premiere", "vn.", "funimate", "inshot"),
            category = AppCategory.Entertainment
        ),
        TaskIntent(
            id = "scan",
            title = "Scan a document",
            subtitle = "Scanner and camera apps",
            keywords = listOf("scan", "scan document", "receipt", "scanning receipts"),
            packageHints = listOf("scan", "camscanner", "adobe", "genius", "drive", "notes"),
            category = AppCategory.Utilities
        ),
        TaskIntent(
            id = "work",
            title = "Work mode",
            subtitle = "Teams, mail, calendar, browser",
            keywords = listOf("work", "work mode", "office", "meeting"),
            packageHints = listOf("teams", "outlook", "slack", "zoom", "calendar", "gmail", "chrome", "acumatica"),
            category = AppCategory.Work,
            space = SpaceKind.Work
        ),
        TaskIntent(
            id = "listen",
            title = "Continue listening",
            subtitle = "Music and podcasts",
            keywords = listOf("music", "listen", "spotify", "podcast"),
            packageHints = listOf("spotify", "youtube", "music", "podcast", "audible"),
            category = AppCategory.Entertainment
        )
    )

    fun match(query: String): List<TaskIntent> {
        val q = FuzzySearch.normalize(query)
        if (q.length < 3) return emptyList()
        return all.filter { intent ->
            intent.keywords.any { key ->
                q.contains(key) || key.contains(q) || FuzzySearch.score(q, key) >= 700
            }
        }
    }

    fun appsFor(intent: TaskIntent, apps: List<AppInfo>, limit: Int = 4): List<AppInfo> {
        return apps
            .map { app ->
                val haystack = "${app.label} ${app.packageName}".lowercase()
                val hintScore = intent.packageHints.maxOfOrNull { hint ->
                    if (haystack.contains(hint)) 80 else 0
                } ?: 0
                val catScore = if (intent.category != null && app.category == intent.category) 30 else 0
                app to hintScore + catScore
            }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .map { it.first }
            .distinctBy { it.key }
            .take(limit)
    }
}
