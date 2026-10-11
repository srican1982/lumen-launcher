package com.lumen.launcher.widgets

/** Stable keys are shared by the Android picker and Lumen's widget page. */
enum class WidgetKind(val title: String, val description: String, val wide: Boolean = false) {
    Trip("Trip Mode", "Your current trip and recent travel photos", true),
    Scribble("Scribble", "Turn a quick sketch into a sticker"),
    Quote("Quote", "A little calm, with a fresh thought on tap", true),
    Photos("Photos", "Your recent Lumen photos and cutouts"),
    Social("Social Today", "Today's time across your social apps"),
    Tasks("Tasks", "Today's tasks, with tap-to-complete circles"),
    Focus("Focus", "A quiet moment, one session at a time"),
    Contacts("Contacts", "Choose four people. Tap a face to call."),
    Hush("Hush", "A quiet overview of your notifications"),
    TravelDocs("Travel Docs", "Boarding passes, bookings and saved documents"),
    Battery("Battery", "Your phone's real battery and charging status");

    companion object {
        fun parse(key: String?): WidgetKind? = entries.firstOrNull { it.name == key }
    }

    val heightDp: Int get() = if (this == Quote) 96 else 132
}

object WidgetQuotes {
    // Original Lumen copy, stored locally. No network or generated personal claims.
    val lines = listOf(
        "A little room to breathe. A little space to begin.",
        "One small step is enough for right now.",
        "Make room for what matters to you.",
        "Let the next thing be a simple thing.",
        "Your pace can be a peaceful one."
    )
    fun at(index: Int): String = lines[Math.floorMod(index, lines.size)]
}
