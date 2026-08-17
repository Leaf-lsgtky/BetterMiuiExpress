package com.moefactory.bettermiuiexpress.base.app

import android.app.Application
import android.content.Context
import com.moefactory.bettermiuiexpress.ktx.hideLauncherIcon
import com.moefactory.bettermiuiexpress.ktx.isLauncherIconEnabled

class BetterMiuiExpress : Application() {

    override fun onCreate() {
        super.onCreate()

        val prefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val trackId = prefs.getString(PREF_KEY_DEVICE_TRACK_ID, "") ?: ""
        
        if (isLauncherIconEnabled() && trackId.isNotEmpty()) {
            hideLauncherIcon()
        }
    }
}