package com.breckneck.deptbook.domain.usecase.Debt

import com.breckneck.deptbook.domain.util.LedgerDate

class GetCurrentDateUseCase {
    fun execute(): String = LedgerDate.today()
}
