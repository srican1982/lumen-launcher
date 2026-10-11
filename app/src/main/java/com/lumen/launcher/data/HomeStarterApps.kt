package com.lumen.launcher.data

object HomeStarterApps {
    private val groups = listOf(
        listOf("com.whatsapp", "org.telegram.messenger", "com.facebook.orca", "com.google.android.apps.messaging"),
        listOf("com.google.android.gm", "com.microsoft.office.outlook", "com.samsung.android.email.provider"),
        listOf("com.google.android.apps.maps", "com.waze"),
        listOf("com.android.chrome", "org.mozilla.firefox", "com.sec.android.app.sbrowser"),
        listOf("com.spotify.music", "com.google.android.apps.youtube.music", "com.apple.android.music"),
        listOf("com.google.android.youtube"),
        listOf("com.google.android.GoogleCamera", "com.sec.android.app.camera", "com.android.camera"),
        listOf("com.google.android.calendar", "com.samsung.android.calendar"),
        listOf("com.google.android.apps.photos", "com.sec.android.gallery3d"),
        listOf("com.amazon.mShop.android.shopping", "com.ebay.mobile", "com.walmart.android"),
        listOf("com.chase.sig.android", "com.infonow.bofa", "com.wf.wellsfargomobile", "com.citi.citimobile", "com.konylabs.capitalone", "com.usbank.mobilebanking"),
        listOf("com.instagram.android", "com.zhiliaoapp.musically", "com.ss.android.ugc.trill", "com.snapchat.android", "com.twitter.android", "com.facebook.katana")
    )
    fun picks(apps: List<AppInfo>, dock: Set<String>): List<AppInfo> {
        val eligible = apps.filterNot { it.packageName in dock }
        val primary = groups.mapNotNull { group -> group.firstNotNullOfOrNull { pkg -> eligible.find { it.packageName == pkg } } }
        return primary.distinctBy { it.key }.take(12)
    }

    /** Cover different purposes first, then fill from actual usage with at most two per purpose. */
    fun fromUsage(ranked: List<AppInfo>, dock: Set<String>): List<AppInfo> {
        val eligible = ranked.filterNot { it.packageName in dock }.distinctBy { it.packageName }
        val primary = eligible.distinctBy(::purpose).take(12)
        val counts = primary.groupingBy(::purpose).eachCount().toMutableMap()
        val result = primary.toMutableList()
        for (app in eligible) {
            if (result.size == 12) break
            val group = purpose(app)
            if (result.none { it.key == app.key } && (counts[group] ?: 0) < 2) {
                result.add(app)
                counts[group] = (counts[group] ?: 0) + 1
            }
        }
        return result
    }

    private fun purpose(app: AppInfo): String {
        val known = groups.indexOfFirst { app.packageName in it }
        return when {
            known >= 0 -> "purpose:$known"
            Regex("browser|chrome|firefox|opera|edge", RegexOption.IGNORE_CASE).containsMatchIn(app.label) -> "purpose:3"
            Regex("email|mail|outlook", RegexOption.IGNORE_CASE).containsMatchIn(app.label) -> "purpose:1"
            Regex("music|spotify", RegexOption.IGNORE_CASE).containsMatchIn(app.label) -> "purpose:4"
            else -> "category:${app.category}"
        }
    }
}
