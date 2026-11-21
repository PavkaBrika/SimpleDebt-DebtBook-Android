package com.breckneck.deptbook.domain.repository

import java.math.BigDecimal

import com.breckneck.deptbook.domain.model.HumanDomain

interface HumanRepository {

    fun getHuman(id: Int): HumanDomain?

    fun getAllHumans() : List<HumanDomain>

    fun replaceAllHumans(humanList: List<HumanDomain>)

    fun getPositiveHumans(): List<HumanDomain>

    fun getNegativeHumans(): List<HumanDomain>

    fun insertHuman(humanDomain: HumanDomain)

    fun getLastHumanId() : Int

    fun addSum(humanId: Int,sum: BigDecimal)

    fun getAllDebtsSum(currency: String): List<BigDecimal>

    fun getHumanSumDebt(humanId: Int): BigDecimal

    fun deleteHuman(id: Int)

    fun updateHuman(human: HumanDomain)


}