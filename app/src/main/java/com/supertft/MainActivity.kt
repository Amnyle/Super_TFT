package com.supertft

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.supertft.data.DataRepository
import com.supertft.data.Prefs
import com.supertft.overlay.OverlayService
import com.supertft.ui.TftBrowser
import com.supertft.ui.theme.SuperTftTheme
import com.supertft.ui.theme.Tft
import com.supertft.update.AppUpdater
import com.supertft.util.LocaleHelper

class MainActivity : ComponentActivity() {

    private val canDraw = mutableStateOf(false)

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null && !BuildConfig.DEBUG) AppUpdater.check()
        setContent {
            SuperTftTheme {
                Column(Modifier.fillMaxSize().background(Tft.Bg).safeDrawingPadding()) {
                    var settings by rememberSaveable { mutableStateOf(false) }
                    BackHandler(enabled = settings) { settings = false }
                    UpdateBanner()
                    if (settings) {
                        SettingsScreen(onBack = { settings = false }, onLanguage = ::changeLanguage)
                    } else {
                        TftBrowser(compact = false) {
                            HomeHeader(
                                canDraw = canDraw.value,
                                onSettings = { settings = true },
                                onToggleOverlay = ::toggleOverlay,
                                onGrant = ::requestOverlayPermission,
                                onAppInfo = ::openAppInfo,
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        canDraw.value = OverlayService.canDraw(this)
        DataRepository.refresh(force = false)
    }

    /** App info page: its ⋮ menu holds "Allow restricted settings" on Android 13+. */
    private fun openAppInfo() {
        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
    }

    private fun requestOverlayPermission() {
        startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
    }

    private fun toggleOverlay() {
        if (OverlayService.running.value) {
            OverlayService.stop(this)
            return
        }
        if (!OverlayService.canDraw(this)) {
            requestOverlayPermission()
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        OverlayService.start(this)
        // Go back to the home screen / game so the bubble is visible right away.
        moveTaskToBack(true)
    }

    private fun changeLanguage(tag: String) {
        Prefs.setLanguage(tag)
        // Restart the overlay so it picks up the new language too.
        if (OverlayService.running.value) {
            OverlayService.stop(this)
            OverlayService.start(this)
        }
        recreate()
    }
}

@Composable
private fun HomeHeader(
    canDraw: Boolean,
    onSettings: () -> Unit,
    onToggleOverlay: () -> Unit,
    onGrant: () -> Unit,
    onAppInfo: () -> Unit,
) {
    val running by OverlayService.running.collectAsState()
    val state by DataRepository.state.collectAsState()
    val data = (state as? DataRepository.State.Ready)?.index?.data
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp, top = 6.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text("Super TFT", color = Tft.Gold, fontWeight = FontWeight.Black, fontSize = 20.sp)
                if (data != null) {
                    Text(stringResource(R.string.data_info, data.set, data.patch), color = Tft.Muted, fontSize = 11.sp)
                }
            }
            Button(
                onClick = onToggleOverlay,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (running) Tft.Surface2 else Tft.Gold,
                    contentColor = if (running) Tft.Text else Color(0xFF1A1305),
                ),
            ) {
                Text(stringResource(if (running) R.string.overlay_stop else R.string.overlay_start), fontWeight = FontWeight.Bold)
            }
            Icon(
                Icons.Default.Settings, stringResource(R.string.settings), tint = Tft.Muted,
                modifier = Modifier.size(44.dp).clickable(onClick = onSettings).padding(10.dp),
            )
        }
        if (!canDraw) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp)
                    .background(Tft.Gold.copy(alpha = 0.1f), RoundedCornerShape(10.dp))
                    .border(1.dp, Tft.Gold.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                    .padding(12.dp),
            ) {
                Text(stringResource(R.string.overlay_permission_title), color = Tft.Gold, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.overlay_permission_desc), color = Tft.Text, fontSize = 13.sp)
                Button(onClick = onGrant, modifier = Modifier.padding(top = 8.dp)) { Text(stringResource(R.string.grant)) }
                // Android 13+ blocks this permission for sideloaded APKs until "restricted settings" are allowed.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    Text(
                        stringResource(R.string.restricted_help), color = Tft.Muted, fontSize = 12.sp, lineHeight = 17.sp,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                    OutlinedButton(onClick = onAppInfo, modifier = Modifier.padding(top = 6.dp)) {
                        Text(stringResource(R.string.open_app_info))
                    }
                }
            }
        } else if (running) {
            Text(
                stringResource(R.string.overlay_hint), color = Tft.Muted, fontSize = 11.sp,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun UpdateBanner() {
    val state by AppUpdater.state.collectAsState()
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val (text, action) = when (val s = state) {
        is AppUpdater.State.Available -> stringResource(R.string.update_available, s.version) to true
        is AppUpdater.State.Downloading -> stringResource(R.string.update_downloading, (s.progress * 100).toInt()) to false
        is AppUpdater.State.Installing -> stringResource(R.string.update_installing) to false
        is AppUpdater.State.Failed -> stringResource(R.string.update_failed, s.message) to false
        else -> return
    }
    Column(Modifier.fillMaxWidth().background(Tft.Surface2).padding(horizontal = 14.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text, color = Tft.Text, fontSize = 13.sp, modifier = Modifier.weight(1f))
            if (action) {
                Button(onClick = { AppUpdater.downloadAndInstall(ctx) }) { Text(stringResource(R.string.update_install)) }
            }
        }
        (state as? AppUpdater.State.Downloading)?.let {
            LinearProgressIndicator(
                progress = { it.progress }, color = Tft.Gold, trackColor = Tft.Border,
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            )
        }
    }
}

@Composable
private fun SettingsScreen(onBack: () -> Unit, onLanguage: (String) -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val opacity by Prefs.overlayOpacity.collectAsState()
    val sync by DataRepository.sync.collectAsState()
    val update by AppUpdater.state.collectAsState()
    val state by DataRepository.state.collectAsState()
    val data = (state as? DataRepository.State.Ready)?.index?.data
    val lang = Prefs.language(ctx)

    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().background(Tft.Surface)) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back), tint = Tft.Text,
                modifier = Modifier.size(48.dp).clickable(onClick = onBack).padding(12.dp),
            )
            Text(stringResource(R.string.settings), color = Tft.Text, fontWeight = FontWeight.Bold, fontSize = 17.sp)
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Label(stringResource(R.string.language))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("" to stringResource(R.string.lang_system), "fr" to "Français", "en" to "English").forEach { (tag, name) ->
                    val sel = tag == lang
                    OutlinedButton(
                        onClick = { if (!sel) onLanguage(tag) },
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (sel) Tft.Gold else Color.Transparent,
                            contentColor = if (sel) Color(0xFF1A1305) else Tft.Text,
                        ),
                    ) { Text(name) }
                }
            }

            Label(stringResource(R.string.opacity) + "  ${(opacity * 100).toInt()}%")
            Slider(
                value = opacity, onValueChange = { Prefs.setOverlayOpacity(it) }, valueRange = 0.5f..1f,
                colors = SliderDefaults.colors(thumbColor = Tft.Gold, activeTrackColor = Tft.Gold),
            )

            Label(if (data != null) stringResource(R.string.data_info, data.set, data.patch) else stringResource(R.string.loading))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { DataRepository.refresh(force = true) }) { Text(stringResource(R.string.data_refresh)) }
                Text(
                    when (sync) {
                        DataRepository.Sync.RUNNING -> "…"
                        DataRepository.Sync.UPDATED -> stringResource(R.string.data_updated)
                        DataRepository.Sync.UP_TO_DATE -> stringResource(R.string.data_uptodate)
                        DataRepository.Sync.FAILED -> stringResource(R.string.data_failed)
                        DataRepository.Sync.IDLE -> ""
                    },
                    color = Tft.Muted, fontSize = 12.sp, modifier = Modifier.padding(start = 12.dp),
                )
            }

            Label(stringResource(R.string.version, BuildConfig.VERSION_NAME))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { AppUpdater.check() }) { Text(stringResource(R.string.update_check)) }
                Text(
                    when (val u = update) {
                        is AppUpdater.State.Checking -> stringResource(R.string.update_checking)
                        is AppUpdater.State.UpToDate -> stringResource(R.string.up_to_date, u.version)
                        is AppUpdater.State.Available -> stringResource(R.string.update_available, u.version)
                        is AppUpdater.State.Failed -> stringResource(R.string.update_failed, u.message)
                        else -> ""
                    },
                    color = Tft.Muted, fontSize = 12.sp, modifier = Modifier.padding(start = 12.dp),
                )
            }

            Spacer(Modifier.height(24.dp))
            Text(stringResource(R.string.source_credit), color = Tft.Muted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(text, color = Tft.Gold, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, modifier = Modifier.padding(top = 12.dp))
}
