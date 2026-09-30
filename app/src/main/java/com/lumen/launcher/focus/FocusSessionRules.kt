package com.lumen.launcher.focus

internal object FocusSessionRules {
    // Ignore an old alarm after the session is extended or replaced.
    fun acceptFinish(until: Long, expected: Long?, now: Long): Boolean =
        expected == null || (until > 0 && until == expected && now >= until)

    // Never overwrite a different DND choice the user made during a legacy session.
    fun restoreLegacy(currentFilter: Int, appliedFilter: Int): Boolean = currentFilter == appliedFilter
}
