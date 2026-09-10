package app.agenda.wallpaper.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import app.agenda.wallpaper.R

val BrandBlack = Color(0xFF121212)
val CardDark = Color(0xFF222222)
val TextWhite = Color(0xFFFFFFFF)
val TextGrey = Color(0xFF888888)

val CardMobile = Color(0xFFDCDDFF)
val CardWireframe = Color(0xFFD4F5F7)
val CardWebsite = Color(0xFFEEF6A4)

val PillHigh = Color(0xFFFF5252)
val PillMedium = Color(0xFFFFC107)

val NavActive = Color(0xFFFFE7C3)
val NavInactive = Color(0xFF666666)
val NavBackground = Color(0xFF1E1E1E)

val NType82 = FontFamily(
    Font(R.font.ntype82_regular, FontWeight.Normal),
    Font(R.font.ntype82_regular, FontWeight.Medium),
    Font(R.font.ntype82_regular, FontWeight.SemiBold),
    Font(R.font.ntype82_regular, FontWeight.Bold),
)

private val AgendaColorScheme = darkColorScheme(
    primary = NavActive,
    background = BrandBlack,
    surface = CardDark,
    onPrimary = BrandBlack,
    onBackground = TextWhite,
    onSurface = TextWhite,
)

@Composable
fun AgendaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AgendaColorScheme,
        typography = MaterialTheme.typography.copy(
            displayLarge = TextStyle(fontFamily = NType82, fontSize = 36.sp, lineHeight = 44.sp, fontWeight = FontWeight.Bold),
            headlineMedium = TextStyle(fontFamily = NType82, fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold),
            titleMedium = TextStyle(fontFamily = NType82, fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
            titleSmall = TextStyle(fontFamily = NType82, fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
            bodyLarge = TextStyle(fontFamily = NType82, fontSize = 16.sp, lineHeight = 24.sp),
            bodyMedium = TextStyle(fontFamily = NType82, fontSize = 14.sp, lineHeight = 20.sp),
            labelLarge = TextStyle(fontFamily = NType82, fontSize = 13.sp, fontWeight = FontWeight.Medium),
            labelSmall = TextStyle(fontFamily = NType82, fontSize = 11.sp, fontWeight = FontWeight.Medium),
        ),
        content = content,
    )
}
