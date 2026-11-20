package com.breckneck.debtbook.debt.details.adapter

import com.breckneck.deptbook.domain.util.MoneyDisplay
import com.breckneck.deptbook.domain.util.compareTo

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.recyclerview.widget.RecyclerView
import com.breckneck.debtbook.R
import com.breckneck.debtbook.core.ui.theme.DebtBookTheme
import com.breckneck.debtbook.core.ui.theme.spacing
import com.breckneck.debtbook.debt.details.historyTitle
import com.breckneck.debtbook.debt.details.originalDebtLabel
import com.breckneck.deptbook.domain.model.DebtDomain
import com.breckneck.deptbook.domain.util.DebtReminderPolicy
import com.breckneck.deptbook.domain.util.LedgerDate
import java.math.BigDecimal

class DebtDetailsAdapter(
    debtDomainListImmutable: List<DebtDomain>,
    private val debtClickListener: OnDebtClickListener,
    private val currencyText: String,
    private val remaining: (DebtDomain) -> BigDecimal,
    private val originalDebt: (Int) -> DebtDomain?
) : RecyclerView.Adapter<DebtDetailsAdapter.DebtViewHolder>() {

    private val debtDomainList: MutableList<DebtDomain> = debtDomainListImmutable.toMutableList()

    interface OnDebtClickListener {
        fun onDebtClick(debtDomain: DebtDomain, position: Int)
    }

    @SuppressLint("NotifyDataSetChanged")
    fun updateDebtList(debtList: List<DebtDomain>) {
        debtDomainList.clear()
        debtDomainList.addAll(debtList)
        notifyDataSetChanged()
    }

    class DebtViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val debt: TextView = itemView.findViewById(R.id.debtTextView)
        val operation: TextView = itemView.findViewById(R.id.operationTextView)
        val remaining: TextView = itemView.findViewById(R.id.remainingTextView)
        val info: TextView = itemView.findViewById(R.id.infoTextView)
        val currency: TextView = itemView.findViewById(R.id.currencyTextVew)
        val debtCardView: CardView = itemView.findViewById(R.id.debtCardView)
        val infoLayout: LinearLayout = itemView.findViewById(R.id.infoLayout)
        val greenColor = itemView.resources.getColor(R.color.green)
        val redColor = itemView.resources.getColor(R.color.red)
        private var dates by mutableStateOf(Dates(""))

        init {
            itemView.findViewById<LinearLayout>(R.id.debtDatesLayout).addView(
                ComposeView(itemView.context).apply {
                    setContent {
                        DebtBookTheme {
                            val data = dates
                            val spacing = MaterialTheme.spacing
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(end = spacing.space4).testTag("historyDebtDates"),
                                verticalArrangement = Arrangement.spacedBy(spacing.space4)
                            ) {
                                Text(data.operationDay, Modifier.fillMaxWidth(), textAlign = TextAlign.End,
                                    style = MaterialTheme.typography.bodyLarge)
                                data.dueLabel?.let { due ->
                                    Text(
                                        due, Modifier.fillMaxWidth(), textAlign = TextAlign.End,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (data.overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                data.reminderLabel?.let { reminder ->
                                    Text(reminder, Modifier.fillMaxWidth(), textAlign = TextAlign.End,
                                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }, 0, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            )
        }

        fun bindDates(debt: DebtDomain, rest: BigDecimal) {
            val operationDay = LedgerDate.display(debt.date)
            val day = debt.dueDay?.takeIf { debt.repaymentOfId == null }
            if (day == null) {
                dates = Dates(operationDay)
                return
            }
            val today = LedgerDate.today()
            val overdue = DebtReminderPolicy.overdue(debt, rest, today)
            val dueLabel = when {
                overdue -> R.string.due_overdue
                day == today && rest.signum() > 0 -> R.string.due_today
                else -> R.string.due_date
            }
            val reminderLabel = when {
                debt.reminderDelivered -> null
                debt.reminderMinute != null && !overdue && rest.signum() > 0 -> R.string.due_reminder_on
                else -> R.string.due_off
            }
            dates = Dates(
                operationDay,
                itemView.context.getString(dueLabel, LedgerDate.display(day)),
                reminderLabel?.let(itemView.context::getString),
                overdue
            )
        }

        private data class Dates(
            val operationDay: String,
            val dueLabel: String? = null,
            val reminderLabel: String? = null,
            val overdue: Boolean = false
        )
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DebtViewHolder {
        val itemView =
            LayoutInflater.from(parent.context).inflate(R.layout.item_debt, parent, false)
        return DebtViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: DebtViewHolder, position: Int) {
        val debtDomain = debtDomainList[position]
        holder.debtCardView.tag = debtDomain.id
        val context = holder.itemView.context
        val rest = if (debtDomain.repaymentOfId == null) remaining(debtDomain) else BigDecimal.ZERO
        holder.bindDates(debtDomain, rest)
        holder.itemView.alpha = if (debtDomain.cancelled) 0.65f else 1f
        holder.operation.setText(debtDomain.historyTitle())
        val originalId = debtDomain.repaymentOfId
        val details = if (originalId == null) {
            buildList {
                if (rest < debtDomain.sum.abs()) {
                    add(context.getString(R.string.debt_repaid, MoneyDisplay.format(debtDomain.sum.abs() - rest, currencyText), currencyText))
                    add(if (rest.signum() == 0) context.getString(R.string.repayment_closed)
                        else context.getString(R.string.debt_remaining, MoneyDisplay.format(rest, currencyText), currencyText))
                }
            }.joinToString("\n")
        } else {
            originalDebt(originalId)?.let { context.originalDebtLabel(it, currencyText) }.orEmpty()
        }
        holder.remaining.text = details
        holder.remaining.visibility = if (details.isBlank()) View.GONE else View.VISIBLE
        if (debtDomain.sum > 0) {
            holder.debt.setTextColor(holder.greenColor)
            holder.currency.setTextColor(holder.greenColor)
            holder.debt.text = "+${MoneyDisplay.format(debtDomain.sum, currencyText)}"
        } else {
            holder.debt.setTextColor(holder.redColor)
            holder.currency.setTextColor(holder.redColor)
            holder.debt.text = MoneyDisplay.format(debtDomain.sum, currencyText)
        }
        holder.info.text = debtDomain.info
        holder.currency.text = currencyText
        holder.infoLayout.visibility = if (debtDomain.info.isNullOrBlank()) View.GONE else View.VISIBLE
        holder.debtCardView.setOnClickListener {
            debtClickListener.onDebtClick(debtDomain = debtDomain, position = position)
        }
    }

    override fun getItemCount(): Int {
        return debtDomainList.size
    }
}
