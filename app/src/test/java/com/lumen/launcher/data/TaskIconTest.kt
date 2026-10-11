package com.lumen.launcher.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TaskIconTest {
    @Test fun callsUseTelephone() {
        assertThat(TaskIcon.symbol("Call Steve")).isEqualTo("\uD83D\uDCDE")
        assertThat(TaskIcon.symbol("PHONE Mum")).isEqualTo("\uD83D\uDCDE")
    }
    @Test fun commonTaskKindsHaveUsefulIcons() {
        assertThat(TaskIcon.symbol("Take ADHD medication")).isEqualTo("\uD83D\uDC8A")
        assertThat(TaskIcon.symbol("Pay invoice")).isEqualTo("\uD83D\uDCB3")
        assertThat(TaskIcon.symbol("Buy milk")).isEqualTo("\uD83D\uDED2")
        assertThat(TaskIcon.symbol("Test")).isEqualTo("\uD83D\uDCCB")
    }
}
