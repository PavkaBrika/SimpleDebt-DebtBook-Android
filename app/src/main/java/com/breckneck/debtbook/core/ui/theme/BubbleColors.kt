package com.breckneck.debtbook.core.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

internal data class BubbleColors(
    val background: Color,
    val paper: Color,
    val ink: Color,
    val muted: Color,
    val border: Color,
    val mint: Color,
    val onMint: Color,
    val action: Color,
    val onAction: Color,
    val danger: Color,
    val selectedTab: Color,
    val hero: Color,
    val onHero: Color,
    val heroMuted: Color,
    val heroBorder: Color,
)

private val lightBubble = BubbleColors(
    background = Color(0xFFF6F7EF),
    paper = Color(0xFFFFFEF8),
    ink = Color(0xFF192E22),
    muted = Color(0xFF536459),
    border = Color(0xFFD9DFD3),
    mint = Color(0xFFDBEDCD),
    onMint = Color(0xFF244C30),
    action = Color(0xFF204D39),
    onAction = Color(0xFFF6F9DF),
    danger = Color(0xFF984834),
    selectedTab = Color(0xFFD6EDB4),
    hero = Color(0xFF164837),
    onHero = Color(0xFFFCFFEB),
    heroMuted = Color(0xFFC1D6BD),
    heroBorder = Color(0xFF47735C),
)

private val darkBubble = BubbleColors(
    background = Color(0xFF19251E),
    paper = Color(0xFF243128),
    ink = Color(0xFFEEF5E5),
    muted = Color(0xFFB4C2B2),
    border = Color(0xFF3B4A3E),
    mint = Color(0xFF314E39),
    onMint = Color(0xFFD3EDC7),
    action = Color(0xFFC5E8AC),
    onAction = Color(0xFF18321D),
    danger = Color(0xFFF4B29B),
    selectedTab = Color(0xFFC5E8AC),
    hero = Color(0xFF123D30),
    onHero = Color(0xFFFCFFEB),
    heroMuted = Color(0xFFC4D6B9),
    heroBorder = Color(0xFF456953),
)

@Composable
internal fun bubbleColors(): BubbleColors =
    if (LocalDebtBookDarkTheme.current) darkBubble else lightBubble
