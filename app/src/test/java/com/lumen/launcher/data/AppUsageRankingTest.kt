package com.lumen.launcher.data

import org.junit.Assert.assertEquals
import org.junit.Test

class AppUsageRankingTest {
    private fun app(name: String) = AppInfo(name, name, "Main", 0, AppCategory.Utilities)
    private fun hit(key: String) = SpaceSense.LaunchHour(key, 12)

    @Test fun frequencyWinsOverRecencyAndTiesUseRecency() {
        val a = app("A"); val b = app("B"); val c = app("C")
        assertEquals(listOf(b, c, a), AppUsageRanking.rank(listOf(a, b, c),
            listOf(hit(c.key), hit(a.key), hit(b.key), hit(b.key))))
    }

    @Test fun dockAliasesResolveAndHiddenOrUninstalledAppsStayExcluded() {
        val a = app("A")
        assertEquals(listOf(a), AppUsageRanking.rank(listOf(a),
            listOf(hit("hidden/Main"), hit("A/DockAlias"))))
        assertEquals(emptyList<AppInfo>(), AppUsageRanking.rank(listOf(a), emptyList()))
    }
}
