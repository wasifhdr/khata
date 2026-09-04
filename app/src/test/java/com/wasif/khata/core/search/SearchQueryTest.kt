package com.wasif.khata.core.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SearchQueryTest {

    @Test
    fun `a term becomes a prefix match`() {
        assertEquals("food*", ftsQuery("food"))
    }

    @Test
    fun `only the last term is a prefix, so earlier words must match whole`() {
        // FTS4 ANDs implicitly. Prefixing every term would make "sul din" match far
        // more than the user meant; prefixing only the last is as-you-type.
        assertEquals("sultan din*", ftsQuery("sultan din"))
    }

    @Test
    fun `quotes are stripped rather than escaped`() {
        // The LIKE query this replaces carries a comment about an unescaped % turning
        // a search into "return everything". The FTS equivalent of that mistake is a
        // stray quote, which breaks MATCH syntax into a SQL exception.
        assertEquals("foo*", ftsQuery("""foo"'"""))
        assertEquals("a b*", ftsQuery("a OR b"))
    }

    @Test
    fun `a hyphen is a separator, never FTS's NOT operator`() {
        // A merchant reference like LTD-6-RM7123 is ordinary text to the person
        // typing it. To FTS4 a leading - is NOT, so leaving it in means "ltd, but
        // not 6 and not rm7123" -- which answered with 163 of 1154 rows on a real
        // ledger. unicode61 splits on - when indexing, so the query must too.
        assertEquals("LTD 6 RM7123*", ftsQuery("LTD-6-RM7123"))
        // And a leading one is a malformed MATCH expression, i.e. a SQL exception.
        assertEquals("shop*", ftsQuery("-shop"))
    }

    @Test
    fun `an empty or punctuation-only query searches for nothing`() {
        assertNull(ftsQuery(""))
        assertNull(ftsQuery("   "))
        assertNull(ftsQuery("\"*^"))
        assertNull(ftsQuery("---"))
    }
}
