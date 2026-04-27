package com.gnaled.swing.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Instant

/**
 * Thin wrapper over Health Connect for reading Garmin/Watch heart rate
 * around a swing window. Returns gracefully empty when:
 *  - Health Connect is not installed on the device
 *  - The user hasn't granted READ permission for HeartRateRecord
 *  - The query window contains no records
 */
class HealthConnectRepository(private val context: Context) {

    val readHeartRatePermission: String =
        HealthPermission.getReadPermission(HeartRateRecord::class)

    val requiredPermissions: Set<String> = setOf(readHeartRatePermission)

    val sdkAvailable: Boolean
        get() = HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE

    private val client: HealthConnectClient?
        get() = if (sdkAvailable) HealthConnectClient.getOrCreate(context) else null

    suspend fun grantedPermissions(): Set<String> =
        client?.permissionController?.getGrantedPermissions() ?: emptySet()

    suspend fun hasHeartRatePermission(): Boolean =
        readHeartRatePermission in grantedPermissions()

    suspend fun heartRateBetween(start: Instant, end: Instant): List<HeartRateSample> {
        val c = client ?: return emptyList()
        if (!hasHeartRatePermission()) return emptyList()
        val response = runCatching {
            c.readRecords(
                ReadRecordsRequest(
                    recordType = HeartRateRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(start, end),
                ),
            )
        }.getOrNull() ?: return emptyList()
        return response.records.flatMap { record ->
            record.samples.map { HeartRateSample(time = it.time, bpm = it.beatsPerMinute) }
        }
    }
}

data class HeartRateSample(val time: Instant, val bpm: Long)
