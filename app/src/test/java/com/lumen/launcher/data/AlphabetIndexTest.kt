package com.lumen.launcher.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AlphabetIndexTest {

    private fun app(label: String, category: AppCategory = AppCategory.Utilities) = AppInfo(
        label = label,
        packageName = "com.example.${label.lowercase().replace(Regex("[^a-z0-9]"), "")}",
        activityName = ".Main",
        lastUpdateTime = 0L,
        category = category
    )

    @Test
    fun numbersAndSymbolsLandInHash() {
        assertThat(AlphabetIndex.letterKey("7-Eleven")).isEqualTo("#")
        assertThat(AlphabetIndex.letterKey("*Weather")).isEqualTo("#")
        assertThat(AlphabetIndex.letterKey("Slack")).isEqualTo("S")
    }

    @Test
    fun railStartsWithHashThenAlphabet() {
        assertThat(AlphabetIndex.LETTERS.first()).isEqualTo('#')
        assertThat(AlphabetIndex.LETTERS[1]).isEqualTo('A')
        assertThat(AlphabetIndex.LETTERS.last()).isEqualTo('Z')
    }

    @Test
    fun emptyLettersSnapToNearestAvailable() {
        val apps = listOf(app("Maps"), app("Slack"), app("Spotify"))
        val (_, index) = AlphabetIndex.buildAz(apps, SpaceKind.Home, emptyList())
        assertThat(index.resolve('N')).isEqualTo('M')
        assertThat(index.resolve('R')).isEqualTo('S')
        assertThat(index.firstVisibleIndex('Q')).isNotNull()
    }

    @Test
    fun workSpaceRanksWorkAppsFirstInsideLetter() {
        val apps = listOf(
            app("Tunes", AppCategory.Entertainment),
            app("Teams", AppCategory.Work),
            app("Traveloka", AppCategory.Travel)
        )
        val ranked = AlphabetIndex.rankForSpace(apps, SpaceKind.Work)
        assertThat(ranked.first().label).isEqualTo("Teams")
    }

    @Test
    fun firstIndexSkipsLeadingItems() {
        val sections = listOf(
            "M" to listOf(app("Maps"), app("Meet")),
            "S" to listOf(app("Slack"))
        )
        val index = AlphabetIndex.build(sections, leadingItems = 2)
        assertThat(index.firstIndex['M']).isEqualTo(2)
        assertThat(index.firstIndex['S']).isEqualTo(5)
        assertThat(index.previews['M']!!.map { it.label }).containsExactly("Maps", "Meet").inOrder()
    }

    @Test
    fun letterAtMapsRailFraction() {
        val apps = ('A'..'Z').map { app("${it}pp") } + app("7zip")
        val (_, index) = AlphabetIndex.buildAz(apps, SpaceKind.Home, emptyList())
        assertThat(index.letterAt(0f)).isEqualTo('#')
        assertThat(index.letterAt(0.04f)).isEqualTo('A')
    }
}
