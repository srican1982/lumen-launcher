package com.lumen.launcher.voice.intent

import org.json.JSONObject
import org.junit.Test
import org.junit.Assert.assertTrue
import java.io.File

/** Fixed product phrases, with action and entity expectations; no microphone or AI involved. */
class VoicePhrasePackTest {
    @Test fun realisticPhrasePack() {
        val pack = JSONObject(javaClass.getResource("/voice_phrase_pack.json")!!.readText())
        val apps = pack.getJSONArray("apps").let { a -> (0 until a.length()).map { a.getString(it) } }
        val folders = pack.getJSONArray("folders").let { a -> (0 until a.length()).map { a.getString(it) } }
        val cases = pack.getJSONArray("cases")
        val failures = mutableListOf<String>()
        var core = 0
        var corePassed = 0
        for (i in 0 until cases.length()) {
            val c = cases.getJSONObject(i)
            val a = c.getJSONArray("candidates")
            val said = (0 until a.length()).map { a.getString(it) }
            val actual = VoiceQueryRouter.route(said, apps, folders)
            val ok = actual.action.name == c.getString("action") &&
                (!c.has("app") || actual.appName?.lowercase() == c.getString("app")) &&
                (!c.has("recipient") || actual.recipientName?.lowercase() == c.getString("recipient").lowercase()) &&
                (!c.has("channel") || actual.messagingApp?.lowercase() == c.getString("channel").lowercase()) &&
                (!c.has("seconds") || actual.durationSeconds == c.getInt("seconds")) &&
                (!c.has("slot") || actual.missingSlot?.name == c.getString("slot")) &&
                (!c.has("clock") || actual.clockQuery?.name == c.getString("clock")) &&
                (!c.has("text") || actual.textValue == c.getString("text")) &&
                (!c.has("int") || actual.intValue == c.getInt("int")) &&
                (!c.has("hour") || actual.hour == c.getInt("hour")) &&
                // Bare hours choose the next AM/PM occurrence, so both are valid
                // across test runs at different times of day.
                (!c.has("hours") || c.getJSONArray("hours").let { hours ->
                    (0 until hours.length()).any { actual.hour == hours.getInt(it) }
                }) &&
                (!c.has("minute") || actual.minute == c.getInt("minute")) &&
                (!c.has("enabled") || actual.enabled == c.getBoolean("enabled"))
            if (c.optBoolean("core")) { core++; if (ok) corePassed++ }
            if (!ok) failures += "${said.joinToString(" | ")} -> expected ${c.getString("action")} ${c.optString("app")} ${c.optString("text")}; got $actual"
        }
        val result = JSONObject().put("total", cases.length()).put("passed", cases.length()-failures.size)
            .put("coreTotal", core).put("corePassed", corePassed).put("failures", org.json.JSONArray(failures))
        File("build/reports/voice").mkdirs()
        File("build/reports/voice/phrase-pack.json").writeText(result.toString(2))
        println("VOICE PACK: ${cases.length()-failures.size}/${cases.length()}; required: $corePassed/$core")
        println(failures.joinToString("\n"))
        assertTrue("Expected 250+ realistic phrases", cases.length() >= 250)
        assertTrue("Required phrases failed:\n${failures.joinToString("\n")}", corePassed == core)
        assertTrue("Voice pack below 95%:\n${failures.joinToString("\n")}", failures.size * 20 <= cases.length())
    }
}
