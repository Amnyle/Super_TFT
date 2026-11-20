package com.supertft.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** User preferences shared by the app and the overlay (same process). */
object Prefs {
    private const val NAME = "settings"
    private lateinit var ctx: Context

    private val _favorites = MutableStateFlow<Set<String>>(emptySet())
    val favorites: StateFlow<Set<String>> = _favorites.asStateFlow()

    private val _pinned = MutableStateFlow<String?>(null)
    /** Comp shown in the small always-visible overlay (one at a time). */
    val pinned: StateFlow<String?> = _pinned.asStateFlow()

    private val _overlayOpacity = MutableStateFlow(0.96f)
    val overlayOpacity: StateFlow<Float> = _overlayOpacity.asStateFlow()

    fun init(context: Context) {
        ctx = context.applicationContext
        val p = prefs()
        _favorites.value = p.getStringSet("favorites", emptySet())!!.toSet()
        _overlayOpacity.value = p.getFloat("overlay_opacity", 0.96f)
        _pinned.value = p.getString("pinned", null)
    }

    private fun prefs() = ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun toggleFavorite(compId: String) {
        val next = _favorites.value.toMutableSet().apply { if (!add(compId)) remove(compId) }
        _favorites.value = next
        prefs().edit().putStringSet("favorites", next).apply()
    }

    fun setPinned(compId: String?) {
        _pinned.value = compId
        prefs().edit().putString("pinned", compId).apply()
    }

    fun setOverlayOpacity(value: Float) {
        _overlayOpacity.value = value
        prefs().edit().putFloat("overlay_opacity", value).apply()
    }

    /** "" = follow system, otherwise a language tag ("fr", "en"). */
    fun language(context: Context): String =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getString("language", "") ?: ""

    fun setLanguage(tag: String) {
        prefs().edit().putString("language", tag).apply()
    }

    fun bubblePosition(): Pair<Int, Int> = prefs().let { it.getInt("bubble_x", 0) to it.getInt("bubble_y", 300) }

    fun setBubblePosition(x: Int, y: Int) {
        prefs().edit().putInt("bubble_x", x).putInt("bubble_y", y).apply()
    }
}
