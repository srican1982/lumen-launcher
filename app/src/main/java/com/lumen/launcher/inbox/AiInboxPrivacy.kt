package com.lumen.launcher.inbox

object AiInboxPrivacy {
    private val codes = Regex("(?<!\\d)\\d{4,8}(?!\\d)")
    fun redact(text: String): String = codes.replace(text, "[code removed]")
    fun filter(items: List<InboxItem>, excludedPackages: Set<String>): List<InboxItem> = items
        .filterNot { it.secret || it.packageName in excludedPackages }
        .map { it.copy(title = redact(it.title), preview = redact(it.preview)) }
}
