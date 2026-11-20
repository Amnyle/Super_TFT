package com.supertft

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.supertft.data.DataRepository
import com.supertft.data.Prefs

class SuperTftApp : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
        DataRepository.init(this)
    }

    /** Icons are small and reused everywhere: cache them aggressively (works offline after first view). */
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .okHttpClient { DataRepository.http }
        .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.2).build() }
        .diskCache { DiskCache.Builder().directory(cacheDir.resolve("icons")).maxSizeBytes(80L * 1024 * 1024).build() }
        .respectCacheHeaders(false)
        .crossfade(false)
        .build()
}
