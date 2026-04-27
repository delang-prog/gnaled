package com.gnaled.swing.data

import com.gnaled.swing.data.entity.BallSample
import com.gnaled.swing.data.entity.Metric
import com.gnaled.swing.data.entity.Sample
import com.gnaled.swing.data.entity.Swing
import kotlinx.coroutines.flow.Flow

class SwingRepository(private val dao: SwingDao) {
    fun observeSwings(): Flow<List<Swing>> = dao.observeAll()

    fun observeSwing(id: String): Flow<Swing?> = dao.observeById(id)

    suspend fun get(id: String): Swing? = dao.findById(id)

    suspend fun insert(swing: Swing) = dao.upsertSwing(swing)

    suspend fun delete(id: String) = dao.deleteSwing(id)

    suspend fun samples(swingId: String): List<Sample> = dao.samplesFor(swingId)

    fun observeSamples(swingId: String): Flow<List<Sample>> = dao.observeSamples(swingId)

    suspend fun metrics(swingId: String): List<Metric> = dao.metricsFor(swingId)

    fun observeMetrics(swingId: String): Flow<List<Metric>> = dao.observeMetrics(swingId)

    suspend fun ballSamples(swingId: String): List<BallSample> = dao.ballSamplesFor(swingId)

    fun observeBallSamples(swingId: String): Flow<List<BallSample>> = dao.observeBallSamples(swingId)

    suspend fun replaceAnalysis(
        swing: Swing,
        samples: List<Sample>,
        metrics: List<Metric>,
        ballSamples: List<BallSample> = emptyList(),
    ) = dao.replaceAnalysis(swing, samples, metrics, ballSamples)
}
