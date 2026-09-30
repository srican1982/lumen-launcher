package com.lumen.launcher.travel

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TripPhotoRulesTest {
    @Test fun capturesOutsideTheTripAreExcluded() {
        assertFalse(TripPhotoRules.belongsToTrip(999L, 1000L, 2000L))
        assertTrue(TripPhotoRules.belongsToTrip(1000L, 1000L, 2000L))
        assertTrue(TripPhotoRules.belongsToTrip(2000L, 1000L, 2000L))
        assertFalse(TripPhotoRules.belongsToTrip(2001L, 1000L, 2000L))
    }

    @Test fun delayedImportsCannotUseTodaysPhoneLocation() {
        assertFalse(TripPhotoRules.canUsePhoneLocation(1000L, 86_401_000L))
        assertFalse(TripPhotoRules.canUsePhoneLocation(250_000L, 1000L))
        assertTrue(TripPhotoRules.canUsePhoneLocation(125_000L, 130_000L))
        assertTrue(TripPhotoRules.canUsePhoneLocation(130_000L, 125_000L))
    }
}
