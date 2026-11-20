package com.supertft.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.supertft.R
import com.supertft.data.Augment
import com.supertft.data.TftIndex
import com.supertft.ui.ChipRow
import com.supertft.ui.LocalShowInfo
import com.supertft.ui.components.AugmentIcon
import com.supertft.ui.components.TierBadge
import com.supertft.ui.theme.Tft

@Composable
fun AugmentsScreen(index: TftIndex, query: String) {
    val showInfo = LocalShowInfo.current
    val info = augmentInfo(index)
    val q = query.trim().lowercase()

    val stages = remember(index) { index.data.augmentTiers.map { it.stage }.distinct() }
    var stage by rememberSaveable { mutableStateOf(stages.firstOrNull { it == "All" } ?: stages.firstOrNull() ?: "") }
    var rarity by rememberSaveable { mutableIntStateOf(2) }
    val anyStage = stringResource(R.string.stage_all)

    Column(Modifier.fillMaxSize()) {
        if (q.isNotEmpty()) {
            val found = index.data.augments.filter { it.name.lowercase().contains(q) || it.desc.lowercase().contains(q) }
            AugmentList(index, found.map { null to it }) { showInfo(info(it)) }
            return
        }
        ChipRow(stages, stage, { if (it == "All") anyStage else it }, { stage = it })
        ChipRow(listOf(1, 2, 3), rarity, { rarityLabel(it) }, { rarity = it })
        val list = index.data.augmentTiers.firstOrNull { it.stage == stage && it.rarity == rarity }
        val rows = listOf("S", "A", "B", "C").flatMap { t ->
            list?.tiers?.get(t).orEmpty().mapNotNull { id -> index.augments[id]?.let { t to it } }
        }
        AugmentList(index, rows) { showInfo(info(it)) }
    }
}

@Composable
private fun AugmentList(index: TftIndex, rows: List<Pair<String?, Augment>>, onClick: (String) -> Unit) {
    if (rows.isEmpty()) {
        Text(stringResource(R.string.no_results), color = Tft.Muted, modifier = Modifier.padding(16.dp))
        return
    }
    LazyColumn(contentPadding = PaddingValues(start = 8.dp, end = 8.dp, bottom = 24.dp)) {
        items(rows, key = { it.second.id }) { (tier, a) ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
                    .background(Tft.Surface, RoundedCornerShape(8.dp))
                    .clickable { onClick(a.id) }
                    .padding(8.dp),
            ) {
                if (tier != null) TierBadge(tier, 22.dp)
                AugmentIcon(index, a.id, 34.dp, Modifier.padding(start = if (tier != null) 8.dp else 0.dp))
                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                    Text(a.name, color = Tft.Text, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Text(a.desc, color = Tft.Muted, fontSize = 11.sp, lineHeight = 15.sp, maxLines = 2)
                }
            }
        }
    }
}
