package com.lumen.launcher.ui

import androidx.compose.runtime.staticCompositionLocalOf
import com.lumen.launcher.badge.NotificationBadgeMode

val LocalNotificationBadgeMode = staticCompositionLocalOf { NotificationBadgeMode.Number }

val LocalNotificationBadgeCounts = staticCompositionLocalOf { emptyMap<String, Int>() }
