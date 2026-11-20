package com.breckneck.deptbook.domain.usecase.Debt

import com.breckneck.deptbook.domain.util.LedgerDate

class SetDateUseCase {
    fun execute(year: Int, month: Int, day: Int): String = LedgerDate.day(year, month, day)
}
