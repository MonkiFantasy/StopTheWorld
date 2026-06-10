package dev.stw.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Single source of truth for app-wide colors, typography and card styling.
 * The raw-View overlay palette in blocking/BlockOverlayUi.kt mirrors these values;
 * keep both in sync when changing colors here.
 */
val StwLightColors = lightColorScheme(
    background = Color(0xFFFFFBFF),
    surface = Color(0xFFF6F2F7),
    surfaceVariant = Color(0xFFE7E1EA),
    surfaceContainer = Color(0xFFF0EDF2),
    surfaceContainerHigh = Color(0xFFEAE7EC),
    primary = Color(0xFF536AA3),
    primaryContainer = Color(0xFFE2E7FA),
    secondary = Color(0xFF6E7480),
    secondaryContainer = Color(0xFFE9ECF2),
    tertiary = Color(0xFF6B6477),
    tertiaryContainer = Color(0xFFECE7F2),
    outlineVariant = Color(0xFFC9C5CF),
    onBackground = Color(0xFF1B1B1F),
    onSurface = Color(0xFF1B1B1F),
    onSurfaceVariant = Color(0xFF5F5D66),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
)

val StwTypography = Typography(
    displayLarge = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    displayMedium = TextStyle(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold),
    displaySmall = TextStyle(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
    headlineLarge = TextStyle(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
    headlineMedium = TextStyle(fontSize = 17.sp, lineHeight = 23.sp, fontWeight = FontWeight.SemiBold),
    headlineSmall = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 17.sp, lineHeight = 23.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 13.sp, lineHeight = 19.sp, fontWeight = FontWeight.Normal),
    bodyMedium = TextStyle(fontSize = 13.sp, lineHeight = 19.sp, fontWeight = FontWeight.Normal),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 17.sp, fontWeight = FontWeight.Normal),
    labelLarge = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold),
    labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 15.sp, fontWeight = FontWeight.Medium),
)

val StwCardShape = RoundedCornerShape(20.dp)

@Composable
fun StwTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = StwLightColors, typography = StwTypography, content = content)
}

/** Standard section card: one container color, border, corner radius and inner padding everywhere. */
@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
        shape = StwCardShape,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}
