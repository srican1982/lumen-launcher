package com.lumen.launcher.data

import android.os.SystemClock
import kotlinx.coroutines.*

/** One in-flight request and TTL per source, with keyed invalidation and cancellation. */
class RefreshGate(private val scope: CoroutineScope, private val ttlMs: Long) {
    private var job: Job? = null
    private var stamp = Long.MIN_VALUE
    private var lastKey: Any? = null
    fun run(force: Boolean = false, key: Any? = Unit, work: suspend CoroutineScope.() -> Unit) {
        val now = SystemClock.elapsedRealtime()
        if (!force && key == lastKey && (job?.isActive == true || (stamp != Long.MIN_VALUE && now - stamp < ttlMs))) return
        job?.cancel()
        lastKey = key
        stamp = now
        job = scope.launch { work() }
    }
}
