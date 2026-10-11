package com.lumen.launcher.widgets

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class WidgetContact(val name: String, val number: String, val photo: String? = null, val photoUri: String? = null)

/** Four deliberately chosen contacts, independent for every widget instance. */
object WidgetContacts {
    fun read(context: Context, id: Int): List<WidgetContact?> = runCatching {
        val array = JSONArray(context.getSharedPreferences("widget_contacts", Context.MODE_PRIVATE)
            .getString(id.toString(), "[]"))
        List(4) { index -> array.optJSONObject(index)?.let {
            WidgetContact(it.getString("name"), it.getString("number"), it.optString("photo").takeIf(String::isNotBlank), it.optString("photoUri").takeIf(String::isNotBlank))
        } }
    }.getOrDefault(List(4) { null })

    @Synchronized fun set(context: Context, id: Int, slot: Int, contact: WidgetContact?) {
        require(slot in 0..3)
        val contacts = read(context, id).toMutableList()
        val previous = contacts[slot]
        contacts[slot] = contact
        val array = JSONArray()
        contacts.forEach { array.put(it?.let { person -> JSONObject().put("name", person.name)
            .put("number", person.number).put("photo", person.photo.orEmpty()).put("photoUri", person.photoUri.orEmpty()) } ?: JSONObject.NULL) }
        context.getSharedPreferences("widget_contacts", Context.MODE_PRIVATE).edit()
            .putString(id.toString(), array.toString()).commit()
        previous?.photo?.takeIf { it != contact?.photo }?.let { java.io.File(it).delete() }
    }

    fun remove(context: Context, id: Int) {
        read(context, id).mapNotNull { it?.photo }.forEach { java.io.File(it).delete() }
        context.getSharedPreferences("widget_contacts", Context.MODE_PRIVATE).edit().remove(id.toString()).apply()
    }
}
