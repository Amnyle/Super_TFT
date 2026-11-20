package com.supertft.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.supertft.R
import com.supertft.data.Comp
import com.supertft.data.Prefs
import com.supertft.data.TftIndex
import com.supertft.ui.ChipRow
import com.supertft.ui.LocalCompact
import com.supertft.ui.togglePin
import com.supertft.ui.components.ChampionIcon
import com.supertft.ui.components.PinButton
import com.supertft.ui.components.TierBadge
import com.supertft.ui.theme.Tft

private val TIER_ORDER = listOf("S", "A", "B", "C", "X")

@Composable
fun CompsScreen(index: TftIndex, query: String, onOpen: (String) -> Unit) {
    val favorites by Prefs.favorites.collectAsState()
    val pinned by Prefs.pinned.collectAsState()
    val allLabel = stringResource(R.string.filter_all)
    val favLabel = stringResource(R.string.filter_favorites)
    val styles = remember(index) { index.data.comps.map { it.style }.filter { it.isNotEmpty() }.distinct().sorted() }
    var filter by rememberSaveable { mutableStateOf(allLabel) }

    val q = query.trim().lowercase()
    val comps = index.data.comps.filter { c ->
        (q.isEmpty() || q.split(" ").all { (index.compSearchText[c.id] ?: "").contains(it) }) &&
            when (filter) {
                allLabel -> true
                favLabel -> c.id in favorites
                else -> c.style == filter
            }
    }
    val grouped = comps.groupBy { it.tier.uppercase() }
        .toSortedMap(compareBy { TIER_ORDER.indexOf(it).let { i -> if (i < 0) 99 else i } })

    Column(Modifier.fillMaxSize()) {
        ChipRow(listOf(allLabel, favLabel) + styles, filter, { it }, { filter = it })
        if (comps.isEmpty()) {
            Text(stringResource(R.string.no_results), color = Tft.Muted, modifier = Modifier.padding(16.dp))
        }
        LazyColumn(
            contentPadding = PaddingValues(start = 8.dp, end = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            grouped.forEach { (tier, list) ->
                item(key = "tier_$tier") {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)) {
                        TierBadge(tier, 24.dp)
                        Text(
                            "${list.size}", color = Tft.Muted, fontSize = 12.sp,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
                items(list, key = { it.id }) { c ->
                    CompCard(index, c, c.id in favorites, c.id == pinned, onClick = { onOpen(c.id) })
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CompCard(index: TftIndex, comp: Comp, favorite: Boolean, pinned: Boolean, onClick: () -> Unit) {
    val compact = LocalCompact.current
    val context = LocalContext.current
    val iconSize = if (compact) 30.dp else 36.dp
    Column(
        Modifier.fillMaxWidth()
            .background(Tft.Surface, RoundedCornerShape(10.dp))
            .border(
                if (pinned) 2.dp else 1.dp,
                if (pinned || favorite) Tft.Gold.copy(alpha = if (pinned) 1f else 0.6f) else Tft.Border,
                RoundedCornerShape(10.dp),
            )
            .clickable(onClick = onClick)
            .padding(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TierBadge(comp.tier, if (compact) 24.dp else 28.dp)
            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                Text(
                    comp.title, color = Tft.Text, fontWeight = FontWeight.Bold,
                    fontSize = if (compact) 14.sp else 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    listOf(comp.style, difficultyLabel(comp.difficulty)).filter { it.isNotEmpty() }.joinToString(" · "),
                    color = Tft.Muted, fontSize = 12.sp,
                )
            }
            PinButton(pinned) { togglePin(context, index, comp) }
            Text(
                if (favorite) "★" else "☆", color = if (favorite) Tft.Gold else Tft.Muted, fontSize = 22.sp,
                modifier = Modifier.clickable { Prefs.toggleFavorite(comp.id) }.padding(4.dp),
            )
        }
        val units = comp.board.sortedWith(
            compareBy<com.supertft.data.CompUnit> { index.champions[it.id]?.cost ?: 0 }.thenBy { index.champions[it.id]?.name }
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(top = 8.dp),
        ) {
            units.forEach { ChampionIcon(index, it, iconSize) }
        }
    }
}

@Composable
fun difficultyLabel(d: String): String = when (d) {
    "EASY" -> stringResource(R.string.diff_EASY)
    "MEDIUM" -> stringResource(R.string.diff_MEDIUM)
    "HARD" -> stringResource(R.string.diff_HARD)
    else -> d.lowercase().replaceFirstChar { it.uppercase() }
}
