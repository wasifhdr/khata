package com.wasif.khata.core.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import com.wasif.khata.core.ui.theme.FieldIntensity
import com.wasif.khata.core.ui.theme.KhataPalette
import com.wasif.khata.core.ui.theme.ThemeSpec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PreferencesRepositoryTest {

    /**
     * An in-memory store rather than a file-backed one. Two reasons, and the
     * second is the real one:
     *
     * DataStore's atomic write is write-tmp-then-rename, and on Windows a
     * rename fails while any reader still holds the destination open -- so a
     * file-backed store fails these tests for reasons that have nothing to do
     * with the code under test.
     *
     * And persistence is DataStore's job, already tested by DataStore. What is
     * ours is the mapping: index clamping, null-versus-zero, and what reset
     * clears. None of that needs a filesystem.
     */
    private class InMemoryPreferenceStore : DataStore<Preferences> {
        private val state = MutableStateFlow(emptyPreferences())
        override val data: Flow<Preferences> = state

        override suspend fun updateData(
            transform: suspend (Preferences) -> Preferences,
        ): Preferences = transform(state.value).also { state.value = it }
    }

    private lateinit var store: DataStore<Preferences>
    private lateinit var repo: PreferencesRepository

    @Before
    fun setUp() {
        store = InMemoryPreferenceStore()
        repo = PreferencesRepositoryImpl(store)
    }

    @Test
    fun `an empty store yields the default`() = runTest {
        assertEquals(KhataPreferences.Default, repo.preferences.first())
    }

    @Test
    fun `a stored theme round-trips`() = runTest {
        val spec = ThemeSpec(
            field = KhataPalette.fields[3],
            ground = KhataPalette.grounds[2].color,
            accent = KhataPalette.accents[1].color,
            intensity = FieldIntensity.Dim,
        )

        repo.setTheme(spec)

        assertEquals(spec, repo.preferences.first().themeSpec)
    }

    @Test
    fun `reset restores the default theme without touching the other settings`() = runTest {
        repo.setTheme(ThemeSpec.Default.copy(intensity = FieldIntensity.Off))
        repo.setHomeView(HomeView.Wallet)
        repo.setMonthlyBudget(75_000_00)

        repo.resetTheme()

        val p = repo.preferences.first()
        assertEquals(ThemeSpec.Default, p.themeSpec)
        assertEquals(HomeView.Wallet, p.homeView)
        assertEquals(75_000_00L, p.monthlyBudgetMinor)
    }

    @Test
    fun `an index left over from an older palette falls back instead of crashing`() = runTest {
        // A stored index can outlive the list it pointed into -- a palette edit
        // is enough. On first launch that would throw before any UI exists to
        // report it, so it has to clamp rather than crash.
        store.edit { it[intPreferencesKey("theme_field")] = 999 }

        assertEquals(ThemeSpec.Default.field, repo.preferences.first().themeSpec.field)
    }

    @Test
    fun `no budget is null rather than zero`() = runTest {
        // Zero is a real budget the user could set; "unset" has to be a
        // different value or the ring cannot know whether to draw itself.
        assertNull(repo.preferences.first().monthlyBudgetMinor)

        repo.setMonthlyBudget(0)
        assertEquals(0L, repo.preferences.first().monthlyBudgetMinor)

        repo.setMonthlyBudget(null)
        assertNull(repo.preferences.first().monthlyBudgetMinor)
    }

    @Test
    fun `the backfill flag defaults to false and survives being set`() = runTest {
        assertFalse(repo.preferences.first().hasBackfilled)

        repo.setBackfilled()

        assertTrue(repo.preferences.first().hasBackfilled)
    }
}
