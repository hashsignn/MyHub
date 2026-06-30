package com.contentreg.app.feature1_doomscroll.budget

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * M1.2 — behavioural tests for [TimeBudgetTracker] against an in-memory fake repository. Covers
 * cross-app accumulation, the "only while scrolling" gate, and the persistence/restore path that
 * makes the count survive process death.
 */
class TimeBudgetTrackerTest {

    private val instagram = "com.instagram.android"
    private val tiktok = "com.zhiliaoapp.musically"
    private val targets = setOf(instagram, tiktok)
    private val baseNow = 1_000L * BudgetMath.HOUR_MS + 5_000L

    private class FakeBudgetRepository(var stored: BudgetState? = null) : BudgetRepository {
        var saveCount = 0
        override suspend fun load(): BudgetState? = stored
        override suspend fun save(state: BudgetState) {
            stored = state
            saveCount++
        }
    }

    @Test
    fun `accumulates while scrolling a target app`() = runTest {
        val repo = FakeBudgetRepository()
        var now = baseNow
        val tracker = TimeBudgetTracker(
            repository = repo,
            foregroundProvider = { instagram },
            scrollingProvider = { true },
            targetProvider = { targets },
            clock = { now },
        )
        tracker.loadInitial()

        now += 1_000L; tracker.tick()
        now += 1_000L; tracker.tick()

        assertEquals(2_000L, tracker.state.value.usedMs)
        assertEquals(2_000L, repo.stored?.usedMs)
    }

    @Test
    fun `one shared budget is drawn down across apps`() = runTest {
        val repo = FakeBudgetRepository()
        var now = baseNow
        var foreground = instagram
        val tracker = TimeBudgetTracker(
            repository = repo,
            foregroundProvider = { foreground },
            scrollingProvider = { true },
            targetProvider = { targets },
            clock = { now },
        )
        tracker.loadInitial()

        now += 1_000L; tracker.tick() // 1s in Instagram
        foreground = tiktok
        now += 1_000L; tracker.tick() // 1s in TikTok → same budget

        assertEquals(2_000L, tracker.state.value.usedMs)
    }

    @Test
    fun `does not accumulate when not scrolling or off a target app`() = runTest {
        val repo = FakeBudgetRepository()
        var now = baseNow
        var foreground: String? = instagram
        var scrolling = false
        val tracker = TimeBudgetTracker(
            repository = repo,
            foregroundProvider = { foreground },
            scrollingProvider = { scrolling },
            targetProvider = { targets },
            clock = { now },
        )
        tracker.loadInitial()

        now += 1_000L; tracker.tick() // target app but not scrolling → no charge
        scrolling = true
        foreground = "com.android.settings"
        now += 1_000L; tracker.tick() // scrolling but not a target app → no charge

        assertEquals(0L, tracker.state.value.usedMs)
        assertEquals(0, repo.saveCount) // nothing changed → nothing persisted
    }

    @Test
    fun `restored tracker resumes the persisted count (survives process death)`() = runTest {
        val repo = FakeBudgetRepository()
        var now = baseNow
        val first = TimeBudgetTracker(
            repository = repo,
            foregroundProvider = { instagram },
            scrollingProvider = { true },
            targetProvider = { targets },
            clock = { now },
        )
        first.loadInitial()
        now += 60_000L; first.tick() // burn 60s
        assertEquals(60_000L, repo.stored?.usedMs)

        // Simulate process death + restart: brand-new tracker, same persistent store.
        val restarted = TimeBudgetTracker(
            repository = repo,
            foregroundProvider = { instagram },
            scrollingProvider = { true },
            targetProvider = { targets },
            clock = { now },
        )
        restarted.loadInitial()

        assertEquals(60_000L, restarted.state.value.usedMs)
    }
}
