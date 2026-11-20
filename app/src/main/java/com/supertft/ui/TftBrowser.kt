package com.supertft.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.supertft.R
import com.supertft.data.DataRepository
import com.supertft.data.TftIndex
import com.supertft.ui.components.NetImage
import com.supertft.ui.screens.AugmentsScreen
import com.supertft.ui.screens.CompDetail
import com.supertft.ui.screens.CompsScreen
import com.supertft.ui.screens.ItemsScreen
import com.supertft.ui.screens.TraitsScreen
import com.supertft.ui.theme.Tft

/** Something the user tapped on, shown in the bottom info sheet (no Dialogs: they crash in overlays). */
data class Info(
    val title: String,
    val subtitle: String = "",
    val body: String = "",
    val icon: String? = null,
    val iconColor: Color = Tft.Border,
    val recipe: List<String> = emptyList(), // item icon urls
)

/** Lets any screen open the info sheet. */
val LocalShowInfo = staticCompositionLocalOf<(Info) -> Unit> { {} }

/** True inside the floating overlay: denser layout. */
val LocalCompact = staticCompositionLocalOf { false }

enum class Tab(val label: Int) {
    COMPS(R.string.tab_comps), ITEMS(R.string.tab_items), AUGMENTS(R.string.tab_augments), TRAITS(R.string.tab_traits)
}

@Composable
fun TftBrowser(compact: Boolean, modifier: Modifier = Modifier, header: @Composable () -> Unit = {}) {
    val state by DataRepository.state.collectAsState()
    var info by remember { mutableStateOf<Info?>(null) }

    CompositionLocalProvider(LocalShowInfo provides { info = it }, LocalCompact provides compact) {
        Box(modifier.fillMaxSize().background(Tft.Bg)) {
            when (val s = state) {
                is DataRepository.State.Loading -> Centered {
                    CircularProgressIndicator(color = Tft.Gold)
                    Text(stringResource(R.string.loading), color = Tft.Muted, modifier = Modifier.padding(top = 12.dp))
                }
                is DataRepository.State.Error -> Centered {
                    Text(stringResource(R.string.error_no_data, s.message), color = Tft.Muted)
                    Button(onClick = { DataRepository.refresh(force = true) }, modifier = Modifier.padding(top = 12.dp)) {
                        Text(stringResource(R.string.retry))
                    }
                }
                is DataRepository.State.Ready -> Column(Modifier.fillMaxSize()) {
                    header()
                    Browser(s.index)
                }
            }
            info?.let { InfoSheet(it, onClose = { info = null }, modifier = Modifier.align(Alignment.BottomCenter)) }
        }
    }
}

@Composable
private fun Browser(index: TftIndex) {
    var tab by rememberSaveable { mutableStateOf(Tab.COMPS) }
    var query by rememberSaveable { mutableStateOf("") }
    var openComp by rememberSaveable { mutableStateOf<String?>(null) }
    val compact = LocalCompact.current

    val comp = openComp?.let { id -> index.data.comps.firstOrNull { it.id == id } }
    if (comp != null) {
        CompDetail(index, comp, onBack = { openComp = null })
        return
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = if (compact) 4.dp else 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Tab.entries.forEach { t ->
                val selected = t == tab
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.weight(1f)
                        .background(if (selected) Tft.Gold else Tft.Surface, RoundedCornerShape(8.dp))
                        .clickable { tab = t }
                        .padding(vertical = if (compact) 6.dp else 9.dp),
                ) {
                    Text(
                        stringResource(t.label), fontSize = if (compact) 12.sp else 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (selected) Color(0xFF1A1305) else Tft.Text,
                    )
                }
            }
        }
        SearchField(query, { query = it }, Modifier.padding(horizontal = 8.dp))
        Box(Modifier.weight(1f)) {
            when (tab) {
                Tab.COMPS -> CompsScreen(index, query, onOpen = { openComp = it })
                Tab.ITEMS -> ItemsScreen(index, query)
                Tab.AUGMENTS -> AugmentsScreen(index, query)
                Tab.TRAITS -> TraitsScreen(index, query)
            }
        }
    }
}

@Composable
private fun SearchField(value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth()
            .background(Tft.Surface, RoundedCornerShape(10.dp))
            .border(1.dp, Tft.Border, RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Icon(Icons.Default.Search, null, tint = Tft.Muted, modifier = Modifier.size(18.dp))
        Box(Modifier.weight(1f).padding(horizontal = 8.dp)) {
            if (value.isEmpty()) Text(stringResource(R.string.search_hint), color = Tft.Muted, fontSize = 14.sp)
            BasicTextField(
                value = value, onValueChange = onChange, singleLine = true,
                textStyle = TextStyle(color = Tft.Text, fontSize = 14.sp),
                cursorBrush = SolidColor(Tft.Gold),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (value.isNotEmpty()) {
            Icon(
                Icons.Default.Close, stringResource(R.string.close), tint = Tft.Muted,
                modifier = Modifier.size(18.dp).clickable { onChange("") },
            )
        }
    }
}

@Composable
private fun InfoSheet(info: Info, onClose: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        Modifier.fillMaxSize().background(Color(0x99000000))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose),
    ) {
        Column(
            modifier.fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(Tft.Surface, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                .border(1.dp, Tft.Border, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                info.icon?.let {
                    NetImage(it, 44.dp, Modifier.border(2.dp, info.iconColor, RoundedCornerShape(6.dp)), RoundedCornerShape(6.dp))
                }
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(info.title, color = Tft.Text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    if (info.subtitle.isNotEmpty()) Text(info.subtitle, color = Tft.Muted, fontSize = 12.sp)
                }
                Icon(
                    Icons.Default.Close, stringResource(R.string.close), tint = Tft.Muted,
                    modifier = Modifier.size(24.dp).clickable(onClick = onClose),
                )
            }
            if (info.recipe.isNotEmpty()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 10.dp),
                ) {
                    Text(stringResource(R.string.made_from) + " ", color = Tft.Muted, fontSize = 12.sp)
                    info.recipe.forEachIndexed { i, url ->
                        if (i > 0) Text(" + ", color = Tft.Muted)
                        NetImage(url, 24.dp)
                    }
                }
            }
            if (info.body.isNotEmpty()) {
                Text(info.body, color = Tft.Text, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 10.dp))
            }
        }
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) { content() }
}

/** Horizontal chip selector used by several screens. */
@Composable
fun <T> ChipRow(options: List<T>, selected: T, label: @Composable (T) -> String, onSelect: (T) -> Unit) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp),
    ) {
        items(options) { o ->
            val sel = o == selected
            Text(
                label(o), fontSize = 12.sp,
                color = if (sel) Color(0xFF1A1305) else Tft.Text,
                modifier = Modifier
                    .background(if (sel) Tft.Gold else Tft.Surface, RoundedCornerShape(50))
                    .border(1.dp, if (sel) Tft.Gold else Tft.Border, RoundedCornerShape(50))
                    .clickable { onSelect(o) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}
