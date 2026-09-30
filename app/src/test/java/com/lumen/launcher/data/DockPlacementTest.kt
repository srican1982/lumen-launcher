package com.lumen.launcher.data
import com.google.common.truth.Truth.assertThat
import org.junit.Test
class DockPlacementTest {
    @Test fun occupiedDockSlotSwapsRatherThanShifting() {
        val result = DockPlacement.place(listOf("a", "b", "c"), emptyList(), "a", 2, 4)
        assertThat(result.dock).containsExactly("c", "b", "a").inOrder()
    }
    @Test fun homeDropReturnsOccupantToSourcePosition() {
        val result = DockPlacement.place(listOf("a", "b", "c"), listOf("x", "y", "z"), "y", 1, 4)
        assertThat(result.dock).containsExactly("a", "y", "c").inOrder()
        assertThat(result.home).containsExactly("x", "b", "z").inOrder()
    }
    @Test fun existingHomeOccupantIsNotDuplicated() {
        val result = DockPlacement.place(listOf("a", "b"), listOf("b", "x", "y"), "y", 1, 4)
        assertThat(result.home).containsExactly("y", "x", "b").inOrder()
    }
    @Test fun homeSourceAlreadyInDockStillReceivesDisplacedIcon() {
        val result = DockPlacement.place(listOf("a", "b", "c"), listOf("x", "a", "z"), "a", 1, 4)
        assertThat(result.dock).containsExactly("b", "a", "c").inOrder()
        assertThat(result.home).containsExactly("x", "b", "z").inOrder()
    }}
