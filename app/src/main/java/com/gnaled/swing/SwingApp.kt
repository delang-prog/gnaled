package com.gnaled.swing

import android.app.Application
import com.gnaled.swing.data.AppContainer
import com.gnaled.swing.data.DefaultAppContainer

class SwingApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = DefaultAppContainer(this)
    }
}
