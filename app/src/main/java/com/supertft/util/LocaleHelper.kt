package com.supertft.util

import android.content.Context
import android.content.res.Configuration
import com.supertft.data.Prefs
import java.util.Locale

/**
 * In-app language override (FR / EN / system). Applied in attachBaseContext of the
 * activity AND the overlay service, so both follow the same choice on every Android version.
 */
object LocaleHelper {
    fun wrap(base: Context): Context {
        val tag = Prefs.language(base)
        if (tag.isEmpty()) return base
        val locale = Locale.forLanguageTag(tag)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        return base.createConfigurationContext(config)
    }
}
