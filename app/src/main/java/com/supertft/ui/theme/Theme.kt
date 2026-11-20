package com.supertft.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object Tft {
    val Bg = Color(0xFF0E1017)
    val Surface = Color(0xFF161A24)
    val Surface2 = Color(0xFF1F2431)
    val Border = Color(0xFF2B3142)
    val Text = Color(0xFFE6E8EE)
    val Muted = Color(0xFF8B93A7)
    val Gold = Color(0xFFE7B95A)

    fun tierColor(tier: String): Color = when (tier.uppercase()) {
        "S" -> Color(0xFFFF6B6B)
        "A" -> Color(0xFFFFA94D)
        "B" -> Color(0xFFFFD43B)
        "C" -> Color(0xFF69DB7C)
        else -> Color(0xFF868E96)
    }

    fun costColor(cost: Int): Color = when (cost) {
        1 -> Color(0xFF9AA0A6)
        2 -> Color(0xFF14B87F)
        3 -> Color(0xFF2F8CEB)
        4 -> Color(0xFFC54CE0)
        5 -> Color(0xFFFFB93B)
        else -> Color(0xFF5C6370)
    }

    /** Trait style index from TFT Academy data: 1 bronze, 3 silver, 4 unique, 5 gold, 6 prismatic. */
    fun traitColor(style: Int): Color = when (style) {
        1, 2 -> Color(0xFFB0785A)
        3 -> Color(0xFFAEB8C4)
        4 -> Color(0xFFF07F3C)
        5 -> Color(0xFFF2C14E)
        6 -> Color(0xFFB98CFF)
        else -> Color(0xFF3A4152)
    }

    /** Augment rarity: 1 silver, 2 gold, 3 prismatic. */
    fun augmentColor(rarity: Int): Color = when (rarity) {
        1 -> Color(0xFFAEB8C4)
        2 -> Color(0xFFF2C14E)
        3 -> Color(0xFFB98CFF)
        else -> Muted
    }
}

@Composable
fun SuperTftTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Tft.Gold,
            onPrimary = Color(0xFF1A1305),
            background = Tft.Bg,
            onBackground = Tft.Text,
            surface = Tft.Surface,
            onSurface = Tft.Text,
            surfaceVariant = Tft.Surface2,
            onSurfaceVariant = Tft.Muted,
            outline = Tft.Border,
        ),
        content = content,
    )
}
