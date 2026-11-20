package com.breckneck.debtbook.debt.details.adapter

import com.breckneck.deptbook.domain.util.compareTo

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import com.breckneck.debtbook.R
import com.breckneck.debtbook.debt.details.historyTitle
import com.breckneck.debtbook.debt.details.originalDebtLabel
import com.breckneck.deptbook.domain.model.DebtDomain
import com.breckneck.deptbook.domain.util.DebtReminderPolicy
import com.breckneck.deptbook.domain.util.LedgerDate
import java.math.BigDecimal
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols

class DebtDetailsAdapter(
    debtDomainListImmutable: List<DebtDomain>,
    private val debtClickListener: OnDebtClickListener,
    private val currencyText: String,
    private val remaining: (DebtDomain) -> BigDecimal,
    private val originalDebt: (Int) -> DebtDomain?
) : RecyclerView.Adapter<DebtDetailsAdapter.DebtViewHolder>() {

    private val decimalFormat = DecimalFormat("###,###,###.##").apply { maximumFractionDigits = 100 }
    private val customSymbol: DecimalFormatSymbols = DecimalFormatSymbols()
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
        val due: TextView = itemView.findViewById(R.id.dueTextView)
        val debt: TextView = itemView.findViewById(R.id.debtTextView)
        val operation: TextView = itemView.findViewById(R.id.operationTextView)
        val remaining: TextView = itemView.findViewById(R.id.remainingTextView)
        val date: TextView = itemView.findViewById(R.id.dateTextView)
        val info: TextView = itemView.findViewById(R.id.infoTextView)
        val currency: TextView = itemView.findViewById(R.id.currencyTextVew)
        val debtCardView: CardView = itemView.findViewById(R.id.debtCardView)
        val infoLayout: LinearLayout = itemView.findViewById(R.id.infoLayout)
        val greenColor = itemView.resources.getColor(R.color.green)
        val redColor = itemView.resources.getColor(R.color.red)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DebtViewHolder {
        customSymbol.groupingSeparator = ' '
        decimalFormat.decimalFormatSymbols = customSymbol
        val itemView =
            LayoutInflater.from(parent.context).inflate(R.layout.item_debt, parent, false)
        return DebtViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: DebtViewHolder, position: Int) {
        val debtDomain = debtDomainList[position]
        holder.debtCardView.tag = debtDomain.id
        val context = holder.itemView.context
        val rest = if (debtDomain.repaymentOfId == null) remaining(debtDomain) else BigDecimal.ZERO
        holder.due.visibility = if (debtDomain.repaymentOfId == null && debtDomain.dueDay != null) View.VISIBLE else View.GONE
        debtDomain.dueDay?.let { day ->
            val today = LedgerDate.today()
            val overdue = DebtReminderPolicy.overdue(debtDomain, rest, today)
            val minute = debtDomain.reminderMinute
            val dueLabel = when {
                overdue -> R.string.due_overdue
                day == today && rest.signum() > 0 -> R.string.due_today
                else -> R.string.due_date
            }
            holder.due.text = context.getString(dueLabel, LedgerDate.display(day)) + "\n" +
                when {
                    debtDomain.reminderDelivered -> context.getString(R.string.due_sent)
                    minute != null && !overdue && rest.signum() > 0 -> context.getString(R.string.due_reminder_at,
                        String.format(java.util.Locale.getDefault(), "%02d:%02d", minute / 60, minute % 60))
                    else -> context.getString(R.string.due_off)
                }
            holder.due.setTextColor(if (overdue) holder.redColor else holder.operation.currentTextColor)
        }
        holder.itemView.alpha = if (debtDomain.cancelled) 0.65f else 1f
        holder.operation.setText(debtDomain.historyTitle())
        val originalId = debtDomain.repaymentOfId
        val details = if (originalId == null) {
            buildList {
                if (rest < debtDomain.sum.abs()) {
                    add(context.getString(R.string.debt_repaid, decimalFormat.format(debtDomain.sum.abs() - rest), currencyText))
                    add(if (rest.signum() == 0) context.getString(R.string.repayment_closed)
                        else context.getString(R.string.debt_remaining, decimalFormat.format(rest), currencyText))
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
            holder.debt.text = "+${decimalFormat.format(debtDomain.sum)}"
        } else {
            holder.debt.setTextColor(holder.redColor)
            holder.currency.setTextColor(holder.redColor)
            holder.debt.text = decimalFormat.format(debtDomain.sum)
        }
        holder.date.text = com.breckneck.deptbook.domain.util.LedgerDate.display(debtDomain.date)
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
