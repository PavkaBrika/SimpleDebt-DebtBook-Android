package com.breckneck.debtbook.core.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.unit.sp

internal val DebtBookTypography = Typography().run {
    copy(
        headlineSmall = headlineSmall.copy(lineHeight = 29.sp, letterSpacing = 0.sp),
        titleLarge = titleLarge.copy(lineHeight = 26.sp, letterSpacing = 0.sp),
        titleMedium = titleMedium.copy(lineHeight = 22.sp, letterSpacing = 0.sp),
        titleSmall = titleSmall.copy(lineHeight = 19.sp, letterSpacing = 0.sp),
        bodyLarge = bodyLarge.copy(lineHeight = 22.sp, letterSpacing = 0.sp),
        bodyMedium = bodyMedium.copy(lineHeight = 19.sp, letterSpacing = 0.sp),
        bodySmall = bodySmall.copy(letterSpacing = 0.sp),
        labelLarge = labelLarge.copy(lineHeight = 19.sp, letterSpacing = 0.sp),
        labelMedium = labelMedium.copy(letterSpacing = 0.sp),
        labelSmall = labelSmall.copy(letterSpacing = 0.sp),
    )
}
