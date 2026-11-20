package com.supertft.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.supertft.R
import com.supertft.data.Item
import com.supertft.data.TftIndex
import com.supertft.ui.ChipRow
import com.supertft.ui.LocalShowInfo
import com.supertft.ui.components.ItemIcon
import com.supertft.ui.components.SectionTitle
import com.supertft.ui.components.TierBadge
import com.supertft.ui.theme.Tft

private val ITEM_TYPES = listOf("craftables", "radiants", "ornns", "emblems", "components")

@Composable
fun ItemsScreen(index: TftIndex, query: String) {
    val modes = listOf(stringResource(R.string.recipes), stringResource(R.string.tierlist), stringResource(R.string.all_items))
    var mode by rememberSaveable { mutableIntStateOf(0) }
    val q = query.trim().lowercase()

    Column(Modifier.fillMaxSize()) {
        if (q.isEmpty()) ChipRow((0..2).toList(), mode, { modes[it] }, { mode = it })
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 8.dp)) {
            when {
                q.isNotEmpty() -> ItemList(index, index.data.items.filter {
                    it.name.lowercase().contains(q) || it.desc.lowercase().contains(q)
                })
                mode == 0 -> RecipeMatrix(index)
                mode == 1 -> ItemTierList(index)
                else -> ITEM_TYPES.forEach { type ->
                    val list = index.data.items.filter { it.type == type }
                    if (list.isNotEmpty()) {
                        SectionTitle(itemTypeLabel(type))
                        ItemList(index, list)
                    }
                }
            }
            Box(Modifier.padding(bottom = 24.dp))
        }
    }
}

/** Component x component grid: tap a cell to see the finished item. */
@Composable
private fun RecipeMatrix(index: TftIndex) {
    val showInfo = LocalShowInfo.current
    val info = itemInfo(index)
    val comps = index.components
    if (comps.isEmpty()) return
    BoxWithConstraints(Modifier.fillMaxWidth().padding(top = 4.dp)) {
        val cell = (maxWidth / (comps.size + 1)).coerceAtMost(44.dp)
        val icon = cell - 4.dp
        Column {
            Row {
                Box(Modifier.size(cell))
                comps.forEach { c ->
                    Box(Modifier.size(cell), contentAlignment = Alignment.Center) {
                        ItemIcon(index, c.id, icon) { showInfo(info(c.id)) }
                    }
                }
            }
            comps.forEachIndexed { r, a ->
                Row {
                    Box(Modifier.size(cell), contentAlignment = Alignment.Center) {
                        ItemIcon(index, a.id, icon) { showInfo(info(a.id)) }
                    }
                    comps.forEachIndexed { c, b ->
                        val result = index.craft(a.id, b.id)
                        Box(
                            Modifier.size(cell).padding(1.dp)
                                .background(if ((r + c) % 2 == 0) Tft.Surface else Tft.Surface2, RoundedCornerShape(3.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (result != null) ItemIcon(index, result.id, icon - 2.dp) { showInfo(info(result.id)) }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ItemTierList(index: TftIndex) {
    val showInfo = LocalShowInfo.current
    val info = itemInfo(index)
    ITEM_TYPES.forEach { type ->
        val tiers = index.data.itemTiers[type] ?: return@forEach
        SectionTitle(itemTypeLabel(type))
        listOf("S", "A", "B", "C").forEach { t ->
            val ids = tiers[t].orEmpty()
            if (ids.isEmpty()) return@forEach
            Row(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
                TierBadge(t, 28.dp)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.weight(1f).padding(start = 8.dp),
                ) { ids.forEach { id -> ItemIcon(index, id, 30.dp) { showInfo(info(id)) } } }
            }
        }
    }
}

@Composable
private fun ItemList(index: TftIndex, items: List<Item>) {
    if (items.isEmpty()) {
        Text(stringResource(R.string.no_results), color = Tft.Muted, modifier = Modifier.padding(8.dp))
        return
    }
    items.forEach { item ->
        Row(
            Modifier.fillMaxWidth().padding(vertical = 3.dp)
                .background(Tft.Surface, RoundedCornerShape(8.dp)).padding(8.dp),
        ) {
            ItemIcon(index, item.id, 34.dp)
            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(item.name, color = Tft.Text, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    item.from.forEach { ItemIcon(index, it, 18.dp); Box(Modifier.size(2.dp)) }
                }
                if (item.desc.isNotEmpty()) Text(item.desc, color = Tft.Muted, fontSize = 12.sp, lineHeight = 16.sp)
            }
        }
    }
}
