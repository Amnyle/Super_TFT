package com.supertft.data

import kotlinx.serialization.Serializable

/** Mirrors the JSON written by scraper/fetch_tft_academy.py. */
@Serializable
data class TftData(
    val format: Int = 1,
    val source: String = "",
    val assets: String = "https://assets.tftacademy.com",
    val set: Int = 0,
    val patch: String = "",
    val hash: String = "",
    val generatedAt: Long = 0,
    val comps: List<Comp> = emptyList(),
    val champions: List<Champion> = emptyList(),
    val items: List<Item> = emptyList(),
    val traits: List<Trait> = emptyList(),
    val augments: List<Augment> = emptyList(),
    val itemTiers: Map<String, Map<String, List<String>>> = emptyMap(),
    val augmentTiers: List<AugmentTierList> = emptyList(),
)

@Serializable
data class Comp(
    val id: String,
    val slug: String = "",
    val title: String = "",
    val tier: String = "",
    val style: String = "",
    val difficulty: String = "",
    val main: String = "",
    val mainItem: String = "",
    val mainAugment: String = "",
    val board: List<CompUnit> = emptyList(),
    val early: List<CompUnit> = emptyList(),
    val levelUp: List<CompUnit> = emptyList(),
    val capstone: List<CompUnit> = emptyList(),
    val carousel: List<String> = emptyList(),
    val augments: List<String> = emptyList(),
    val augmentTypes: List<String> = emptyList(),
    val augmentsTip: String = "",
    val tips: List<Tip> = emptyList(),
    val updated: String = "",
)

@Serializable
data class CompUnit(
    val id: String,
    val items: List<String> = emptyList(),
    val stars: Int = 1,
    val hex: Int? = null,
    val replaces: List<String> = emptyList(),
)

@Serializable
data class Tip(val stage: String = "", val tip: String = "")

@Serializable
data class Champion(
    val id: String,
    val name: String,
    val cost: Int = 0,
    val role: String = "",
    val traits: List<String> = emptyList(),
    val abilityName: String = "",
    val ability: String = "",
    val items: List<String> = emptyList(),
)

@Serializable
data class Item(
    val id: String,
    val name: String,
    val type: String = "",
    val desc: String = "",
    val from: List<String> = emptyList(),
)

@Serializable
data class Trait(
    val id: String,
    val name: String,
    val type: String = "",
    val desc: String = "",
    val breakpoints: List<Int> = emptyList(),
    val colors: List<Int> = emptyList(),
    val effects: List<String> = emptyList(),
)

@Serializable
data class Augment(
    val id: String,
    val name: String,
    val tier: Int = 0,
    val desc: String = "",
)

@Serializable
data class AugmentTierList(
    val rarity: Int = 0,
    val stage: String = "",
    val tiers: Map<String, List<String>> = emptyMap(),
)

/** A trait active on a board, e.g. 4 Defender reaching the 2nd breakpoint. */
data class ActiveTrait(val trait: Trait, val count: Int, val level: Int, val color: Int)

/** Lookup tables and derived data built once per dataset. */
class TftIndex(val data: TftData) {
    private companion object {
        // Display order of trait styles: prismatic > gold > silver > bronze > unique.
        val STYLE_RANK = mapOf(6 to 5, 5 to 4, 3 to 3, 2 to 2, 1 to 2, 4 to 1)
    }

    val champions = data.champions.associateBy { it.id }
    val items = data.items.associateBy { it.id }
    val traits = data.traits.associateBy { it.id }
    val augments = data.augments.associateBy { it.id }
    private val traitsByName = data.traits.associateBy { it.name }

    fun championIcon(id: String) = "${data.assets}/champions/champion_icons/$id.webp"
    fun itemIcon(id: String) = "${data.assets}/items/$id.webp"
    fun augmentIcon(id: String) = "${data.assets}/augments/$id.webp"
    fun traitIcon(id: String) = "${data.assets}/traits/$id.webp"

    /** Traits activated by a list of units (each distinct champion counts once, emblems add one). */
    fun activeTraits(units: List<CompUnit>): List<ActiveTrait> {
        val counts = HashMap<String, Int>()
        units.map { it.id }.distinct().forEach { id ->
            champions[id]?.traits?.forEach { counts[it] = (counts[it] ?: 0) + 1 }
        }
        units.forEach { u ->
            u.items.forEach { itemId ->
                // Emblems are named "<Trait name> Emblem".
                val item = items[itemId]
                if (item?.type == "emblems") {
                    val traitName = item.name.removeSuffix(" Emblem")
                    traitsByName[traitName]?.let { counts[it.id] = (counts[it.id] ?: 0) + 1 }
                }
            }
        }
        return counts.mapNotNull { (id, count) ->
            val t = traits[id] ?: return@mapNotNull null
            val level = t.breakpoints.count { count >= it }
            val color = if (level > 0) t.colors.getOrElse(level - 1) { 1 } else 0
            ActiveTrait(t, count, level, color)
        }.sortedWith(
            compareByDescending<ActiveTrait> { it.level > 0 }
                .thenByDescending { STYLE_RANK[it.color] ?: 0 }
                .thenByDescending { it.count }
        )
    }

    /** Components ("from" recipes) for the items cheat sheet. */
    val components: List<Item> = data.items.filter { it.type == "components" }

    private val recipes: Map<Set<String>, Item> = data.items
        .filter { it.from.size == 2 }
        .sortedBy { if (it.type == "craftables") 0 else 1 } // craftables win on duplicates
        .reversed()
        .associateBy { it.from.toSet() }

    /** Item crafted from two components (craftables and emblems). */
    fun craft(a: String, b: String): Item? = recipes[setOf(a, b)]

    /** Lowercase text used for search: comp title, champions and items it contains. */
    val compSearchText: Map<String, String> = data.comps.associate { c ->
        val names = buildList {
            add(c.title); add(c.style)
            c.board.forEach { u ->
                champions[u.id]?.let { add(it.name) }
                u.items.forEach { i -> items[i]?.let { add(it.name) } }
            }
            c.augments.forEach { a -> augments[a]?.let { add(it.name) } }
        }
        c.id to names.joinToString(" ").lowercase()
    }
}
