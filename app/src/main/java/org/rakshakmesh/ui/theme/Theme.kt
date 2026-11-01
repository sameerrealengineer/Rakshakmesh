package org.rakshakmesh.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val SosRed = Color(0xFFC62828)
val SafeGreen = Color(0xFF2E7D32)

/** Official warning colours (IMD scheme). */
fun levelColor(level: String): Color = when (level.lowercase()) {
    "red" -> Color(0xFFD32F2F)
    "orange" -> Color(0xFFEF6C00)
    "yellow" -> Color(0xFFF9A825)
    else -> Color(0xFF388E3C)
}

private val Light = lightColorScheme(
    primary = Color(0xFF8E1B1B),
    secondary = Color(0xFF1E5AA8),
    error = SosRed,
    background = Color(0xFFFFFBF7),
    surface = Color(0xFFFFFBF7),
)

// Pure black background in cyclone mode saves battery on OLED screens.
private val Dark = darkColorScheme(
    primary = Color(0xFFFFB4AB),
    secondary = Color(0xFFA8C8FF),
    error = Color(0xFFFF6659),
    background = Color.Black,
    surface = Color.Black,
)

// Larger than default: low-literacy users and old phones (PRD 2.3).
private val Big = Typography(
    bodyLarge = TextStyle(fontSize = 18.sp, lineHeight = 26.sp),
    bodyMedium = TextStyle(fontSize = 16.sp, lineHeight = 23.sp),
    titleLarge = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold, lineHeight = 32.sp),
    titleMedium = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold, lineHeight = 28.sp),
    labelLarge = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
)

@Composable
fun RakshakTheme(cycloneMode: Boolean, content: @Composable () -> Unit) {
    val dark = cycloneMode || isSystemInDarkTheme()
    MaterialTheme(colorScheme = if (dark) Dark else Light, typography = Big, content = content)
}
