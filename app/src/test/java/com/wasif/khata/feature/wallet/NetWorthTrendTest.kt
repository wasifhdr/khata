package com.wasif.khata.feature.wallet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NetWorthTrendTest {

    @Test
    fun `each day ends where the next day's movement starts`() {
        // Today is 1000. The last day added 200, the one before took 50.
        val trend = netWorthTrend(1000, listOf(100, -50, 200))

        assertEquals(listOf(850L, 800L, 1000L), trend)
        // The line has to end on the figure printed above it.
        assertEquals(1000L, trend.last())
    }

    @Test
    fun `a single day is not a line`() {
        assertTrue(netWorthTrend(1000, listOf(100)).isEmpty())
        assertTrue(netWorthTrend(1000, emptyList()).isEmpty())
    }

    @Test
    fun `a flat stretch stays flat`() {
        assertEquals(listOf(500L, 500L, 500L), netWorthTrend(500, listOf(0, 0, 0)))
    }

    @Test
    fun `it works below zero`() {
        assertEquals(listOf(-100L, -300L), netWorthTrend(-300, listOf(50, -200)))
    }
}
