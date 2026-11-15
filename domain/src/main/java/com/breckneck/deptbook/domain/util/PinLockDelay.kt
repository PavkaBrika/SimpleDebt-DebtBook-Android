package com.breckneck.deptbook.domain.util

enum class PinLockDelay(val millis: Long) {
    IMMEDIATE(0L), SECONDS_30(30_000L), MINUTE_1(60_000L), MINUTES_5(300_000L);

    companion object {
        fun fromMillis(value: Long): PinLockDelay = entries.firstOrNull { it.millis == value } ?: IMMEDIATE
    }
}
