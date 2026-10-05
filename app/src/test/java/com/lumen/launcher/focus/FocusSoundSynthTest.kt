package com.lumen.launcher.focus
import org.junit.Assert.*
import org.junit.Test
class FocusSoundSynthTest {
    @Test fun fireplaceIsRemovedAndOldPreferenceResolvesOff() {
        assertFalse(FocusSound.selectable.contains(FocusSound.Fireplace))
        assertEquals(FocusSound.Off, FocusSound.fromId("fireplace"))
    }
    @Test fun eachAvailableSoundHasDistinctNonSilentSamples() {
        val loops = FocusSound.selectable.map { FocusSoundSynth.loop(it, 22050, 24) }
        loops.forEach { samples ->
            assertEquals(529200, samples.size)
            assertTrue(samples.any { kotlin.math.abs(it.toInt()) > 100 })
            assertEquals(0, samples.first().toInt())
            assertEquals(0, samples.last().toInt())
        }
        for(i in loops.indices) for(j in 0 until i) assertFalse(loops[i].contentEquals(loops[j]))
    }
}
