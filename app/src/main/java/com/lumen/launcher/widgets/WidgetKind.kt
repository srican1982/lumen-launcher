package com.lumen.launcher.widgets

/** Stable keys are shared by the Android picker and Lumen's widget page. */
enum class WidgetKind(val title: String, val description: String, val wide: Boolean = false) {
    Trip("Active Trip", "Your current journey, photos and next stop", true),
    TripUpcoming("Upcoming Trip", "Your saved destination and itinerary", false),
    TripCompleted("Trip Complete", "Relive your latest completed journey", false),
    TripCompact("Compact Trip", "Your trip at a glance", false),
    TripSmall("Small Trip", "Trip progress and next destination", true),
    TripActions("Trip Quick Actions", "Photo, place, note and documents", true),
    TripCollage("Trip Photo Collage", "Four memories from your trip", false),

    Scribble("Scribble", "Turn a quick sketch into a sticker"),
    Quote("Quote", "A little calm, with a fresh thought on tap", true),
    Photos("Photos", "Your recent Lumen photos and cutouts"),
    Social("Social Today", "Today's time across your social apps"),
    Tasks("Today Tasks", "Today's task list and a little motivation", true),
    TasksOverview("Task Progress", "Your to-do, done and overdue counts", false),
    TasksFilters("Task Quick Filters", "Today, inbox and important tasks", true),
    TasksNext("Next Up", "Focus on your next task and mark it done", false),
    TasksManager("Task Manager", "Today, this week and all your tasks", true),

    Focus("Focus", "A quiet moment, one session at a time"),
    Contacts("Favorite Contacts", "Choose four people. Tap a face to call."),
    ContactsRecent("Recent Contacts", "Recent calls to your chosen people", true),
    ContactsSingle("Single Contact", "One person with call and message actions", false),
    ContactsRow("People", "Your four chosen contacts in one row", true),
    ContactsQuick("Quick Contact", "Find contacts and reach your chosen people", true),
    ContactsFamily("Family", "Your chosen people with call and message actions", true),

    Hush("Hush", "A quiet overview of your notifications"),
    TravelDocs("Travel Docs", "Boarding passes, bookings and saved documents"),
    Battery("Battery", "Your phone's real battery and charging status");

    companion object {
        fun parse(key: String?): WidgetKind? = entries.firstOrNull { it.name == key }
    }

    val isContact: Boolean get() = this in setOf(Contacts, ContactsRecent, ContactsSingle, ContactsRow, ContactsQuick, ContactsFamily)
    val isTask: Boolean get() = this in setOf(Tasks, TasksOverview, TasksFilters, TasksNext, TasksManager)
    val isTrip: Boolean get() = this in setOf(Trip, TripUpcoming, TripCompleted, TripCompact, TripSmall, TripActions, TripCollage)
    val heightDp: Int get() = when (this) {
        Tasks, TasksFilters, TasksManager -> 240
        TasksOverview, TasksNext -> 180
        Contacts -> 240
        ContactsRecent, ContactsFamily -> 240
        ContactsSingle, ContactsRow, ContactsQuick -> 180
        Trip -> 240
        TripUpcoming, TripCompleted, TripCollage -> 180
        Quote -> 96
        else -> 132
    }
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
