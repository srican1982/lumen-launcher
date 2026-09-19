package com.lumen.launcher.data

data class CaptureNote(
    val id: String,
    val text: String,
    val space: SpaceKind = SpaceKind.Home,
    val createdAt: Long = 0L
)

data class LaterItem(
    val id: String,
    val text: String,
    val kind: String = "note",
    val appKey: String = "",
    val createdAt: Long = 0L
)

enum class CaptureKind { Task, Note, Reminder }
