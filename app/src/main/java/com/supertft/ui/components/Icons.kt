package com.supertft.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.supertft.data.CompUnit
import com.supertft.data.TftIndex
import com.supertft.ui.theme.Tft

/** Test hook: screenshot tests provide local images instead of network loading. */
val LocalImageOverride = staticCompositionLocalOf<((String) -> Painter?)?> { null }

/** Remote image (cached by Coil). */
@Composable
fun UrlImage(url: String, modifier: Modifier = Modifier, contentScale: ContentScale = ContentScale.Crop) {
    val override = LocalImageOverride.current?.invoke(url)
    if (override != null) {
        Image(override, null, modifier, contentScale = contentScale)
    } else {
        AsyncImage(model = url, contentDescription = null, contentScale = contentScale, modifier = modifier)
    }
}

@Composable
fun NetImage(url: String, size: Dp, modifier: Modifier = Modifier, shape: RoundedCornerShape = RoundedCornerShape(4.dp)) {
    UrlImage(url, modifier.size(size).clip(shape).background(Tft.Surface2))
}

/** Champion portrait with cost-coloured border, star level and up to 3 item icons below. */
@Composable
fun ChampionIcon(
    index: TftIndex,
    unit: CompUnit,
    size: Dp = 40.dp,
    showName: Boolean = false,
    showItems: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    val champ = index.champions[unit.id]
    val color = Tft.costColor(champ?.cost ?: 0)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(size + 4.dp).let { if (onClick != null) it.clickable(onClick = onClick) else it },
    ) {
        Box {
            NetImage(
                index.championIcon(unit.id), size,
                Modifier.border(2.dp, color, RoundedCornerShape(6.dp)),
                RoundedCornerShape(6.dp),
            )
            if (unit.stars >= 2) {
                Text(
                    "★".repeat(unit.stars),
                    color = Tft.Gold, fontSize = 9.sp, lineHeight = 9.sp,
                    modifier = Modifier.align(Alignment.TopCenter)
                        .background(Color(0xCC000000), RoundedCornerShape(3.dp))
                        .padding(horizontal = 2.dp),
                )
            }
        }
        if (showItems && unit.items.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(1.dp), modifier = Modifier.padding(top = 2.dp)) {
                unit.items.take(3).forEach { ItemIcon(index, it, size / 3 - 1.dp) }
            }
        }
        if (showName && champ != null) {
            Text(
                champ.name, fontSize = 9.sp, color = Tft.Muted, maxLines = 1,
                overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
fun ItemIcon(index: TftIndex, id: String, size: Dp, onClick: (() -> Unit)? = null) {
    NetImage(
        index.itemIcon(id), size,
        if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
        RoundedCornerShape(3.dp),
    )
}

@Composable
fun AugmentIcon(index: TftIndex, id: String, size: Dp, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val rarity = index.augments[id]?.tier ?: 0
    NetImage(
        index.augmentIcon(id), size,
        modifier.border(1.5.dp, Tft.augmentColor(rarity), RoundedCornerShape(6.dp))
            .let { if (onClick != null) it.clickable(onClick = onClick) else it },
        RoundedCornerShape(6.dp),
    )
}

@Composable
fun TraitChip(index: TftIndex, traitId: String, count: Int, style: Int) {
    val color = Tft.traitColor(style)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(if (style > 0) color.copy(alpha = 0.18f) else Tft.Surface2, RoundedCornerShape(50))
            .border(1.dp, if (style > 0) color else Tft.Border, RoundedCornerShape(50))
            .padding(start = 2.dp, end = 7.dp, top = 2.dp, bottom = 2.dp),
    ) {
        NetImage(index.traitIcon(traitId), 16.dp, Modifier.background(Color.Black, CircleShape), RoundedCornerShape(50))
        Text(
            "$count ${index.traits[traitId]?.name ?: traitId}",
            fontSize = 11.sp, color = if (style > 0) Tft.Text else Tft.Muted,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}

@Composable
fun TierBadge(tier: String, size: Dp = 28.dp) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(size).background(Tft.tierColor(tier), RoundedCornerShape(6.dp)),
    ) {
        Text(
            tier.ifEmpty { "?" }, color = Color(0xFF14161D), fontWeight = FontWeight.Black,
            fontSize = (size.value * 0.55f).sp,
        )
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(), color = Tft.Gold, fontWeight = FontWeight.Bold, fontSize = 12.sp,
        letterSpacing = 1.sp, modifier = modifier.padding(top = 14.dp, bottom = 6.dp),
    )
}

/** Pin toggle: a pinned comp stays visible in the small overlay and its team code is copied. */
@Composable
fun PinButton(pinned: Boolean, onClick: () -> Unit) {
    Text(
        "📌", fontSize = 18.sp,
        color = Color.Unspecified,
        modifier = Modifier
            .clickable(onClick = onClick)
            .background(if (pinned) Tft.Gold.copy(alpha = 0.25f) else Color.Transparent, RoundedCornerShape(8.dp))
            .border(1.dp, if (pinned) Tft.Gold else Color.Transparent, RoundedCornerShape(8.dp))
            .padding(horizontal = 6.dp, vertical = 4.dp)
            .let { if (pinned) it else it.alphaCompat(0.45f) },
    )
}

private fun Modifier.alphaCompat(a: Float) = this.then(Modifier.graphicsLayer(alpha = a))
