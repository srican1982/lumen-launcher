package com.lumen.launcher.search

import com.lumen.launcher.data.ContactLookup

object PeopleActions {

    enum class Kind { Call, WhatsApp, WhatsAppCall }

    data class Phrase(val kind: Kind, val rest: String)

    fun parse(raw: String): Phrase? {
        val q = raw.trim().replace(Regex("\\s+"), " ")
        if (q.length < 5) return null
        waCall(q)?.let { return it }
        message(q)?.let { return it }
        phoneCall(q)?.let { return it }
        return null
    }

    fun hits(lookup: ContactLookup, query: String): List<SearchHit.Action> {
        val phrase = parse(query) ?: return emptyList()
        if (!lookup.hasAccess()) {
            return listOf(unresolved(phrase, phrase.rest, needsPermission = true))
        }
        val split = lookup.bestPrefix(phrase.rest)
        if (split == null) {
            return listOf(unresolved(phrase, phrase.rest, needsPermission = false))
        }
        val (contact, leftover) = split
        val message = leftover.takeIf { phrase.kind == Kind.WhatsApp }.orEmpty()
        val others = lookup.matches(contact.name, 3).filter { it.phone != contact.phone }
        return listOf(resolved(phrase.kind, contact.name, contact.phone, message, query)) +
            others.map { resolved(phrase.kind, it.name, it.phone, message, query) }
    }

    private fun resolved(
        kind: Kind,
        name: String,
        phone: String,
        message: String,
        query: String
    ): SearchHit.Action {
        val pretty = prettyPhone(phone)
        return when (kind) {
            Kind.Call -> SearchHit.Action(
                id = "call",
                title = "Call $name",
                subtitle = "$pretty · Phone",
                query = query,
                phone = phone
            )
            Kind.WhatsAppCall -> SearchHit.Action(
                id = "whatsapp_call",
                title = "WhatsApp $name",
                subtitle = "Opens their chat — tap the call button in WhatsApp",
                query = query,
                phone = phone
            )
            Kind.WhatsApp -> SearchHit.Action(
                id = "whatsapp",
                title = if (message.isBlank()) "WhatsApp $name" else "WhatsApp $name: $message",
                subtitle = if (message.isBlank()) "Open their chat" else "Chat opens with this text ready",
                query = query,
                phone = phone,
                message = message
            )
        }
    }

    private fun unresolved(phrase: Phrase, who: String, needsPermission: Boolean): SearchHit.Action {
        val label = who.ifBlank { "this person" }
        val access = if (needsPermission) "Allow Contacts so Lumen can find them" else "No matching contact"
        return when (phrase.kind) {
            Kind.Call -> SearchHit.Action("call", "Call $label", access, phrase.rest)
            Kind.WhatsAppCall -> SearchHit.Action("whatsapp_call", "WhatsApp $label", access, phrase.rest)
            Kind.WhatsApp -> SearchHit.Action("whatsapp", "WhatsApp $label", access, phrase.rest)
        }
    }

    private fun clean(who: String): String {
        return who.trim().replace(Regex("""\s+(?:on|in|via|with)\s+whatsapp$""", RegexOption.IGNORE_CASE), "").trim()
    }

    private fun waCall(q: String): Phrase? {
        Regex("""^(?:whatsapp\s+call|call\s+(?:on|in|via|with)\s+whatsapp)\s+(.+)$""", RegexOption.IGNORE_CASE)
            .find(q)?.let { return Phrase(Kind.WhatsAppCall, clean(it.groupValues[1])) }
        Regex("""^call\s+(.+?)\s+(?:on|in|via|with)\s+whatsapp$""", RegexOption.IGNORE_CASE)
            .find(q)?.let { return Phrase(Kind.WhatsAppCall, clean(it.groupValues[1])) }
        return null
    }

    private fun phoneCall(q: String): Phrase? {
        val match = Regex("""^call\s+(.+)$""", RegexOption.IGNORE_CASE).find(q) ?: return null
        val who = clean(match.groupValues[1])
        if (who.isBlank()) return null
        return Phrase(Kind.Call, who)
    }

    private fun message(q: String): Phrase? {
        Regex(
            """^(?:send\s+(?:a\s+)?(?:message|msg|text)|(?:send\s+)?(?:a\s+)?message|text|msg)\s+(?:to\s+)?(.+)$""",
            RegexOption.IGNORE_CASE
        ).find(q)?.let { return Phrase(Kind.WhatsApp, clean(it.groupValues[1])) }
        Regex("""^whatsapp\s+(?!call\b)(.+)$""", RegexOption.IGNORE_CASE).find(q)?.let {
            return Phrase(Kind.WhatsApp, clean(it.groupValues[1]))
        }
        return null
    }

    private fun prettyPhone(phone: String): String {
        val digits = phone.filter { it.isDigit() || it == '+' }
        return digits.ifBlank { phone }
    }
}
