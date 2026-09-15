package com.lumen.launcher.inbox

data class InboxItem(
    val key: String,
    val source: InboxSource,
    val title: String,
    val preview: String,
    val packageName: String,
    val postedAt: Long
) {
    val isDigest: Boolean
        get() {
            val t = title.lowercase()
            return t.contains("new message") ||
                t.contains("new email") ||
                t.contains("new mail") ||
                (preview.contains('@') && !preview.contains(' ') && preview.length < 48)
        }
}

enum class InboxSource(val title: String, val packages: Set<String>) {
    Outlook(
        "Outlook",
        setOf("com.microsoft.office.outlook")
    ),
    Gmail(
        "Gmail",
        setOf("com.google.android.gm", "com.google.android.gm.lite")
    );

    companion object {
        fun fromPackage(packageName: String): InboxSource? =
            entries.find { packageName in it.packages }
    }
}
