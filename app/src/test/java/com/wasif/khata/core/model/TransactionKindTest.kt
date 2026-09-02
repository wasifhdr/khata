package com.wasif.khata.core.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Money leaving an account is not the same as money spent. Lending it out,
 * settling a debt or moving it between your own accounts all look identical to a
 * balance and are nothing alike to a person.
 */
class TransactionKindTest {

    @Test
    fun `buying something is spending`() {
        assertTrue(TransactionKind.NORMAL.countsAsSpending)
        assertTrue(TransactionKind.FEE.countsAsSpending)
    }

    @Test
    fun `lending money out is not spending, because it is coming back`() {
        assertFalse(TransactionKind.LENT.countsAsSpending)
    }

    @Test
    fun `paying back what you borrowed is not spending`() {
        // The spending happened when the borrowed money was used.
        assertFalse(TransactionKind.BORROWED_RETURNED.countsAsSpending)
        assertFalse(TransactionKind.LOAN_REPAYMENT.countsAsSpending)
    }

    @Test
    fun `moving your own money is never spending`() {
        assertFalse(TransactionKind.TRANSFER.countsAsSpending)
        assertFalse(TransactionKind.ADJUSTMENT.countsAsSpending)
    }

    @Test
    fun `earning is income`() {
        assertTrue(TransactionKind.NORMAL.countsAsIncome)
    }

    @Test
    fun `money you owe back is not income`() {
        assertFalse(TransactionKind.BORROWED.countsAsIncome)
        assertFalse(TransactionKind.LOAN_DISBURSEMENT.countsAsIncome)
    }

    @Test
    fun `your own money coming home is not income`() {
        // A returned loan was always yours; a reimbursement is a bill you fronted.
        assertFalse(TransactionKind.LENT_RETURNED.countsAsIncome)
        assertFalse(TransactionKind.REIMBURSEMENT.countsAsIncome)
    }

    @Test
    fun `the SQL that totals spending lists exactly the kinds that do not count`() {
        // The query cannot call countsAsSpending, so the two are pinned together
        // here: adding a kind without deciding its side fails this.
        val excludedInSql = setOf(
            TransactionKind.TRANSFER,
            TransactionKind.ADJUSTMENT,
            TransactionKind.LENT,
            TransactionKind.BORROWED_RETURNED,
            TransactionKind.LOAN_REPAYMENT,
        )
        assertTrue(
            TransactionKind.entries.filterNot { it.countsAsSpending }.toSet() == excludedInSql,
        )
    }
}
