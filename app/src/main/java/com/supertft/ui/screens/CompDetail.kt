package com.supertft.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.supertft.R
import com.supertft.data.Comp
import com.supertft.data.CompUnit
import com.supertft.data.Prefs
import com.supertft.data.TftIndex
import com.supertft.ui.LocalCompact
import com.supertft.ui.LocalShowInfo
import com.supertft.ui.components.AugmentIcon
import com.supertft.ui.components.ChampionIcon
import com.supertft.ui.components.HexBoard
import com.supertft.ui.components.ItemIcon
import com.supertft.ui.components.SectionTitle
import com.supertft.ui.components.TierBadge
import com.supertft.ui.components.TraitChip
import com.supertft.ui.theme.Tft

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CompDetail(index: TftIndex, comp: Comp, onBack: () -> Unit) {
    val favorites by Prefs.favorites.collectAsState()
    val fav = comp.id in favorites
    val showInfo = LocalShowInfo.current
    val item = itemInfo(index)
    val augment = augmentInfo(index)
    val champion = championInfo(index)
    val compact = LocalCompact.current

    Column(Modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().background(Tft.Surface).padding(horizontal = 4.dp, vertical = 6.dp),
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back), tint = Tft.Text,
                modifier = Modifier.size(40.dp).clickable(onClick = onBack).padding(8.dp),
            )
            TierBadge(comp.tier, 26.dp)
            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                Text(comp.title, color = Tft.Text, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOf(comp.style, difficultyLabel(comp.difficulty)).filter { it.isNotEmpty() }.joinToString(" · "),
                    color = Tft.Muted, fontSize = 12.sp,
                )
            }
            Text(
                if (fav) "★" else "☆", color = if (fav) Tft.Gold else Tft.Muted, fontSize = 24.sp,
                modifier = Modifier.clickable { Prefs.toggleFavorite(comp.id) }.padding(8.dp),
            )
        }

        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 10.dp)) {
            SectionTitle(stringResource(R.string.final_board))
            HexBoard(index, comp.board)

            // Carries with their items, tappable for details.
            val carries = comp.board.filter { it.items.isNotEmpty() }
            if (carries.isNotEmpty()) {
                Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    carries.forEach { u -> CarryRow(index, u, onChampion = { showInfo(champion(u.id)) }, onItem = { showInfo(item(it)) }) }
                }
            }

            val traits = index.activeTraits(comp.board).filter { it.level > 0 }
            if (traits.isNotEmpty()) {
                SectionTitle(stringResource(R.string.traits))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    traits.forEach { TraitChip(index, it.trait.id, it.count, it.color) }
                }
            }

            if (comp.augments.isNotEmpty()) {
                SectionTitle(stringResource(R.string.augments) + if (comp.augmentTypes.isNotEmpty()) "  ·  " + comp.augmentTypes.joinToString(" / ") else "")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    comp.augments.forEach { id ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.width(if (compact) 58.dp else 66.dp).clickable { showInfo(augment(id)) },
                        ) {
                            AugmentIcon(index, id, if (compact) 36.dp else 42.dp)
                            Text(
                                index.augments[id]?.name ?: id, color = Tft.Muted, fontSize = 9.sp, lineHeight = 10.sp,
                                maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
                if (comp.augmentsTip.isNotEmpty()) Note(comp.augmentsTip)
            }

            if (comp.carousel.isNotEmpty()) {
                SectionTitle(stringResource(R.string.carousel))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    comp.carousel.forEachIndexed { i, id ->
                        if (i > 0) Text("›", color = Tft.Muted)
                        ItemIcon(index, id, 32.dp) { showInfo(item(id)) }
                    }
                }
            }

            UnitsSection(index, stringResource(R.string.early_board), comp.early) { showInfo(champion(it)) }
            UnitsSection(index, stringResource(R.string.level_up), comp.levelUp) { showInfo(champion(it)) }

            if (comp.capstone.isNotEmpty()) {
                SectionTitle(stringResource(R.string.capstone))
                comp.capstone.forEach { u ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 3.dp)) {
                        ChampionIcon(index, u, 34.dp, onClick = { showInfo(champion(u.id)) })
                        val replaced = u.replaces.mapNotNull { index.champions[it]?.name }
                        Text(
                            (index.champions[u.id]?.name ?: u.id) +
                                if (replaced.isNotEmpty()) "  —  " + stringResource(R.string.replaces, replaced.joinToString()) else "",
                            color = Tft.Text, fontSize = 13.sp, modifier = Modifier.padding(start = 10.dp),
                        )
                    }
                }
            }

            if (comp.tips.isNotEmpty()) {
                SectionTitle(stringResource(R.string.tips))
                comp.tips.forEach { t ->
                    Column(
                        Modifier.fillMaxWidth().padding(bottom = 6.dp)
                            .background(Tft.Surface, RoundedCornerShape(8.dp)).padding(10.dp),
                    ) {
                        Text(t.stage, color = Tft.Gold, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                        Text(t.tip, color = Tft.Text, fontSize = 13.sp, lineHeight = 18.sp)
                    }
                }
            }
            Text(
                stringResource(R.string.source_credit), color = Tft.Muted, fontSize = 10.sp,
                modifier = Modifier.padding(vertical = 16.dp),
            )
        }
    }
}

@Composable
private fun CarryRow(index: TftIndex, u: CompUnit, onChampion: () -> Unit, onItem: (String) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().background(Tft.Surface, RoundedCornerShape(8.dp)).padding(6.dp),
    ) {
        ChampionIcon(index, u, 34.dp, showItems = false, onClick = onChampion)
        Text(
            index.champions[u.id]?.name ?: u.id, color = Tft.Text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            u.items.forEach { id -> ItemIcon(index, id, 28.dp) { onItem(id) } }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun UnitsSection(index: TftIndex, title: String, units: List<CompUnit>, onClick: (String) -> Unit) {
    if (units.isEmpty()) return
    SectionTitle(title)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        units.forEach { u -> ChampionIcon(index, u, 36.dp, showName = true, onClick = { onClick(u.id) }) }
    }
}

@Composable
private fun Note(text: String) {
    Text(
        text, color = Tft.Text, fontSize = 12.sp, lineHeight = 17.sp,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            .background(Tft.Gold.copy(alpha = 0.08f), RoundedCornerShape(8.dp)).padding(10.dp),
    )
}
