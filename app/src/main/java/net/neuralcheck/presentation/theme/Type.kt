package net.neuralcheck.presentation.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.unit.sp
import net.neuralcheck.R

/**
 * Brand typography:
 *   - Tajawal  → all Arabic UI copy
 *   - Outfit   → all numbers / Latin technical labels (grades, MB/s, °C, …)
 *
 * Both families are pulled at runtime from the Google Fonts provider bundled
 * with Google Play Services. No TTF files are checked into the repo; the
 * provider cert array lives in [res/values/font_certs.xml].
 *
 * If Play Services is missing (very rare on modern devices) we fall back to
 * SansSerif so the app stays usable instead of rendering in Roboto 0 px.
 */
private val googleFontProvider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs
)

private val tajawal = GoogleFont("Tajawal")
private val outfit = GoogleFont("Outfit")

val TajawalFamily: FontFamily = FontFamily(
    Font(googleFont = tajawal, fontProvider = googleFontProvider, weight = FontWeight.Light),
    Font(googleFont = tajawal, fontProvider = googleFontProvider, weight = FontWeight.Normal),
    Font(googleFont = tajawal, fontProvider = googleFontProvider, weight = FontWeight.Medium),
    Font(googleFont = tajawal, fontProvider = googleFontProvider, weight = FontWeight.Bold),
    Font(googleFont = tajawal, fontProvider = googleFontProvider, weight = FontWeight.ExtraBold)
)

val OutfitFamily: FontFamily = FontFamily(
    Font(googleFont = outfit, fontProvider = googleFontProvider, weight = FontWeight.Normal),
    Font(googleFont = outfit, fontProvider = googleFontProvider, weight = FontWeight.Medium),
    Font(googleFont = outfit, fontProvider = googleFontProvider, weight = FontWeight.SemiBold),
    Font(googleFont = outfit, fontProvider = googleFontProvider, weight = FontWeight.Bold),
    Font(googleFont = outfit, fontProvider = googleFontProvider, weight = FontWeight.ExtraBold)
)

val NeuralCheckTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = TajawalFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 32.sp,
        lineHeight = 44.sp,
        color = TextPrimary
    ),
    displayMedium = TextStyle(
        fontFamily = TajawalFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 38.sp,
        color = TextPrimary
    ),
    displaySmall = TextStyle(
        fontFamily = TajawalFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        color = TextPrimary
    ),
    headlineLarge = TextStyle(
        fontFamily = TajawalFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 34.sp,
        color = TextPrimary
    ),
    headlineMedium = TextStyle(
        fontFamily = TajawalFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 28.sp,
        color = TextPrimary
    ),
    headlineSmall = TextStyle(
        fontFamily = TajawalFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 26.sp,
        color = TextPrimary
    ),
    titleLarge = TextStyle(
        fontFamily = TajawalFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 26.sp,
        color = TextPrimary
    ),
    titleMedium = TextStyle(
        fontFamily = TajawalFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        color = TextPrimary
    ),
    titleSmall = TextStyle(
        fontFamily = TajawalFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        color = TextPrimary
    ),
    bodyLarge = TextStyle(
        fontFamily = TajawalFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 26.sp,
        color = TextSecondary
    ),
    bodyMedium = TextStyle(
        fontFamily = TajawalFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 22.sp,
        color = TextSecondary
    ),
    bodySmall = TextStyle(
        fontFamily = TajawalFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 18.sp,
        color = TextMuted
    ),
    labelLarge = TextStyle(
        fontFamily = TajawalFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        color = TextPrimary
    ),
    labelMedium = TextStyle(
        fontFamily = OutfitFamily, // numeric / Latin tokens
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        color = TextPrimary
    ),
    labelSmall = TextStyle(
        fontFamily = OutfitFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        color = TextMuted
    )
)

// Backwards compat — some callers still reference the old name.
val Typography = NeuralCheckTypography
