package com.breckneck.deptbook.domain.repository

/** Atomic boundary for related local writes. Execute off the UI thread. */
interface DataTransaction {
    fun <T> run(block: () -> T): T
}
