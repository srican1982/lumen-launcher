package com.lumen.launcher.data

data class NeedNowHint(
    val title: String,
    val detail: String,
    val app: AppInfo? = null,
    val phone: String? = null
)

object NeedNowResolver {
    fun hints(
        task: TodoItem?,
        apps: List<AppInfo>,
        recents: List<String>,
        space: SpaceKind
    ): List<NeedNowHint> {
        val out = mutableListOf<NeedNowHint>()
        task?.let { matchTask(it, apps)?.let { hint -> out += hint } }
        val recent = recents.mapNotNull { key -> apps.find { it.key == key } }
        recent.firstOrNull()?.let { app ->
            if (out.none { it.app?.key == app.key }) {
                out += NeedNowHint("Continue", app.label, app)
            }
        }
        val rest = com.lumen.launcher.search.Routine.likelyNext(apps, recents, space)
            .filter { app -> out.none { it.app?.key == app.key } }
            .take(3)
            .map { NeedNowHint(it.label, space.kicker, it) }
        return (out + rest).distinctBy { it.app?.key ?: it.title }.take(4)
    }

    fun matchTask(task: TodoItem, apps: List<AppInfo>): NeedNowHint? {
        val linked = matchTaskApps(task, apps).firstOrNull() ?: return null
        callName(task.text)?.let { name ->
            return NeedNowHint("Call $name", task.text, linked)
        }
        return NeedNowHint(linked.label, task.text, linked)
    }

    fun matchTaskApps(task: TodoItem, apps: List<AppInfo>): List<AppInfo> {
        val specific = taskNeedles(task).mapNotNull { needles ->
            find(*needles.toTypedArray(), apps = apps)
        }.distinctBy { it.key }
        val capabilities = com.lumen.launcher.search.IntentIndex.match(task.text).flatMap {
            com.lumen.launcher.search.IntentIndex.appsFor(it, apps)
        }
        return (specific + capabilities).distinctBy { it.key }.take(4)
    }

    private fun taskNeedles(task: TodoItem): List<List<String>> {
        val q = task.text.lowercase()
        if (callName(task.text) != null) {
            return listOf(
                listOf("phone", "dialer"),
                listOf("contacts", "people"),
                listOf("whatsapp", "teams"),
                listOf("notes", "keep", "notepad")
            )
        }
        return when {
            Regex("\\b(website|webpage|coding|code|programming|html|css)\\b").containsMatchIn(q) -> listOf(
                listOf("chrome", "firefox", "browser"),
                listOf("vscode", "acode", "spck", "code editor"),
                listOf("github", "gitlab"),
                listOf("notes", "keep", "notepad")
            )
            q.contains("timesheet") || q.contains("time sheet") -> listOf(
                listOf("adp", "workday", "kronos", "paychex", "timesheet"),
                listOf("outlook", "gmail"),
                listOf("teams", "slack"),
                listOf("chrome", "browser")
            )
            q.contains("email") || q.contains("inbox") || q.contains("mail") -> listOf(
                listOf("gmail", "outlook", "mail", "yahoo"),
                listOf("calendar"),
                listOf("chrome", "browser")
            )
            q.contains("slack") || q.contains("teams") || q.contains("message") -> listOf(
                listOf("slack", "teams", "whatsapp", "messages"),
                listOf("phone", "dialer"),
                listOf("notes", "keep")
            )
            q.contains("doc") || q.contains("slides") || q.contains("sheet") || q.contains("write") || q.contains("report") || q.contains("proposal") -> listOf(
                listOf("docs", "sheets", "slides", "drive"),
                listOf("chrome", "browser")
            )
            q.contains("pay") || q.contains("bank") || q.contains("rent") -> listOf(
                listOf("bank", "chase", "paypal", "venmo", "wallet"),
                listOf("chrome", "browser")
            )
            q.contains("map") || q.contains("drive to") || q.contains("uber") -> listOf(
                listOf("maps", "waze", "uber", "lyft"),
                listOf("phone", "dialer")
            )
            q.contains("calendar") || q.contains("meeting") -> listOf(
                listOf("calendar", "outlook"),
                listOf("teams", "slack", "meet", "zoom"),
                listOf("chrome", "browser")
            )
            q.contains("study") || q.contains("research") || q.contains("read") -> listOf(listOf("chrome", "browser"), listOf("notes", "keep", "notepad"), listOf("drive", "files", "pdf"))
            else -> listOf(
                task.text.split(Regex("\\s+")).filter { it.length >= 4 }.map { it.lowercase() }
            )
        }
    }

    private fun callName(q: String): String? {
        val match = Regex("""^(?:call|phone|ring|dial)\s+(.+)$""", RegexOption.IGNORE_CASE).find(q) ?: return null
        return match.groupValues[1].trim().takeIf { it.isNotBlank() }
    }

    private fun find(vararg needles: String, apps: List<AppInfo>): AppInfo? {
        return apps.firstOrNull { app ->
            val hay = "${app.label} ${app.packageName}".lowercase()
            needles.any { hay.contains(it) }
        }
    }
}
