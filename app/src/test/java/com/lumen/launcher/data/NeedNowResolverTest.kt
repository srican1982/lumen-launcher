package com.lumen.launcher.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class NeedNowResolverTest {

    @Test
    fun timesheetFindsAdp() {
        val adp = AppInfo("ADP", "com.adp.mobile", "", 0L, AppCategory.Work)
        val maps = AppInfo("Maps", "com.google.android.apps.maps", "", 0L, AppCategory.Travel)
        val hint = NeedNowResolver.matchTask(
            TodoItem("1", "Submit timesheet", space = SpaceKind.Work),
            listOf(maps, adp)
        )
        assertThat(hint?.app?.label).isEqualTo("ADP")
    }

    @Test
    fun callTaskSurfacesPhone() {
        val phone = AppInfo("Phone", "com.google.android.dialer", "", 0L, AppCategory.Utilities)
        val hint = NeedNowResolver.matchTask(
            TodoItem("1", "Call Steve", space = SpaceKind.Work),
            listOf(phone)
        )
        assertThat(hint?.title).isEqualTo("Call Steve")
        assertThat(hint?.app?.label).isEqualTo("Phone")
    }
}
