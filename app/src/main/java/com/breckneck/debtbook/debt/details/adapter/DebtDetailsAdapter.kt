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
import com.breckneck.deptbook.domain.model.DebtDomain
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols

class DebtDetailsAdapter(
    debtDomainListImmutable: List<DebtDomain>,
    private val debtClickListener: OnDebtClickListener,
    private val currencyText: String,
    private val remaining: (DebtDomain) -> java.math.BigDecimal
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
        holder.itemView.alpha = if (debtDomain.cancelled) 0.65f else 1f
        if (debtDomain.repaymentOfId == null) {
            holder.operation.text = context.getString(R.string.debt_record, debtDomain.id,
                context.getString(if (debtDomain.sum.signum() >= 0) R.string.debt_lent else R.string.debt_borrowed))
            val rest = remaining(debtDomain)
            holder.remaining.text = context.getString(R.string.debt_repaid,
                decimalFormat.format(debtDomain.sum.abs() - rest), currencyText) + "\n" +
                context.getString(R.string.debt_remaining, decimalFormat.format(rest), currencyText) +
                if (rest.signum() == 0) " · ${context.getString(R.string.repayment_closed)}" else ""
            holder.remaining.visibility = View.VISIBLE
        } else {
            holder.operation.text = context.getString(R.string.repayment_parent, debtDomain.repaymentOfId,
                context.getString(if (debtDomain.cancelled) R.string.repayment_cancelled else
                    if (debtDomain.sum.signum() < 0) R.string.repayment_received else R.string.repayment_paid))
            holder.remaining.visibility = View.GONE
        }
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
