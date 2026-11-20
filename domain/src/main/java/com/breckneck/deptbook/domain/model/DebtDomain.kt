package com.breckneck.deptbook.domain.model

import java.math.BigDecimal

data class DebtDomain
    (var id: Int,
     var sum: BigDecimal,
     var idHuman: Int,
     var info: String?,
     var date: String,
     var legacyDate: String? = null,
     val repaymentOfId: Int? = null,
     val operationId: String? = null,
     val cancelled: Boolean = false,
     val dueDay: String? = null,
     val reminderMinute: Int? = null,
     val reminderToken: String? = null,
     val reminderDelivered: Boolean = false) {
    val effectiveSum: BigDecimal get() = if (cancelled) BigDecimal.ZERO else sum
}
