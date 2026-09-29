package com.wasif.khata.core.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TransactionKindTest {

    @Test
    fun `buying something or incurring an IOU is spending`() {
        assertTrue(TransactionKind.NORMAL.countsAsSpending)
        assertTrue(TransactionKind.FEE.countsAsSpending)
        assertTrue(TransactionKind.IOU.countsAsSpending)
    }

    @Test
    fun `paying back an institutional loan is not spending`() {
        assertFalse(TransactionKind.LOAN_REPAYMENT.countsAsSpending)
    }

    @Test
    fun `moving your own money is never spending`() {
        assertFalse(TransactionKind.TRANSFER.countsAsSpending)
        assertFalse(TransactionKind.ADJUSTMENT.countsAsSpending)
    }

    @Test
    fun `earning is income, while loans and IOUs are not`() {
        assertTrue(TransactionKind.NORMAL.countsAsIncome)
        assertFalse(TransactionKind.LOAN_DISBURSEMENT.countsAsIncome)
        assertFalse(TransactionKind.IOU.countsAsIncome)
    }

    @Test
    fun `the SQL that totals spending lists exactly the kinds that do not count`() {
        val excludedInSql = setOf(
            TransactionKind.TRANSFER,
            TransactionKind.ADJUSTMENT,
            TransactionKind.LOAN_REPAYMENT,
        )
        assertTrue(
            TransactionKind.entries.filterNot { it.countsAsSpending }.toSet() == excludedInSql,
        )
    }
}
