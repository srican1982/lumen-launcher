package com.lumen.launcher.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class FocusAiSuggestionsTest {
    @Test fun rejectsUninstalledAppsAndDeduplicates() {
        assertThat(FocusAiSuggestions.parse("""{"apps":["browser","invented","browser","notes"]}""", setOf("browser", "notes")))
            .containsExactly("browser", "notes").inOrder()
    }
    @Test fun ambiguousTaskCanReturnNoApps() {
        assertThat(FocusAiSuggestions.parse("""{"apps":[]}""", setOf("browser"))).isEmpty()
    }
    @Test fun malformedResultFallsBack() {
        assertThat(FocusAiSuggestions.parse("not json", setOf("browser"))).isNull()
    }
}
