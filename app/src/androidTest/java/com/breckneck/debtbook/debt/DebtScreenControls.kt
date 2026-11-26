package com.breckneck.debtbook.debt

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.ComposeTestRule
import com.breckneck.debtbook.core.ui.awaitRoute

internal fun ComposeTestRule.openDebts() {
    awaitTag("bottomTab-debts").performClick()
    awaitRoute("Debts")
}

internal fun ComposeTestRule.record(id: Int): SemanticsNodeInteraction {
    val tag = "record-$id"
    val list = awaitTag("personHistory").performScrollToNode(hasTestTag(tag))
    val record = onNodeWithTag(tag)
    val addDebt = onAllNodesWithTag("addDebtButton").fetchSemanticsNodes().singleOrNull()
    if (addDebt != null) {
        val overlap = record.fetchSemanticsNode().boundsInWindow.bottom - addDebt.boundsInWindow.top
        if (overlap > 0f) list.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, overlap + 8f) }
    }
    return record
}

internal fun ComposeTestRule.awaitTag(tag: String): SemanticsNodeInteraction {
    waitUntil(12_000) { onAllNodesWithTag(tag).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
    return onNodeWithTag(tag)
}
