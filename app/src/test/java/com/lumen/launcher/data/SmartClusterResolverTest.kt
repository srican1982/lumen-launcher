package com.lumen.launcher.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SmartClusterResolverTest {

    private val slack = app("Slack", "com.slack", AppCategory.Work)
    private val gmail = app("Gmail", "com.google.android.gm", AppCategory.Work)
    private val maps = app("Maps", "com.google.android.apps.maps", AppCategory.Travel)
    private val photos = app("Photos", "com.google.android.apps.photos", AppCategory.Entertainment)
    private val instagram = app("Instagram", "com.instagram.android", AppCategory.Social)

    @Test
    fun focusKeepsFourTaskApps() {
        val cluster = SmartClusterResolver.apps(
            space = SpaceKind.Work,
            focusing = true,
            focusApps = listOf(slack, gmail, maps, photos, instagram),
            visible = listOf(slack, gmail, maps, photos, instagram),
            recents = emptyList(),
            needNow = emptyList()
        )
        assertThat(cluster.map { it.label }).containsExactly("Slack", "Gmail", "Maps", "Photos").inOrder()
    }

    @Test
    fun workPrefersWorkApps() {
        val cluster = SmartClusterResolver.apps(
            space = SpaceKind.Work,
            focusing = false,
            focusApps = emptyList(),
            visible = listOf(photos, instagram, maps, slack, gmail),
            recents = listOf(photos.key),
            needNow = listOf(slack)
        )
        assertThat(cluster.map { it.label }.take(2)).containsExactly("Slack", "Gmail").inOrder()
    }

    @Test
    fun travelSurfacesMaps() {
        val cluster = SmartClusterResolver.apps(
            space = SpaceKind.Travel,
            focusing = false,
            focusApps = emptyList(),
            visible = listOf(photos, slack, maps),
            recents = emptyList(),
            needNow = emptyList()
        )
        assertThat(cluster.map { it.label }).contains("Maps")
    }

    @Test
    fun focusSpaceFillsVisibleApps() {
        val cluster = SmartClusterResolver.apps(
            space = SpaceKind.Focus,
            focusing = false,
            focusApps = emptyList(),
            visible = listOf(photos, instagram, maps, slack),
            recents = emptyList(),
            needNow = emptyList()
        )
        assertThat(cluster).hasSize(4)
        assertThat(cluster.map { it.label }).contains("Slack")
    }

    @Test
    fun workFillsFromVisibleWhenNoWorkApps() {
        val cluster = SmartClusterResolver.apps(
            space = SpaceKind.Work,
            focusing = false,
            focusApps = emptyList(),
            visible = listOf(photos, instagram, maps),
            recents = listOf(maps.key),
            needNow = emptyList()
        )
        assertThat(cluster.map { it.label }).containsExactly("Maps", "Photos", "Instagram").inOrder()
    }

    @Test
    fun matchSpaceUsesMonoWhileFocusing() {
        val treatment = IconSkin.treatment(IconSkin.MatchSpace, SpaceKind.Personal, focusing = true)
        assertThat(treatment).isEqualTo(IconTreatment.Mono)
    }

    private fun app(label: String, pkg: String, category: AppCategory) =
        AppInfo(label, pkg, "", 0L, category)
}
