package com.lumen.launcher.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class HomeStarterAppsTest {
    private fun app(pkg: String, category: AppCategory = AppCategory.Utilities) = AppInfo(pkg, pkg, "Main", 0, category)
    @Test fun knownEverydayAppsBeatAlphabeticFillersAndExcludeDock() {
        val apps = listOf(app("adp"), app("affirm"), app("com.whatsapp"), app("com.google.android.gm"), app("com.android.chrome"))
        val picks = HomeStarterApps.picks(apps, setOf("com.android.chrome"))
        assertThat(picks.take(2).map { it.packageName }).containsExactly("com.whatsapp", "com.google.android.gm").inOrder()
        assertThat(picks.map { it.packageName }).doesNotContain("com.android.chrome")
    }
    @Test fun selectionIsBoundedAndUnique() {
        val apps = (1..20).map { app("app$it", AppCategory.Social) } + listOf(app("com.whatsapp"), app("org.telegram.messenger"))
        val picks = HomeStarterApps.picks(apps, emptySet())
        assertThat(picks.size).isAtMost(12)
        assertThat(picks.map { it.key }.distinct()).hasSize(picks.size)
    }
    @Test fun suggestionsNeverFillWithAlternativeBrowsersEmailOrMusic() {
        val apps = listOf("com.android.chrome", "org.mozilla.firefox", "com.sec.android.app.sbrowser", "com.google.android.gm", "com.microsoft.office.outlook", "com.spotify.music", "com.google.android.apps.youtube.music").map { app(it) }
        assertThat(HomeStarterApps.picks(apps, emptySet()).map { it.packageName })
            .containsExactly("com.android.chrome", "com.google.android.gm", "com.spotify.music")
        assertThat(HomeStarterApps.fromUsage(apps.reversed(), emptySet()).take(3).map { it.packageName })
            .containsExactly("com.google.android.apps.youtube.music", "com.microsoft.office.outlook", "com.sec.android.app.sbrowser").inOrder()
    }
    @Test fun starterPicksIncludeOtherEverydayPurposesBeforeDuplicates() {
        val apps = listOf("com.whatsapp", "com.facebook.orca", "com.google.android.gm", "com.microsoft.office.outlook", "com.google.android.apps.photos", "com.amazon.mShop.android.shopping", "com.chase.sig.android", "com.instagram.android").map { app(it) }
        assertThat(HomeStarterApps.picks(apps, emptySet()).map { it.packageName }).containsExactly(
            "com.whatsapp", "com.google.android.gm", "com.google.android.apps.photos", "com.amazon.mShop.android.shopping", "com.chase.sig.android", "com.instagram.android")
        val ranked = HomeStarterApps.fromUsage(apps, emptySet())
        assertThat(ranked.take(6).map { it.packageName }).doesNotContain("com.facebook.orca")
        assertThat(ranked.map { it.packageName }).contains("com.facebook.orca")
    }
}
