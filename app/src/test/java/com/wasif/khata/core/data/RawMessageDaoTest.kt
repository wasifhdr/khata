package com.wasif.khata.core.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.wasif.khata.core.data.entity.RawMessageEntity
import com.wasif.khata.core.model.RawMessageStatus
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RawMessageDaoTest {

    private lateinit var db: KhataDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            KhataDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() = db.close()

    private fun message(uuid: String, body: String, receivedAt: Long) = RawMessageEntity(
        uuid = uuid,
        sender = "bKash",
        body = body,
        receivedAt = receivedAt,
        bodyHash = body.hashCode().toString(),
        status = RawMessageStatus.PENDING,
        matchedRuleId = null,
        createdAt = receivedAt,
        updatedAt = receivedAt,
    )

    @Test
    fun `re-inserting the same message is a no-op so backfill is idempotent`() = runTest {
        val dao = db.rawMessageDao()
        dao.insertIgnoringDuplicate(message("m-1", "hello", 1000))

        val second = dao.insertIgnoringDuplicate(message("m-2", "hello", 1000))

        assertEquals(-1L, second)
        assertEquals(1, dao.countByStatus(RawMessageStatus.PENDING))
    }

    @Test
    fun `the same body at a different timestamp is a distinct message`() = runTest {
        val dao = db.rawMessageDao()
        dao.insertIgnoringDuplicate(message("m-1", "hello", 1000))
        dao.insertIgnoringDuplicate(message("m-2", "hello", 2000))

        assertEquals(2, dao.countByStatus(RawMessageStatus.PENDING))
    }

    @Test
    fun `markStatus moves a message out of the pending batch`() = runTest {
        val dao = db.rawMessageDao()
        val id = dao.insertIgnoringDuplicate(message("m-1", "hello", 1000))

        dao.markStatus(id, RawMessageStatus.IGNORED, ruleId = 7, updatedAt = 2000)

        assertEquals(0, dao.countByStatus(RawMessageStatus.PENDING))
        assertEquals(1, dao.countByStatus(RawMessageStatus.IGNORED))
        assertEquals(emptyList<RawMessageEntity>(), dao.pendingBatch(10))
        assertEquals(7L, dao.findById(id)?.matchedRuleId)
    }

    @Test
    fun `allForReparse returns every message regardless of status`() = runTest {
        val dao = db.rawMessageDao()
        val a = dao.insertIgnoringDuplicate(message("m-1", "a", 1000))
        dao.insertIgnoringDuplicate(message("m-2", "b", 2000))
        dao.markStatus(a, RawMessageStatus.UNMATCHED, ruleId = null, updatedAt = 3000)

        assertEquals(2, dao.allForReparse().size)
    }

    @Test
    fun `built-in rules are seeded and ordered by priority with IGNORE first`() = runTest {
        db.parsingRuleDao().upsertAll(com.wasif.khata.core.sms.BUILT_IN_RULES)

        val enabled = db.parsingRuleDao().enabled()

        assertEquals(com.wasif.khata.core.sms.BUILT_IN_RULES.size, enabled.size)
        assertEquals(com.wasif.khata.core.model.RuleKind.IGNORE, enabled.first().kind)
        assertEquals(enabled.map { it.priority }.sorted(), enabled.map { it.priority })
    }
}
