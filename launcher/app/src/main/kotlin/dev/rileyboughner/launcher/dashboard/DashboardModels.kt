package dev.rileyboughner.launcher.dashboard

/** Host-level metrics parsed from the "server" section of the dashboard's `/api/all` response. */
data class ServerMetrics(
    val cpuPercent: Double?,
    val memoryUsedBytes: Long?,
    val memoryTotalBytes: Long?,
    val diskUsedBytes: Long?,
    val diskTotalBytes: Long?,
    val uptimeSeconds: Double?,
    val containersRunning: Int?,
    val containersTotal: Int?,
)
