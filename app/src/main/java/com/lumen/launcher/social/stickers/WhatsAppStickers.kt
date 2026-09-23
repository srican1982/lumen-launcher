package com.lumen.launcher.social.stickers

import android.app.Activity
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri

/** WhatsApp or WhatsApp Business — both accept third-party sticker packs. */
enum class WhatsAppTarget(val packageName: String, val label: String) {
    Consumer("com.whatsapp", "WhatsApp"),
    Business("com.whatsapp.w4b", "WhatsApp Business")
}

/**
 * Talks to WhatsApp using its documented sticker-pack API:
 * https://github.com/WhatsApp/stickers/tree/main/Android
 */
object WhatsAppStickers {
    private const val ADD_ACTION = "com.whatsapp.intent.action.ENABLE_STICKER_PACK"

    fun providerAuthority(context: Context): String = "${context.packageName}.stickercontentprovider"

    fun installedTargets(context: Context): List<WhatsAppTarget> =
        WhatsAppTarget.entries.filter { isInstalled(context, it.packageName) }

    private fun isInstalled(context: Context, packageName: String): Boolean = runCatching {
        @Suppress("DEPRECATION")
        context.packageManager.getApplicationInfo(packageName, 0).enabled
    }.getOrDefault(false)

    /**
     * Asks WhatsApp whether it already has this pack.
     * Returns true / false, or null if WhatsApp is missing, too old, or the query failed.
     * Do not call on the main thread.
     */
    fun isPackAdded(context: Context, target: WhatsAppTarget, packId: String): Boolean? = runCatching {
        val uri = Uri.Builder()
            .scheme(ContentResolver.SCHEME_CONTENT)
            .authority("${target.packageName}.provider.sticker_whitelist_check")
            .appendPath("is_whitelisted")
            .appendQueryParameter("authority", providerAuthority(context))
            .appendQueryParameter("identifier", packId)
            .build()
        context.contentResolver.query(uri, null, null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getInt(c.getColumnIndexOrThrow("result")) == 1 else null
        }
    }.getOrNull()

    /**
     * The "Add to WhatsApp" intent. It MUST be launched with startActivityForResult
     * (e.g. ActivityResultContracts.StartActivityForResult) so WhatsApp can see which app
     * is asking; WhatsApp then shows its own confirmation.
     */
    fun addPackIntent(context: Context, pack: LumenStickerPack, target: WhatsAppTarget): Intent =
        Intent(ADD_ACTION)
            .putExtra("sticker_pack_id", pack.identifier)
            .putExtra("sticker_pack_authority", providerAuthority(context))
            .putExtra("sticker_pack_name", pack.name)
            .setPackage(target.packageName)

    /** WhatsApp returns RESULT_CANCELED with "validation_error" when it rejects a pack. */
    fun validationError(resultCode: Int, data: Intent?): String? =
        if (resultCode == Activity.RESULT_CANCELED) data?.getStringExtra("validation_error") else null
}
