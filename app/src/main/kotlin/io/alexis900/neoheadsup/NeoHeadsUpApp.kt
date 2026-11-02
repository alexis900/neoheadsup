package io.alexis900.neoheadsup

import android.app.Application

class NeoHeadsUpApp : Application() {

    override fun onCreate() {
        super.onCreate()
        ThemeManager.apply(this)
    }
}
