package com.supertft.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.supertft.R
import com.supertft.data.TftIndex
import com.supertft.ui.Info
import com.supertft.ui.theme.Tft

@Composable
fun rarityLabel(rarity: Int): String = when (rarity) {
    1 -> stringResource(R.string.rarity_1)
    2 -> stringResource(R.string.rarity_2)
    3 -> stringResource(R.string.rarity_3)
    else -> ""
}

@Composable
fun itemTypeLabel(type: String): String = when (type) {
    "craftables" -> stringResource(R.string.type_craftables)
    "radiants" -> stringResource(R.string.type_radiants)
    "ornns" -> stringResource(R.string.type_ornns)
    "emblems" -> stringResource(R.string.type_emblems)
    "components" -> stringResource(R.string.type_components)
    else -> type.replaceFirstChar { it.uppercase() }
}

/** Builders return lambdas so they can be called from click handlers (non-composable). */
@Composable
fun itemInfo(index: TftIndex): (String) -> Info {
    val labels = listOf("craftables", "radiants", "ornns", "emblems", "components").associateWith { itemTypeLabel(it) }
    return { id ->
        val item = index.items[id]
        Info(
            title = item?.name ?: id,
            subtitle = item?.type?.let { labels[it] ?: it }.orEmpty(),
            body = item?.desc.orEmpty(),
            icon = index.itemIcon(id),
            recipe = item?.from.orEmpty().map { index.itemIcon(it) },
        )
    }
}

@Composable
fun augmentInfo(index: TftIndex): (String) -> Info {
    val labels = (1..3).associateWith { rarityLabel(it) }
    return { id ->
        val a = index.augments[id]
        Info(
            title = a?.name ?: id,
            subtitle = labels[a?.tier ?: 0].orEmpty(),
            body = a?.desc.orEmpty(),
            icon = index.augmentIcon(id),
            iconColor = Tft.augmentColor(a?.tier ?: 0),
        )
    }
}

@Composable
fun championInfo(index: TftIndex): (String) -> Info {
    val res = LocalContext.current.resources
    return { id ->
        val c = index.champions[id]
        val traits = c?.traits.orEmpty().mapNotNull { index.traits[it]?.name }.joinToString(" · ")
        Info(
            title = c?.name ?: id,
            subtitle = listOfNotNull(
                c?.cost?.takeIf { it > 0 }?.let { res.getString(R.string.cost, it) }, traits.ifEmpty { null }, c?.role?.ifEmpty { null },
            ).joinToString("  •  "),
            body = listOfNotNull(
                c?.abilityName?.ifEmpty { null }?.let { "【$it】" }, c?.ability?.ifEmpty { null },
            ).joinToString("\n"),
            icon = index.championIcon(id),
            iconColor = Tft.costColor(c?.cost ?: 0),
            recipe = emptyList(),
        )
    }
}
