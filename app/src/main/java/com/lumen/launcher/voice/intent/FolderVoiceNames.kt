package com.lumen.launcher.voice.intent

import com.lumen.launcher.search.FuzzySearch
import com.lumen.launcher.voice.VoiceQuery

internal object FolderVoiceNames {
    fun normalize(raw: String, folders: List<String>): String {
        val q = VoiceQuery.clean(raw)
        val match = Regex("""^(?:add|put|move) (.+?) (?:to|into|in) (?:my |the )?(?:folder (?:called |named )?)?(.+?)(?: folder)?$""")
            .matchEntire(q) ?: return raw
        val name = match.groupValues[2]
        val folder = folders.singleOrNull { FuzzySearch.normalize(it) == FuzzySearch.normalize(name) } ?: return raw
        return "add ${match.groupValues[1]} to $folder folder"
    }
}
