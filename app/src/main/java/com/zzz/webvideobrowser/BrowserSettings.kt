package com.zzz.webvideobrowser

import android.content.Context
import android.content.SharedPreferences

class BrowserSettings(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("zweb_settings", Context.MODE_PRIVATE)

    var enableAdBlock: Boolean
        get() = prefs.getBoolean("enable_adblock", false)
        set(value) = prefs.edit().putBoolean("enable_adblock", value).apply()

    var enableDesktopMode: Boolean
        get() = prefs.getBoolean("desktop_mode", false)
        set(value) = prefs.edit().putBoolean("desktop_mode", value).apply()

    var enableDnt: Boolean
        get() = prefs.getBoolean("enable_dnt", true)
        set(value) = prefs.edit().putBoolean("enable_dnt", value).apply()

    var isIncognitoMode: Boolean
        get() = prefs.getBoolean("incognito_mode", false)
        set(value) = prefs.edit().putBoolean("incognito_mode", value).apply()
}
