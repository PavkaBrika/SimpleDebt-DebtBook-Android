package com.breckneck.deptbook.domain.util

import com.breckneck.deptbook.domain.model.DebtDomain
import java.math.BigDecimal
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/** Calendar dates float with the device zone; persisted delivery state never floats back. */
object DebtReminderPolicy {
    fun validate(debt: DebtDomain) {
        debt.dueDay?.let { require(LedgerDate.canonical(it) == it) { "Invalid due day" } }
        require(debt.dueDay == null || (debt.repaymentOfId == null && debt.sum.signum() != 0))
        require((debt.reminderMinute == null) == (debt.reminderToken == null))
        debt.reminderMinute?.let {
            require(it in 0..1439 && debt.dueDay != null)
            require(debt.reminderToken!!.matches(Regex("[A-Za-z0-9-]{1,100}")))
        }
        require(!debt.reminderDelivered || debt.reminderToken != null)
    }

    fun trigger(day: String, minute: Int, zone: TimeZone = TimeZone.getDefault()): Long {
        require(LedgerDate.canonical(day) == day && minute in 0..1439)
        return Calendar.getInstance(zone, Locale.ROOT).apply {
            clear()
            // Calendar resolves missing/ambiguous local DST times once; token guards repeats.
            set(day.substring(0, 4).toInt(), day.substring(5, 7).toInt() - 1,
                day.substring(8, 10).toInt(), minute / 60, minute % 60)
        }.timeInMillis
    }

    fun today(now: Long, zone: TimeZone = TimeZone.getDefault()): String =
        Calendar.getInstance(zone).apply { timeInMillis = now }.let {
            LedgerDate.day(it.get(Calendar.YEAR), it.get(Calendar.MONTH), it.get(Calendar.DAY_OF_MONTH))
        }

    fun overdue(debt: DebtDomain, remaining: BigDecimal, today: String = LedgerDate.today()) =
        debt.dueDay?.let { remaining.signum() > 0 && it < today } ?: false

    fun next(debt: DebtDomain, history: List<DebtDomain>, now: Long,
             zone: TimeZone = TimeZone.getDefault()): Long? {
        if (debt.reminderToken == null || debt.reminderDelivered || debt.dueDay == null ||
            debt.reminderMinute == null || debt.dueDay < today(now, zone) ||
            RepaymentPolicy.remaining(debt, history).signum() <= 0) return null
        return maxOf(trigger(debt.dueDay, debt.reminderMinute, zone), now + 1000)
    }
}
