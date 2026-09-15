package com.lumen.launcher.data

data class ActionCard(
    val id: String,
    val kicker: String,
    val title: String,
    val detail: String,
    val app: AppInfo?,
    val actionId: String? = null
)
