package com.lumen.launcher.widgets

/** Groups shown in the in-app widget picker. */
enum class WidgetCategory(val title: String) {
    Clock("Clock"),
    Media("Media"),
    Contacts("Contacts"),
    Tasks("Tasks"),
    Travel("Travel"),
    Focus("Focus"),
    Creative("Creative"),
    Phone("Phone");
}

/** Stable keys are shared by the Android picker and Lumen's widget page. */
enum class WidgetKind(
    val title: String,
    val description: String,
    val wide: Boolean = false,
    val category: WidgetCategory
) {
    // Clock — home screen moments
    ClockWeather("Clock & Weather", "Time, weather and a daily greeting", false, WidgetCategory.Clock),
    WorldClock("World Time", "Analog clock with cities you choose", true, WidgetCategory.Clock),
    ClockFeatured("Featured Clock", "Time, weather and your next event", true, WidgetCategory.Clock),
    CalendarDay("Calendar Day", "Today's date with a week strip", false, WidgetCategory.Clock),
    MomentClock("Moment", "A quiet quote with the time", false, WidgetCategory.Clock),

    // Media — now playing + shortcuts
    MediaContinue("Continue Watching", "Resume the video or track that's playing", false, WidgetCategory.Media),
    MediaPlayer("Media Player", "Full player with artwork and controls", true, WidgetCategory.Media),
    MediaNowPlaying("Now Playing", "Track info with a live visualizer", false, WidgetCategory.Media),
    MediaShortcuts("Media Controls", "Quick open YouTube, Spotify, Music, Podcasts", false, WidgetCategory.Media),

    // Travel — few Apple-style cards
    Trip("Trip", "Your trip, photos and album", true, WidgetCategory.Travel),
    TripComplete("Trip Complete", "A finished trip with memories to revisit", false, WidgetCategory.Travel),
    TravelDocs("Travel Docs", "Boarding passes and bookings", false, WidgetCategory.Travel),

    Scribble("Scribble", "Turn a quick sketch into a sticker", false, WidgetCategory.Creative),
    Quote("Quote", "A little calm, with a fresh thought on tap", true, WidgetCategory.Creative),
    Photos("Photos", "Your recent Lumen photos and cutouts", false, WidgetCategory.Creative),
    Social("Social Today", "Today's time across your social apps", false, WidgetCategory.Creative),
    Tasks("Today Tasks", "Today's task list and a little motivation", true, WidgetCategory.Tasks),
    TasksOverview("Task Progress", "Your to-do, done and overdue counts", false, WidgetCategory.Tasks),
    TasksFilters("Task Quick Filters", "Today, inbox and important tasks", true, WidgetCategory.Tasks),
    TasksNext("Next Up", "Focus on your next task and mark it done", false, WidgetCategory.Tasks),
    TasksManager("Task Manager", "Today, this week and all your tasks", true, WidgetCategory.Tasks),

    Focus("Focus", "A quiet moment, one session at a time", false, WidgetCategory.Focus),
    Contacts("Favorites", "Favorite contacts in a glass grid.", false, WidgetCategory.Contacts),
    ContactsSingle("Single Contact", "Single contact with quick actions.", false, WidgetCategory.Contacts),
    ContactsRow("People", "Favorite contacts row.", true, WidgetCategory.Contacts),

    Hush("Hush", "A quiet overview of your notifications", false, WidgetCategory.Focus),
    Battery("Battery", "Your phone's real battery and charging status", false, WidgetCategory.Phone);

    companion object {
        fun parse(key: String?): WidgetKind? = entries.firstOrNull { it.name == key }

        /** Picker order: category sections, then kinds inside each. */
        fun pickerSections(): List<Pair<WidgetCategory, List<WidgetKind>>> =
            WidgetCategory.entries.mapNotNull { category ->
                val kinds = entries.filter { it.category == category }
                if (kinds.isEmpty()) null else category to kinds
            }
    }

    val isContact: Boolean get() = this in setOf(Contacts, ContactsSingle, ContactsRow)
    val isTask: Boolean get() = this in setOf(Tasks, TasksOverview, TasksFilters, TasksNext, TasksManager)
    val isTrip: Boolean get() = this in setOf(Trip, TripComplete, TravelDocs)
    val isClock: Boolean get() = this in setOf(ClockWeather, WorldClock, ClockFeatured, CalendarDay, MomentClock)
    val isMedia: Boolean get() = this in setOf(MediaContinue, MediaPlayer, MediaNowPlaying, MediaShortcuts)
    val heightDp: Int get() = when (this) {
        Tasks, TasksFilters, TasksManager -> 240
        TasksOverview, TasksNext -> 180
        Contacts -> 220
        ContactsSingle, ContactsRow -> 180
        Trip -> 200
        TripComplete -> 280
        TravelDocs -> 220
        ClockWeather, MomentClock -> 200
        CalendarDay -> 230
        WorldClock, ClockFeatured -> 240
        MediaContinue, MediaNowPlaying -> 220
        MediaShortcuts -> 230
        MediaPlayer -> 240
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
