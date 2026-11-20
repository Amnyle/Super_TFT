package com.supertft.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import com.supertft.R
import com.supertft.data.Comp
import com.supertft.data.Prefs
import com.supertft.data.TftIndex

/**
 * Pins [comp] (replacing any previously pinned one) and copies its Team Planner code,
 * so it can be imported in TFT. Pinning the pinned comp again unpins it.
 */
fun togglePin(context: Context, index: TftIndex, comp: Comp) {
    if (Prefs.pinned.value == comp.id) {
        Prefs.setPinned(null)
        return
    }
    Prefs.setPinned(comp.id)
    copyTeamCode(context, index, comp)
}

fun copyTeamCode(context: Context, index: TftIndex, comp: Comp) {
    val code = index.teamCode(comp) ?: return
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("TFT team code", code))
    Toast.makeText(context, R.string.team_code_copied, Toast.LENGTH_LONG).show()
}
