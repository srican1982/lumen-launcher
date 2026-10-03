package com.lumen.launcher.focus

import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import android.telephony.PhoneNumberUtils

/** Match the selected person, including additional numbers saved on their contact. */
internal object FocusCallerMatcher {
    fun selected(context: Context, people: List<FocusPerson>, incoming: String): Boolean {
        if (incoming.isBlank()) return false
        val allowed = people.filter { it.reach.allowsCalls }
        if (allowed.any { it.phone.isNotBlank() && PhoneNumberUtils.compare(context, it.phone, incoming) }) return true
        val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(incoming))
        return context.contentResolver.query(uri,
            arrayOf(ContactsContract.PhoneLookup._ID, ContactsContract.PhoneLookup.LOOKUP_KEY), null, null, null)?.use { cursor ->
            var match = false
            while (cursor.moveToNext()) {
                val id = cursor.getString(0)
                val lookup = cursor.getString(1).orEmpty()
                if (allowed.any { it.id == id || (lookup.isNotBlank() && it.contactLookupKey == lookup) }) { match = true; break }
            }
            match
        } ?: false
    }
}
