package com.lumen.launcher.social.stickers

import android.content.ContentProvider
import android.content.ContentValues
import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.FileNotFoundException

/**
 * Exposes Lumen's sticker packs to WhatsApp. The contract (paths + column names) is copied
 * verbatim from WhatsApp's official sample StickerContentProvider:
 * https://github.com/WhatsApp/stickers/tree/main/Android
 *
 *   <authority>/metadata                          all packs
 *   <authority>/metadata/<packId>                 one pack
 *   <authority>/stickers/<packId>                 stickers in a pack
 *   <authority>/stickers_asset/<packId>/<file>    sticker WebP or tray PNG
 *
 * Unlike the sample, asset paths are resolved dynamically, because Lumen creates stickers
 * at runtime (the sample registers each file once at startup, which would miss new stickers).
 * Protected by android:readPermission="com.whatsapp.sticker.READ" in the manifest.
 */
class LumenStickerProvider : ContentProvider() {

    companion object {
        private const val METADATA = "metadata"
        private const val STICKERS = "stickers"
        private const val STICKERS_ASSET = "stickers_asset"

        // Pack columns (names must match WhatsApp exactly).
        private const val STICKER_PACK_IDENTIFIER_IN_QUERY = "sticker_pack_identifier"
        private const val STICKER_PACK_NAME_IN_QUERY = "sticker_pack_name"
        private const val STICKER_PACK_PUBLISHER_IN_QUERY = "sticker_pack_publisher"
        private const val STICKER_PACK_ICON_IN_QUERY = "sticker_pack_icon"
        private const val ANDROID_APP_DOWNLOAD_LINK_IN_QUERY = "android_play_store_link"
        private const val IOS_APP_DOWNLOAD_LINK_IN_QUERY = "ios_app_download_link"
        private const val PUBLISHER_EMAIL = "sticker_pack_publisher_email"
        private const val PUBLISHER_WEBSITE = "sticker_pack_publisher_website"
        private const val PRIVACY_POLICY_WEBSITE = "sticker_pack_privacy_policy_website"
        private const val LICENSE_AGREEMENT_WEBSITE = "sticker_pack_license_agreement_website"
        private const val IMAGE_DATA_VERSION = "image_data_version"
        private const val AVOID_CACHE = "whatsapp_will_not_cache_stickers"
        private const val ANIMATED_STICKER_PACK = "animated_sticker_pack"

        // Sticker columns.
        private const val STICKER_FILE_NAME_IN_QUERY = "sticker_file_name"
        private const val STICKER_FILE_EMOJI_IN_QUERY = "sticker_emoji"
        private const val STICKER_FILE_ACCESSIBILITY_TEXT_IN_QUERY = "sticker_accessibility_text"

        private val SAFE_NAME = Regex("^[A-Za-z0-9._-]{1,128}$")
    }

    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? {
        val ctx = context ?: return null
        val segments = uri.pathSegments
        // Only packs that meet WhatsApp's 3–30 rule are ever exposed.
        val packs = StickerLibrary.current(ctx).filter { it.isValid }
        return when {
            segments.size == 1 && segments[0] == METADATA -> packInfo(uri, packs)
            segments.size == 2 && segments[0] == METADATA -> packInfo(uri, packs.filter { it.identifier == segments[1] })
            segments.size == 2 && segments[0] == STICKERS -> stickersFor(uri, packs.firstOrNull { it.identifier == segments[1] })
            else -> null
        }
    }

    private fun packInfo(uri: Uri, packs: List<LumenStickerPack>): Cursor {
        val cursor = MatrixCursor(
            arrayOf(
                STICKER_PACK_IDENTIFIER_IN_QUERY,
                STICKER_PACK_NAME_IN_QUERY,
                STICKER_PACK_PUBLISHER_IN_QUERY,
                STICKER_PACK_ICON_IN_QUERY,
                ANDROID_APP_DOWNLOAD_LINK_IN_QUERY,
                IOS_APP_DOWNLOAD_LINK_IN_QUERY,
                PUBLISHER_EMAIL,
                PUBLISHER_WEBSITE,
                PRIVACY_POLICY_WEBSITE,
                LICENSE_AGREEMENT_WEBSITE,
                IMAGE_DATA_VERSION,
                AVOID_CACHE,
                ANIMATED_STICKER_PACK
            )
        )
        packs.forEach { p ->
            cursor.newRow()
                .add(p.identifier)
                .add(p.name)
                .add(StickerLibrary.publisher)
                .add(p.trayFile)
                .add("")
                .add("")
                .add("")
                .add("")
                .add("")
                .add("")
                .add(p.imageDataVersion.toString())
                .add(0) // deprecated: WhatsApp ≥2.25.9.78 always caches
                .add(0) // static stickers only
        }
        context?.let { cursor.setNotificationUri(it.contentResolver, uri) }
        return cursor
    }

    private fun stickersFor(uri: Uri, pack: LumenStickerPack?): Cursor {
        val cursor = MatrixCursor(
            arrayOf(STICKER_FILE_NAME_IN_QUERY, STICKER_FILE_EMOJI_IN_QUERY, STICKER_FILE_ACCESSIBILITY_TEXT_IN_QUERY)
        )
        pack?.stickers?.forEach { s ->
            cursor.addRow(arrayOf<Any>(s.fileName, s.emojis.joinToString(","), s.accessibilityText))
        }
        context?.let { cursor.setNotificationUri(it.contentResolver, uri) }
        return cursor
    }

    override fun openAssetFile(uri: Uri, mode: String): AssetFileDescriptor? {
        val ctx = context ?: return null
        val segments = uri.pathSegments
        if (segments.size != 3 || segments[0] != STICKERS_ASSET) throw FileNotFoundException(uri.toString())
        val packId = segments[1]
        val fileName = segments[2]
        if (!SAFE_NAME.matches(packId) || !SAFE_NAME.matches(fileName)) throw FileNotFoundException(uri.toString())
        val file = StickerLibrary.resolveAsset(ctx, packId, fileName) ?: throw FileNotFoundException(uri.toString())
        return AssetFileDescriptor(
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY),
            0,
            AssetFileDescriptor.UNKNOWN_LENGTH
        )
    }

    override fun getType(uri: Uri): String? {
        val segments = uri.pathSegments
        val authority = uri.authority ?: return null
        return when {
            segments.size == 1 && segments[0] == METADATA -> "vnd.android.cursor.dir/vnd.$authority.$METADATA"
            segments.size == 2 && segments[0] == METADATA -> "vnd.android.cursor.item/vnd.$authority.$METADATA"
            segments.size == 2 && segments[0] == STICKERS -> "vnd.android.cursor.dir/vnd.$authority.$STICKERS"
            segments.size == 3 && segments[0] == STICKERS_ASSET ->
                if (segments[2].endsWith(".png")) "image/png" else "image/webp"
            else -> null
        }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? =
        throw UnsupportedOperationException("Not supported")

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int =
        throw UnsupportedOperationException("Not supported")

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int =
        throw UnsupportedOperationException("Not supported")
}
