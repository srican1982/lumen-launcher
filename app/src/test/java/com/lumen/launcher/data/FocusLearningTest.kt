package com.lumen.launcher.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class FocusLearningTest {
    @Test fun learnsSimilarTasksWithoutLeakingUnrelatedApps() {
        val history = mapOf("Submit client proposal" to mapOf("docs" to 3, "removed" to 5),
            "Call Steve" to mapOf("phone" to 8))
        assertThat(FocusLearning.rank("Prepare client proposal", history, setOf("docs", "phone")))
            .containsExactly("docs")
    }
    @Test fun vagueTasksDoNotInventMatches() {
        assertThat(FocusLearning.rank("Test", mapOf("Test website" to mapOf("browser" to 4)), setOf("browser"))).isEmpty()
    }
    @Test fun matchesRelatedContentButNotGenericWords() {
        assertThat(FocusLearning.related("Prepare client proposal", "Client proposal notes")).isTrue()
        assertThat(FocusLearning.related("Prepare client proposal", "Prepare dinner")).isFalse()
    }
}
