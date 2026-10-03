package com.lumen.launcher.focus
import org.junit.Assert.*
import org.junit.Test
class FocusSelectionTest {
    @Test fun disabledMemberDoesNotAllowCallsUnlessAnotherSelectedGroupEnablesThem() {
        val off = FocusPerson("1", "Person", "123", reach = FocusReach.Neither)
        val on = off.copy(reach = FocusReach.CallsOnly)
        assertTrue(combineCallGroups(listOf(listOf(off))).isEmpty())
        assertEquals(listOf(on), combineCallGroups(listOf(listOf(off), listOf(on), listOf(on))))
    }
    @Test fun appRestrictionEndsWithSessionAndAlwaysAllowsLauncher() {
        assertFalse(FocusAppAccess.packageAllowed(true, "social", "lumen", setOf("work")))
        assertTrue(FocusAppAccess.packageAllowed(true, "work", "lumen", setOf("work")))
        assertTrue(FocusAppAccess.packageAllowed(false, "social", "lumen", emptySet()))
        assertTrue(FocusAppAccess.packageAllowed(true, "lumen", "lumen", emptySet()))
    }
}
