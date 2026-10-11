package com.lumen.launcher.data

object SpaceStarterApps {
    fun picks(space: SpaceKind, apps: List<AppInfo>, dock: Set<String>): List<AppInfo> {
        if (space == SpaceKind.Home) return HomeStarterApps.picks(apps, dock)
        val groups = when (space) {
            SpaceKind.Work -> listOf(
                listOf("com.microsoft.office.outlook", "com.google.android.gm"),
                listOf("com.google.android.calendar", "com.samsung.android.calendar"),
                listOf("com.microsoft.teams", "com.Slack"),
                listOf("us.zoom.videomeetings", "com.google.android.apps.tachyon", "com.google.android.apps.meetings"),
                listOf("com.microsoft.skydrive", "com.google.android.apps.docs"),
                listOf("com.microsoft.office.officehubrow", "com.microsoft.office.word", "com.google.android.apps.docs.editors.docs"),
                listOf("com.azure.authenticator", "com.google.android.apps.authenticator2"),
                listOf("com.adpmobile.android"))
            SpaceKind.Personal -> listOf(
                listOf("com.whatsapp", "org.telegram.messenger", "com.facebook.orca"),
                listOf("com.instagram.android", "com.facebook.katana"),
                listOf("com.zhiliaoapp.musically", "com.snapchat.android"),
                listOf("com.google.android.apps.photos", "com.sec.android.gallery3d"),
                listOf("com.spotify.music", "com.google.android.apps.youtube.music"))
            SpaceKind.Travel -> listOf(
                listOf("com.google.android.apps.maps", "com.waze"),
                listOf("com.google.android.apps.translate"),
                listOf("com.ubercab", "me.lyft.android"),
                listOf("com.booking", "com.airbnb.android"),
                listOf("com.aa.android", "com.delta.mobile.android", "com.united.mobile.android"),
                listOf("com.google.android.apps.walletnfcrel", "com.samsung.android.spay"))
            else -> emptyList()
        }
        val eligible = apps.filterNot { it.packageName in dock }
        return groups.mapNotNull { group -> group.firstNotNullOfOrNull { pkg -> eligible.find { it.packageName == pkg } } }.distinctBy { it.key }
    }
}
