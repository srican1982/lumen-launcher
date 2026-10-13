package com.lumen.launcher.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ModeAppsResolverTest {
    private val chat = AppInfo("Chat", "com.whatsapp", "Main", 0, AppCategory.Social)
    private val work = AppInfo("Mail", "com.microsoft.office.outlook", "Main", 0, AppCategory.Work)
    private val maps = AppInfo("Maps", "com.google.android.apps.maps", "Main", 0, AppCategory.Travel)
    private val visible = listOf(chat, work, maps)
    private fun resolve(space: SpaceKind, selected: List<String>? = null) =
        ModeAppsResolver.resolve(space, visible, selected, listOf(chat), listOf(maps))

    @Test fun workExcludesUnrelatedFavorites() {
        assertThat(resolve(SpaceKind.Work)).containsExactly(work)
    }
    @Test fun socialUsesMessagingApps() {
        assertThat(resolve(SpaceKind.Personal)).containsExactly(chat)
    }
    @Test fun travelUsesTravelApps() {
        assertThat(resolve(SpaceKind.Travel)).containsExactly(maps)
    }
    @Test fun focusDoesNotInferAnAllowlist() {
        assertThat(resolve(SpaceKind.Focus)).isEmpty()
    }
    @Test fun explicitlyEmptySelectionStaysEmpty() {
        assertThat(resolve(SpaceKind.Work, emptyList())).isEmpty()
    }
    @Test fun explicitSelectionKeepsOrderAndFiltersHiddenOrMissingApps() {
        assertThat(resolve(SpaceKind.Focus, listOf(maps.key, "hidden/Main", work.key, maps.key)))
            .containsExactly(maps, work).inOrder()
    }
    @Test fun homePrioritizesFavoritesThenRecents() {
        assertThat(resolve(SpaceKind.Home)).containsExactly(chat, maps, work).inOrder()
    }
    @Test fun homeRanksUsageAfterPinsWithoutAlphabeticalFill() {
        assertThat(ModeAppsResolver.resolve(SpaceKind.Home, visible, null, listOf(chat), listOf(maps), listOf(work, maps)))
            .containsExactly(chat, work, maps).inOrder()
    }
    @Test fun homeExcludesDockPackagesIncludingExplicitSelections() {
        assertThat(ModeAppsResolver.resolve(SpaceKind.Home, visible, listOf(chat.key, maps.key), emptyList(), emptyList(), dockPackages = setOf(chat.packageName)))
            .containsExactly(maps)
    }
    @Test fun newHomeFillsAvailableSlots() {
        assertThat(ModeAppsResolver.resolve(SpaceKind.Home, visible, null, emptyList(), emptyList())).containsExactlyElementsIn(visible)
    }
    @Test fun savedHomeOrderIsUnaffectedByUsageChanges() {
        assertThat(ModeAppsResolver.resolve(SpaceKind.Home, visible, listOf(maps.key, chat.key, work.key), emptyList(), listOf(work), listOf(work, chat)))
            .containsExactly(maps, chat, work).inOrder()
    }
    @Test fun homeFillsThreeRowsWithoutDockApps() {
        val apps = (1..20).map { AppInfo("App $it", "app$it", "Main", 0, AppCategory.Utilities) }
        val result = ModeAppsResolver.resolve(SpaceKind.Home, apps, null, emptyList(), emptyList(), dockPackages = setOf("app1"), homeCapacity = 12)
        assertThat(result).hasSize(12)
        assertThat(result.map { it.packageName }).doesNotContain("app1")
    }
    @Test fun modeSelectionsDoNotLeak() {
        assertThat(resolve(SpaceKind.Work, listOf(chat.key))).containsExactly(chat)
        assertThat(resolve(SpaceKind.Travel)).containsExactly(maps)
    }
    @Test fun savedModeCannotShowMoreThanTwelveApps() {
        val apps = (1..18).map { AppInfo("App $it", "app$it", "Main", 0, AppCategory.Work) }
        val result = ModeAppsResolver.resolve(SpaceKind.Work, apps, apps.map { it.key }, emptyList(), emptyList())
        assertThat(result).hasSize(12)
        assertThat(result).containsExactlyElementsIn(apps.take(12)).inOrder()
    }
}
