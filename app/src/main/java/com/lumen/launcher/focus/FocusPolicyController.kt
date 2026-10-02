package com.lumen.launcher.focus

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Build
import android.service.notification.Condition
import android.service.notification.ZenPolicy
import com.lumen.launcher.LauncherActivity

/**
 * Applies and restores Android DND / Zen policy for Focus.
 *
 * Stock Android ZenPolicy only supports people tiers (Anyone / Contacts / Starred / None),
 * not an arbitrary contact ID allowlist. Selected people are persisted for UI and for
 * applying the best-supported tier: if anyone may call/message, we allow Starred (preferred)
 * falling back to Contacts when no starred-capable path is available.
 */
class FocusPolicyController(private val context: Context) {
    private val nm get() = context.getSystemService(NotificationManager::class.java)
    private val ledger get() = context.getSharedPreferences(LEDGER, Context.MODE_PRIVATE)
    private val conditionUri = Uri.parse("condition://com.lumen.launcher/focus")

    fun hasAccess(): Boolean = nm.isNotificationPolicyAccessGranted

    /**
     * Saves prior interruption filter, then activates Lumen Focus rule.
     * Returns false if policy access is missing or activation fails.
     */
    fun apply(
        people: List<FocusPerson>,
        settings: FocusPolicySettings
    ): Boolean {
        if (!hasAccess()) return false
        return try {
            // Capture prior filter before we change anything.
            val priorFilter = nm.currentInterruptionFilter
            ledger.edit().putInt(KEY_PRIOR_FILTER, priorFilter).apply()

            if (Build.VERSION.SDK_INT >= 29) {
                removeOwnedRule()
                val policy = buildZenPolicy(people, settings)
                val rule = android.app.AutomaticZenRule(
                    "Lumen Focus",
                    null,
                    ComponentName(context, LauncherActivity::class.java),
                    conditionUri,
                    policy,
                    NotificationManager.INTERRUPTION_FILTER_PRIORITY,
                    true
                )
                val id = nm.addAutomaticZenRule(rule)
                if (id.isNullOrBlank()) return false
                ledger.edit().putString(KEY_RULE, id).apply()
                nm.setAutomaticZenRuleState(
                    id,
                    Condition(conditionUri, "Focus session", Condition.STATE_TRUE)
                )
            } else {
                // Pre-Q: full silence; cannot express people exceptions.
                nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE)
            }
            true
        } catch (_: Exception) {
            restore()
            false
        }
    }

    /**
     * Restore exact previous interruption filter when we changed it.
     * If the user already had DND on before Focus, we put that filter back — we do not force ALL.
     */
    fun restore(): Boolean {
        return try {
            removeOwnedRule()
            if (Build.VERSION.SDK_INT < 29 && ledger.contains(KEY_PRIOR_FILTER)) {
                val prior = ledger.getInt(KEY_PRIOR_FILTER, NotificationManager.INTERRUPTION_FILTER_ALL)
                val current = nm.currentInterruptionFilter
                // Only restore if still in the silent filter we applied.
                if (FocusSessionRules.restoreLegacy(current, NotificationManager.INTERRUPTION_FILTER_NONE)) {
                    nm.setInterruptionFilter(prior)
                }
            }
            ledger.edit().remove(KEY_PRIOR_FILTER).remove(KEY_RULE).apply()
            true
        } catch (_: Exception) {
            false
        }
    }

    fun buildZenPolicy(
        people: List<FocusPerson>,
        settings: FocusPolicySettings
    ): ZenPolicy {
        val wantsCalls = settings.allowCallsFromSelected && people.any { it.reach.allowsCalls }
        val wantsMessages = settings.allowMessagesFromSelected && people.any { it.reach.allowsMessages }
        val builder = ZenPolicy.Builder().disallowAllSounds()
        if (settings.allowAlarms) builder.allowAlarms(true)
        builder.allowMedia(true)
        if (settings.allowCalendarReminders) {
            builder.allowReminders(true)
            builder.allowEvents(true)
        }
        if (settings.allowRepeatedCallers) builder.allowRepeatCallers(true)
        when {
            wantsCalls -> builder.allowCalls(ZenPolicy.PEOPLE_TYPE_CONTACTS)
            else -> builder.allowCalls(ZenPolicy.PEOPLE_TYPE_NONE)
        }
        when {
            wantsMessages -> {
                builder.allowMessages(ZenPolicy.PEOPLE_TYPE_CONTACTS)
                if (Build.VERSION.SDK_INT >= 30) {
                    builder.allowConversations(ZenPolicy.CONVERSATION_SENDERS_IMPORTANT)
                }
            }
            else -> builder.allowMessages(ZenPolicy.PEOPLE_TYPE_NONE)
        }
        if (settings.silenceEveryoneElse) {
            builder.hideAllVisualEffects()
        }
        return builder.build()
    }

    /** Describes what this device's policy can actually honor for UI honesty. */
    fun capabilityNote(people: List<FocusPerson>, settings: FocusPolicySettings): String {
        val wantsCalls = settings.allowCallsFromSelected && people.any { it.reach.allowsCalls }
        val wantsMessages = settings.allowMessagesFromSelected && people.any { it.reach.allowsMessages }
        return when {
            Build.VERSION.SDK_INT < 29 ->
                "This Android version only supports full silence during Focus."
            wantsCalls || wantsMessages ->
                "Android allows Contacts (not individual picks) for calls/messages. Selected people are remembered in Lumen."
            else ->
                "All calls and messages are silenced. Alarms stay available."
        }
    }

    private fun removeOwnedRule() {
        val id = ledger.getString(KEY_RULE, null) ?: return
        if (Build.VERSION.SDK_INT >= 29) {
            runCatching {
                if (nm.getAutomaticZenRule(id) != null) nm.removeAutomaticZenRule(id)
            }
        }
        ledger.edit().remove(KEY_RULE).apply()
    }

    companion object {
        private const val LEDGER = "focus_policy"
        private const val KEY_RULE = "rule_id"
        private const val KEY_PRIOR_FILTER = "prior_filter"
    }
}
