package com.supertft

import android.graphics.BitmapFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import app.cash.paparazzi.detectEnvironment
import com.android.resources.ScreenOrientation
import com.supertft.data.DataRepository
import com.supertft.data.TftData
import com.supertft.data.TftIndex
import com.supertft.ui.LocalCompact
import com.supertft.ui.TftBrowser
import com.supertft.ui.components.LocalImageOverride
import com.supertft.ui.screens.AugmentsScreen
import com.supertft.ui.screens.CompDetail
import com.supertft.ui.screens.ItemsScreen
import com.supertft.ui.screens.TraitsScreen
import com.supertft.ui.theme.SuperTftTheme
import com.supertft.ui.theme.Tft
import kotlinx.serialization.json.Json
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 * Renders the main screens with the real data snapshot.
 * Run: ./gradlew recordPaparazziDebug (images in app/src/test/snapshots/images).
 */
class ScreenshotTest {
    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5,
        maxPercentDifference = 1.0,
        // Paparazzi 1.3.x ships an API 34 layoutlib: render against the API 34 platform.
        environment = detectEnvironment().copy(compileSdkVersion = 34),
    )

    private val index: TftIndex? by lazy {
        val f = File("src/main/assets/tft.json").takeIf { it.exists() } ?: return@lazy null
        TftIndex(Json { ignoreUnknownKeys = true }.decodeFromString(TftData.serializer(), f.readText()))
    }

    private val icons = System.getProperty("supertft.icons")?.let(::File)
    private val painters = HashMap<String, Painter?>()
    private fun painterFor(url: String): Painter? = painters.getOrPut(url) {
        val name = url.substringAfter("assets.tftacademy.com/").replace("/", "__")
        val f = icons?.resolve(name)?.takeIf { it.exists() } ?: return@getOrPut null
        BitmapFactory.decodeFile(f.path)?.let { BitmapPainter(it.asImageBitmap()) }
    }

    private fun shot(compact: Boolean = false, content: @Composable (TftIndex) -> Unit) {
        val idx = index
        assumeTrue("no tft.json snapshot", idx != null)
        paparazzi.snapshot {
            CompositionLocalProvider(
                LocalImageOverride provides ::painterFor,
                LocalCompact provides compact,
            ) {
                SuperTftTheme { Box(Modifier.fillMaxSize().background(Tft.Bg)) { content(idx!!) } }
            }
        }
    }

    @Test fun comps() {
        index?.let { DataRepository.setForTest(it.data) }
        shot { TftBrowser(compact = false) }
    }

    @Test fun compDetail() = shot { CompDetail(it, it.data.comps.first(), onBack = {}) }

    @Test fun items() = shot { ItemsScreen(it, "") }

    @Test fun augments() = shot { AugmentsScreen(it, "") }

    @Test fun traits() = shot { TraitsScreen(it, "") }

    @Test fun overlayLandscape() {
        paparazzi.unsafeUpdateConfig(
            DeviceConfig.PIXEL_5.copy(screenWidth = 1000, screenHeight = 1080, orientation = ScreenOrientation.LANDSCAPE)
        )
        shot(compact = true) { CompDetail(it, it.data.comps[1], onBack = {}) }
    }
}
