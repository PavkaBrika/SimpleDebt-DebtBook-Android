package com.breckneck.debtbook.debt

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.ComposeTestRule

internal fun ComposeTestRule.record(id: Int): SemanticsNodeInteraction {
    awaitTag("personHistory").performScrollToNode(hasTestTag("record-$id"))
    return onNodeWithTag("record-$id")
}

internal fun ComposeTestRule.awaitTag(tag: String): SemanticsNodeInteraction {
    waitUntil(12_000) { onAllNodesWithTag(tag).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
    return onNodeWithTag(tag)
}
