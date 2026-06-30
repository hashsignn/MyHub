package com.contentreg.app.feature1_doomscroll.budget

/**
 * M1.2 — the durable budget state.
 *
 * One shared budget is drawn down by scrolling across *all* target apps (Instagram + TikTok +
 * … share the same allowance). Only two numbers need to survive process death:
 *  - [usedMs]: how much budget has been consumed in the current window.
 *  - [windowStartMs]: which hour window that consumption belongs to, so a restart in the *same*
 *    hour resumes the count, but the first tick of a *new* hour resets it.
 *
 * This is a plain data class with no Android/Room types so [BudgetMath] can be unit-tested on the
 * JVM. Persistence mapping lives in `BudgetStateEntity`.
 */
data class BudgetState(
    val usedMs: Long,
    val windowStartMs: Long,
)
