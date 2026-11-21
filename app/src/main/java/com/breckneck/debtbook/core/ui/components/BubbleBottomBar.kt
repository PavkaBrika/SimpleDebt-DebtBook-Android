package com.breckneck.debtbook.core.ui.components

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.breckneck.debtbook.R
import com.breckneck.debtbook.core.ui.theme.bubbleColors

private data class BarTab(val destination: Int, val title: Int, val icon: ImageVector)

private val barTabs = listOf(
    BarTab(R.id.mainFragment, R.string.debts, Icons.Outlined.MenuBook),
    BarTab(R.id.financeFragment, R.string.finances, Icons.Outlined.DonutSmall),
    BarTab(R.id.goalsFragment, R.string.goals, Icons.Outlined.Flag),
    BarTab(R.id.settingsFragment, R.string.settings, Icons.Outlined.Tune),
)

/** Place above the system gesture inset. The host supplies the current nav graph destination. */
@Composable
internal fun BubbleBottomBar(
    destination: Int,
    onDestinationSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectedIndex = barTabs.indexOfFirst { it.destination == destination }
    if (selectedIndex < 0) return

    val colors = bubbleColors()

    Box(
        modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier.widthIn(max = 342.dp).fillMaxWidth(),
            shape = CircleShape,
            color = colors.paper,
            border = BorderStroke(1.dp, colors.border),
            shadowElevation = 3.dp,
        ) {
            BoxWithConstraints(Modifier.padding(6.dp)) {
                val tabWidth = maxWidth / barTabs.size
                val offset by animateDpAsState(
                    targetValue = tabWidth * selectedIndex,
                    animationSpec = tween(240, easing = CubicBezierEasing(.2f, .8f, .2f, 1f)),
                    label = "Selected destination",
                )
                Box(Modifier.matchParentSize()) {
                    Box(Modifier.offset(x = offset).width(tabWidth).fillMaxHeight().background(colors.selectedTab, CircleShape))
                }
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).selectableGroup()) {
                    barTabs.forEach { tab ->
                        val selected = tab.destination == destination
                        val tint = if (selected) Color(0xFF203D26) else colors.muted
                        Column(
                            modifier = Modifier.weight(1f).fillMaxHeight().heightIn(min = 50.dp)
                                .clip(CircleShape)
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
