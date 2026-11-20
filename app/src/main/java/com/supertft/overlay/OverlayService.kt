package com.supertft.overlay

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.supertft.MainActivity
import com.supertft.R
import com.supertft.data.DataRepository
import com.supertft.data.Prefs
import com.supertft.ui.PinnedMini
import com.supertft.ui.TftBrowser
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.supertft.ui.theme.SuperTftTheme
import com.supertft.ui.theme.Tft
import com.supertft.util.LocaleHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Floating bubble + expandable panel drawn above other apps (the game).
 * The panel hosts the same Compose UI as the app, in compact mode.
 */
class OverlayService : LifecycleService(), SavedStateRegistryOwner {

    companion object {
        private const val CHANNEL = "overlay"
        private const val NOTIF_ID = 1
        private const val ACTION_STOP = "com.supertft.STOP_OVERLAY"

        /**
         * Windows added by a service are software-rendered unless they ask for the GPU, and
         * Compose content then stops redrawing after the first frame (the panel looked frozen).
         */
        private const val HW = WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED

        private val _running = MutableStateFlow(false)
        val running: StateFlow<Boolean> = _running.asStateFlow()

        fun canDraw(context: Context) = Settings.canDrawOverlays(context)

        /** Test hook: the running service. */
        @androidx.annotation.VisibleForTesting
        internal var current: OverlayService? = null

        fun start(context: Context) {
            context.startForegroundService(Intent(context, OverlayService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, OverlayService::class.java))
        }
    }

    private val savedStateController = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    private lateinit var wm: WindowManager
    internal var bubble: View? = null
    private var bubbleParams: WindowManager.LayoutParams? = null
    internal var panel: View? = null
    private var panelParams: WindowManager.LayoutParams? = null
    private var panelShown = false
    private var closeTarget: View? = null
    private var mini: View? = null

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(LocaleHelper.wrap(base))
    }

    override fun onCreate() {
        savedStateController.performAttach()
        savedStateController.performRestore(null)
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        DataRepository.init(this)
        DataRepository.refresh(force = false)
        _running.value = true
        current = this
        // Pinned comp mini overlay follows the pinned id and the data.
        lifecycleScope.launch {
            combine(Prefs.pinned, DataRepository.state) { id, state -> id != null && state is DataRepository.State.Ready }
                .distinctUntilChanged()
                .collect { show -> if (show) showMini() else hideMini() }
        }
        lifecycleScope.launch {
            Prefs.overlayOpacity.collect { a -> panelParams?.let { it.alpha = a; if (panelShown) wm.updateViewLayout(panel, it) } }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        startInForeground()
        if (!canDraw(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (bubble == null) showBubble()
        return START_STICKY
    }

    private fun startInForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.notif_channel), NotificationManager.IMPORTANCE_MIN)
        )
        val openApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, OverlayService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE
        )
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.notif_text))
            .setContentIntent(openApp)
            .addAction(0, getString(R.string.overlay_stop), stop)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        ServiceCompat.startForeground(this, NOTIF_ID, notification, type)
    }

    private fun overlayType() = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
    private fun dp(v: Int) = (v * resources.displayMetrics.density).roundToInt()

    // ---------------------------------------------------------------- bubble

    @SuppressLint("ClickableViewAccessibility")
    private fun showBubble() {
        val size = dp(54)
        val view = FrameLayout(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xEE0E1017.toInt())
                setStroke(dp(2), 0xFFE7B95A.toInt())
            }
            elevation = dp(6).toFloat()
            addView(ImageView(context).apply { setImageResource(R.drawable.ic_launcher_fg) },
                FrameLayout.LayoutParams(size, size))
        }
        val (x, y) = Prefs.bubblePosition()
        val params = WindowManager.LayoutParams(
            size, size, overlayType(),
            HW or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            this.x = x
            this.y = y
        }

        val slop = ViewConfiguration.get(this).scaledTouchSlop
        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        var dragging = false
        view.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX; downY = e.rawY
                    startX = params.x; startY = params.y
                    dragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX
                    val dy = e.rawY - downY
                    if (!dragging && (abs(dx) > slop || abs(dy) > slop)) {
                        dragging = true
                        showCloseTarget()
                    }
                    if (dragging) {
                        params.x = startX + dx.roundToInt()
                        params.y = startY + dy.roundToInt()
                        wm.updateViewLayout(view, params)
                        highlightCloseTarget(isOverCloseTarget(e.rawX, e.rawY))
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (dragging) {
                        val close = isOverCloseTarget(e.rawX, e.rawY)
                        hideCloseTarget()
                        if (close) {
                            stopSelf()
                        } else {
                            snapToEdge(view, params)
                        }
                    } else if (e.actionMasked == MotionEvent.ACTION_UP) {
                        togglePanel()
                    }
                    true
                }
                else -> false
            }
        }
        wm.addView(view, params)
        bubble = view
        bubbleParams = params
        if (Prefs.pinned.value != null && DataRepository.state.value is DataRepository.State.Ready) showMini()
    }

    private fun screenSize(): Pair<Int, Int> {
        val m = resources.displayMetrics
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val b = wm.currentWindowMetrics.bounds
            b.width() to b.height()
        } else m.widthPixels to m.heightPixels
    }

    private fun snapToEdge(view: View, params: WindowManager.LayoutParams) {
        val (w, h) = screenSize()
        params.x = if (params.x + view.width / 2 < w / 2) 0 else w - view.width
        params.y = params.y.coerceIn(0, (h - view.height).coerceAtLeast(0))
        wm.updateViewLayout(view, params)
        Prefs.setBubblePosition(params.x, params.y)
    }

    private fun showCloseTarget() {
        if (closeTarget != null) return
        val size = dp(64)
        val v = TextView(this).apply {
            text = "✕"
            textSize = 26f
            gravity = Gravity.CENTER
            setTextColor(0xFFFFFFFF.toInt())
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(0x99000000.toInt()) }
        }
        val p = WindowManager.LayoutParams(
            size, size, overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL; y = dp(32) }
        wm.addView(v, p)
        closeTarget = v
    }

    private fun highlightCloseTarget(active: Boolean) {
        (closeTarget?.background as? GradientDrawable)?.setColor(if (active) 0xCCD9480F.toInt() else 0x99000000.toInt())
    }

    private fun hideCloseTarget() {
        closeTarget?.let { runCatching { wm.removeView(it) } }
        closeTarget = null
    }

    private fun isOverCloseTarget(rawX: Float, rawY: Float): Boolean {
        val (w, h) = screenSize()
        val cx = w / 2f
        val cy = h - dp(32) - dp(32).toFloat()
        val r = dp(70)
        return abs(rawX - cx) < r && abs(rawY - cy) < r
    }

    // ---------------------------------------------------------------- pinned mini overlay

    /** Small card at the top right with the pinned comp's carries and items. */
    private fun showMini() {
        if (mini != null || bubble == null) return
        val compose = ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnLifecycleDestroyed(lifecycle))
            setContent {
                val state by DataRepository.state.collectAsState()
                val pinned by Prefs.pinned.collectAsState()
                val index = (state as? DataRepository.State.Ready)?.index
                val comp = index?.data?.comps?.firstOrNull { it.id == pinned }
                if (index != null && comp != null) {
                    SuperTftTheme {
                        PinnedMini(index, comp, onOpen = { showPanel() }, onUnpin = { Prefs.setPinned(null) })
                    }
                }
            }
        }
        val root = FrameLayout(this).apply {
            addView(compose)
            setViewTreeLifecycleOwner(this@OverlayService)
            setViewTreeSavedStateRegistryOwner(this@OverlayService)
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT, overlayType(),
            // Never takes focus or blocks the game outside its own small area.
            HW or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = dp(8)
            y = dp(8)
        }
        wm.addView(root, params)
        mini = root
    }

    private fun hideMini() {
        mini?.let { runCatching { wm.removeView(it) } }
        mini = null
    }

    // ---------------------------------------------------------------- panel

    private fun buildPanel(): View {
        val compose = ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnLifecycleDestroyed(lifecycle))
            setContent {
                SuperTftTheme {
                    TftBrowser(compact = true) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().background(Tft.Surface).padding(start = 12.dp, end = 4.dp),
                        ) {
                            Text(
                                stringResource(R.string.app_name), color = Tft.Gold, fontWeight = FontWeight.Bold,
                                fontSize = 14.sp, modifier = Modifier.weight(1f),
                            )
                            Icon(
                                Icons.Default.Close, stringResource(R.string.close), tint = Tft.Text,
                                modifier = Modifier.size(40.dp).clickable { hidePanel() }.padding(9.dp),
                            )
                        }
                    }
                }
            }
        }
        // Root that turns the Back key into "minimise".
        val root = object : FrameLayout(this) {
            override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                if (event.keyCode == KeyEvent.KEYCODE_BACK) {
                    if (event.action == KeyEvent.ACTION_UP) hidePanel()
                    return true
                }
                return super.dispatchKeyEvent(event)
            }
        }
        root.clipToOutline = true
        root.background = GradientDrawable().apply {
            cornerRadius = dp(14).toFloat()
            setColor(0xFF0E1017.toInt())
            setStroke(dp(1), 0xFF2B3142.toInt())
        }
        root.addView(compose, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        root.setViewTreeLifecycleOwner(this)
        root.setViewTreeSavedStateRegistryOwner(this)
        return root
    }

    private fun panelLayout(): WindowManager.LayoutParams {
        val (w, h) = screenSize()
        val landscape = w > h
        val margin = dp(8)
        // In landscape (how TFT is played) the panel takes a side column; in portrait most of the screen.
        val pw = if (landscape) (w * 0.46f).roundToInt().coerceAtLeast(dp(340)).coerceAtMost(w - 2 * margin) else w - 2 * margin
        val ph = if (landscape) h - 2 * margin else (h * 0.78f).roundToInt()
        val bubbleOnRight = (bubbleParams?.x ?: 0) > w / 2
        return WindowManager.LayoutParams(
            pw, ph, overlayType(),
            // Not touch-modal: touches outside the panel still reach the game.
            HW or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or (if (bubbleOnRight) Gravity.END else Gravity.START)
            x = if (landscape) dp(66) else margin
            y = if (landscape) margin else dp(72)
            alpha = Prefs.overlayOpacity.value
            // Still the only way to resize an overlay window for the keyboard.
            @Suppress("DEPRECATION")
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }
    }

    private fun togglePanel() = if (panelShown) hidePanel() else showPanel()

    private fun showPanel() {
        if (panelShown) return
        val view = panel ?: buildPanel().also { panel = it }
        val params = panelLayout()
        panelParams = params
        wm.addView(view, params)
        panelShown = true
        DataRepository.refresh(force = false)
        // Keep the bubble above the panel so it can close it.
        bubble?.let { b -> wm.removeView(b); wm.addView(b, bubbleParams) }
    }

    private fun hidePanel() {
        if (!panelShown) return
        panel?.let { runCatching { wm.removeView(it) } }
        panelShown = false
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Rotation: re-layout the panel and keep the bubble on screen.
        if (panelShown) panel?.let { wm.updateViewLayout(it, panelLayout().also { p -> panelParams = p }) }
        bubble?.let { b -> bubbleParams?.let { b.post { snapToEdge(b, it) } } }
    }

    override fun onDestroy() {
        hideMini()
        hidePanel()
        hideCloseTarget()
        bubble?.let { runCatching { wm.removeView(it) } }
        bubble = null
        _running.value = false
        super.onDestroy()
    }
}
