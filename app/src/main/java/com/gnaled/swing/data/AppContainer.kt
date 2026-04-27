package com.gnaled.swing.data

import android.content.Context
import com.gnaled.swing.analysis.PoseAnalysisScheduler
import com.gnaled.swing.capture.AutoClipExtractor
import com.gnaled.swing.capture.AutoClipFinalizer

interface AppContainer {
    val swingRepository: SwingRepository
    val poseAnalysisScheduler: PoseAnalysisScheduler
    val autoClipFinalizer: AutoClipFinalizer
}

class DefaultAppContainer(context: Context) : AppContainer {
    private val appContext = context.applicationContext
    private val database: AppDatabase = AppDatabase.build(appContext)
    override val swingRepository: SwingRepository = SwingRepository(database.swingDao())
    override val poseAnalysisScheduler: PoseAnalysisScheduler = PoseAnalysisScheduler(appContext)
    override val autoClipFinalizer: AutoClipFinalizer = AutoClipFinalizer(
        context = appContext,
        extractor = AutoClipExtractor(appContext),
        repository = swingRepository,
        scheduler = poseAnalysisScheduler,
    )
}
