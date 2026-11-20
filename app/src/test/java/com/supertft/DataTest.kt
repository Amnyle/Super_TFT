package com.supertft

import com.supertft.data.CompUnit
import com.supertft.data.TftData
import com.supertft.data.TftIndex
import com.supertft.update.AppUpdater
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

class DataTest {
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    @Test
    fun versionComparison() {
        assertTrue(AppUpdater.isNewer("1.0.1", "1.0.0"))
        assertTrue(AppUpdater.isNewer("1.10.0", "1.9.9"))
        assertTrue(AppUpdater.isNewer("2.0", "1.99.99"))
        assertFalse(AppUpdater.isNewer("1.0.0", "1.0.0"))
        assertFalse(AppUpdater.isNewer("0.9.0", "1.0.0"))
    }

    @Test
    fun activeTraitsUseBreakpointsAndEmblems() {
        val data = json.decodeFromString(
            TftData.serializer(),
            """
            {"champions":[
              {"id":"a","name":"A","cost":1,"traits":["t_def"]},
              {"id":"b","name":"B","cost":2,"traits":["t_def","t_mage"]}
            ],
             "items":[{"id":"e","name":"Defender Emblem","type":"emblems"}],
             "traits":[{"id":"t_def","name":"Defender","breakpoints":[2,3],"colors":[1,5]},
                       {"id":"t_mage","name":"Mage","breakpoints":[2],"colors":[1]}]}
            """.trimIndent(),
        )
        val traits = TftIndex(data).activeTraits(listOf(CompUnit("a"), CompUnit("b", items = listOf("e"))))
        val def = traits.first { it.trait.id == "t_def" }
        assertEquals(3, def.count)
        assertEquals(2, def.level)
        assertEquals(5, def.color)
        assertEquals(0, traits.first { it.trait.id == "t_mage" }.level)
    }

    /** Parses the real snapshot produced by the scraper (skipped if it has not been generated). */
    @Test
    fun realSnapshotParsesAndResolves() {
        val f = listOf("src/main/assets/tft.json", "app/src/main/assets/tft.json").map(::File).firstOrNull { it.exists() }
        assumeTrue("no tft.json snapshot", f != null)
        val index = TftIndex(json.decodeFromString(TftData.serializer(), f!!.readText()))
        assertTrue(index.data.comps.isNotEmpty())
        for (comp in index.data.comps) {
            for (u in comp.board) assertTrue("unknown champion ${u.id}", u.id in index.champions)
            for (u in comp.board) for (i in u.items) assertTrue("unknown item $i", i in index.items)
            assertTrue("no active trait in ${comp.title}", index.activeTraits(comp.board).any { it.level > 0 })
        }
        // Every craftable item is reachable from the recipe matrix.
        val craftables = index.data.items.filter { it.from.size == 2 }
        for (it in craftables) assertEquals(it.id, index.craft(it.from[1], it.from[0])?.id)
    }
}
