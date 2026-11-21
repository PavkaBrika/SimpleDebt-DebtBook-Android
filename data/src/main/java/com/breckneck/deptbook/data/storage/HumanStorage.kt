package com.breckneck.deptbook.data.storage

import java.math.BigDecimal

import entity.Human

interface HumanStorage  {

    fun getHuman(id: Int): Human?

    fun getAllHumans() : List<Human>

    fun replaceAllHumans(humanList: List<Human>)

    fun getPositiveHumans(): List<Human>

    fun getNegativeHumans(): List<Human>

    fun insertHuman(human: Human)

    fun getLastHumanId() : Int

    fun addSum(humanId: Int,sum: BigDecimal)

    fun getAllDebtsSum(currency: String): List<BigDecimal>

    fun getHumanSumDebtUseCase(humanId: Int): BigDecimal

    fun deleteHumanById(id: Int)

    fun updateHuman(human: Human)
}