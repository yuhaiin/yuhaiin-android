package io.github.asutorufa.yuhaiin.service

/** Each collection window gets a new sampler so resuming never includes unobserved time. */
internal class TrafficRateSampler {
    private data class Sample(
        val startedAt: Long,
        val at: Long,
        val download: ULong,
        val upload: ULong,
    )

    private var previous: Sample? = null

    fun sample(status: NativeStatus, elapsedMillis: Long): String? {
        val download = status.session.download.toULongOrNull() ?: return null
        val upload = status.session.upload.toULongOrNull() ?: return null
        val current = Sample(status.startedAt, elapsedMillis, download, upload)
        val last = previous
        if (last != null && elapsedMillis <= last.at) return null
        previous = current
        if (
            last == null ||
                last.startedAt != current.startedAt ||
                download < last.download ||
                upload < last.upload
        )
            return null
        val seconds = (elapsedMillis - last.at) / 1000.0
        val downloadRate = (download - last.download).toDouble() / seconds
        val uploadRate = (upload - last.upload).toDouble() / seconds
        return "↓(${formatBytes(status.session.totalDownload)}): ${formatBytes(downloadRate.toString())}/S " +
            "↑(${formatBytes(status.session.totalUpload)}): ${formatBytes(uploadRate.toString())}/S"
    }
}
