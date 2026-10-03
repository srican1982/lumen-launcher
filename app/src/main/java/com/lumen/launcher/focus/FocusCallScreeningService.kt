package com.lumen.launcher.focus

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telecom.Call
import android.telecom.CallScreeningService
import android.telephony.PhoneNumberUtils
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*

object FocusCallAccess {
    fun ready(context: Context): Boolean = Build.VERSION.SDK_INT >= 29 &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED &&
        context.getSystemService(RoleManager::class.java)?.isRoleHeld(RoleManager.ROLE_CALL_SCREENING) == true
}

/** Never rejects a call: outside-list callers remain visible and in call history, but silent. */
class FocusCallScreeningService : CallScreeningService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    override fun onDestroy() { scope.cancel(); super.onDestroy() }
    override fun onScreenCall(details: Call.Details) {
        if (details.callDirection != Call.Details.DIRECTION_INCOMING) return
        scope.launch {
        val ledger = getSharedPreferences("focus_session", MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val running = ledger.getLong("until", 0) > now && ledger.getLong("paused_remaining", 0) == 0L
        var silence = false
        if (running && FocusCallAccess.ready(this@FocusCallScreeningService)) {
            val repo = FocusAllowedPeopleRepository.get(this@FocusCallScreeningService)
            val settings = repo.settingsNow()
            val number = details.handle?.schemeSpecificPart.orEmpty()
            val lookup = scope.async(Dispatchers.IO) {
                runCatching { FocusCallerMatcher.selected(this@FocusCallScreeningService, repo.peopleNow(), number) }.getOrDefault(false)
            }
            val selected = try { withTimeoutOrNull(1200L) { lookup.await() } } finally { lookup.cancel() }
                ?: repo.peopleNow().any { it.reach.allowsCalls && it.phone.isNotBlank() && PhoneNumberUtils.compare(this@FocusCallScreeningService, it.phone, number) }
            val history = getSharedPreferences("focus_call_attempts", MODE_PRIVATE)
            val key = PhoneNumberUtils.normalizeNumber(number)
            val previous = if (key.isNotBlank()) history.getLong(key, 0) else 0
            val repeated = previous >= ledger.getLong("started", now) && now - previous in 1..900_000L
            silence = !FocusCallRules.allow(settings.allowCallsFromSelected && selected, settings.allowRepeatedCallers, repeated)
            if (key.isNotBlank()) {
                val edit = history.edit()
                history.all.forEach { (k,v) -> if (v !is Long || now - v > 900_000L) edit.remove(k) }
                edit.putLong(key, now).apply()
            }
        }
        // Keep only a decision and sound-state snapshot, never the caller's number/name.
        getSharedPreferences("focus_call_diagnostics", MODE_PRIVATE).edit()
            .putLong("time", now).putBoolean("silenced", silence).putBoolean("running", running)
            .putString("sound", FocusRingDiagnostics.read(this@FocusCallScreeningService)).apply()
        respondToCall(details, CallResponse.Builder().setDisallowCall(false).setRejectCall(false)
            .setSkipCallLog(false).setSkipNotification(false).setSilenceCall(silence).build())
        }
    }
}

internal object FocusCallRules {
    fun allow(selected: Boolean, repeatedEnabled: Boolean, repeated: Boolean): Boolean =
        selected || (repeatedEnabled && repeated)
}
