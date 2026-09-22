package com.lumen.launcher.social

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

private val Context.creationStore: DataStore<Preferences> by preferencesDataStore(name = "social_creations")

class CreationRepository(private val context: Context) {

    private val dir: File
        get() = File(context.filesDir, "social_creations").apply { mkdirs() }

    val items: Flow<List<CreationItem>> = context.creationStore.data.map { prefs ->
        parseIndex(prefs[INDEX] ?: "")
    }

    suspend fun savePng(kind: CreationKind, bytes: ByteArray): CreationItem = withContext(Dispatchers.IO) {
        val id = UUID.randomUUID().toString()
        val file = File(dir, "$id.png")
        file.writeBytes(bytes)
        val item = CreationItem(id, kind, file.absolutePath, System.currentTimeMillis())
        context.creationStore.edit { prefs ->
            val current = parseIndex(prefs[INDEX] ?: "").toMutableList()
            current.add(0, item)
            val trimmed = current.take(MAX_ITEMS)
            prefs[INDEX] = encodeIndex(trimmed)
            val keep = trimmed.map { it.id }.toSet()
            dir.listFiles()?.forEach { f ->
                if (f.nameWithoutExtension !in keep) f.delete()
            }
        }
        item
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        context.creationStore.edit { prefs ->
            val kept = parseIndex(prefs[INDEX] ?: "").filterNot { it.id == id }
            prefs[INDEX] = encodeIndex(kept)
        }
        File(dir, "$id.png").delete()
    }

    suspend fun loadRecent(limit: Int = 4): List<CreationItem> =
        items.first().take(limit)

    fun fileFor(item: CreationItem): File = File(item.filePath)

    private fun parseIndex(raw: String): List<CreationItem> =
        raw.lineSequence()
            .mapNotNull { line ->
                val parts = line.split('|')
                if (parts.size < 4) return@mapNotNull null
                val path = parts[2]
                if (!File(path).exists()) return@mapNotNull null
                CreationItem(
                    id = parts[0],
                    kind = runCatching { CreationKind.valueOf(parts[1]) }.getOrDefault(CreationKind.Scribble),
                    filePath = path,
                    createdAt = parts[3].toLongOrNull() ?: 0L
                )
            }
            .sortedByDescending { it.createdAt }
            .take(MAX_ITEMS)
            .toList()

    private fun encodeIndex(items: List<CreationItem>): String =
        items.joinToString("\n") { "${it.id}|${it.kind.name}|${it.filePath}|${it.createdAt}" }

    companion object {
        private val INDEX = stringPreferencesKey("creation_index")
        const val MAX_ITEMS = 24
    }
}
