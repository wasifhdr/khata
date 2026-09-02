package com.wasif.khata.feature.unmatched

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.wasif.khata.core.data.KhataDatabase
import com.wasif.khata.core.data.entity.RawMessageEntity
import com.wasif.khata.core.model.RawMessageStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class UnmatchedViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var db: KhataDatabase

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        // Room delivers Flow query results on its own executor. Left as the default
        // that is a real background thread, which advanceUntilIdle() cannot drive, so
        // every assertion would read the initial value.
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            KhataDatabase::class.java,
        )
            .setQueryExecutor(dispatcher.asExecutor())
            .setTransactionExecutor(dispatcher.asExecutor())
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    private suspend fun insert(
        uuid: String,
        body: String,
        receivedAt: Long,
        status: RawMessageStatus,
        sender: String = "bKash",
    ) = db.rawMessageDao().insertIgnoringDuplicate(
        RawMessageEntity(
            uuid = uuid,
            sender = sender,
            body = body,
            receivedAt = receivedAt,
            bodyHash = uuid,
            status = status,
            matchedRuleId = null,
            createdAt = receivedAt,
            updatedAt = receivedAt,
        )
    )

    /**
     * state is WhileSubscribed, so without a collector it never leaves its initial
     * value and every assertion below would read the empty default.
     */
    private fun TestScope.startedViewModel(): UnmatchedViewModel =
        UnmatchedViewModel(db.rawMessageDao()).also { vm ->
            backgroundScope.launch { vm.state.collect {} }
        }

    @Test
    fun `only unmatched messages appear`() = runTest(dispatcher) {
        insert("a", "unknown format", 3000, RawMessageStatus.UNMATCHED)
        insert("b", "an otp", 2000, RawMessageStatus.IGNORED)
        insert("c", "a payment", 1000, RawMessageStatus.PARSED)

        val vm = startedViewModel()
        advanceUntilIdle()

        assertEquals(listOf("unknown format"), vm.state.value.messages.map { it.body })
    }

    @Test
    fun `newest first, because a format that broke recently is the one worth a rule`() = runTest(dispatcher) {
        insert("old", "older", 1000, RawMessageStatus.UNMATCHED)
        insert("new", "newer", 3000, RawMessageStatus.UNMATCHED)

        val vm = startedViewModel()
        advanceUntilIdle()

        assertEquals(listOf("newer", "older"), vm.state.value.messages.map { it.body })
    }

    @Test
    fun `an empty list is distinguishable from not yet loaded`() = runTest(dispatcher) {
        val vm = startedViewModel()

        assertFalse("must not claim loaded before the query returns", vm.state.value.isLoaded)

        advanceUntilIdle()

        assertTrue(vm.state.value.isLoaded)
        assertTrue(vm.state.value.isEmpty)
    }

    @Test
    fun `sender and received time survive to the UI model`() = runTest(dispatcher) {
        insert("a", "unknown", 4242, RawMessageStatus.UNMATCHED, sender = "EBL")

        val vm = startedViewModel()
        advanceUntilIdle()

        val message = vm.state.value.messages.single()
        assertEquals("EBL", message.sender)
        assertEquals(4242L, message.receivedAt)
    }

    @Test
    fun `a Bengali body is flagged so it gets a Bengali type style`() = runTest(dispatcher) {
        insert("bn", "আপনার হিসাব", 2000, RawMessageStatus.UNMATCHED)
        insert("en", "Your account", 1000, RawMessageStatus.UNMATCHED)

        val vm = startedViewModel()
        advanceUntilIdle()

        assertEquals(
            mapOf("আপনার হিসাব" to true, "Your account" to false),
            vm.state.value.messages.associate { it.body to it.isBengali },
        )
    }

    @Test
    fun `a message that becomes parsed leaves the list`() = runTest(dispatcher) {
        val id = insert("a", "unknown", 1000, RawMessageStatus.UNMATCHED)
        val vm = startedViewModel()
        advanceUntilIdle()
        assertEquals(1, vm.state.value.messages.size)

        db.rawMessageDao().markStatus(id, RawMessageStatus.PARSED, ruleId = 1, updatedAt = 2000)
        advanceUntilIdle()

        assertTrue(vm.state.value.isEmpty)
    }
}
