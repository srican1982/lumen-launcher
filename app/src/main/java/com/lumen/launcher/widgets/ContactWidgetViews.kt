package com.lumen.launcher.widgets

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CallLog
import android.telephony.PhoneNumberUtils
import android.view.View
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import com.lumen.launcher.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ContactWidgetViews {
    fun views(context: Context, id: Int, kind: WidgetKind, data: WidgetSnapshot): RemoteViews {
        val horizontal = kind in setOf(WidgetKind.ContactsRow, WidgetKind.ContactsQuick, WidgetKind.ContactsFamily)
        val view = RemoteViews(context.packageName, if (horizontal) R.layout.widget_contact_row else R.layout.widget_contact_card)
        val light = kind == WidgetKind.ContactsFamily
        val text = if (light) 0xFF152237.toInt() else 0xFFF5F7FF.toInt()
        val muted = if (light) 0xFF526174.toInt() else 0xFFBAC8DB.toInt()
        view.setInt(R.id.contact_card_root, "setBackgroundResource", when (kind) {
            WidgetKind.ContactsRecent -> R.drawable.widget_contact_recent_glass
            WidgetKind.ContactsSingle -> R.drawable.widget_contact_single_glass
            WidgetKind.ContactsRow -> R.drawable.widget_contact_people_glass
            WidgetKind.ContactsQuick -> R.drawable.widget_contact_quick_glass
            WidgetKind.ContactsFamily -> R.drawable.widget_contact_family_glass
            else -> R.drawable.widget_contact_dark_glass
        })
        view.setImageViewResource(R.id.contact_card_add, if (light) R.drawable.widget_contact_plus_dark else R.drawable.widget_contact_plus)
        view.setInt(R.id.contact_card_add, "setBackgroundResource", if (light) R.drawable.widget_contact_light_button else R.drawable.widget_contact_button)
        view.setTextViewTextSize(R.id.contact_card_title, android.util.TypedValue.COMPLEX_UNIT_SP, if (kind == WidgetKind.ContactsSingle) 15f else 18f)
        view.setTextColor(R.id.contact_card_title, text); view.setTextColor(R.id.contact_card_subtitle, muted)
        view.setTextViewText(R.id.contact_card_title, when (kind) {
            WidgetKind.Contacts -> "Favorites"
            WidgetKind.ContactsSingle -> "Your Person"
            else -> kind.title
        })
        view.setOnClickPendingIntent(R.id.contact_card_root, WidgetActions.contacts(context, id))
        view.setOnClickPendingIntent(R.id.contact_card_add, WidgetActions.contacts(context, id))
        listOf(R.id.contact_card_recent, R.id.contact_card_group, R.id.contact_card_search, R.id.contact_card_extra).forEach { view.setViewVisibility(it, View.GONE) }
        view.setViewVisibility(R.id.contact_card_people, View.VISIBLE)
        if (horizontal) view.setViewVisibility(R.id.contact_card_note, if (kind == WidgetKind.ContactsFamily && !data.locked) View.VISIBLE else View.GONE)
        view.setViewVisibility(R.id.contact_card_bottom, if (horizontal || kind == WidgetKind.ContactsSingle) View.GONE else View.VISIBLE)
        if (data.locked) {
            view.setTextViewText(R.id.contact_card_subtitle, "Unlock to see your people")
            view.setViewVisibility(R.id.contact_card_people, View.GONE)
            return view
        }
        val people = WidgetContacts.read(context, id)
        val containers = listOf(R.id.contact_card_person1, R.id.contact_card_person2, R.id.contact_card_person3, R.id.contact_card_person4)
        val images = listOf(R.id.contact_card_photo1, R.id.contact_card_photo2, R.id.contact_card_photo3, R.id.contact_card_photo4)
        val labels = listOf(R.id.contact_card_name1, R.id.contact_card_name2, R.id.contact_card_name3, R.id.contact_card_name4)
        val calls = listOf(R.id.contact_card_call1, R.id.contact_card_call2, R.id.contact_card_call3, R.id.contact_card_call4)
        val messages = listOf(R.id.contact_card_message1, R.id.contact_card_message2, R.id.contact_card_message3, R.id.contact_card_message4)
        val actionRows = listOf(R.id.contact_card_actions1, R.id.contact_card_actions2, R.id.contact_card_actions3, R.id.contact_card_actions4)
        people.forEachIndexed { index, person ->
            view.setViewVisibility(containers[index], if (kind == WidgetKind.ContactsSingle && index != 0) View.GONE else View.VISIBLE)
            val name = person?.name ?: "Add"
            val photo = WidgetRenderer.thumbnail(context, person?.photo ?: person?.photoUri)
            view.setImageViewBitmap(images[index], if (kind == WidgetKind.ContactsFamily && photo != null) portrait(photo)
                else contactAvatar(photo, person?.name ?: "+"))
            view.setTextViewText(labels[index], name); view.setTextColor(labels[index], text)
            view.setContentDescription(images[index], if (person == null) "Choose contact" else person.name)
            view.setOnClickPendingIntent(images[index], if (person == null) WidgetActions.contacts(context, id, index) else WidgetActions.dial(context, id, index, person.number))
            view.setOnClickPendingIntent(labels[index], WidgetActions.contacts(context, id, index))
            view.setInt(calls[index], "setBackgroundResource", if (light) R.drawable.widget_contact_light_button else R.drawable.widget_contact_button)
            view.setViewVisibility(actionRows[index], if (person != null && kind !in setOf(WidgetKind.Contacts, WidgetKind.ContactsRow)) View.VISIBLE else View.GONE)
            if (person != null) {
                view.setOnClickPendingIntent(calls[index], WidgetActions.dial(context, id, index, person.number))
                view.setOnClickPendingIntent(messages[index], WidgetActions.message(context, id, index, listOf(person.number)))
            }
        }
        view.setTextViewText(R.id.contact_card_subtitle, when (kind) {
            WidgetKind.ContactsSingle -> "Call or message your person"
            WidgetKind.ContactsQuick -> "Find. Call. Message."
            WidgetKind.ContactsFamily -> "Always connected."
            WidgetKind.ContactsRecent -> "Your recent calls"
            else -> "The important ones."
        })
        if (kind == WidgetKind.ContactsSingle) people.firstOrNull()?.let { person ->
            view.setViewVisibility(R.id.contact_card_extra, View.VISIBLE)
            view.setOnClickPendingIntent(R.id.contact_card_video, WidgetActions.contactVideo(context, id, person.number))
            val email = email(context, person.number)
            view.setViewVisibility(R.id.contact_card_email, if (email == null) View.GONE else View.VISIBLE)
            if (email != null) view.setOnClickPendingIntent(R.id.contact_card_email, WidgetActions.contactEmail(context, id, email))
        }
        if (kind == WidgetKind.ContactsQuick) {
            view.setViewVisibility(R.id.contact_card_search, View.VISIBLE)
            view.setOnClickPendingIntent(R.id.contact_card_search, WidgetActions.pending(context, id, "Contacts"))
        }
        if (kind == WidgetKind.ContactsFamily && people.any { it != null }) {
            view.setViewVisibility(R.id.contact_card_group, View.VISIBLE)
            view.setOnClickPendingIntent(R.id.contact_card_group, WidgetActions.message(context, id, -1, people.filterNotNull().map { it.number }))
        }
        if (kind == WidgetKind.ContactsRecent) {
            view.setViewVisibility(R.id.contact_card_people, View.GONE)
            val permission = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED
            val recent = if (permission) recent(context, people) else emptyList()
            val rowIds = listOf(R.id.contact_card_recent_row1, R.id.contact_card_recent_row2, R.id.contact_card_recent_row3, R.id.contact_card_recent_row4)
            val photoIds = listOf(R.id.contact_card_recent_photo1, R.id.contact_card_recent_photo2, R.id.contact_card_recent_photo3, R.id.contact_card_recent_photo4)
            val nameIds = listOf(R.id.contact_card_recent_name1, R.id.contact_card_recent_name2, R.id.contact_card_recent_name3, R.id.contact_card_recent_name4)
            val timeIds = listOf(R.id.contact_card_recent_time1, R.id.contact_card_recent_time2, R.id.contact_card_recent_time3, R.id.contact_card_recent_time4)
            val callIds = listOf(R.id.contact_card_recent_call1, R.id.contact_card_recent_call2, R.id.contact_card_recent_call3, R.id.contact_card_recent_call4)
            val messageIds = listOf(R.id.contact_card_recent_message1, R.id.contact_card_recent_message2, R.id.contact_card_recent_message3, R.id.contact_card_recent_message4)
            view.setViewVisibility(R.id.contact_card_recent, View.VISIBLE)
            rowIds.forEachIndexed { index, row ->
                val item = recent.getOrNull(index)
                view.setViewVisibility(row, if (item == null) View.GONE else View.VISIBLE)
                if (item != null) {
                    val (slot, date) = item; val person = people[slot]!!
                    view.setImageViewBitmap(photoIds[index], contactAvatar(WidgetRenderer.thumbnail(context, person.photo ?: person.photoUri), person.name))
                    view.setTextViewText(nameIds[index], person.name); view.setTextColor(nameIds[index], text)
                    view.setTextViewText(timeIds[index], SimpleDateFormat("MMM d h:mm a", Locale.getDefault()).format(Date(date))); view.setTextColor(timeIds[index], muted)
                    view.setOnClickPendingIntent(callIds[index], WidgetActions.dial(context, id, slot, person.number))
                    view.setOnClickPendingIntent(messageIds[index], WidgetActions.message(context, id, slot, listOf(person.number)))
                }
            }
            if (recent.isEmpty()) view.setTextViewText(R.id.contact_card_subtitle, if (!permission) "Allow Call logs in Android settings to see recent calls." else "Choose people with +. Their recent calls appear here.")
        }
        return view
    }

    private fun contactAvatar(source: android.graphics.Bitmap?, name: String): android.graphics.Bitmap {
        val result = android.graphics.Bitmap.createBitmap(128, 128, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(result)
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        paint.color = 0xFF546676.toInt(); canvas.drawCircle(64f, 64f, 61f, paint)
        if (source != null) {
            val shader = android.graphics.BitmapShader(source, android.graphics.Shader.TileMode.CLAMP, android.graphics.Shader.TileMode.CLAMP)
            val scale = 120f / minOf(source.width, source.height)
            shader.setLocalMatrix(android.graphics.Matrix().apply {
                setScale(scale, scale); postTranslate((128 - source.width * scale) / 2f, (128 - source.height * scale) / 2f)
            })
            paint.shader = shader; canvas.drawCircle(64f, 64f, 60f, paint); paint.shader = null
        } else {
            paint.color = android.graphics.Color.WHITE; paint.textSize = 42f; paint.textAlign = android.graphics.Paint.Align.CENTER
            canvas.drawText(WidgetContentRules.initials(name), 64f, 79f, paint)
        }
        paint.style = android.graphics.Paint.Style.STROKE; paint.strokeWidth = 2f; paint.color = 0xFFE8EDF5.toInt()
        canvas.drawCircle(64f, 64f, 61f, paint)
        return result
    }

    private fun portrait(source: android.graphics.Bitmap): android.graphics.Bitmap {
        val image = android.graphics.Bitmap.createBitmap(120, 180, android.graphics.Bitmap.Config.ARGB_8888)
        val shader = android.graphics.BitmapShader(source, android.graphics.Shader.TileMode.CLAMP, android.graphics.Shader.TileMode.CLAMP)
        val scale = maxOf(120f / source.width, 180f / source.height)
        shader.setLocalMatrix(android.graphics.Matrix().apply {
            setScale(scale, scale); postTranslate((120f - source.width * scale) / 2f, (180f - source.height * scale) / 2f)
        })
        android.graphics.Canvas(image).drawRoundRect(android.graphics.RectF(0f, 0f, 120f, 180f), 14f, 14f,
            android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { this.shader = shader })
        return image
    }

    private fun email(context: Context, number: String): String? {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return null
        return runCatching {
            val uri = android.net.Uri.withAppendedPath(android.provider.ContactsContract.PhoneLookup.CONTENT_FILTER_URI, android.net.Uri.encode(number))
            val contact = context.contentResolver.query(uri, arrayOf(android.provider.ContactsContract.PhoneLookup._ID), null, null, null)?.use {
                if (it.moveToFirst()) it.getLong(0) else null
            } ?: return@runCatching null
            context.contentResolver.query(android.provider.ContactsContract.CommonDataKinds.Email.CONTENT_URI,
                arrayOf(android.provider.ContactsContract.CommonDataKinds.Email.ADDRESS),
                "${android.provider.ContactsContract.CommonDataKinds.Email.CONTACT_ID} = ?", arrayOf(contact.toString()), null)?.use {
                if (it.moveToFirst()) it.getString(0)?.takeIf(String::isNotBlank) else null
            }
        }.getOrNull()
    }

    private fun recent(context: Context, people: List<WidgetContact?>): List<Pair<Int, Long>> = runCatching {
        val result = mutableListOf<Pair<Int, Long>>()
        context.contentResolver.query(CallLog.Calls.CONTENT_URI, arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.DATE), null, null, "${CallLog.Calls.DATE} DESC")?.use { cursor ->
            var scanned = 0
            while (cursor.moveToNext() && scanned++ < 200 && result.size < 4) {
                val slot = people.indexOfFirst { it != null && PhoneNumberUtils.compare(it.number, cursor.getString(0)) }
                if (slot >= 0 && result.none { it.first == slot }) result += slot to cursor.getLong(1)
            }
        }
        result
    }.getOrDefault(emptyList())
}
