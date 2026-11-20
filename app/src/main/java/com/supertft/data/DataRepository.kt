package com.supertft.data

import android.content.Context
import android.util.Log
import com.supertft.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Data source shared by the app and the overlay.
 *
 * Load order: cached download (filesDir) -> snapshot bundled in the APK (assets/tft.json).
 * Then [refresh] downloads the latest JSON published by the GitHub Action on the "data" branch.
 */
object DataRepository {
    private const val TAG = "DataRepository"
    private const val CACHE_FILE = "tft.json"
    private const val PREFS = "data_repo"
    private const val REFRESH_INTERVAL_MS = 30 * 60 * 1000L

    sealed interface State {
        data object Loading : State
        data class Ready(val index: TftIndex) : State
        data class Error(val message: String) : State
    }

    enum class Sync { IDLE, RUNNING, UPDATED, UP_TO_DATE, FAILED }

    val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
    val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private lateinit var appContext: Context

    private val _state = MutableStateFlow<State>(State.Loading)
    val state: StateFlow<State> = _state.asStateFlow()

    private val _sync = MutableStateFlow(Sync.IDLE)
    val sync: StateFlow<Sync> = _sync.asStateFlow()

    /** For previews and screenshot tests only. */
    fun setForTest(data: TftData) {
        _state.value = State.Ready(TftIndex(data))
    }

    fun init(context: Context) {
        if (::appContext.isInitialized) return
        appContext = context.applicationContext
        scope.launch {
            loadLocal()
            refresh(force = false)
        }
    }

    private suspend fun loadLocal() = withContext(Dispatchers.IO) {
        val cached = File(appContext.filesDir, CACHE_FILE)
        val fromCache = runCatching { if (cached.exists()) parse(cached.readText()) else null }
            .onFailure { Log.w(TAG, "Bad cache, ignoring", it) }.getOrNull()
        val fromAssets = runCatching {
            appContext.assets.open(CACHE_FILE).bufferedReader().use { parse(it.readText()) }
        }.getOrNull()
        // Prefer the most recent of the two (a fresh APK may ship newer data than an old cache).
        val best = listOfNotNull(fromCache, fromAssets).maxByOrNull { it.generatedAt }
        if (best != null) _state.value = State.Ready(TftIndex(best))
    }

    private fun parse(text: String): TftData = json.decodeFromString(TftData.serializer(), text)

    /** Downloads the latest data if it is stale (or [force]). Safe to call often. */
    fun refresh(force: Boolean) {
        scope.launch {
            mutex.withLock {
                val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                val last = prefs.getLong("last_check", 0)
                if (!force && System.currentTimeMillis() - last < REFRESH_INTERVAL_MS &&
                    _state.value is State.Ready
                ) return@withLock

                _sync.value = Sync.RUNNING
                try {
                    val req = Request.Builder()
                        .url(BuildConfig.DATA_URL)
                        .header("Cache-Control", "no-cache")
                        .build()
                    val body = http.newCall(req).execute().use { resp ->
                        if (!resp.isSuccessful) error("HTTP ${resp.code}")
                        resp.body!!.string()
                    }
                    val fresh = parse(body)
                    val current = (_state.value as? State.Ready)?.index?.data
                    prefs.edit().putLong("last_check", System.currentTimeMillis()).apply()
                    if (current != null && current.hash == fresh.hash) {
                        _sync.value = Sync.UP_TO_DATE
                    } else {
                        File(appContext.filesDir, CACHE_FILE).writeText(body)
                        _state.value = State.Ready(TftIndex(fresh))
                        _sync.value = Sync.UPDATED
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Refresh failed", e)
                    _sync.value = Sync.FAILED
                    if (_state.value !is State.Ready) {
                        _state.value = State.Error(e.message ?: e.javaClass.simpleName)
                    }
                }
            }
        }
    }
}
