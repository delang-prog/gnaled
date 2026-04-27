package com.gnaled.swing.analysis

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf

class PoseAnalysisScheduler(private val context: Context) {

    fun enqueue(swingId: String) {
        val request = OneTimeWorkRequestBuilder<PoseAnalysisWorker>()
            .setInputData(workDataOf(PoseAnalysisWorker.KEY_SWING_ID to swingId))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            PoseAnalysisWorker.UNIQUE_PREFIX + swingId,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }
}
