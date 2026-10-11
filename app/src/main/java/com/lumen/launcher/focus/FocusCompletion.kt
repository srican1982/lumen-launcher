package com.lumen.launcher.focus

data class FocusCompletion(val startedAt: Long, val endedAt: Long, val focusedMs: Long, val sessions: Int, val streak: Int, val sound: String = "", val peopleCount: Int = -1) {
    companion object {
        fun elapsed(total: Long, until: Long, paused: Long, now: Long): Long {
            val remaining = if (paused > 0) paused else (until - now).coerceAtLeast(0)
            return (total - remaining).coerceIn(0, total.coerceAtLeast(0))
        }
    }
}
