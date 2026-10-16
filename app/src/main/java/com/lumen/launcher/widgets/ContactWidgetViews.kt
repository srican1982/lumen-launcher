package com.lumen.launcher.widgets

import android.Manifest
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import com.lumen.launcher.R

/** Renders Favorites, Single Contact, and People widgets. */
object ContactWidgetViews {
    fun views(context: Context, id: Int, kind: WidgetKind, data: WidgetSnapshot): RemoteViews {
        val layout = when (kind) {
            WidgetKind.Contacts -> R.layout.widget_contacts_favorites
            WidgetKind.ContactsSingle -> R.layout.widget_contacts_single
            WidgetKind.ContactsRow -> R.layout.widget_contacts_people
            else -> R.layout.widget_contacts_favorites
        }
        val view = RemoteViews(context.packageName, layout)
        view.setOnClickPendingIntent(R.id.contact_card_root, WidgetActions.contacts(context, id))
        runCatching { view.setOnClickPendingIntent(R.id.contact_card_add, WidgetActions.contacts(context, id)) }
        val options = if (id > 0) AppWidgetManager.getInstance(context).getAppWidgetOptions(id) else android.os.Bundle()
        val width = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, if (kind.wide) 300 else 150)
        val height = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, kind.heightDp)
        val scale = WidgetContentRules.scale(width, height, kind)

        if (data.locked) {
            setText(view, R.id.contact_card_subtitle, "Unlock to see your people")
            return view
        }

        val people = WidgetContacts.read(context, id)
        return when (kind) {
            WidgetKind.Contacts -> bindFavorites(context, id, view, people, scale)
            WidgetKind.ContactsSingle -> bindSingle(context, id, view, people, scale)
            WidgetKind.ContactsRow -> bindPeople(context, id, view, people, scale)
            else -> view
        }
    }

    private fun bindFavorites(
        context: Context,
        id: Int,
        view: RemoteViews,
        people: List<WidgetContact?>,
        scale: WidgetContentRules.SizeScale
    ): RemoteViews {
        setText(view, R.id.contact_card_title, "Favorites")
        setText(view, R.id.contact_card_subtitle, "Tap to call or message")
        view.setTextViewTextSize(R.id.contact_card_title, TypedValue.COMPLEX_UNIT_SP, scale.sp(15f))
        view.setTextViewTextSize(R.id.contact_card_subtitle, TypedValue.COMPLEX_UNIT_SP, scale.sp(11f))
        val face = scale.dp(48f)
        val photos = listOf(
            R.id.contact_card_photo1, R.id.contact_card_photo2,
            R.id.contact_card_photo3, R.id.contact_card_photo4
        )
        val names = listOf(
            R.id.contact_card_name1, R.id.contact_card_name2,
            R.id.contact_card_name3, R.id.contact_card_name4
        )
        people.forEachIndexed { index, person ->
            WidgetContentRules.setDipSize(view, photos[index], face, face)
            view.setTextViewTextSize(names[index], TypedValue.COMPLEX_UNIT_SP, scale.sp(11f))
            bindFace(context, id, view, index, person, photos[index], names[index])
        }
        return view
    }

    private fun bindSingle(
        context: Context,
        id: Int,
        view: RemoteViews,
        people: List<WidgetContact?>,
        scale: WidgetContentRules.SizeScale
    ): RemoteViews {
        val face = scale.dp(64f)
        val action = scale.dp(36f)
        WidgetContentRules.setDipSize(view, R.id.contact_card_photo1, face, face)
        view.setTextViewTextSize(R.id.contact_card_name1, TypedValue.COMPLEX_UNIT_SP, scale.sp(15f))
        view.setTextViewTextSize(R.id.contact_card_subtitle, TypedValue.COMPLEX_UNIT_SP, scale.sp(11f))
        listOf(R.id.contact_card_call1, R.id.contact_card_message1, R.id.contact_card_video, R.id.contact_card_email)
            .forEach { WidgetContentRules.setDipSize(view, it, action, action) }
        val person = people.firstOrNull()
        if (person == null) {
            view.setImageViewBitmap(R.id.contact_card_photo1, contactAvatar(null, "+", status = false))
            setText(view, R.id.contact_card_name1, "Add someone")
            setText(view, R.id.contact_card_subtitle, "Choose your person")
            view.setOnClickPendingIntent(R.id.contact_card_photo1, WidgetActions.contacts(context, id, 0))
            view.setOnClickPendingIntent(R.id.contact_card_name1, WidgetActions.contacts(context, id, 0))
            listOf(R.id.contact_card_call1, R.id.contact_card_message1, R.id.contact_card_video, R.id.contact_card_email)
                .forEach { view.setViewVisibility(it, View.INVISIBLE) }
            return view
        }
        val photo = WidgetRenderer.thumbnail(context, person.photo ?: person.photoUri)
        view.setImageViewBitmap(R.id.contact_card_photo1, contactAvatar(photo, person.name, status = true))
        setText(view, R.id.contact_card_name1, firstName(person.name))
        setText(view, R.id.contact_card_subtitle, "Favorite")
        view.setOnClickPendingIntent(R.id.contact_card_photo1, WidgetActions.dial(context, id, 0, person.number))
        view.setOnClickPendingIntent(R.id.contact_card_name1, WidgetActions.contacts(context, id, 0))
        view.setViewVisibility(R.id.contact_card_call1, View.VISIBLE)
        view.setViewVisibility(R.id.contact_card_message1, View.VISIBLE)
        view.setViewVisibility(R.id.contact_card_video, View.VISIBLE)
        view.setOnClickPendingIntent(R.id.contact_card_call1, WidgetActions.dial(context, id, 0, person.number))
        view.setOnClickPendingIntent(R.id.contact_card_message1, WidgetActions.message(context, id, 0, listOf(person.number)))
        view.setOnClickPendingIntent(R.id.contact_card_video, WidgetActions.contactVideo(context, id, person.number))
        val email = email(context, person.number)
        view.setViewVisibility(R.id.contact_card_email, if (email == null) View.GONE else View.VISIBLE)
        if (email != null) {
            view.setOnClickPendingIntent(R.id.contact_card_email, WidgetActions.contactEmail(context, id, email))
        }
        return view
    }

    private fun bindPeople(
        context: Context,
        id: Int,
        view: RemoteViews,
        people: List<WidgetContact?>,
        scale: WidgetContentRules.SizeScale
    ): RemoteViews {
        setText(view, R.id.contact_card_title, "People")
        setText(view, R.id.contact_card_subtitle, "The important ones")
        view.setTextViewTextSize(R.id.contact_card_title, TypedValue.COMPLEX_UNIT_SP, scale.sp(16f))
        view.setTextViewTextSize(R.id.contact_card_subtitle, TypedValue.COMPLEX_UNIT_SP, scale.sp(11f))
        val face = scale.dp(52f)
        view.setOnClickPendingIntent(R.id.contact_card_add_label, WidgetActions.contacts(context, id))
        view.setOnClickPendingIntent(R.id.contact_card_add_circle, WidgetActions.contacts(context, id, emptySlot(people)))
        WidgetContentRules.setDipSize(view, R.id.contact_card_add_circle, face, face)
        val photos = listOf(
            R.id.contact_card_photo1, R.id.contact_card_photo2,
            R.id.contact_card_photo3, R.id.contact_card_photo4
        )
        val names = listOf(
            R.id.contact_card_name1, R.id.contact_card_name2,
            R.id.contact_card_name3, R.id.contact_card_name4
        )
        people.forEachIndexed { index, person ->
            WidgetContentRules.setDipSize(view, photos[index], face, face)
            view.setTextViewTextSize(names[index], TypedValue.COMPLEX_UNIT_SP, scale.sp(11f))
            bindFace(context, id, view, index, person, photos[index], names[index])
        }
        return view
    }

    private fun bindFace(
        context: Context,
        id: Int,
        view: RemoteViews,
        index: Int,
        person: WidgetContact?,
        photoId: Int,
        nameId: Int
    ) {
        if (person == null) {
            view.setImageViewBitmap(photoId, contactAvatar(null, "+", status = false))
            setText(view, nameId, "Add")
            view.setOnClickPendingIntent(photoId, WidgetActions.contacts(context, id, index))
            view.setOnClickPendingIntent(nameId, WidgetActions.contacts(context, id, index))
        } else {
            val photo = WidgetRenderer.thumbnail(context, person.photo ?: person.photoUri)
            view.setImageViewBitmap(photoId, contactAvatar(photo, person.name, status = true))
            setText(view, nameId, firstName(person.name))
            view.setOnClickPendingIntent(photoId, WidgetActions.dial(context, id, index, person.number))
            view.setOnClickPendingIntent(nameId, WidgetActions.contacts(context, id, index))
        }
    }

    private fun contactAvatar(
        source: Bitmap?,
        name: String,
        status: Boolean
    ): Bitmap {
        val result = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = 0xFF4A5A6E.toInt()
        canvas.drawCircle(64f, 64f, 60f, paint)
        if (source != null) {
            val shader = BitmapShader(source, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            val scale = 120f / minOf(source.width, source.height)
            shader.setLocalMatrix(Matrix().apply {
                setScale(scale, scale)
                postTranslate((128 - source.width * scale) / 2f, (128 - source.height * scale) / 2f)
            })
            paint.shader = shader
            canvas.drawCircle(64f, 64f, 58f, paint)
            paint.shader = null
        } else {
            paint.color = Color.WHITE
            paint.textSize = 40f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText(WidgetContentRules.initials(name), 64f, 78f, paint)
        }
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.5f
        paint.color = 0x66FFFFFF.toInt()
        canvas.drawCircle(64f, 64f, 60f, paint)
        if (status) {
            paint.style = Paint.Style.FILL
            paint.color = 0xFF121820.toInt()
            canvas.drawCircle(98f, 98f, 14f, paint)
            paint.color = 0xFF4ADE80.toInt()
            canvas.drawCircle(98f, 98f, 10f, paint)
        }
        return result
    }

    private fun email(context: Context, number: String): String? {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) !=
            PackageManager.PERMISSION_GRANTED
        ) return null
        return runCatching {
            val uri = android.net.Uri.withAppendedPath(
                android.provider.ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                android.net.Uri.encode(number)
            )
            val contact = context.contentResolver.query(
                uri,
                arrayOf(android.provider.ContactsContract.PhoneLookup._ID),
                null,
                null,
                null
            )?.use {
                if (it.moveToFirst()) it.getLong(0) else null
            } ?: return@runCatching null
            context.contentResolver.query(
                android.provider.ContactsContract.CommonDataKinds.Email.CONTENT_URI,
                arrayOf(android.provider.ContactsContract.CommonDataKinds.Email.ADDRESS),
                "${android.provider.ContactsContract.CommonDataKinds.Email.CONTACT_ID} = ?",
                arrayOf(contact.toString()),
                null
            )?.use {
                if (it.moveToFirst()) it.getString(0)?.takeIf(String::isNotBlank) else null
            }
        }.getOrNull()
    }

    private fun firstName(name: String): String = name.trim().substringBefore(' ').ifBlank { name }

    private fun emptySlot(people: List<WidgetContact?>): Int =
        people.indexOfFirst { it == null }.takeIf { it >= 0 } ?: 0

    private fun setText(view: RemoteViews, id: Int, value: String) {
        runCatching { view.setTextViewText(id, value) }
    }
}
