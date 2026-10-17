package com.lumen.launcher.ui

import com.lumen.launcher.data.*
import com.lumen.launcher.vm.LauncherUiState
import org.junit.Assert.*
import org.junit.Test

class MeFavoritesTest {
    private fun app(n: Int) = AppInfo("App $n", "pkg$n", "Activity", 0L, AppCategory.Utilities)
    @Test fun privateAndHiddenAppsNeverLeakIntoPersonalFavorites() {
        val apps = (1..8).map(::app)
        val state = LauncherUiState(apps = apps, privateApps = setOf(apps[0].key), hidden = setOf(apps[1].packageName))
        val profile = MeProfile(favorites = apps.map { it.key }, favoritesSet = true)
        assertEquals(apps.drop(2).map { it.key }, meFavorites(profile, state).map { it.key })
    }
    @Test fun savedOrderingSurvivesMissingAppAndDoesNotRefillDeliberatelyEmptyList() {
        val apps = (1..8).map(::app)
        val state = LauncherUiState(apps = apps)
        val profile = MeProfile(favorites = listOf(apps[5].key, "uninstalled", apps[2].key), favoritesSet = true)
        assertEquals(listOf(apps[5], apps[2]), meFavorites(profile, state))
        assertTrue(meFavorites(profile.copy(favorites = emptyList()), state).isEmpty())
    }
    @Test fun defaultsAreFourAndAnExplicitSelectionIsCappedAtSix() {
        val apps = (1..9).map(::app)
        val state = LauncherUiState(apps = apps)
        assertEquals(4, meFavorites(MeProfile(), state).size)
        assertEquals(6, meFavorites(MeProfile(favorites = apps.map { it.key }, favoritesSet = true), state).size)
    }
}
