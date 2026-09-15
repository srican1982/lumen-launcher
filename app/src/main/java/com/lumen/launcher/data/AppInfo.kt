package com.lumen.launcher.data

data class AppInfo(
    val label: String,
    val packageName: String,
    val activityName: String,
    val lastUpdateTime: Long,
    val category: AppCategory
) {
    val key: String get() = "$packageName/$activityName"
}

enum class AppCategory(val label: String) {
    All("All"),
    Recent("Recent"),
    Work("Work"),
    Social("Social"),
    Entertainment("Entertainment"),
    Finance("Finance"),
    Food("Food"),
    Shopping("Shopping"),
    Travel("Travel"),
    Utilities("Utilities"),
    Games("Games")
}
