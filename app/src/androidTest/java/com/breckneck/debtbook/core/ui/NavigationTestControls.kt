package com.breckneck.debtbook.core.ui

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithTag
import com.breckneck.debtbook.core.navigation.RouteResumed

internal fun ComposeTestRule.awaitRoute(route: String, timeoutMillis: Long = 10_000) {
    val currentRoute = hasTestTag(route) and SemanticsMatcher.expectValue(RouteResumed, true)
    waitUntil(timeoutMillis) { onNode(currentRoute).isDisplayed() }
}

internal fun ComposeTestRule.routeIsVisible(route: String): Boolean = onNodeWithTag(route).isDisplayed()
