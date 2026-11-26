package com.breckneck.debtbook.core.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DonutSmall
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.breckneck.debtbook.R
import com.breckneck.debtbook.core.navigation.MainTab
import com.breckneck.debtbook.core.ui.theme.bubbleColors

private data class BarTab(val destination: MainTab, val title: Int, val icon: ImageVector)

private val barTabs = listOf(
    BarTab(MainTab.FINANCES, R.string.finances, Icons.Outlined.DonutSmall),
    BarTab(MainTab.DEBTS, R.string.debts, Icons.Outlined.MenuBook),
    BarTab(MainTab.GOALS, R.string.goals, Icons.Outlined.Flag),
    BarTab(MainTab.SETTINGS, R.string.settings, Icons.Outlined.Tune),
)

internal val LocalHostedTabBarHeight = compositionLocalOf<Dp?> { null }

/** Keep the content clear of the shared panel. Previews can render their own panel. */
@Composable
internal fun BubbleScreenTabs(destination: MainTab) {
    val hostedHeight = LocalHostedTabBarHeight.current
    if (hostedHeight == null) {
        BubbleBottomBar(destination, {})
    } else {
        Spacer(Modifier.height(hostedHeight))
    }
}

/** Place above the system gesture inset. The host supplies the current nav graph destination. */
@Composable
internal fun BubbleBottomBar(
    destination: MainTab,
    onDestinationSelected: (MainTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectedIndex = barTabs.indexOfFirst { it.destination == destination }
    if (selectedIndex < 0) return

    val colors = bubbleColors()
    val layoutDirection = LocalLayoutDirection.current
    val position by animateFloatAsState(
        targetValue = selectedIndex.toFloat(),
        animationSpec = tween(300, easing = FastOutSlowInEasing),
        label = "Selected tab",
    )

    Box(
        modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).testTag("bubbleBottomBar-${destination.key}"),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier.widthIn(max = 342.dp).fillMaxWidth(),
            shape = CircleShape,
            color = colors.paper,
            border = BorderStroke(1.dp, colors.border),
            shadowElevation = 3.dp,
        ) {
            Box(Modifier.padding(6.dp)) {
                Box(Modifier.matchParentSize()) {
                    Box(
                        Modifier.fillMaxWidth(1f / barTabs.size).fillMaxHeight()
                            .graphicsLayer {
                                val direction = if (layoutDirection == LayoutDirection.Ltr) 1f else -1f
                                translationX = direction * size.width * position
                            }
                            .background(colors.selectedTab, CircleShape).testTag("bottomTabIndicator"),
                    )
                }
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).selectableGroup()) {
                    barTabs.forEach { tab ->
                        val selected = tab.destination == destination
                        val tint = if (selected) Color(0xFF203D26) else colors.muted
                        Column(
                            modifier = Modifier.weight(1f).fillMaxHeight().heightIn(min = 50.dp)
                                .clip(CircleShape)
                                .testTag("bottomTab-${tab.destination.key}")
                                .selectable(selected, role = Role.Tab) { onDestinationSelected(tab.destination) }
                                .padding(horizontal = 1.dp, vertical = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically),
                        ) {
                            Icon(tab.icon, contentDescription = null, modifier = Modifier.size(21.dp), tint = tint)
                            Text(
                                stringResource(tab.title), color = tint, fontSize = 11.sp, lineHeight = 13.sp, letterSpacing = 0.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
        }
    }
}
