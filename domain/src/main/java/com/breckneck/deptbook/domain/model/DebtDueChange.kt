package com.breckneck.deptbook.domain.model

data class DebtDueChange(val day: String?, val minute: Int?) {
    fun schedulesReminderComparedTo(previous: DebtDomain): Boolean =
        minute != null && (day != previous.dueDay || minute != previous.reminderMinute || previous.reminderDelivered)
}
