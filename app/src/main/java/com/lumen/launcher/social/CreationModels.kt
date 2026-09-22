package com.lumen.launcher.social

enum class CreationKind { Scribble, Quote, Photo }

enum class SocialCreateTool { Scribble, Quote, Photo }

data class CreationItem(
    val id: String,
    val kind: CreationKind,
    val filePath: String,
    val createdAt: Long
)
