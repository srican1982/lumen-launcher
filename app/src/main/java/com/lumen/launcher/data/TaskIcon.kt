package com.lumen.launcher.data

object TaskIcon {
    fun symbol(text: String): String {
        val words = text.lowercase()
        return when {
            listOf("call", "phone", "dial").any(words::contains) -> "\uD83D\uDCDE"
            listOf("medic", "pill", "tablet", "vitamin").any(words::contains) -> "\uD83D\uDC8A"
            listOf("invoice", "pay", "bank", "bill").any(words::contains) -> "\uD83D\uDCB3"
            listOf("buy", "shop", "grocer").any(words::contains) -> "\uD83D\uDED2"
            listOf("meet", "sync", "team").any(words::contains) -> "\uD83D\uDC65"
            listOf("note", "review", "file", "name").any(words::contains) -> "\uD83D\uDCDD"
            else -> "\uD83D\uDCCB"
        }
    }
}
