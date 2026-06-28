package net.kannagi.pageturn.ui.theme

import androidx.compose.ui.graphics.Color

// High-contrast white & green palette, tuned for E-Ink legibility.
val Background = Color(0xFFFFFFFF)   // pure white background
val Surface = Color(0xFFFFFFFF)      // card surface (separated by strong borders)
val BorderSoft = Color(0xFF233029)   // strong dark hairline (visible on E-Ink)
val BorderStrong = Color(0xFF0A0F0C) // near-black emphasized border

val Primary = Color(0xFF0A6E45)      // deep emerald — reads as dark gray on mono E-Ink
val PrimaryDark = Color(0xFF064A2E)  // even deeper green for text on tint
val PrimaryTint = Color(0xFFBCE6CD)  // light green fill (chips, selected)

val TextPrimary = Color(0xFF0A0F0C)  // near-black primary text
val TextMuted = Color(0xFF2F3D36)    // dark gray-green secondary text (still high contrast)

val StatusOn = Color(0xFF0A6E45)     // service enabled
val Warning = Color(0xFFB02216)      // strong red for disabled / warnings
