package com.jianzhang.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Immutable
data class JzColors(
    val bg: Color,
    val surface: Color,
    val surfaceRaised: Color,
    val track: Color,
    val ink: Color,
    val ink2: Color,
    val ink3: Color,
    val line: Color,
    val lineHard: Color,
    val accent: Color,
    val onAccent: Color,
    val income: Color,
    val expense: Color,
    val glassTop: Color,
    val glassMid: Color,
    val glassBottom: Color,
    val isDark: Boolean,
)

val DarkJzColors = JzColors(
    bg = Color(0xFF121316),
    surface = Color(0xFF1A1C20),
    surfaceRaised = Color(0xFF202227),
    track = Color(0xFF0D0E11),
    ink = Color(0xFFEDEFF2),
    ink2 = Color(0xFF98A0AA),
    ink3 = Color(0xFF7E8894),
    line = Color(0x1AFFFFFF),
    lineHard = Color(0x2EFFFFFF),
    accent = Color(0xFFE9B44C),
    onAccent = Color(0xFF2B1C05),
    income = Color(0xFF4FD1A5),
    expense = Color(0xFFFF7A5A),
    glassTop = Color(0xB823252B),
    glassMid = Color(0xD9121316),
    glassBottom = Color(0xF20B0C0F),
    isDark = true,
)

val LightJzColors = JzColors(
    bg = Color(0xFFF4F1EA),
    surface = Color(0xFFFCFAF5),
    surfaceRaised = Color(0xFFFFFFFF),
    track = Color(0xFFE7E1D6),
    ink = Color(0xFF1A1C20),
    ink2 = Color(0xFF5C626B),
    ink3 = Color(0xFF626A73),
    line = Color(0x1A1A1C20),
    lineHard = Color(0x2E1A1C20),
    accent = Color(0xFF8A5F12),
    onAccent = Color(0xFFFFF8E8),
    income = Color(0xFF15795A),
    expense = Color(0xFFB1452D),
    glassTop = Color(0xE6FCFAF5),
    glassMid = Color(0xF2F4F1EA),
    glassBottom = Color(0xFAF4F1EA),
    isDark = false,
)

val LocalJzColors = staticCompositionLocalOf { DarkJzColors }

@Immutable
data class JzSpacing(
    val xs: androidx.compose.ui.unit.Dp = 4.dp,
    val sm: androidx.compose.ui.unit.Dp = 8.dp,
    val md: androidx.compose.ui.unit.Dp = 12.dp,
    val lg: androidx.compose.ui.unit.Dp = 16.dp,
    val xl: androidx.compose.ui.unit.Dp = 24.dp,
    val xxl: androidx.compose.ui.unit.Dp = 32.dp,
    val page: androidx.compose.ui.unit.Dp = 20.dp,
)

val LocalJzSpacing = staticCompositionLocalOf { JzSpacing() }

private val JzTypography = Typography(
    displayLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 44.sp, lineHeight = 46.sp, letterSpacing = (-0.8).sp),
    displayMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 34.sp, lineHeight = 38.sp, letterSpacing = (-0.5).sp),
    headlineLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 28.sp),
    headlineMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 24.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 22.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 19.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 17.sp, letterSpacing = 0.2.sp),
    labelSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 10.sp, lineHeight = 13.sp, letterSpacing = 1.2.sp),
)

private val JzShapes = Shapes(
    extraSmall = RoundedCornerShape(0.dp),
    small = RoundedCornerShape(0.dp),
    medium = RoundedCornerShape(0.dp),
    large = RoundedCornerShape(0.dp),
    extraLarge = RoundedCornerShape(0.dp),
)

@Composable
fun JzTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkJzColors else LightJzColors
    val material = if (darkTheme) {
        darkColorScheme(
            primary = colors.accent,
            onPrimary = colors.onAccent,
            background = colors.bg,
            onBackground = colors.ink,
            surface = colors.surface,
            onSurface = colors.ink,
            outline = colors.lineHard,
            error = colors.expense,
        )
    } else {
        lightColorScheme(
            primary = colors.accent,
            onPrimary = colors.onAccent,
            background = colors.bg,
            onBackground = colors.ink,
            surface = colors.surface,
            onSurface = colors.ink,
            outline = colors.lineHard,
            error = colors.expense,
        )
    }
    CompositionLocalProvider(
        LocalJzColors provides colors,
        LocalJzSpacing provides JzSpacing(),
    ) {
        MaterialTheme(
            colorScheme = material,
            typography = JzTypography,
            shapes = JzShapes,
            content = content,
        )
    }
}

object Jz {
    val colors: JzColors @Composable get() = LocalJzColors.current
    val space: JzSpacing @Composable get() = LocalJzSpacing.current
}
