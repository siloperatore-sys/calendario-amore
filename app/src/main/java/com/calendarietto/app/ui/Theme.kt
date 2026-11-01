package com.calendarietto.app.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.calendarietto.app.data.Mascot

data class KawaiiPalette(
    val background: Color,
    val card: Color,
    val primary: Color,
    val onPrimary: Color,
    val secondary: Color,
    val accent: Color,
    val text: Color,
    val muted: Color,
    val soft: Color,
    val blush: Color,
)

/** Unicorno: lilla, menta e rosa confetto. */
val UnicornPalette = KawaiiPalette(
    background = Color(0xFFFFF7FB),
    card = Color(0xFFFFFFFF),
    primary = Color(0xFFB08FEA),
    onPrimary = Color(0xFFFFFFFF),
    secondary = Color(0xFF9FE3C9),
    accent = Color(0xFFFFB3D1),
    text = Color(0xFF5B4A6B),
    muted = Color(0xFF9C8BAD),
    soft = Color(0xFFF3ECFF),
    blush = Color(0xFFFFC2D9),
)

/** Robottino: blu notte, azzurro cielo e menta. */
val RobotPalette = KawaiiPalette(
    background = Color(0xFFF2F7FF),
    card = Color(0xFFFFFFFF),
    primary = Color(0xFF4A5AA8),
    onPrimary = Color(0xFFFFFFFF),
    secondary = Color(0xFF8EC9F5),
    accent = Color(0xFFA8E6CF),
    text = Color(0xFF2B3150),
    muted = Color(0xFF7A84A8),
    soft = Color(0xFFE4EEFF),
    blush = Color(0xFFFFC9D6),
)

fun paletteFor(m: Mascot) = if (m == Mascot.ROBOT) RobotPalette else UnicornPalette

val LocalPalette = staticCompositionLocalOf { UnicornPalette }

@Composable
fun KawaiiTheme(mascot: Mascot, content: @Composable () -> Unit) {
    val p = paletteFor(mascot)
    val base = Typography()
    val typography = Typography(
        headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.ExtraBold, color = p.text),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.Bold, color = p.text),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.Bold, color = p.text),
        bodyLarge = base.bodyLarge.copy(color = p.text),
        bodyMedium = base.bodyMedium.copy(color = p.text),
        labelLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 15.sp, letterSpacing = 0.3.sp),
    )
    CompositionLocalProvider(LocalPalette provides p) {
        MaterialTheme(
            colorScheme = lightColorScheme(
                primary = p.primary,
                onPrimary = p.onPrimary,
                secondary = p.secondary,
                onSecondary = p.text,
                tertiary = p.accent,
                background = p.background,
                onBackground = p.text,
                surface = p.card,
                onSurface = p.text,
                surfaceVariant = p.soft,
                onSurfaceVariant = p.muted,
                primaryContainer = p.soft,
                onPrimaryContainer = p.text,
                secondaryContainer = p.soft,
                onSecondaryContainer = p.text,
                surfaceContainerHigh = p.card,
                surfaceContainer = p.card,
                outline = p.muted,
            ),
            shapes = Shapes(
                extraSmall = RoundedCornerShape(12.dp),
                small = RoundedCornerShape(16.dp),
                medium = RoundedCornerShape(22.dp),
                large = RoundedCornerShape(28.dp),
                extraLarge = RoundedCornerShape(32.dp),
            ),
            typography = typography,
            content = content,
        )
    }
}
