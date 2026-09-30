package com.lumen.launcher.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ModeAppsResolverTest {
    private val chat = AppInfo("Chat", "chat", "Main", 0, AppCategory.Social)
    private val work = AppInfo("Mail", "work", "Main", 0, AppCategory.Work)
    private val maps = AppInfo("Maps", "maps", "Main", 0, AppCategory.Travel)
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
    @Test fun modeSelectionsDoNotLeak() {
        assertThat(resolve(SpaceKind.Work, listOf(chat.key))).containsExactly(chat)
        assertThat(resolve(SpaceKind.Travel)).containsExactly(maps)
    }
}
