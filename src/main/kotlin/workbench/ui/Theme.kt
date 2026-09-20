package workbench.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Fluent / VS Code 계열 모던 데스크탑 다크 */
val AppBg = Color(0xFF1B1B1F)
val Chrome = Color(0xFF222228)
val Panel = Color(0xFF2B2B32)
val Hairline = Color(0xFF3C3C44)
val Accent = Color(0xFF4C8DFF)
val OnAccent = Color(0xFFFFFFFF)
val TextPrimary = Color(0xFFF2F2F5)
val TextMuted = Color(0xFFA1A1AA)
val Danger = Color(0xFFFF6B6B)
val Ok = Color(0xFF3DDC97)
val DirColor = Color(0xFF8AB4FF)
val FileColor = Color(0xFFD4D4D8)
val TermBg = Color(0xFF111114)
val TermFg = Color(0xFFD4F5E2)

private val WorkbenchColors: ColorScheme = darkColorScheme(
    primary = Accent,
    onPrimary = OnAccent,
    background = AppBg,
    onBackground = TextPrimary,
    surface = Chrome,
    onSurface = TextPrimary,
    surfaceVariant = Panel,
    onSurfaceVariant = TextMuted,
    error = Danger,
    outline = Hairline,
)

private val WorkbenchTypography = Typography(
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = TextPrimary),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, color = TextMuted, letterSpacing = 0.4.sp),
    labelMedium = TextStyle(fontSize = 12.sp, color = TextMuted),
    bodySmall = TextStyle(fontSize = 13.sp, color = TextPrimary, fontFamily = FontFamily.Monospace, lineHeight = 18.sp),
    bodyMedium = TextStyle(fontSize = 13.sp, color = TextPrimary),
)

@Composable
fun WorkbenchTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = WorkbenchColors, typography = WorkbenchTypography, content = content)
}
