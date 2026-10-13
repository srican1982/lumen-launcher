package com.lumen.launcher.data

/** Recover legacy hidden packages into the authenticated Private Space without losing missing packages. */
object HiddenAppsMigration {
    data class Result(val hidden: Set<String>, val privateApps: Set<String>)

    fun resolve(apps: List<AppInfo>, hidden: Set<String>, privateApps: Set<String>): Result {
        val recovered = apps.filter { it.packageName in hidden }
        return Result(hidden - recovered.map { it.packageName }.toSet(), privateApps + recovered.map { it.key })
    }
}
