package com.gnaled.swing.data

import android.content.Context
import com.gnaled.swing.analysis.PoseAnalysisScheduler

interface AppContainer {
    val swingRepository: SwingRepository
    val poseAnalysisScheduler: PoseAnalysisScheduler
}

class DefaultAppContainer(context: Context) : AppContainer {
    private val appContext = context.applicationContext
    private val database: AppDatabase = AppDatabase.build(appContext)
    override val swingRepository: SwingRepository = SwingRepository(database.swingDao())
    override val poseAnalysisScheduler: PoseAnalysisScheduler = PoseAnalysisScheduler(appContext)
}
