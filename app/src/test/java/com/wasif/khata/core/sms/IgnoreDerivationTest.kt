package com.wasif.khata.core.sms

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Of 836 messages a real phone left unread, roughly 700 were bank spam: 108
 * verification codes, OTP warnings, Bengali marketing, half-yearly statement
 * notices. None of them can be taught as a transaction, so without a way to say
 * "not a transaction" the review list can never be finished.
 */
class IgnoreDerivationTest {

    private fun matches(pattern: String, body: String) = Regex(pattern).containsMatchIn(body)

    @Test
    fun `one verification code teaches Khata about all of them`() {
        val pattern = deriveIgnorePattern(
            "Your bKash verification code is 350404. The code will expire in 2 minutes. " +
                "Please do NOT share your OTP or PIN with others.",
        )!!

        assertTrue(
            matches(
                pattern,
                "Your bKash verification code is 525524. The code will expire in 2 minutes. " +
                    "Please do NOT share your OTP or PIN with others.",
            ),
        )
    }

    @Test
    fun `the opening words are what identify the message`() {
        val pattern = deriveIgnorePattern("Dear Customer, your Deposit Account(s) Half-Yearly statement is ready")!!

        assertTrue(matches(pattern, "Dear Customer, your Deposit Account(s) Half-Yearly statement is ready"))
        // Parentheses in the source must be escaped, not treated as a group.
        assertTrue(pattern.contains("""\("""))
    }

    @Test
    fun `a Bengali notice works the same way`() {
        val pattern = deriveIgnorePattern("প্রিয় গ্রাহক, সেবার মান উন্নয়নের জন্য আগামীকাল সিস্টেম বন্ধ থাকবে")!!

        assertTrue(matches(pattern, "প্রিয় গ্রাহক, সেবার মান উন্নয়নের জন্য রাত ১টা থেকে সেবা বন্ধ"))
    }

    @Test
    fun `it anchors at the start so a phrase buried mid-message is not enough`() {
        val pattern = deriveIgnorePattern("Your bKash verification code is 350404")!!

        assertFalse(
            "otherwise a transaction quoting the phrase would be silenced",
            matches(pattern, "Payment of Tk 500 done. Your bKash verification code is 350404"),
        )
    }

    @Test
    fun `a different message is left alone`() {
        val pattern = deriveIgnorePattern("Your bKash verification code is 350404")!!

        assertFalse(matches(pattern, "Payment of Tk 856.00 to FOODPANDA is successful. Balance Tk 41.98"))
    }

    @Test
    fun `too few words to be distinctive is refused rather than guessed`() {
        // "Payment Tk 20.00 to X" would reduce to "Payment Tk" -- two words, which
        // would silence a whole class of real payments.
        assertNull(deriveIgnorePattern("Payment Tk 20.00 to Grameenphone Ltd-Skitto"))
        assertNull(deriveIgnorePattern("Tk 500 received"))
        assertNull(deriveIgnorePattern(""))
    }

    @Test
    fun `the number that varies is not part of the pattern`() {
        val pattern = deriveIgnorePattern("Your bKash verification code is 350404")!!

        assertFalse("the code itself varies every time", pattern.contains("350404"))
    }

    @Test
    fun `a long opening is trimmed rather than pinned word for word`() {
        val body = "NEVER EVER share your OTP or PIN with anyone including someone claiming to be from the bank"
        val pattern = deriveIgnorePattern(body)!!

        assertTrue(matches(pattern, body))
        // A different tail must still be caught.
        assertTrue(matches(pattern, "NEVER EVER share your OTP or PIN with bank staff"))
    }
}
