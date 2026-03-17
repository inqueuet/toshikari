package com.valoser.toshikari.videoeditor.domain.model

/**
 * 時間（ミリ秒）を表す値オブジェクト
 */
@JvmInline
value class TimeMillis(val value: Long) {
    operator fun plus(other: TimeMillis) = TimeMillis(value + other.value)
    operator fun minus(other: TimeMillis) = TimeMillis(value - other.value)
    operator fun compareTo(other: TimeMillis) = value.compareTo(other.value)

    companion object {
        val ZERO = TimeMillis(0L)
    }
}

/**
 * 音量を表す値オブジェクト（0.0 ~ 2.0）
 */
@JvmInline
value class Volume(val value: Float) {
    init {
        require(value in 0f..2f) { "Volume must be between 0 and 2" }
    }

    companion object {
        val ZERO = Volume(0f)
        val NORMAL = Volume(1f)
        val MAX = Volume(2f)
    }
}

/**
 * 時間範囲を表すデータクラス
 */
data class TimeRange(
    val start: TimeMillis,
    val end: TimeMillis
) {
    init {
        require(start <= end) { "Start must be before or equal to end" }
    }

    val duration: TimeMillis
        get() = end - start

    operator fun contains(time: TimeMillis): Boolean {
        return time >= start && time <= end
    }

    fun overlaps(other: TimeRange): Boolean {
        return start < other.end && end > other.start
    }

    companion object {
        fun fromMillis(startMs: Long, endMs: Long) = TimeRange(
            TimeMillis(startMs),
            TimeMillis(endMs)
        )
    }
}

/**
 * メディア情報
 */
data class MediaInfo(
    val duration: Long,
    val width: Int,
    val height: Int,
    val hasAudio: Boolean,
    val frameRate: Float,
    val bitrate: Int
)

/**
 * エクスポート設定
 */
data class ExportSettings(
    val preset: ExportPreset,
    val outputPath: String
)

enum class ExportCompression(
    val displayName: String,
    val videoBitratePercent: Int,
    val audioBitrate: Int
) {
    HIGH("高圧縮", 60, 128_000),
    STANDARD("標準", 100, 192_000),
    LOW("低圧縮", 150, 256_000)
}

data class ExportOptions(
    val resolution: Resolution = Resolution.HD1080,
    val compression: ExportCompression = ExportCompression.STANDARD,
    val frameRate: Int = 30,
    val audioSampleRate: Int = 48_000,
    val audioChannels: Int = 2
) {
    val width: Int get() = resolution.width
    val height: Int get() = resolution.height
    val videoBitrate: Int
        get() = (resolution.defaultVideoBitrate * compression.videoBitratePercent) / 100
    val audioBitrate: Int get() = compression.audioBitrate
}

/**
 * エクスポートプリセット
 */
enum class ExportPreset(
    val displayName: String,
    val resolution: Resolution,
    val fps: Int,
    val videoBitrate: Int,
    val audioBitrate: Int,
    val audioSampleRate: Int = 48000,
    val audioChannels: Int = 2
) {
    SNS(
        displayName = "SNS最適化",
        resolution = Resolution.HD720,
        fps = 30,
        videoBitrate = 8_000_000,
        audioBitrate = 192_000
    ),
    STANDARD(
        displayName = "標準品質",
        resolution = Resolution.HD1080,
        fps = 30,
        videoBitrate = 12_000_000,
        audioBitrate = 192_000
    ),
    HIGH_QUALITY(
        displayName = "高品質",
        resolution = Resolution.HD1080,
        fps = 60,
        videoBitrate = 20_000_000,
        audioBitrate = 192_000
    );

    val width: Int get() = resolution.width
    val height: Int get() = resolution.height
    val frameRate: Int get() = fps
}

private val Resolution.defaultVideoBitrate: Int
    get() = when (this) {
        Resolution.HD720 -> 8_000_000
        Resolution.HD1080 -> 12_000_000
    }

/**
 * エクスポート進捗
 */
data class ExportProgress(
    val currentFrame: Int,
    val totalFrames: Int,
    val percentage: Float,
    val estimatedTimeRemaining: Long
)
