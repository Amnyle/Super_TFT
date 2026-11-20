package com.supertft.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.supertft.data.Comp
import com.supertft.data.TftIndex
import com.supertft.ui.components.ChampionIcon
import com.supertft.ui.components.ItemIcon
import com.supertft.ui.components.TierBadge
import com.supertft.ui.theme.Tft

/** Small always-on-top card: the pinned comp's carries and their items. Tap = open, ✕ = unpin. */
@Composable
fun PinnedMini(index: TftIndex, comp: Comp, onOpen: () -> Unit, onUnpin: () -> Unit) {
    Column(
        Modifier
            .width(IntrinsicSize.Max)
            .widthIn(max = 220.dp)
            .background(Color(0xE60E1017), RoundedCornerShape(10.dp))
            .border(1.dp, Tft.Gold.copy(alpha = 0.7f), RoundedCornerShape(10.dp))
            .clickable(onClick = onOpen)
            .padding(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TierBadge(comp.tier, 16.dp)
            Text(
                comp.title, color = Tft.Text, fontWeight = FontWeight.Bold, fontSize = 11.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(horizontal = 5.dp),
            )
            Text(
                "✕", color = Tft.Muted, fontSize = 13.sp,
                modifier = Modifier.clickable(onClick = onUnpin).padding(horizontal = 4.dp),
            )
        }
        index.carries(comp).take(4).forEach { u ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                ChampionIcon(index, u, 28.dp, showItems = false)
                u.items.forEach { ItemIcon(index, it, 22.dp) }
            }
        }
    }
}
