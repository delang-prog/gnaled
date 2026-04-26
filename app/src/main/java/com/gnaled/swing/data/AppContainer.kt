package com.gnaled.swing.data

import android.content.Context

interface AppContainer {
    val swingRepository: SwingRepository
}

class DefaultAppContainer(context: Context) : AppContainer {
    private val database: AppDatabase = AppDatabase.build(context)
    override val swingRepository: SwingRepository = SwingRepository(database.swingDao())
}
