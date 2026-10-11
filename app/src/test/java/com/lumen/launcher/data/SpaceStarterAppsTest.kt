package com.lumen.launcher.data
import com.google.common.truth.Truth.assertThat
import org.junit.Test
class SpaceStarterAppsTest {
    @Test fun workSuggestsRelevantAppsOnePerJob() {
        val packages = listOf("ai.chat", "com.google.android.gm", "com.microsoft.office.outlook", "com.microsoft.teams", "com.Slack", "com.adpmobile.android")
        val apps = packages.map { AppInfo(it, it, "Main", 0, AppCategory.Work) }
        assertThat(SpaceStarterApps.picks(SpaceKind.Work, apps, emptySet()).map { it.packageName })
            .containsExactly("com.microsoft.office.outlook", "com.microsoft.teams", "com.adpmobile.android").inOrder()
        assertThat(SpaceStarterApps.picks(SpaceKind.Work, apps, setOf("com.microsoft.office.outlook")).map { it.packageName }).contains("com.google.android.gm")
    }
}
