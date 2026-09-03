package com.wasif.khata.core.ui.component

import org.junit.Assert.assertEquals
import org.junit.Test

class AmountKeypadTest {

    @Test
    fun `a leading zero is replaced rather than built on`() {
        assertEquals("5", appendAmountKey("0", '5'))
        assertEquals("0", appendAmountKey("0", '0'))
        assertEquals("100", appendAmountKey("10", '0'))
    }

    @Test
    fun `a dot on an empty field reads as a number while being typed`() {
        assertEquals("0.", appendAmountKey("", '.'))
        assertEquals("12.", appendAmountKey("12", '.'))
    }

    @Test
    fun `a second dot is ignored`() {
        assertEquals("12.5", appendAmountKey("12.5", '.'))
    }

    @Test
    fun `a third decimal has nowhere to go`() {
        assertEquals("0.5", appendAmountKey("0.", '5'))
        assertEquals("0.56", appendAmountKey("0.5", '6'))
        assertEquals("0.56", appendAmountKey("0.56", '7'))
    }

    @Test
    fun `whole taka are not limited by the decimal rule`() {
        assertEquals("123456", appendAmountKey("12345", '6'))
    }

    // Backspace is String.dropLast(1) and gets no function of its own. This pins
    // the empty case, so a later "improvement" that replaces dropLast with index
    // arithmetic cannot reintroduce the crash.
    @Test
    fun `backspace on an empty field is a no-op`() {
        assertEquals("", "".dropLast(1))
    }
}
