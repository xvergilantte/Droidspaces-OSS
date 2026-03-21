package com.droidspaces.app.util

import android.util.Log
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.regex.Pattern

/**
 * Collects real-time Android system statistics directly from the device.
 * Reads from /proc files on Android system, not from containers.
 */
object AndroidSystemStatsCollector {
    private const val TAG = "AndroidSystemStatsCollector"

    /**
     * Android system usage statistics.
     */
    data class SystemUsage(
        val cpuPercent: Double = 0.0,
        val ramPercent: Double = 0.0,
        val temperature: String = "N/A"
    )

    // Previous values for calculating CPU usage
    private var prevCpuTotal = 0L
    private var prevCpuIdle = 0L

    /**
     * Collect usage statistics from Android system.
     */
    suspend fun collectUsage(): SystemUsage = withContext(Dispatchers.IO) {
        try {
            val cpuPercent = getCpuUsage()
            val ramPercent = getRamUsage()
            val temperature = getTemperature()

            SystemUsage(
                cpuPercent = cpuPercent,
                ramPercent = ramPercent,
                temperature = temperature
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to collect system usage", e)
            SystemUsage() // Return zeros on error
        }
    }

    /**
     * Get CPU usage percentage from Android /proc/stat.
     */
    private suspend fun getCpuUsage(): Double = withContext(Dispatchers.IO) {
        try {
            val result = Shell.cmd("cat /proc/stat | head -1").exec()

            if (result.isSuccess && result.out.isNotEmpty()) {
                val parts = result.out[0].trim().split("\\s+".toRegex())
                if (parts.size >= 8) {
                    val user = parts[1].toLongOrNull() ?: 0L
                    val nice = parts[2].toLongOrNull() ?: 0L
                    val system = parts[3].toLongOrNull() ?: 0L
                    val idle = parts[4].toLongOrNull() ?: 0L
                    val iowait = parts[5].toLongOrNull() ?: 0L
                    val total = user + nice + system + idle + iowait

                    if (prevCpuTotal > 0 && total > prevCpuTotal) {
                        val totalDelta = total - prevCpuTotal
                        val idleDelta = idle - prevCpuIdle
                        val used = totalDelta - idleDelta
                        val percent = if (totalDelta > 0) {
                            ((used.toDouble() / totalDelta.toDouble()) * 100.0).coerceIn(0.0, 100.0)
                        } else {
                            0.0
                        }

                        prevCpuTotal = total
                        prevCpuIdle = idle
                        return@withContext percent
                    } else {
                        prevCpuTotal = total
                        prevCpuIdle = idle
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting CPU usage", e)
        }
        0.0
    }

    /**
     * Get RAM usage percentage from Android /proc/meminfo.
     */
    private suspend fun getRamUsage(): Double = withContext(Dispatchers.IO) {
        try {
            val result = Shell.cmd("cat /proc/meminfo | grep -E 'MemTotal|MemAvailable'").exec()

            if (result.isSuccess && result.out.size >= 2) {
                var memTotal = 0L
                var memAvailable = 0L

                result.out.forEach { line ->
                    when {
                        line.contains("MemTotal") -> {
                            val parts = line.trim().split("\\s+".toRegex())
                            if (parts.size >= 2) {
                                memTotal = parts[1].toLongOrNull() ?: 0L
                            }
                        }
                        line.contains("MemAvailable") -> {
                            val parts = line.trim().split("\\s+".toRegex())
                            if (parts.size >= 2) {
                                memAvailable = parts[1].toLongOrNull() ?: 0L
                            }
                        }
                    }
                }

                if (memTotal > 0) {
                    val memUsed = memTotal - memAvailable
                    return@withContext ((memUsed.toDouble() / memTotal.toDouble()) * 100.0).coerceIn(0.0, 100.0)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting RAM usage", e)
        }
        0.0
    }

    /**
     * Get CPU temperature from Android thermal sensors.
     */
    private suspend fun getTemperature(): String = withContext(Dispatchers.IO) {
        try {
            // Try multiple thermal sensor paths
            val thermalPaths = listOf(
                "/sys/class/thermal/thermal_zone0/temp",
                "/sys/class/thermal/thermal_zone1/temp",
                "/sys/devices/virtual/thermal/thermal_zone0/temp"
            )

            for (path in thermalPaths) {
                val result = Shell.cmd("cat $path 2>/dev/null").exec()
                if (result.isSuccess && result.out.isNotEmpty()) {
                    val tempMilliCelsius = result.out[0].trim().toLongOrNull()
                    if (tempMilliCelsius != null && tempMilliCelsius > 0) {
                        val tempCelsius = tempMilliCelsius / 1000.0
                        return@withContext String.format("%.1f°C", tempCelsius)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting temperature", e)
        }
        "N/A"
    }


}

