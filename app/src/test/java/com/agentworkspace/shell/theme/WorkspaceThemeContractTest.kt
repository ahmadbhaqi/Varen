package com.agentworkspace.shell.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class WorkspaceThemeContractTest {
    @Test
    fun paletteIsStrictlyMonochromeWithClearElevationSteps() {
        assertEquals(Color(0xFF050505), BackgroundColor)
        assertEquals(Color(0xFF0C0C0C), SurfaceColor)
        assertEquals(Color(0xFF111111), SurfaceVariantColor)
        assertEquals(Color(0xFF171717), ElevatedColor)
        assertEquals(Color.White, Primary)
        assertEquals(Color.White, Accent)
        assertEquals(Color(0xFFF7F7F7), TextPrimary)
        assertNotEquals(BackgroundColor, SurfaceColor)
        assertNotEquals(SurfaceColor, ElevatedColor)
    }

    @Test
    fun typographyUsesFourPrimaryLevelsAndTwoWeights() {
        assertEquals(28.sp, Typography.displaySmall.fontSize)
        assertEquals(18.sp, Typography.titleLarge.fontSize)
        assertEquals(15.sp, Typography.bodyLarge.fontSize)
        assertEquals(12.sp, Typography.labelMedium.fontSize)
        assertEquals(FontWeight.SemiBold, Typography.titleLarge.fontWeight)
        assertEquals(FontWeight.Normal, Typography.bodyLarge.fontWeight)
    }

    @Test
    fun shapesStayControlledBetweenEightAndSixteenDp() {
        assertEquals(RoundedCornerShape(8.dp), Shapes.extraSmall)
        assertEquals(RoundedCornerShape(10.dp), Shapes.small)
        assertEquals(RoundedCornerShape(12.dp), Shapes.medium)
        assertEquals(RoundedCornerShape(14.dp), Shapes.large)
        assertEquals(RoundedCornerShape(16.dp), Shapes.extraLarge)
    }
}
