package net.neuralcheck.presentation.theme

import androidx.compose.ui.graphics.Color

/**
 * NeuralCheck brand identity colour palette.
 *
 * The app ships in dark-only mode: a deep-space background conveys a technical
 * feel and preserves battery life during the 11-minute stress test. Every
 * value here maps one-to-one to the tokens in neuralcheck_brand_identity.html.
 */

// ===== Backgrounds =====
val DeepSpace = Color(0xFF0A0E1A)       // Screen background
val CardDark = Color(0xFF111827)        // Card container
val Surface = Color(0xFF1A2235)         // Elevated surfaces
val SurfaceVariant = Color(0xFF1E293B)  // Inner inputs / pills

// ===== Brand colours =====
val NeuralGreen = Color(0xFF4ADE80)     // Primary brand
val CoreGlow = Color(0xFFA3E635)        // Logo core + strongest accent
val SkyCyan = Color(0xFF38BDF8)         // Titles + hyperlinks
val NeonCyan = Color(0xFF22D3EE)        // Icons + highlights
val RoyalBlue = Color(0xFF2563EB)       // Secondary brand
val BrandPurple = Color(0xFF7C3AED)     // Rare decorative gradients

// ===== Grade colours =====
val GradeA = Color(0xFF22C55E)          // A — 85-100
val GradeB = Color(0xFF3B82F6)          // B — 70-84
val GradeC = Color(0xFFF59E0B)          // C — 50-69
val GradeD = Color(0xFFEF4444)          // D — 0-49

// ===== Status (test outcome) colours =====
val StatusPass = Color(0xFF22C55E)
val StatusWarning = Color(0xFFF59E0B)
val StatusFail = Color(0xFFEF4444)
val StatusUnavailable = Color(0xFF64748B)

// ===== Chart colours =====
val ChartSafe = NeuralGreen             // mid-line (<threshold)
val ChartWarning = StatusWarning
val ChartDanger = StatusFail
val ChartLine = SkyCyan                 // neutral trace

// ===== Text =====
val TextPrimary = Color(0xFFF1F5F9)
val TextSecondary = Color(0xFF94A3B8)
val TextMuted = Color(0xFF64748B)

// ===== Effects =====
val GreenGlow = Color(0x404ADE80)       // 25% NeuralGreen
val CyanGlow = Color(0x4038BDF8)        // 25% SkyCyan
val CardBorder = Color(0x15FFFFFF)      // 8% white hairline

// ===== Aliases used by LoginScreen =====
// These point at existing brand tokens so the login flow can be read in
// isolation (`NeuralDark`, `BorderColor`, `TextTertiary` are the names used
// in the designer hand-off) without drifting from the rest of the app.
val NeuralDark = DeepSpace              // Screen background for login
val BorderColor = CardBorder            // Hairline border around inputs / dividers
val TextTertiary = TextMuted            // "or" divider label, secondary muted copy

// ===== Material3 dark scheme base containers (used by library components) =====
val md_theme_dark_primary = NeuralGreen
val md_theme_dark_onPrimary = DeepSpace
val md_theme_dark_primaryContainer = Color(0xFF064E3B)
val md_theme_dark_onPrimaryContainer = Color(0xFFBBF7D0)

val md_theme_dark_secondary = SkyCyan
val md_theme_dark_onSecondary = DeepSpace
val md_theme_dark_secondaryContainer = Color(0xFF0C4A6E)
val md_theme_dark_onSecondaryContainer = Color(0xFFBAE6FD)

val md_theme_dark_tertiary = NeonCyan
val md_theme_dark_onTertiary = DeepSpace
val md_theme_dark_tertiaryContainer = Color(0xFF164E63)
val md_theme_dark_onTertiaryContainer = Color(0xFFCFFAFE)

val md_theme_dark_error = StatusFail
val md_theme_dark_onError = Color(0xFFFFFFFF)
val md_theme_dark_errorContainer = Color(0xFF7F1D1D)
val md_theme_dark_onErrorContainer = Color(0xFFFECACA)

val md_theme_dark_background = DeepSpace
val md_theme_dark_onBackground = TextPrimary
val md_theme_dark_surface = CardDark
val md_theme_dark_onSurface = TextPrimary
val md_theme_dark_surfaceVariant = Surface
val md_theme_dark_onSurfaceVariant = TextSecondary
val md_theme_dark_outline = CardBorder
