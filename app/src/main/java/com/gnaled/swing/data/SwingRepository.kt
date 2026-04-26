package com.gnaled.swing.data

import com.gnaled.swing.data.entity.Metric
import com.gnaled.swing.data.entity.Sample
import com.gnaled.swing.data.entity.Swing
import kotlinx.coroutines.flow.Flow

class SwingRepository(private val dao: SwingDao) {
    fun observeSwings(): Flow<List<Swing>> = dao.observeAll()

    suspend fun get(id: String): Swing? = dao.findById(id)

    suspend fun insert(swing: Swing) = dao.upsertSwing(swing)

    suspend fun delete(id: String) = dao.deleteSwing(id)

    suspend fun samples(swingId: String): List<Sample> = dao.samplesFor(swingId)

    suspend fun metrics(swingId: String): List<Metric> = dao.metricsFor(swingId)

    suspend fun replaceAnalysis(swing: Swing, samples: List<Sample>, metrics: List<Metric>) =
        dao.replaceAnalysis(swing, samples, metrics)
}
