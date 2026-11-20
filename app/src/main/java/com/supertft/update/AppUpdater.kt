package com.supertft.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.supertft.BuildConfig
import com.supertft.data.DataRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.Request
import java.io.File

/**
 * Self-updater backed by GitHub Releases of [BuildConfig.GITHUB_REPO].
 * A release must be tagged vX.Y.Z and carry one .apk asset signed with the same key.
 */
object AppUpdater {
    private const val TAG = "AppUpdater"

    @Serializable
    data class Release(
        @SerialName("tag_name") val tag: String,
        val name: String? = null,
        val body: String? = null,
        val draft: Boolean = false,
        val prerelease: Boolean = false,
        val assets: List<Asset> = emptyList(),
    )

    @Serializable
    data class Asset(
        val name: String,
        val size: Long = 0,
        @SerialName("browser_download_url") val url: String,
    )

    sealed interface State {
        data object Idle : State
        data object Checking : State
        data class UpToDate(val version: String) : State
        data class Available(val version: String, val notes: String, val asset: Asset) : State
        data class Downloading(val version: String, val progress: Float) : State
        data class Installing(val version: String) : State
        data class Failed(val message: String) : State
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    val currentVersion: String get() = BuildConfig.VERSION_NAME.removeSuffix("-debug")

    fun check() {
        if (_state.value is State.Checking || _state.value is State.Downloading) return
        _state.value = State.Checking
        scope.launch {
            _state.value = try {
                val req = Request.Builder()
                    .url("https://api.github.com/repos/${BuildConfig.GITHUB_REPO}/releases/latest")
                    .header("Accept", "application/vnd.github+json")
                    .build()
                val body = DataRepository.http.newCall(req).execute().use { r ->
                    if (r.code == 404) return@use null // no release yet
                    if (!r.isSuccessful) error("GitHub HTTP ${r.code}")
                    r.body!!.string()
                }
                if (body == null) {
                    State.UpToDate(currentVersion)
                } else {
                    val rel = DataRepository.json.decodeFromString(Release.serializer(), body)
                    val remote = rel.tag.removePrefix("v")
                    val apk = rel.assets.firstOrNull { it.name.endsWith(".apk") }
                    if (apk != null && isNewer(remote, currentVersion)) {
                        State.Available(remote, rel.body.orEmpty(), apk)
                    } else {
                        State.UpToDate(currentVersion)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "check failed", e)
                State.Failed(e.message ?: e.javaClass.simpleName)
            }
        }
    }

    /** Downloads and installs the available update. Call from a visible activity. */
    fun downloadAndInstall(context: Context) {
        val available = _state.value as? State.Available ?: return
        val app = context.applicationContext
        if (!app.packageManager.canRequestPackageInstalls()) {
            // The state stays "Available": the user taps again after allowing the permission.
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${app.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return
        }
        scope.launch {
            try {
                val apk = download(app, available)
                _state.value = State.Installing(available.version)
                install(app, apk)
            } catch (e: Exception) {
                Log.w(TAG, "update failed", e)
                _state.value = State.Failed(e.message ?: e.javaClass.simpleName)
            }
        }
    }

    private fun download(context: Context, a: State.Available): File {
        val dir = File(context.cacheDir, "updates").apply { deleteRecursively(); mkdirs() }
        val out = File(dir, "SuperTFT-${a.version}.apk")
        _state.value = State.Downloading(a.version, 0f)
        DataRepository.http.newCall(Request.Builder().url(a.asset.url).build()).execute().use { r ->
            if (!r.isSuccessful) error("Download HTTP ${r.code}")
            val body = r.body!!
            val total = body.contentLength().takeIf { it > 0 } ?: a.asset.size
            body.byteStream().use { input ->
                out.outputStream().use { output ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    var lastEmit = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        output.write(buf, 0, n)
                        done += n
                        if (total > 0 && done - lastEmit > 256 * 1024) {
                            lastEmit = done
                            _state.value = State.Downloading(a.version, done.toFloat() / total)
                        }
                    }
                }
            }
        }
        return out
    }

    private fun install(context: Context, apk: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
        }
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            apk.inputStream().use { input ->
                session.openWrite("base.apk", 0, apk.length()).use { out ->
                    input.copyTo(out)
                    session.fsync(out)
                }
            }
            val intent = Intent(context, InstallResultReceiver::class.java)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
            val pi = PendingIntent.getBroadcast(context, sessionId, intent, flags)
            session.commit(pi.intentSender)
        }
    }

    internal fun onInstallFailed(message: String) {
        _state.value = State.Failed(message)
    }

    /** Semantic version comparison: "1.10.0" > "1.9.3". */
    fun isNewer(remote: String, local: String): Boolean {
        fun parts(v: String) = v.split('.', '-').take(3).map { it.toIntOrNull() ?: 0 }
            .let { it + List(3 - it.size) { 0 } }
        val r = parts(remote)
        val l = parts(local)
        for (i in 0 until 3) if (r[i] != l[i]) return r[i] > l[i]
        return false
    }
}
