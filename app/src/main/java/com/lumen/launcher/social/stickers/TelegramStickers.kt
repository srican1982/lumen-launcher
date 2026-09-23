package com.lumen.launcher.social.stickers

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/**
 * Sends a Lumen sticker pack to Telegram using Telegram's sticker import
 * (https://core.telegram.org/import-stickers, Telegram 7.8+).
 *
 * Unlike WhatsApp, Telegram takes a one-time copy: each import creates a new pack in
 * Telegram, and stickers added in Lumen later need another import.
 */
object TelegramStickers {
    private const val ACTION = "org.telegram.messenger.CREATE_STICKER_PACK"
    private const val EXTRA_EMOJIS = "STICKER_EMOJIS"
    private const val EXTRA_IMPORTER = "IMPORTER"

    private fun baseIntent() = Intent(ACTION).setType("image/*")

    /** True if Telegram (or another app supporting the import) is installed. */
    fun isAvailable(context: Context): Boolean =
        baseIntent().resolveActivity(context.packageManager) != null

    fun importIntent(context: Context, pack: LumenStickerPack): Intent? {
        if (pack.stickers.isEmpty()) return null
        val authority = "${context.packageName}.fileprovider"
        val dir = StickerLibrary.packDir(context, pack.identifier)
        val uris = ArrayList<Uri>()
        val emojis = ArrayList<String>()
        pack.stickers.forEach { s ->
            val file = File(dir, s.fileName)
            if (file.exists()) {
                uris += FileProvider.getUriForFile(context, authority, file)
                emojis += s.emojis.firstOrNull() ?: "✨"
            }
        }
        if (uris.isEmpty()) return null
        val clip = ClipData.newRawUri(pack.name, uris.first())
        uris.drop(1).forEach { clip.addItem(ClipData.Item(it)) }
        return baseIntent().apply {
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            putStringArrayListExtra(EXTRA_EMOJIS, emojis)
            putExtra(EXTRA_IMPORTER, context.packageName)
            clipData = clip
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
