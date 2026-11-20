package com.supertft.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.supertft.R
import com.supertft.data.CompUnit
import com.supertft.data.TftIndex
import com.supertft.ui.LocalShowInfo
import com.supertft.ui.components.ChampionIcon
import com.supertft.ui.components.NetImage
import com.supertft.ui.theme.Tft

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TraitsScreen(index: TftIndex, query: String) {
    val showInfo = LocalShowInfo.current
    val champInfo = championInfo(index)
    val q = query.trim().lowercase()

    val byTrait = remember(index) {
        index.data.traits.associate { t ->
            t.id to index.data.champions.filter { t.id in it.traits && it.cost > 0 }.sortedBy { it.cost }
        }
    }
    val traits = remember(index) {
        index.data.traits.sortedWith(compareBy({ if (it.type == "unique") 1 else 0 }, { it.name }))
    }.filter { t ->
        q.isEmpty() || t.name.lowercase().contains(q) || byTrait[t.id].orEmpty().any { it.name.lowercase().contains(q) }
    }

    if (traits.isEmpty()) {
        Text(stringResource(R.string.no_results), color = Tft.Muted, modifier = Modifier.padding(16.dp))
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 6.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(traits, key = { it.id }) { t ->
            Column(Modifier.fillMaxWidth().background(Tft.Surface, RoundedCornerShape(10.dp)).padding(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    NetImage(index.traitIcon(t.id), 26.dp, Modifier.background(Color.Black, CircleShape), RoundedCornerShape(50))
                    Text(
                        t.name, color = Tft.Text, fontWeight = FontWeight.Bold, fontSize = 14.sp,
                        modifier = Modifier.weight(1f).padding(start = 8.dp),
                    )
                    t.breakpoints.forEachIndexed { i, bp ->
                        val c = Tft.traitColor(t.colors.getOrElse(i) { 1 })
                        Text(
                            "$bp", color = c, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 3.dp)
                                .border(1.dp, c, RoundedCornerShape(4.dp)).padding(horizontal = 5.dp, vertical = 1.dp),
                        )
                    }
                }
                if (t.desc.isNotEmpty()) {
                    Text(t.desc, color = Tft.Muted, fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.padding(top = 6.dp))
                }
                t.effects.filter { it.isNotBlank() }.forEach {
                    Text(it, color = Tft.Text, fontSize = 12.sp, lineHeight = 16.sp)
                }
                val champs = byTrait[t.id].orEmpty()
                if (champs.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                        modifier = Modifier.padding(top = 8.dp),
                    ) {
                        champs.forEach { c ->
                            ChampionIcon(index, CompUnit(c.id), 30.dp, onClick = { showInfo(champInfo(c.id)) })
                        }
                    }
                }
            }
        }
    }
}
