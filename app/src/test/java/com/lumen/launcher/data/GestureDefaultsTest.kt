package com.lumen.launcher.data

import org.junit.Assert.*
import org.junit.Test

class GestureDefaultsTest {
    private val camera = AppInfo("Camera", "com.sec.android.app.camera", "Main", 0, AppCategory.Utilities)
    private val maps = AppInfo("Maps", "com.google.android.apps.maps", "Main", 0, AppCategory.Travel)
    private val music = AppInfo("Spotify", "com.spotify.music", "Main", 0, AppCategory.Utilities)
    @Test fun defaultsResolveInstalledApps() {
        val apps = listOf(camera, maps, music)
        assertEquals(GestureAction.app(camera.key), GestureAction.resolveDefault(GestureAction.DEFAULT_CAMERA, apps))
        assertEquals(GestureAction.app(maps.key), GestureAction.resolveDefault(GestureAction.DEFAULT_MAPS, apps))
        assertEquals(GestureAction.app(music.key), GestureAction.resolveDefault(GestureAction.DEFAULT_MUSIC, apps))
    }
    @Test fun configuredClearedAndDisabledShortcutsRemainUntouched() {
        listOf("", GestureAction.OFF, GestureAction.PRIVATE, GestureAction.app("custom")).forEach {
            assertEquals(it, GestureAction.resolveDefault(it, listOf(camera, maps, music)))
        }
    }
    @Test fun missingAppsKeepSystemIntentFallback() {
        assertEquals(GestureAction.DEFAULT_CAMERA, GestureAction.resolveDefault(GestureAction.DEFAULT_CAMERA, emptyList()))
        assertEquals(GestureAction.DEFAULT_MAPS, GestureAction.resolveDefault(GestureAction.DEFAULT_MAPS, emptyList()))
        assertEquals(GestureAction.DEFAULT_MUSIC, GestureAction.resolveDefault(GestureAction.DEFAULT_MUSIC, emptyList()))
    }
}
