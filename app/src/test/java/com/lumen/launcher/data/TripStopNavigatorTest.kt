package com.lumen.launcher.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.util.Calendar

class TripStopNavigatorTest {

    private fun at(hour: Int, minute: Int = 0): Long =
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    @Test
    fun picksUpcomingTimedStopNotPastAirport() {
        val now = at(11, 0)
        val stops = listOf(
            TripStop(id = "1", place = "Airport", category = TripStopCategory.Airport, at = at(9, 0)),
            TripStop(id = "2", place = "Hilton San Diego Bay", category = TripStopCategory.Hotel, at = at(14, 0)),
            TripStop(id = "3", place = "Dinner", category = TripStopCategory.Restaurant, at = at(19, 30))
        )
        val next = TripStopNavigator.nextStop(now, stops)
        assertThat(next?.place).isEqualTo("Hilton San Diego Bay")
    }

    @Test
    fun markedNextWinsWhenNoUpcomingTime() {
        val now = at(21, 0)
        val stops = listOf(
            TripStop(id = "1", place = "Airport", category = TripStopCategory.Airport, at = at(9, 0)),
            TripStop(id = "2", place = "Gaslamp", category = TripStopCategory.Attraction, markedNext = true)
        )
        assertThat(TripStopNavigator.nextStop(now, stops)?.place).isEqualTo("Gaslamp")
    }

    @Test
    fun todaysRouteOrdersByTime() {
        val now = at(12, 0)
        val stops = listOf(
            TripStop(id = "3", place = "Dinner", at = at(19, 30)),
            TripStop(id = "1", place = "Airport", at = at(9, 0)),
            TripStop(id = "2", place = "Hotel", at = at(14, 0))
        )
        assertThat(TripStopNavigator.todaysRoute(now, stops).map { it.place })
            .containsExactly("Airport", "Hotel", "Dinner")
            .inOrder()
    }
}
