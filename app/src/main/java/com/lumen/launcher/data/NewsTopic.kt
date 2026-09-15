package com.lumen.launcher.data

enum class NewsTopic(
    val title: String,
    val kicker: String,
    val tint: Long,
    val feeds: List<String>
) {
    World(
        "World",
        "Global headlines",
        0xFF7C3AED,
        listOf(
            "https://feeds.bbci.co.uk/news/world/rss.xml",
            "https://rss.nytimes.com/services/xml/rss/nyt/World.xml",
            "https://news.google.com/rss/headlines/section/topic/WORLD?hl=en-US&gl=US&ceid=US:en"
        )
    ),
    Nation(
        "Nation",
        "Closer to home",
        0xFF2563EB,
        listOf(
            "https://feeds.bbci.co.uk/news/uk/rss.xml",
            "https://rss.nytimes.com/services/xml/rss/nyt/US.xml",
            "https://news.google.com/rss/headlines/section/topic/NATION?hl=en-US&gl=US&ceid=US:en"
        )
    ),
    Business(
        "Business",
        "Markets and money",
        0xFF0F766E,
        listOf(
            "https://feeds.bbci.co.uk/news/business/rss.xml",
            "https://rss.nytimes.com/services/xml/rss/nyt/Business.xml",
            "https://news.google.com/rss/headlines/section/topic/BUSINESS?hl=en-US&gl=US&ceid=US:en"
        )
    ),
    Technology(
        "Tech",
        "Gadgets and the web",
        0xFFDB2777,
        listOf(
            "https://www.theverge.com/rss/index.xml",
            "https://feeds.bbci.co.uk/news/technology/rss.xml",
            "https://news.google.com/rss/headlines/section/topic/TECHNOLOGY?hl=en-US&gl=US&ceid=US:en"
        )
    ),
    Entertainment(
        "Culture",
        "Film, music, TV",
        0xFFEA580C,
        listOf(
            "https://feeds.bbci.co.uk/news/entertainment_and_arts/rss.xml",
            "https://rss.nytimes.com/services/xml/rss/nyt/Arts.xml",
            "https://news.google.com/rss/headlines/section/topic/ENTERTAINMENT?hl=en-US&gl=US&ceid=US:en"
        )
    ),
    Sports(
        "Sports",
        "Scores and stories",
        0xFF16A34A,
        listOf(
            "https://feeds.bbci.co.uk/sport/rss.xml",
            "https://www.espn.com/espn/rss/news",
            "https://news.google.com/rss/headlines/section/topic/SPORTS?hl=en-US&gl=US&ceid=US:en"
        )
    ),
    Science(
        "Science",
        "Space and discovery",
        0xFF0891B2,
        listOf(
            "https://feeds.bbci.co.uk/news/science_and_environment/rss.xml",
            "https://www.nasa.gov/news-release/feed/",
            "https://news.google.com/rss/headlines/section/topic/SCIENCE?hl=en-US&gl=US&ceid=US:en"
        )
    ),
    Health(
        "Health",
        "Body and mind",
        0xFFE11D48,
        listOf(
            "https://feeds.bbci.co.uk/news/health/rss.xml",
            "https://rss.nytimes.com/services/xml/rss/nyt/Health.xml",
            "https://news.google.com/rss/headlines/section/topic/HEALTH?hl=en-US&gl=US&ceid=US:en"
        )
    );

    companion object {
        fun fromName(name: String): NewsTopic? = entries.find { it.name.equals(name, true) }
    }
}
