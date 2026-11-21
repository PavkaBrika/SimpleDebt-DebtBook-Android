package com.breckneck.debtbook.debt

import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import com.breckneck.debtbook.debt.statement.formatPersonDebtStatement
import com.breckneck.deptbook.domain.model.DebtDomain
import com.breckneck.deptbook.domain.model.PersonDebtHistory
import com.breckneck.deptbook.domain.util.DebtOrderAttribute
import com.breckneck.deptbook.domain.util.Filter
import org.junit.Assert.*
import org.junit.Test
import java.util.Locale

class PersonDebtStatementTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val old = DebtDomain(1, 1000.toBigDecimal(), 1, "Original note", "2025-01-01")
    private fun statement(rows: List<DebtDomain>, language: String, balance: String = "0", detailed: Boolean = false): String {
        val config = Configuration(context.resources.configuration).apply { setLocale(Locale.forLanguageTag(language)) }
        return formatPersonDebtStatement(context.createConfigurationContext(config).resources, "Anna", "RUB",
            balance.toBigDecimal(), PersonDebtHistory.from(rows), Filter.ALL, DebtOrderAttribute.CreationDate to false, detailed)
    }

    @Test fun oppositeLoansAndUnclassifiedMovementsInRussianAndEnglish() {
        val oldRows = listOf(old, old.copy(id = 2, sum = -old.sum, info = "Other note", date = "raw date"))
        for (lang in listOf("ru", "en")) {
            val neutral = statement(oldRows, lang)
            val known = statement(oldRows.map { it.copy(recordedAsLoan = true) }, lang)
            val lend = if (lang == "ru") "Заём от меня вам — " else "I lent you "
            val borrow = if (lang == "ru") "Заём от вас мне — " else "You lent me "
            val balance = if (lang == "ru") "Общий баланс · 0 RUB" else "Overall balance · 0 RUB"
            assertTrue(known.contains(balance) && neutral.contains(balance))
            assertTrue(known.contains(lend + "1 000 RUB") && known.contains(borrow + "1 000 RUB"))
            assertFalse(neutral.contains(lend) || neutral.contains(borrow))
            listOf("Original note", "Other note", "raw date", "+1 000 RUB", "-1 000 RUB").forEach { assertTrue(neutral.contains(it)) }
        }
    }

    @Test fun runningTotalsAndOldFractionsSurviveCancellationAndOpeningAdjustment() {
        val loan = old.copy(sum = "-10.005".toBigDecimal())
        val payment = old.copy(id = 2, sum = "2.50".toBigDecimal(), repaymentOfId = loan.id)
        val rows = listOf(loan, payment, payment.copy(id = 3, cancelled = true))
        // Includes a +1 opening adjustment.
        val text = statement(rows, "en", "-6.505", detailed = true)
        listOf("Overall balance · I owe you 6.505 RUB", "You lent me 10.005 RUB", "I repaid you 2.50 RUB",
            "Total repaid · 2.50 RUB", "Remaining on this loan · 7.505 RUB").forEach { assertTrue(text, text.contains(it)) }
        assertEquals(2, text.lines().count { it == "Running total of entries shown · -7.505 RUB" })
    }
}
