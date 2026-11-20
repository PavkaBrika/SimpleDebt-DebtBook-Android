package com.breckneck.debtbook.debt.reminders

import android.Manifest
import android.app.DatePickerDialog
import android.app.Dialog
import android.app.TimePickerDialog
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.format.DateFormat
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.breckneck.debtbook.R
import com.breckneck.debtbook.debt.details.DebtDetailsViewModel
import com.breckneck.deptbook.domain.util.LedgerDate
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.appcompat.widget.AppCompatCheckBox
import database.AppDataBase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.Locale
import java.util.UUID

/** Due settings remain editable even when principal editing is locked by repayments. */
class DebtDueDialogFragment : DialogFragment() {
    private val parentVm by viewModels<DebtDetailsViewModel>({ requireParentFragment() })
    private var day: String? = null
    private var minute = 9 * 60
    private var enabled = false
    private var loaded = false
    private var outstanding = false
    private var busy = false
    private var rendering = false
    private lateinit var content: View
    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        enabled = granted && DebtReminders.get(requireContext()).allowed()
        render()
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        content = layoutInflater.inflate(R.layout.dialog_debt_due, null)
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.due_settings).setView(content)
            .setPositiveButton(R.string.save, null).setNegativeButton(R.string.cancel, null).create()
        content.findViewById<Button>(R.id.dueDateButton).setOnClickListener {
            val calendar = Calendar.getInstance()
            day?.let { LedgerDate.localDate(it) }?.let { calendar.time = it }
            DatePickerDialog(requireContext(), { _, y, m, d -> day = LedgerDate.day(y, m, d); render() },
                calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
        }
        content.findViewById<Button>(R.id.dueClearButton).setOnClickListener { day = null; enabled = false; render() }
        content.findViewById<Button>(R.id.dueTimeButton).setOnClickListener {
            TimePickerDialog(requireContext(), { _, h, m -> minute = h * 60 + m; render() },
                minute / 60, minute % 60, DateFormat.is24HourFormat(requireContext())).show()
        }
        content.findViewById<AppCompatCheckBox>(R.id.dueReminderSwitch).setOnCheckedChangeListener { _, checked ->
            if (rendering) return@setOnCheckedChangeListener
            enabled = checked
            if (checked && !DebtReminders.get(requireContext()).allowed()) {
                enabled = false
                if (Build.VERSION.SDK_INT >= 33 &&
                    androidx.core.content.ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
            render()
        }
        content.findViewById<Button>(R.id.dueNotificationSettings).setOnClickListener {
            val settings = if (Build.VERSION.SDK_INT >= 26) {
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, requireContext().packageName)
            } else Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                android.net.Uri.parse("package:${requireContext().packageName}"))
            startActivity(settings)
        }
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { save() }
            render()
        }
        lifecycleScope.launch {
            val rows = withContext(Dispatchers.IO) { AppDataBase(requireContext()).appDao().getAllDebts() }
            val row = rows.firstOrNull { it.id == requireArguments().getInt("debtId") }
            if (row == null) { dismiss(); return@launch }
            outstanding = com.breckneck.deptbook.domain.util.RepaymentPolicy.remaining(row.toDomain(), rows.map { it.toDomain() }).signum() > 0
            day = if (savedInstanceState != null) savedInstanceState.getString("day") else row.dueDay
            minute = savedInstanceState?.getInt("minute") ?: row.reminderMinute ?: 9 * 60
            val storedReminderPending = outstanding && row.reminderMinute != null && !row.reminderDelivered &&
                row.dueDay?.let { it >= LedgerDate.today() } == true
            enabled = savedInstanceState?.getBoolean("enabled") ?: storedReminderPending
            loaded = true
            render()
        }
        return dialog
    }

    private fun render() {
        if (!::content.isInitialized) return
        val switch = content.findViewById<AppCompatCheckBox>(R.id.dueReminderSwitch)
        switch.isEnabled = loaded && !busy && day != null && outstanding
        rendering = true
        switch.isChecked = enabled
        rendering = false
        content.findViewById<Button>(R.id.dueDateButton).apply {
            text = day?.let { getString(R.string.due_date, LedgerDate.display(it)) } ?: getString(R.string.due_choose_date)
            isEnabled = loaded && !busy
        }
        content.findViewById<Button>(R.id.dueClearButton).isEnabled = loaded && !busy && day != null
        content.findViewById<Button>(R.id.dueTimeButton).apply {
            text = getString(R.string.due_time, String.format(Locale.getDefault(), "%02d:%02d", minute / 60, minute % 60))
            isEnabled = loaded && !busy && day != null
        }
        val allowed = DebtReminders.get(requireContext()).allowed()
        content.findViewById<TextView>(R.id.duePermissionStatus).visibility = if (allowed) View.GONE else View.VISIBLE
        content.findViewById<Button>(R.id.dueNotificationSettings).visibility = if (allowed) View.GONE else View.VISIBLE
        (dialog as? AlertDialog)?.getButton(AlertDialog.BUTTON_POSITIVE)?.isEnabled = loaded && !busy
        (dialog as? AlertDialog)?.getButton(AlertDialog.BUTTON_NEGATIVE)?.isEnabled = !busy
        isCancelable = !busy
    }

    private fun save() {
        if (!loaded || busy) return
        busy = true
        render()
        val context = requireContext().applicationContext
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val active = enabled && DebtReminders.get(context).allowed()
                    AppDataBase(context).appDao().setDebtDueDate(requireArguments().getInt("debtId"), day,
                        if (active) minute else null, if (active) UUID.randomUUID().toString() else null, System.currentTimeMillis())
                }
                DebtReminders.get(context).refresh()
                parentVm.retry()
                dismiss()
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (error: Exception) {
                content.findViewById<TextView>(R.id.dueError).apply {
                    visibility = View.VISIBLE
                    setText(if (error is IllegalArgumentException) R.string.due_future_required else R.string.repayment_save_failed)
                }
            } finally { busy = false; render() }
        }
    }

    override fun onResume() {
        super.onResume()
        if (loaded && !DebtReminders.get(requireContext()).allowed()) enabled = false
        render()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("day", day); outState.putInt("minute", minute); outState.putBoolean("enabled", enabled)
    }
    companion object {
        const val TAG = "debtDue"
        fun create(id: Int) = DebtDueDialogFragment().apply { arguments = Bundle().apply { putInt("debtId", id) } }
    }
}
