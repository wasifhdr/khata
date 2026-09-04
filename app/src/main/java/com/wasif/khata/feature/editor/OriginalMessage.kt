package com.wasif.khata.feature.editor

/**
 * The text of the SMS a transaction was parsed from.
 *
 * A fun interface rather than RawMessageDao itself, the way RecentCategoryIds and
 * MonthLimits already are: the editor wants one string, and the test for it should
 * need one lambda rather than a whole DAO.
 */
fun interface OriginalMessage {
    suspend fun forRawMessage(id: Long): String?
}
