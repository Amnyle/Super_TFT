package com.supertft

import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.node.RootForTest
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import com.supertft.data.DataRepository
import com.supertft.data.TftData
import com.supertft.overlay.OverlayService
import kotlinx.serialization.json.Json
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowSettings
import java.io.File
import java.time.Duration

/** Drives the real overlay service like a finger would: tap the bubble, then the panel's tabs. */
@RunWith(RobolectricTestRunner::class)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk = [34], qualifiers = "w800dp-h400dp-land")
class OverlayTest {

    private fun idle() = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500))

    private fun tap(view: View, x: Float, y: Float) {
        val t = SystemClock.uptimeMillis()
        view.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, x, y, 0))
        view.dispatchTouchEvent(MotionEvent.obtain(t, t + 40, MotionEvent.ACTION_UP, x, y, 0))
        idle()
    }

    private fun nodes(root: RootForTest): List<SemanticsNode> {
        // Robolectric does not draw, and Compose lays out during draw: force it like ComposeTestRule does.
        root.measureAndLayoutForTest()
        val out = ArrayList<SemanticsNode>()
        fun walk(n: SemanticsNode) { out += n; n.children.forEach(::walk) }
        walk(root.semanticsOwner.unmergedRootSemanticsNode)
        return out
    }

    private fun texts(root: RootForTest) = nodes(root).flatMap { n ->
        n.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }
    }

    private fun tapText(root: RootForTest, text: String) {
        val node = nodes(root).first { n -> n.config.getOrNull(SemanticsProperties.Text).orEmpty().any { it.text == text } }
        val b = node.boundsInRoot
        tap(root as View, b.center.x, b.center.y)
    }

    @Test
    fun panelNavigationWorks() {
        val f = File("src/main/assets/tft.json")
        DataRepository.setForTest(Json { ignoreUnknownKeys = true }.decodeFromString(TftData.serializer(), f.readText()))
        ShadowSettings.setCanDrawOverlays(true)
        Robolectric.buildService(OverlayService::class.java).create().startCommand(0, 1)
        idle()
        val service = OverlayService.current!!
        tap(service.bubble!!, 10f, 10f)
        val panel = assertNotNull(service.panel).let { service.panel as ViewGroup }
        val root = (panel.getChildAt(0) as ViewGroup).getChildAt(0) as RootForTest
        assertTrue("Comps tab not shown", "Comps" in texts(root))
        tapText(root, "Items")
        assertTrue("Items tab did not open", "Recipes" in texts(root))
        tapText(root, "Comps")
        tapText(root, "AP Fast 9")
        assertTrue("Comp detail did not open", "FINAL BOARD" in texts(root).map { it.uppercase() })
    }
}
