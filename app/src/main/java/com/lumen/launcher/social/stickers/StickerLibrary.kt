package com.lumen.launcher.social.stickers

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.lumen.launcher.social.StickerMaker
import com.lumen.launcher.social.quote.QuoteAspect
import com.lumen.launcher.social.quote.QuoteBackgroundKind
import com.lumen.launcher.social.quote.QuoteStyle
import com.lumen.launcher.social.quote.QuoteStyleRenderer
import com.lumen.launcher.social.scribble.InkStroke
import com.lumen.launcher.social.scribble.ScribbleBrushStyle
import com.lumen.launcher.social.scribble.ScribbleExport
import com.lumen.launcher.social.scribble.ScribbleExportBackground
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

data class LumenSticker(
    val fileName: String,
    val emojis: List<String>,
    val accessibilityText: String,
    val source: String,
    val createdAt: Long
) {
    val isStarter: Boolean get() = source == SOURCE_STARTER

    companion object {
        const val SOURCE_STARTER = "Starter"
    }
}

data class LumenStickerPack(
    val identifier: String,
    val name: String,
    val imageDataVersion: Int,
    val stickers: List<LumenSticker>
) {
    val trayFile: String get() = StickerLibrary.TRAY_FILE
    /** WhatsApp accepts 3–30 static stickers per pack. */
    val isValid: Boolean get() = stickers.size in StickerLibrary.MIN_STICKERS..StickerLibrary.MAX_STICKERS
    val isFull: Boolean get() = stickers.size >= StickerLibrary.MAX_STICKERS
}

sealed interface AddStickerResult {
    data class Added(val pack: LumenStickerPack) : AddStickerResult
    data class Failed(val reason: String) : AddStickerResult
}

/**
 * Lumen's own sticker library, which is also what WhatsApp reads through [LumenStickerProvider].
 *
 * Layout on disk (app-internal, never visible to other apps except via the provider):
 *   filesDir/stickers/index.json
 *   filesDir/stickers/<packId>/<sticker>.webp  (512x512, ≤100KB)
 *   filesDir/stickers/<packId>/tray.png        (96x96, ≤50KB)
 *
 * Every pack starts with 3 built-in starter stickers so it is valid for WhatsApp immediately.
 * When a pack reaches 30 stickers, a new pack ("Lumen Stickers 2", …) is created, also seeded
 * with the 3 starters, so every pack can be added to WhatsApp as soon as it exists.
 * Any change to a pack bumps its image_data_version so WhatsApp knows to refresh it.
 */
object StickerLibrary {
    const val MIN_STICKERS = 3
    const val MAX_STICKERS = 30
    const val TRAY_FILE = "tray.png"
    private const val TRAY_SIZE = 96
    private const val TRAY_MAX_BYTES = 50 * 1024
    private const val PUBLISHER = "Lumen"

    private val lock = Any()
    private var loaded = false
    private val _packs = MutableStateFlow<List<LumenStickerPack>>(emptyList())
    val packs: StateFlow<List<LumenStickerPack>> = _packs

    fun rootDir(context: Context): File = File(context.filesDir, "stickers").apply { mkdirs() }
    fun packDir(context: Context, packId: String): File = File(rootDir(context), packId)
    private fun indexFile(context: Context) = File(rootDir(context), "index.json")

    val publisher: String get() = PUBLISHER

    /** Current packs, loading from disk if needed. Safe to call from the ContentProvider. */
    fun current(context: Context): List<LumenStickerPack> = synchronized(lock) {
        if (!loaded) loadLocked(context)
        _packs.value
    }

    /** Loads the index and creates the first pack with starters on first run. Call off the main thread. */
    fun ensureReady(context: Context) {
        synchronized(lock) {
            if (!loaded) loadLocked(context)
            if (_packs.value.isEmpty()) {
                val first = createPackLocked(context, index = 1)
                _packs.value = listOf(first)
                saveLocked(context)
            }
        }
    }

    /**
     * Adds a sticker built from a transparent-background bitmap to the newest pack with room,
     * rolling over to a new pack at 30.
     */
    fun add(
        context: Context,
        transparentBitmap: Bitmap,
        emoji: String,
        accessibilityText: String,
        source: String
    ): AddStickerResult = synchronized(lock) {
        runCatching {
            if (!loaded) loadLocked(context)
            if (_packs.value.isEmpty()) {
                _packs.value = listOf(createPackLocked(context, index = 1))
            }
            var packs = _packs.value
            var target = packs.last()
            if (target.isFull) {
                target = createPackLocked(context, index = packs.size + 1)
                packs = packs + target
            }
            val bytes = StickerMaker.encodeWebp(StickerMaker.make(transparentBitmap))
            val fileName = "s_${System.currentTimeMillis()}.webp"
            File(packDir(context, target.identifier), fileName).writeBytes(bytes)
            val sticker = LumenSticker(
                fileName = fileName,
                emojis = listOf(emoji),
                accessibilityText = cleanAccessibilityText(accessibilityText),
                source = source,
                createdAt = System.currentTimeMillis()
            )
            val updated = target.copy(
                stickers = target.stickers + sticker,
                imageDataVersion = target.imageDataVersion + 1
            )
            writeTrayLocked(context, updated)
            _packs.value = packs.map { if (it.identifier == updated.identifier) updated else it }
            saveLocked(context)
            AddStickerResult.Added(updated)
        }.getOrElse { AddStickerResult.Failed(it.message ?: "Couldn't create the sticker") }
    }

    /** Removes a sticker; refuses if the pack would drop below WhatsApp's minimum of 3. */
    fun remove(context: Context, packId: String, fileName: String): Boolean = synchronized(lock) {
        if (!loaded) loadLocked(context)
        val pack = _packs.value.firstOrNull { it.identifier == packId } ?: return false
        if (pack.stickers.size <= MIN_STICKERS) return false
        val remaining = pack.stickers.filterNot { it.fileName == fileName }
        if (remaining.size == pack.stickers.size) return false
        File(packDir(context, packId), fileName).delete()
        val updated = pack.copy(stickers = remaining, imageDataVersion = pack.imageDataVersion + 1)
        writeTrayLocked(context, updated)
        _packs.value = _packs.value.map { if (it.identifier == packId) updated else it }
        saveLocked(context)
        true
    }

    /** Resolves a file WhatsApp asked for, only if it is a known sticker or tray icon of that pack. */
    fun resolveAsset(context: Context, packId: String, fileName: String): File? {
        val pack = current(context).firstOrNull { it.identifier == packId } ?: return null
        val known = fileName == TRAY_FILE || pack.stickers.any { it.fileName == fileName }
        if (!known) return null
        val dir = packDir(context, packId).canonicalFile
        val file = File(dir, fileName).canonicalFile
        if (file.parentFile != dir || !file.exists()) return null
        return file
    }

    /** WhatsApp: ≤125 chars for static stickers and no emoji in accessibility text. */
    private fun cleanAccessibilityText(text: String): String =
        text.filterNot { Character.isSurrogate(it) || Character.getType(it) == Character.OTHER_SYMBOL.toInt() }
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(125)

    // ───────────── internals (call with lock held) ─────────────

    private fun createPackLocked(context: Context, index: Int): LumenStickerPack {
        val id = "lumen_$index"
        val dir = packDir(context, id).apply { mkdirs() }
        val starters = renderStarters(context).map { (bitmap, meta) ->
            val (name, emoji, label) = meta
            File(dir, name).writeBytes(StickerMaker.encodeWebp(StickerMaker.make(bitmap)))
            LumenSticker(name, listOf(emoji), label, LumenSticker.SOURCE_STARTER, System.currentTimeMillis())
        }
        val pack = LumenStickerPack(
            identifier = id,
            name = if (index == 1) "Lumen Stickers" else "Lumen Stickers $index",
            imageDataVersion = 1,
            stickers = starters
        )
        writeTrayLocked(context, pack)
        return pack
    }

    /** Three built-in stickers so every pack is valid (≥3) from the start. */
    private fun renderStarters(context: Context): List<Pair<Bitmap, Triple<String, String, String>>> {
        val hello = QuoteStyleRenderer.render(context, "Hello", QuoteStyle.Bubble, QuoteAspect.Square, QuoteBackgroundKind.Transparent)
        val thanks = QuoteStyleRenderer.render(context, "Thanks", QuoteStyle.Bubble, QuoteAspect.Square, QuoteBackgroundKind.Transparent)
        val heart = ScribbleExport.render(
            listOf(InkStroke(heartPoints(), Color(0xFFF472B6), 6f, false)),
            ScribbleBrushStyle.Marker,
            ScribbleExportBackground.Transparent,
            sticker = true
        )
        return listOf(
            hello to Triple("starter_hello.webp", "👋", "The word Hello in bold white bubble letters"),
            heart to Triple("starter_heart.webp", "❤️", "A hand-drawn pink heart"),
            thanks to Triple("starter_thanks.webp", "🙏", "The word Thanks in bold white bubble letters")
        )
    }

    /** Classic parametric heart, drawn as one smooth stroke. */
    private fun heartPoints(): List<Offset> {
        val pts = mutableListOf<Offset>()
        val steps = 120
        for (i in 0..steps) {
            val t = (i.toDouble() / steps) * 2 * Math.PI
            val x = 16 * sin(t).pow(3)
            val y = 13 * cos(t) - 5 * cos(2 * t) - 2 * cos(3 * t) - cos(4 * t)
            pts += Offset((200 + x * 10).toFloat(), (200 - y * 10).toFloat())
        }
        return pts
    }

    /** 96x96 PNG tray icon (≤50KB) made from the pack's newest sticker. */
    private fun writeTrayLocked(context: Context, pack: LumenStickerPack) {
        val dir = packDir(context, pack.identifier)
        val source = pack.stickers.lastOrNull()?.let { File(dir, it.fileName) } ?: return
        val decoded = android.graphics.BitmapFactory.decodeFile(source.absolutePath) ?: return
        val tray = Bitmap.createScaledBitmap(decoded, TRAY_SIZE, TRAY_SIZE, true)
        val bytes = ByteArrayOutputStream().use { s ->
            tray.compress(Bitmap.CompressFormat.PNG, 100, s)
            s.toByteArray()
        }
        if (bytes.size <= TRAY_MAX_BYTES) File(dir, TRAY_FILE).writeBytes(bytes)
    }

    private fun loadLocked(context: Context) {
        loaded = true
        val file = indexFile(context)
        if (!file.exists()) {
            _packs.value = emptyList()
            return
        }
        _packs.value = runCatching {
            val root = JSONObject(file.readText())
            val arr = root.optJSONArray("packs") ?: JSONArray()
            (0 until arr.length()).map { i ->
                val p = arr.getJSONObject(i)
                val stickersJson = p.optJSONArray("stickers") ?: JSONArray()
                LumenStickerPack(
                    identifier = p.getString("id"),
                    name = p.getString("name"),
                    imageDataVersion = p.optInt("version", 1),
                    stickers = (0 until stickersJson.length()).map { j ->
                        val s = stickersJson.getJSONObject(j)
                        val emojis = s.optJSONArray("emojis") ?: JSONArray()
                        LumenSticker(
                            fileName = s.getString("file"),
                            emojis = (0 until emojis.length()).map { emojis.getString(it) },
                            accessibilityText = s.optString("a11y"),
                            source = s.optString("source"),
                            createdAt = s.optLong("created")
                        )
                    }
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun saveLocked(context: Context) {
        val arr = JSONArray()
        _packs.value.forEach { p ->
            val stickers = JSONArray()
            p.stickers.forEach { s ->
                stickers.put(
                    JSONObject()
                        .put("file", s.fileName)
                        .put("emojis", JSONArray(s.emojis))
                        .put("a11y", s.accessibilityText)
                        .put("source", s.source)
                        .put("created", s.createdAt)
                )
            }
            arr.put(
                JSONObject()
                    .put("id", p.identifier)
                    .put("name", p.name)
                    .put("version", p.imageDataVersion)
                    .put("stickers", stickers)
            )
        }
        val target = indexFile(context)
        val tmp = File(target.parentFile, "index.json.tmp")
        tmp.writeText(JSONObject().put("packs", arr).toString())
        if (!tmp.renameTo(target)) {
            target.writeText(tmp.readText())
            tmp.delete()
        }
    }
}
