package org.example.slippy.foundation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BudgetTest {
    @Test
    fun ticksAreWorkedOutPerMillionTokens() =
        // 1000 input at 10000/M = 10 ticks, 200 output at 50000/M = 10 ticks
        assertEquals(20, ticksFor(1000, 200, 10_000, 50_000))

    @Test
    fun partOfATickRoundsUp() = assertEquals(1, ticksFor(1, 0, 10_000, 50_000))

    @Test
    fun noTokensCostNothing() = assertEquals(0, ticksFor(0, 0, 10_000, 50_000))

    @Test
    fun withinBudgetIsNoError() = assertNull(budgetExceeded(10, 100, BudgetLimits(2000, 20000)))

    @Test
    fun usedUpWeekIsBudgetExceeded() =
        assertEquals(ERROR_CODE_BUDGET_EXCEEDED, budgetExceeded(2000, 2000, BudgetLimits(2000, 20000))?.code)

    @Test
    fun totalIsCheckedBeforeTheWeek() =
        assertEquals(ERROR_CODE_TOTAL_BUDGET_EXCEEDED, budgetExceeded(5000, 20000, BudgetLimits(2000, 20000))?.code)

    @Test
    fun guestWithoutWeeklyBudgetOnlyHasItsTotal() {
        assertNull(budgetExceeded(999, 999, BudgetLimits(null, 1000)))
        assertEquals(429, budgetExceeded(0, 1000, BudgetLimits(null, 1000))?.status)
    }

    @Test
    fun guestBudgetZeroAllowsNoPaidCall() =
        assertEquals(ERROR_CODE_TOTAL_BUDGET_EXCEEDED, budgetExceeded(0, 0, BudgetLimits(null, 0))?.code)

    @Test
    fun userOverrideWinsOverTheVar() {
        val user = User(1, isGuest = false, name = null, weeklyBudgetTicks = 5, totalBudgetTicks = 50)
        val env = js("({WEEKLY_BUDGET_TICKS: '2000', TOTAL_BUDGET_TICKS: '20000'})").unsafeCast<Env>()
        assertEquals(BudgetLimits(5, 50), budgetLimits(user, env))
    }

    @Test
    fun guestUsesTheGuestTotalAndNoWeek() {
        val guest = User(2, isGuest = true, name = "Ann", weeklyBudgetTicks = null, totalBudgetTicks = null)
        val env = js("({WEEKLY_BUDGET_TICKS: '2000', TOTAL_BUDGET_TICKS: '20000', GUEST_TOTAL_BUDGET_TICKS: '300'})")
            .unsafeCast<Env>()
        assertEquals(BudgetLimits(null, 300), budgetLimits(guest, env))
    }
}
