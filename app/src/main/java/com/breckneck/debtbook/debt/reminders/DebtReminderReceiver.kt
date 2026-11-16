package com.breckneck.debtbook.debt.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class DebtReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val reminders = DebtReminders.get(context)
        if (intent.action == DebtReminders.ACTION_DELIVER) {
            reminders.deliver(intent.getIntExtra(DebtReminders.EXTRA_DEBT, -1),
                intent.getStringExtra(DebtReminders.EXTRA_TOKEN)) { pending.finish() }
        } else reminders.refresh { pending.finish() }
    }
}
