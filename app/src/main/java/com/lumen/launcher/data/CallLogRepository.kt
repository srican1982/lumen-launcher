package com.lumen.launcher.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CallLog
import android.telephony.PhoneNumberUtils
import androidx.core.content.ContextCompat

data class MissedCall(
    val number: String,
    val name: String,
    val at: Long,
    val count: Int,
    val whatsapp: Boolean = false
) {
    val display: String get() = name.ifBlank { number.ifBlank { "Unknown" } }
    val key: String get() = CallLogRepository.missedKey(number, name, whatsapp)
}

data class MissedSnapshot(
    val calls: List<MissedCall> = emptyList(),
    val outgoingKeys: Set<String> = emptySet()
)

class CallLogRepository(private val context: Context) {

    fun snapshot(now: Long = System.currentTimeMillis()): MissedSnapshot {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) !=
            PackageManager.PERMISSION_GRANTED
        ) return MissedSnapshot()
        val since = now - THREE_DAYS
        val outgoingKeys = HashSet<String>()
        val missed = ArrayList<MissedCall>()
        val projection = arrayOf(
            CallLog.Calls.NUMBER,
            CallLog.Calls.CACHED_NAME,
            CallLog.Calls.DATE,
            CallLog.Calls.TYPE,
            CallLog.Calls.PHONE_ACCOUNT_COMPONENT_NAME
        )
        val cursor = runCatching {
            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                projection,
                "${CallLog.Calls.DATE}>=?",
                arrayOf(since.toString()),
                "${CallLog.Calls.DATE} DESC"
            )
        }.getOrNull() ?: context.contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            arrayOf(
                CallLog.Calls.NUMBER,
                CallLog.Calls.CACHED_NAME,
                CallLog.Calls.DATE,
                CallLog.Calls.TYPE
            ),
            "${CallLog.Calls.DATE}>=?",
            arrayOf(since.toString()),
            "${CallLog.Calls.DATE} DESC"
        )
        cursor?.use { rows ->
            val hasAccount = rows.columnCount > 4
            while (rows.moveToNext()) {
                val number = rows.getString(0).orEmpty()
                val name = rows.getString(1).orEmpty()
                val date = rows.getLong(2)
                val type = rows.getInt(3)
                val account = if (hasAccount) rows.getString(4).orEmpty() else ""
                val whatsapp = looksLikeWhatsApp(account, number)
                val key = missedKey(number, name, whatsapp)
                if (key.isBlank()) continue
                if (type == CallLog.Calls.OUTGOING_TYPE) {
                    outgoingKeys += key
                    outgoingKeys += missedKey(number, name, false)
                    outgoingKeys += missedKey(number, name, true)
                    if (name.isNotBlank()) {
                        outgoingKeys += missedKey("", name, true)
                        outgoingKeys += missedKey("", name, false)
                    }
                } else if (type == CallLog.Calls.MISSED_TYPE) {
                    missed += MissedCall(
                        number = number,
                        name = name,
                        at = date,
                        count = 1,
                        whatsapp = whatsapp
                    )
                }
            }
        }
        val calls = missed
            .groupBy { it.key }
            .mapNotNull { (key, rows) ->
                val latest = rows.maxBy { it.at }
                if (key in outgoingKeys || latest.at < since) null
                else latest.copy(
                    name = rows.firstOrNull { it.name.isNotBlank() }?.name.orEmpty(),
                    count = rows.size
                )
            }
            .sortedByDescending { it.at }
        return MissedSnapshot(calls = calls, outgoingKeys = outgoingKeys)
    }

    companion object {
        private const val THREE_DAYS = 3L * 24 * 60 * 60 * 1000

        fun missedKey(number: String, name: String, whatsapp: Boolean): String {
            val id = normalizeKey(number).ifBlank { name.trim().lowercase() }
            if (id.isBlank()) return ""
            return if (whatsapp) "wa:$id" else "tel:$id"
        }

        fun normalizeKey(number: String): String {
            val digits = number.filter { it.isDigit() }
            return when {
                digits.length >= 10 -> digits.takeLast(10)
                digits.isNotEmpty() -> digits
                else -> PhoneNumberUtils.normalizeNumber(number).orEmpty().filter { it.isDigit() }
            }
        }

        fun looksLikeWhatsApp(account: String, number: String): Boolean {
            val hay = "$account $number".lowercase()
            return hay.contains("whatsapp")
        }
    }
}
