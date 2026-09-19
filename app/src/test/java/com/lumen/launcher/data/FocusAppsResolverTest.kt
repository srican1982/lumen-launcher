package com.lumen.launcher.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class FocusAppsResolverTest {

    private val adp = app("ADP", "com.adp.mobile", AppCategory.Work)
    private val outlook = app("Outlook", "com.microsoft.office.outlook", AppCategory.Work)
    private val teams = app("Teams", "com.microsoft.teams", AppCategory.Work)
    private val chrome = app("Chrome", "com.android.chrome", AppCategory.Utilities)
    private val maps = app("Maps", "com.google.android.apps.maps", AppCategory.Travel)
    private val phone = app("Phone", "com.google.android.dialer", AppCategory.Utilities)
    private val contacts = app("Contacts", "com.google.android.contacts", AppCategory.Utilities)
    private val whatsapp = app("WhatsApp", "com.whatsapp", AppCategory.Social)
    private val notes = app("Notes", "com.google.android.keep", AppCategory.Utilities)
    private val photos = app("Photos", "com.google.android.apps.photos", AppCategory.Entertainment)

    @Test
    fun timesheetRanksTaskAppsFirst() {
        val ranked = FocusAppsResolver.apps(
            task = TodoItem("1", "Submit timesheet", space = SpaceKind.Work),
            apps = listOf(maps, chrome, teams, outlook, adp, photos),
            recents = listOf(photos.key),
            space = SpaceKind.Work
        )
        assertThat(ranked.map { it.label }).containsExactly("ADP", "Outlook", "Teams", "Chrome").inOrder()
    }

    @Test
    fun callRanksPhoneThenContacts() {
        val ranked = FocusAppsResolver.apps(
            task = TodoItem("1", "Call Steve", space = SpaceKind.Work),
            apps = listOf(photos, notes, whatsapp, contacts, phone),
            recents = listOf(photos.key),
            space = SpaceKind.Work
        )
        assertThat(ranked.map { it.label }).containsExactly("Phone", "Contacts", "WhatsApp", "Notes").inOrder()
    }

    @Test
    fun unknownTaskFallsBackToPinnedHome() {
        val ranked = FocusAppsResolver.apps(
            task = TodoItem("1", "Water the plants", space = SpaceKind.Home),
            apps = listOf(photos, maps, chrome, phone),
            recents = listOf(phone.key),
            space = SpaceKind.Home,
            favorites = listOf(maps.key, chrome.key, photos.key, phone.key)
        )
        assertThat(ranked.map { it.label }.take(2)).containsExactly("Maps", "Chrome").inOrder()
    }

    @Test
    fun spaceAppsExplainOftenUsed() {
        val pick = FocusAppsResolver.picks(
            task = TodoItem("1", "Water the plants", space = SpaceKind.Work),
            apps = listOf(outlook, maps),
            recents = listOf(outlook.key),
            space = SpaceKind.Work
        ).first()
        assertThat(pick.app.label).isEqualTo("Outlook")
        assertThat(pick.reason(SpaceKind.Work)).isEqualTo("Often used in Work")
    }

    @Test
    fun longPressExplainsWhy() {
        val picks = FocusAppsResolver.picks(
            task = TodoItem("1", "Submit timesheet", space = SpaceKind.Work),
            apps = listOf(maps, adp, photos, outlook),
            recents = listOf(maps.key),
            space = SpaceKind.Work,
            focusPins = listOf(photos.key)
        )
        val reasons = picks.associate { it.app.label to it.reason(SpaceKind.Work) }
        assertThat(reasons["ADP"]).isEqualTo("Needed for this task")
        assertThat(reasons["Outlook"]).isEqualTo("Needed for this task")
        assertThat(reasons["Photos"]).isEqualTo("Always keep in Work Focus")
        assertThat(reasons["Maps"]).isEqualTo("Used recently")
    }

    @Test
    fun focusPinsFollowTaskApps() {
        val ranked = FocusAppsResolver.apps(
            task = TodoItem("1", "Submit timesheet", space = SpaceKind.Work),
            apps = listOf(maps, adp, photos),
            recents = emptyList(),
            space = SpaceKind.Work,
            focusPins = listOf(photos.key)
        )
        assertThat(ranked.map { it.label }.take(2)).containsExactly("ADP", "Photos").inOrder()
    }

    private fun app(label: String, pkg: String, category: AppCategory) =
        AppInfo(label, pkg, "", 0L, category)
}
