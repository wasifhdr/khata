package com.wasif.khata.core.sms

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every literal here is copied verbatim from docs/superpowers/specs/transfer-shapes.md.
 */
class TransferShapeTest {

    @Test
    fun `a movement that could have been between my own accounts asks`() {
        listOf(
            "EBL Account Transfer",
            "EBL Skybanking MFS Transfer-bKash",
            "Own Account Transfer",
            "NPSB FUND TRANSFER",
            "AC TRANSFER THROUGH EBL CONNECT",
            "bKash Cash Out",
            "ATM Withdrawal",
            "Send Money",
        ).forEach { assertTrue("should ask about: $it", isTransferShaped(it)) }
    }

    @Test
    fun `an ordinary purchase never asks`() {
        // The load-bearing half. Ten questions a month about dinner gets the whole
        // feature muted, and a muted question settles nothing.
        listOf(
            "UBER BANGLADESH LTD-UBER",
            "FOODPANDA BANGLADESH LIMITED",
            "EBL Skybanking Mobile Recharge",
            "North South University",
            "CINEPLEXBD",
        ).forEach { assertFalse("should stay quiet about: $it", isTransferShaped(it)) }
    }

    @Test
    fun `nothing to match on is nothing to ask about`() {
        assertFalse(isTransferShaped(null))
        assertFalse(isTransferShaped(""))
        assertFalse(isTransferShaped("   "))
    }
}
