package com.lumen.launcher.focus

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Build
import android.service.notification.Condition
import android.service.notification.ZenPolicy
import com.lumen.launcher.LauncherActivity

/** Own a separate rule on Android 10+, preserving manual DND and other apps' rules. */
class FocusPolicyController(private val context: Context) {
    private val nm get() = context.getSystemService(NotificationManager::class.java)
    private val ledger get() = context.getSharedPreferences("focus_policy", Context.MODE_PRIVATE)
    private val conditionUri = Uri.parse("condition://com.lumen.launcher/focus")
    fun hasAccess(): Boolean = runCatching { nm.isNotificationPolicyAccessGranted }.getOrDefault(false)
    fun hasOwnedRule(): Boolean = ledger.contains("rule_id") || ledger.contains("prior_filter")

    fun apply(people: List<FocusPerson>, settings: FocusPolicySettings): Boolean {
        if (!hasAccess() || !FocusCallAccess.ready(context)) return false
        return try {
            if (!ledger.contains("prior_filter")) {
                val old = nm.notificationPolicy
                check(ledger.edit().putInt("prior_filter", nm.currentInterruptionFilter)
                    .putInt("prior_categories", old.priorityCategories).putInt("prior_calls", old.priorityCallSenders)
                    .putInt("prior_messages", old.priorityMessageSenders)
                    .putInt("prior_visual", if (Build.VERSION.SDK_INT >= 28) old.suppressedVisualEffects else 0).commit())
            }
            if (Build.VERSION.SDK_INT >= 29) {
                val oldId = ledger.getString("rule_id", null)
                val existing = oldId?.let { nm.getAutomaticZenRule(it) }
                val rule = existing ?: android.app.AutomaticZenRule("Lumen Focus", null,
                    ComponentName(context, LauncherActivity::class.java), conditionUri,
                    buildZenPolicy(people, settings), NotificationManager.INTERRUPTION_FILTER_PRIORITY, true)
                rule.zenPolicy = buildZenPolicy(people, settings)
                rule.isEnabled = true
                val id = if (existing != null) {
                    check(nm.updateAutomaticZenRule(oldId!!, rule)); oldId
                } else nm.addAutomaticZenRule(rule).also { check(!it.isNullOrBlank()) }
                check(ledger.edit().putString("rule_id", id).commit())
                nm.setAutomaticZenRuleState(id, Condition(conditionUri, "Focus session", Condition.STATE_TRUE))
            } else {
                val wanted = legacyPolicy(settings)
                nm.notificationPolicy = wanted
                check(ledger.edit().putInt("applied_categories", wanted.priorityCategories)
                    .putInt("applied_calls", wanted.priorityCallSenders).putInt("applied_messages", wanted.priorityMessageSenders)
                    .putInt("applied_visual", if (Build.VERSION.SDK_INT >= 28) wanted.suppressedVisualEffects else 0).commit())
                nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
            }
            true
        } catch (_: Exception) { restore(); false }
    }

    fun restore(): Boolean = try {
        if (Build.VERSION.SDK_INT >= 29) {
            ledger.getString("rule_id", null)?.let { id ->
                if (nm.getAutomaticZenRule(id) != null) check(nm.removeAutomaticZenRule(id))
            }
        } else if (ledger.contains("prior_filter")) {
            val current = nm.notificationPolicy
            if (ledger.contains("applied_categories") && sameLegacyPolicy(current, storedPolicy("applied"))) {
                nm.notificationPolicy = storedPolicy("prior")
            }
            if (FocusSessionRules.restoreLegacy(nm.currentInterruptionFilter, NotificationManager.INTERRUPTION_FILTER_PRIORITY)) {
                nm.setInterruptionFilter(ledger.getInt("prior_filter", NotificationManager.INTERRUPTION_FILTER_ALL))
            }
        }
        ledger.edit().clear().commit()
    } catch (_: Exception) { false } // Retain ownership so cleanup can retry after access returns.

    fun ownedRuleExists(): Boolean = if (Build.VERSION.SDK_INT >= 29) runCatching {
        ledger.getString("rule_id", null)?.let { nm.getAutomaticZenRule(it)?.isEnabled == true } ?: false
    }.getOrDefault(false) else ledger.contains("prior_filter")

    fun buildZenPolicy(people: List<FocusPerson>, settings: FocusPolicySettings): ZenPolicy {
        val builder = ZenPolicy.Builder().disallowAllSounds()
            .allowAlarms(settings.allowAlarms).allowMedia(false)
            .allowReminders(settings.allowCalendarReminders).allowEvents(settings.allowCalendarReminders)
            .allowRepeatCallers(settings.allowRepeatedCallers)
            .allowCalls(if (settings.allowCallsFromSelected) ZenPolicy.PEOPLE_TYPE_CONTACTS else ZenPolicy.PEOPLE_TYPE_NONE)
            .allowMessages(if (settings.allowMessagesFromSelected) ZenPolicy.PEOPLE_TYPE_STARRED else ZenPolicy.PEOPLE_TYPE_NONE)
        if (Build.VERSION.SDK_INT >= 30) builder.allowConversations(ZenPolicy.CONVERSATION_SENDERS_NONE)
        if (settings.silenceEveryoneElse) builder.hideAllVisualEffects()
        return builder.build()
    }

    private fun legacyPolicy(s: FocusPolicySettings): NotificationManager.Policy {
        var categories = 0
        if (s.allowCallsFromSelected) categories = categories or NotificationManager.Policy.PRIORITY_CATEGORY_CALLS
        if (s.allowMessagesFromSelected) categories = categories or NotificationManager.Policy.PRIORITY_CATEGORY_MESSAGES
        if (s.allowRepeatedCallers) categories = categories or NotificationManager.Policy.PRIORITY_CATEGORY_REPEAT_CALLERS
        if (s.allowAlarms) categories = categories or NotificationManager.Policy.PRIORITY_CATEGORY_ALARMS
        if (s.allowCalendarReminders) categories = categories or NotificationManager.Policy.PRIORITY_CATEGORY_EVENTS or NotificationManager.Policy.PRIORITY_CATEGORY_REMINDERS
        return NotificationManager.Policy(categories, NotificationManager.Policy.PRIORITY_SENDERS_STARRED, NotificationManager.Policy.PRIORITY_SENDERS_STARRED)
    }
    private fun sameLegacyPolicy(a: NotificationManager.Policy, b: NotificationManager.Policy): Boolean =
        a.priorityCategories == b.priorityCategories && a.priorityCallSenders == b.priorityCallSenders &&
            a.priorityMessageSenders == b.priorityMessageSenders &&
            (Build.VERSION.SDK_INT < 28 || a.suppressedVisualEffects == b.suppressedVisualEffects)

    private fun storedPolicy(prefix: String): NotificationManager.Policy = if (Build.VERSION.SDK_INT >= 28)
        NotificationManager.Policy(ledger.getInt("${prefix}_categories", 0), ledger.getInt("${prefix}_calls", 0), ledger.getInt("${prefix}_messages", 0), ledger.getInt("${prefix}_visual", 0))
    else NotificationManager.Policy(ledger.getInt("${prefix}_categories", 0), ledger.getInt("${prefix}_calls", 0), ledger.getInt("${prefix}_messages", 0))

    fun capabilityNote(people: List<FocusPerson>, settings: FocusPolicySettings): String {
        return "Selected people with Calls enabled can ring through for ordinary phone calls. Other screened calls are silenced, not rejected." +
            (if (settings.allowRepeatedCallers) " A repeat caller within 15 minutes can also ring." else "") +
            " Contacts access and Lumen's caller ID & spam role are required. Silent mode, zero ring volume, or another DND rule can still silence calls. WhatsApp and other internet calls are not controlled by this list." +
            (if (settings.allowMessagesFromSelected) " Message exceptions still use ALL Android favorites, not this list." else " Message sounds are silenced by Lumen's DND rule.")
    }
}
