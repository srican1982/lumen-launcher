package com.lumen.launcher.widgets

import android.app.AlertDialog
import android.appwidget.AppWidgetManager
import android.os.Bundle
import android.content.Intent
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A small widget editor, never the launcher's Contacts page. The system picker
 * grants access to just the chosen phone row; no broad contacts permission needed. */
class WidgetContactsActivity : ComponentActivity() {
    private var widgetId = -1
    private var slot = 0
    private var waitingForPicker = false
    private val photoPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        lifecycleScope.launch {
            if (granted) withContext(Dispatchers.IO) {
                val person = WidgetContacts.read(this@WidgetContactsActivity, widgetId)[slot]
                val path = person?.photoUri?.let(::cachePhoto)
                if (person != null && path != null) WidgetContacts.set(this@WidgetContactsActivity, widgetId, slot, person.copy(photo = path))
            }
            LumenWidgetProvider.refreshAll(this@WidgetContactsActivity)
            showSlots()
        }
    }
    private val pick = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        waitingForPicker = false
        val uri = result.data?.data
        if (result.resultCode != RESULT_OK || uri == null) { showSlots(); return@registerForActivityResult }
        lifecycleScope.launch {
            val contact = withContext(Dispatchers.IO) {
                runCatching {
                    contentResolver.query(uri, arrayOf(Phone.DISPLAY_NAME, Phone.NUMBER, Phone.PHOTO_URI), null, null, null)?.use { cursor ->
                        if (!cursor.moveToFirst()) return@use null
                        val name = cursor.getString(0).orEmpty()
                        val number = cursor.getString(1).orEmpty()
                        if (number.isBlank()) return@use null
                        val photoUri = cursor.getString(2)
                        WidgetContact(name.ifBlank { number }, number, photoUri?.let(::cachePhoto), photoUri)
                    }
                }.getOrNull()
            }
            if (contact != null) {
                withContext(Dispatchers.IO) { WidgetContacts.set(this@WidgetContactsActivity, widgetId, slot, contact) }
                LumenWidgetProvider.refreshAll(this@WidgetContactsActivity)
                if (contact.photo == null && contact.photoUri != null &&
                    androidx.core.content.ContextCompat.checkSelfPermission(this@WidgetContactsActivity, android.Manifest.permission.READ_CONTACTS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    photoPermission.launch(android.Manifest.permission.READ_CONTACTS)
                    return@launch
                }
            } else Toast.makeText(this@WidgetContactsActivity, "Choose a contact with a phone number", Toast.LENGTH_SHORT).show()
            showSlots()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)
        slot = savedInstanceState?.getInt("slot") ?: intent.getIntExtra("slot", -1)
        waitingForPicker = savedInstanceState?.getBoolean("waiting") ?: false
        if (WidgetKind.entries.filter { it.isContact }.none { widgetId in AppWidgetManager.getInstance(this).getAppWidgetIds(LumenWidgetProvider.component(this, it)) }) { finish(); return }
        if (!waitingForPicker) {
            if (savedInstanceState == null && slot in 0..3) choose(slot) else showSlots()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) { outState.putInt("slot", slot); outState.putBoolean("waiting", waitingForPicker); super.onSaveInstanceState(outState) }

    private fun showSlots() {
        if (isFinishing) return
        val people = WidgetContacts.read(this, widgetId)
        AlertDialog.Builder(this).setTitle("Your widget contacts")
            .setItems(people.mapIndexed { i, p -> "${i + 1}. ${p?.name ?: "Add contact"}" }.toTypedArray()) { _, index -> choose(index) }
            .setPositiveButton("Done") { _, _ -> finish() }.setOnCancelListener { finish() }.show()
    }

    private fun choose(index: Int) {
        slot = index
        val existing = WidgetContacts.read(this, widgetId)[slot]
        if (existing == null) launchPicker() else AlertDialog.Builder(this).setTitle(existing.name)
            .setItems(arrayOf("Replace contact", "Remove from widget")) { _, option ->
                if (option == 0) launchPicker() else lifecycleScope.launch {
                    withContext(Dispatchers.IO) { WidgetContacts.set(this@WidgetContactsActivity, widgetId, slot, null) }
                    LumenWidgetProvider.refreshAll(this@WidgetContactsActivity)
                    showSlots()
                }
            }.setNegativeButton("Back") { _, _ -> showSlots() }.setOnCancelListener { showSlots() }.show()
    }

    private fun launchPicker() {
        waitingForPicker = true
        runCatching { pick.launch(Intent(Intent.ACTION_PICK, Phone.CONTENT_URI)) }
            .onFailure { waitingForPicker = false; Toast.makeText(this, "No contact picker is available", Toast.LENGTH_SHORT).show(); showSlots() }
    }

    private fun cachePhoto(source: String): String? = runCatching {
        val uri = android.net.Uri.parse(source)
        val bitmap = contentResolver.openInputStream(uri)?.use {
            android.graphics.BitmapFactory.decodeStream(it, null, android.graphics.BitmapFactory.Options().apply { inSampleSize = 2 })
        } ?: return@runCatching null
        val avatar = android.graphics.Bitmap.createScaledBitmap(bitmap, 128, 128, true)
        val file = java.io.File(filesDir, "widget-contact-${java.util.UUID.randomUUID()}.png")
        file.outputStream().use { avatar.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        file.absolutePath
    }.getOrNull()
}
