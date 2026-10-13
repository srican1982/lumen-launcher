package com.lumen.launcher.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class HiddenAppsMigrationTest {
    private val main = AppInfo("Chat", "chat.app", "Main", 0, AppCategory.Social)
    private val secondary = main.copy(activityName = "Secondary")
    private val other = AppInfo("Mail", "mail.app", "Main", 0, AppCategory.Work)

    @Test fun recoversEveryActivityAndPreservesExistingPrivateApps() {
        val result = HiddenAppsMigration.resolve(listOf(main, secondary, other), setOf("chat.app"), setOf(other.key))
        assertThat(result.hidden).isEmpty()
        assertThat(result.privateApps).containsExactly(main.key, secondary.key, other.key)
    }

    @Test fun preservesMissingPackagesUntilTheyAreInstalled() {
        val result = HiddenAppsMigration.resolve(listOf(main), setOf("chat.app", "missing.app"), emptySet())
        assertThat(result.hidden).containsExactly("missing.app")
        assertThat(result.privateApps).containsExactly(main.key)
    }

    @Test fun migrationIsIdempotentAndDoesNotExposeRecoveredApps() {
        val first = HiddenAppsMigration.resolve(listOf(main, other), setOf("chat.app"), emptySet())
        val second = HiddenAppsMigration.resolve(listOf(main, other), first.hidden, first.privateApps)
        assertThat(second).isEqualTo(first)
        assertThat(main.key in second.privateApps).isTrue()
        assertThat(other.key in second.privateApps).isFalse()
    }
}
