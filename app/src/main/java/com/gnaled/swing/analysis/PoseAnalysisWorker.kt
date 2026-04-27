package com.gnaled.swing.analysis

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.gnaled.swing.SwingApp
import com.gnaled.swing.data.entity.AnalysisStatus
import com.gnaled.swing.data.entity.Metric
import com.gnaled.swing.data.entity.MetricKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Runs [PoseAnalyzer] on a finalized clip and persists the resulting samples
 * via the repository's transactional `replaceAnalysis`. Updates the swing's
 * [AnalysisStatus] as it progresses.
 */
class PoseAnalysisWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.Default) {
        val swingId = inputData.getString(KEY_SWING_ID) ?: return@withContext Result.failure()
        val container = (applicationContext as SwingApp).container
        val repo = container.swingRepository

        val swing = repo.get(swingId) ?: return@withContext Result.failure()

        repo.insert(swing.copy(analysisStatus = AnalysisStatus.Running))

        val analyzer = PoseAnalyzer(applicationContext)
        val outcome = runCatching {
            analyzer.analyze(
                swingId = swingId,
                videoFile = File(swing.videoPath),
                durationMillis = swing.durationMillis,
                frameRate = swing.frameRate,
            )
        }

        outcome.fold(
            onSuccess = { result ->
                val segmentation = SwingSegmenter.segment(result.samples)
                val baseMetrics = segmentation
                    ?.let { MetricsCalculator.compute(swingId, result.samples, it) }
                    ?: emptyList()

                // Audio time-of-flight serve speed. Best-effort: returns null
                // if the clip lacks an audio track, no contact frame, or
                // onset pairing fails.
                val contactTs = segmentation
                    ?.let { result.samples.getOrNull(it.contactFrameIndex)?.timestampMillis }
                val onsets = runCatching { AudioPeakDetector.detect(File(swing.videoPath)) }
                    .getOrDefault(emptyList())
                val serve = ServeSpeedEstimator.estimate(
                    contactTimestampMillis = contactTs,
                    onsets = onsets,
                )
                val metrics = if (serve != null) {
                    baseMetrics + Metric(
                        swingId = swingId,
                        kind = MetricKind.BallSpeedMph,
                        value = serve.mph,
                        unit = "mph",
                    )
                } else {
                    baseMetrics
                }

                repo.replaceAnalysis(
                    swing = swing.copy(
                        analysisStatus = AnalysisStatus.Complete,
                        contactFrameIndex = segmentation?.contactFrameIndex,
                    ),
                    samples = result.samples,
                    metrics = metrics,
                )
                Result.success()
            },
            onFailure = {
                repo.insert(swing.copy(analysisStatus = AnalysisStatus.Failed))
                Result.failure()
            },
        )
    }

    companion object {
        const val KEY_SWING_ID = "swingId"
        const val UNIQUE_PREFIX = "pose-analysis-"
    }
}
