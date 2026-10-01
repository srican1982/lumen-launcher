package com.lumen.launcher.search

import com.lumen.launcher.data.*
import com.lumen.launcher.vm.LauncherUiState
import org.junit.Assert.*
import org.junit.Test

class SpaceSearchTest {
    private fun app(name: String, category: AppCategory) = AppInfo(name, "test.$name", "Main", 0, category)
    private val teams = app("Teams", AppCategory.Work)
    private val game = app("TikTok", AppCategory.Entertainment)
    private val whatsapp = app("WhatsApp", AppCategory.Social)
    private fun state(space: SpaceKind, query: String = "") = LauncherUiState(
        apps = listOf(teams, game, whatsapp), spaceOverride = space, query = query
    )
    @Test fun typoStillFindsWhatsappInEverySpace() {
        listOf(SpaceKind.Home, SpaceKind.Work, SpaceKind.Personal, SpaceKind.Focus, SpaceKind.Travel).forEach {
            assertTrue(SpaceSearch.results(state(it, "watsap")).filterIsInstance<SearchHit.App>().any { hit -> hit.app == whatsapp })
        }
    }
    @Test fun explicitDistractingAppRemainsAvailableInFocus() {
        assertEquals(game, (SpaceSearch.results(state(SpaceKind.Focus, "TikTok")).first() as SearchHit.App).app)
    }
    @Test fun focusSuggestionsExcludeUnpinnedEntertainment() {
        val hits = SpaceSearch.results(state(SpaceKind.Focus).copy(recents = listOf(game.key)))
        assertFalse(hits.filterIsInstance<SearchHit.App>().any { it.app == game })
        assertTrue(hits.filterIsInstance<SearchHit.App>().any { it.app == teams })
    }
    @Test fun privateAndHiddenAppsNeverAppear() {
        val s = state(SpaceKind.Home, "Teams").copy(hidden = setOf(teams.packageName))
        assertFalse(SpaceSearch.results(s).filterIsInstance<SearchHit.App>().any { it.app == teams })
        assertFalse(SpaceSearch.results(s.copy(hidden = emptySet(), privateApps = setOf(teams.key))).filterIsInstance<SearchHit.App>().any { it.app == teams })
    }
    @Test fun quotePrioritizesCreationInSocial() {
        val hit = SpaceSearch.results(state(SpaceKind.Personal, "quote")).first() as SearchHit.Action
        assertEquals("create:Quote", hit.id)
    }
    @Test fun boardingFindsSavedDocumentCategoryInsteadOfMaps() {
        val hit = SpaceSearch.results(state(SpaceKind.Travel, "boarding")).first() as SearchHit.Action
        assertEquals("travel:Flights", hit.id)
    }
    @Test fun workTaskRanksBeforeSameNamedPersonalTask() {
        val s = state(SpaceKind.Work, "Steve").copy(todos = listOf(
            TodoItem("personal", "Steve", space = SpaceKind.Personal),
            TodoItem("work", "Steve", space = SpaceKind.Work)
        ))
        assertEquals("task:work", (SpaceSearch.results(s).first() as SearchHit.Action).id)
    }
    @Test fun exactAppBeatsLooselyMatchingContext() {
        val s = state(SpaceKind.Work, "Teams").copy(todos = listOf(TodoItem("t", "Ask teams about lunch", space = SpaceKind.Work)))
        assertEquals(teams, (SpaceSearch.results(s).first() as SearchHit.App).app)
    }
}
