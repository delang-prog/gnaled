package com.gnaled.swing.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.gnaled.swing.data.entity.BallSample
import com.gnaled.swing.data.entity.Metric
import com.gnaled.swing.data.entity.Sample
import com.gnaled.swing.data.entity.Swing
import kotlinx.coroutines.flow.Flow

@Dao
interface SwingDao {

    @Query("SELECT * FROM swings ORDER BY recordedAtMillis DESC")
    fun observeAll(): Flow<List<Swing>>

    @Query("SELECT * FROM swings WHERE id = :id")
    suspend fun findById(id: String): Swing?

    @Query("SELECT * FROM swings WHERE id = :id")
    fun observeById(id: String): Flow<Swing?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSwing(swing: Swing)

    @Query("DELETE FROM swings WHERE id = :id")
    suspend fun deleteSwing(id: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSamples(samples: List<Sample>)

    @Query("SELECT * FROM samples WHERE swingId = :swingId ORDER BY frameIndex")
    suspend fun samplesFor(swingId: String): List<Sample>

    @Query("SELECT * FROM samples WHERE swingId = :swingId ORDER BY frameIndex")
    fun observeSamples(swingId: String): Flow<List<Sample>>

    @Query("DELETE FROM samples WHERE swingId = :swingId")
    suspend fun clearSamples(swingId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMetrics(metrics: List<Metric>)

    @Query("SELECT * FROM metrics WHERE swingId = :swingId")
    suspend fun metricsFor(swingId: String): List<Metric>

    @Query("SELECT * FROM metrics WHERE swingId = :swingId")
    fun observeMetrics(swingId: String): Flow<List<Metric>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBallSamples(samples: List<BallSample>)

    @Query("SELECT * FROM ball_samples WHERE swingId = :swingId ORDER BY frameIndex")
    suspend fun ballSamplesFor(swingId: String): List<BallSample>

    @Query("SELECT * FROM ball_samples WHERE swingId = :swingId ORDER BY frameIndex")
    fun observeBallSamples(swingId: String): Flow<List<BallSample>>

    @Query("DELETE FROM ball_samples WHERE swingId = :swingId")
    suspend fun clearBallSamples(swingId: String)

    @Transaction
    suspend fun replaceAnalysis(
        swing: Swing,
        samples: List<Sample>,
        metrics: List<Metric>,
        ballSamples: List<BallSample> = emptyList(),
    ) {
        upsertSwing(swing)
        clearSamples(swing.id)
        clearBallSamples(swing.id)
        if (samples.isNotEmpty()) upsertSamples(samples)
        if (ballSamples.isNotEmpty()) upsertBallSamples(ballSamples)
        if (metrics.isNotEmpty()) upsertMetrics(metrics)
    }
}
