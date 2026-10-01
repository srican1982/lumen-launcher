package com.lumen.launcher.data

import org.junit.Assert.assertEquals
import org.junit.Test

class UsageTimelineTest {
    @Test fun clipsMidnightAndCurrentSession() {
        val events = listOf(UsageTimeline.Event(50,"chat"), UsageTimeline.Event(150,"chat",false), UsageTimeline.Event(180,"chat"))
        assertEquals(mapOf("chat" to 70L), UsageTimeline.totals(events,100,200,setOf("chat")))
    }
    @Test fun appSwitchAndScreenOffDoNotDoubleCount() {
        val events = listOf(UsageTimeline.Event(100,"chat"), UsageTimeline.Event(120,"other"), UsageTimeline.Event(125,"chat",false), UsageTimeline.Event(150,null,false))
        assertEquals(mapOf("chat" to 20L,"other" to 30L), UsageTimeline.totals(events,100,200,setOf("chat","other")))
    }
    @Test fun excludesEventsOutsideComparisonWindow() {
        val events = listOf(UsageTimeline.Event(210,"chat"), UsageTimeline.Event(240,"chat",false))
        assertEquals(emptyMap<String,Long>(), UsageTimeline.totals(events,100,200,setOf("chat")))
    }
}
