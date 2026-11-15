package com.breckneck.deptbook.domain.model

import java.math.BigDecimal

data class DebtDomain
    (var id: Int,
     var sum: BigDecimal,
     var idHuman: Int,
     var info: String?,
     var date: String,
     var legacyDate: String? = null) {
}