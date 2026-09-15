package com.lumen.launcher.data

data class NewsItem(
    val title: String,
    val source: String,
    val url: String,
    val published: String,
    val imageUrl: String,
    val topic: NewsTopic
)
