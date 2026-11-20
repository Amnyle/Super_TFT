package com.supertft.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.supertft.data.CompUnit
import com.supertft.data.TftIndex
import com.supertft.ui.theme.Tft
import kotlin.math.sqrt

private const val COLS = 7
private const val ROWS = 4

/** Pointy-top hexagon. */
private val HexShape = GenericShape { size, _ ->
    val w = size.width
    val h = size.height
    moveTo(w / 2f, 0f)
    lineTo(w, h * 0.25f)
    lineTo(w, h * 0.75f)
    lineTo(w / 2f, h)
    lineTo(0f, h * 0.75f)
    lineTo(0f, h * 0.25f)
    close()
}

/**
 * TFT board: 4 rows x 7 hexes. boardIndex 0..6 = front row (top), 21..27 = back row.
 * Odd rows are shifted half a hex to the right, like in game.
 */
@Composable
fun HexBoard(index: TftIndex, units: List<CompUnit>, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val hexW: Dp = maxWidth / (COLS + 0.5f)
        val hexH: Dp = hexW * (2f / sqrt(3f))
        val rowStep = hexH * 0.75f
        val itemSize = hexW * 0.3f
        val boardH = rowStep * (ROWS - 1) + hexH + itemSize * 0.3f
        val byHex = units.filter { it.hex != null }.associateBy { it.hex!! }

        Box(Modifier.fillMaxWidth().height(boardH)) {
            for (row in 0 until ROWS) for (col in 0 until COLS) {
                val idx = row * COLS + col
                val x = hexW * col + if (row % 2 == 1) hexW / 2 else 0.dp
                val y = rowStep * row
                val unit = byHex[idx]
                val cost = unit?.let { index.champions[it.id]?.cost } ?: 0
                Box(Modifier.offset(x, y).size(hexW, hexH).padding(1.5.dp)) {
                    if (unit == null) {
                        Box(Modifier.fillMaxSize().clip(HexShape).background(Tft.Surface2))
                    } else {
                        Box(Modifier.fillMaxSize().clip(HexShape).background(Tft.costColor(cost)))
                        UrlImage(index.championIcon(unit.id), Modifier.fillMaxSize().padding(2.dp).clip(HexShape))
                        if (unit.stars >= 2) {
                            Text(
                                "★".repeat(unit.stars), color = Tft.Gold, fontSize = 8.sp, lineHeight = 8.sp,
                                modifier = Modifier.align(Alignment.TopCenter).padding(top = 6.dp)
                                    .background(Color(0xCC000000), RoundedCornerShape(3.dp)),
                            )
                        }
                    }
                }
            }
            // Items drawn in a second pass so they sit above neighbouring hexes.
            for ((idx, unit) in byHex) {
                if (unit.items.isEmpty() || idx !in 0 until ROWS * COLS) continue
                val row = idx / COLS
                val col = idx % COLS
                val x = hexW * col + if (row % 2 == 1) hexW / 2 else 0.dp
                val y = rowStep * row + hexH - itemSize * 0.7f
                Row(
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.offset(x, y).width(hexW),
                ) { unit.items.take(3).forEach { ItemIcon(index, it, itemSize) } }
            }
        }
    }
}
