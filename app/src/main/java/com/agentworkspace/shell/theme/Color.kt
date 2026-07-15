package com.agentworkspace.shell.theme

import androidx.compose.ui.graphics.Color

// ---------------------------------------------------------------------------
// Adaptive Canvas monochrome tokens. Hierarchy comes from spacing, type,
// borders, and tonal elevation instead of colored decoration.
// ---------------------------------------------------------------------------

// Neutral ink scale ---------------------------------------------------------
val BackgroundColor = Color(0xFF050505)
val SurfaceColor = Color(0xFF0C0C0C)
val SurfaceVariantColor = Color(0xFF111111)
val ElevatedColor = Color(0xFF171717)
val BorderColor = Color(0x1AFFFFFF)
val BorderStrongColor = Color(0x33FFFFFF)

// Neutral "brand" ink for headings and high-emphasis text -------------------
val Primary = Color(0xFFFFFFFF)
val PrimaryLight = Color(0xFFFFFFFF)
val PrimaryDark = Color(0xFFE5E5E5)
val PrimaryContainer = Color(0xFF171717)
val OnPrimaryContainer = Color(0xFFF5F5F5)

val Secondary = Color(0xFFE5E5E5)
val SecondaryContainer = Color(0xFF111111)
val OnSecondaryContainer = Color(0xFFE5E5E5)

val Tertiary = Color(0xFFA3A3A3)
val TertiaryContainer = Color(0xFF171717)
val OnTertiaryContainer = Color(0xFFE5E5E5)

// Semantic colours — reserved for state where monochrome loses clarity ------
val Success = Color(0xFFF5F5F5)
val SuccessGlow = Color(0x14FFFFFF)
val Warning = Color(0xFFD4D4D4)
val Error = Color(0xFFFFFFFF)
val Info = Color(0xFFBDBDBD)

// Text scale tuned for comfortable contrast on the ink surfaces -------------
val TextPrimary = Color(0xFFF7F7F7)
val TextSecondary = Color(0xFFA3A3A3)
val TextDisabled = Color(0xFF666666)
val TextOnBrand = Color(0xFF050505)

// Signature gradient stops --------------------------------------------------
val BrandStart = Color(0xFFFFFFFF)
val BrandMid = Color(0xFFE5E5E5)
val BrandEnd = Color(0xFFFFFFFF)

// One accent for selection, focus, live state and the primary action --------
val Accent = Color(0xFFFFFFFF)
val AccentSoft = Color(0x14FFFFFF)
val AccentGlow = Color(0x1FFFFFFF)
