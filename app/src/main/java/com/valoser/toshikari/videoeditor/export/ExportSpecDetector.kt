package com.valoser.toshikari.videoeditor.export

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.util.Log
import com.valoser.toshikari.videoeditor.domain.model.EditorSession
import com.valoser.toshikari.videoeditor.domain.model.VideoClip
import com.valoser.toshikari.videoeditor.utils.findTrack

internal const val DEFAULT_WIDTH = 1920
internal const val DEFAULT_HEIGHT = 1080
internal const val DEFAULT_FRAME_RATE = 30
internal const val DEFAULT_VIDEO_BITRATE = 10_000_000
internal const val DEFAULT_AUDIO_BITRATE = 192_000
internal const val DEFAULT_AUDIO_SAMPLE_RATE = 48_000
internal const val DEFAULT_AUDIO_CHANNELS = 2

/**
 * エクスポート仕様を表すデータクラス。
 *
 * ソースメディアから検出できなかったパラメータにはデフォルト値が入る。
 */
internal data class ExportSpec(
    val width: Int,
    val height: Int,
    val frameRate: Int,
    val videoBitrate: Int,
    val audioBitrate: Int,
    val audioSampleRate: Int,
    val audioChannels: Int,
)

/**
 * [EditorSession] 内のクリップ群からエクスポートに必要なメディア仕様を検出する。
 *
 * - 最初に見つかった映像/音声トラックの解像度・フレームレート・ビットレートを採用する。
 * - 回転メタデータ（90/270°）を検出した場合は幅と高さを入れ替える。
 * - 各クリップの [MediaExtractor] は `finally` で確実に解放する。
 */
internal object ExportSpecDetector {

    private const val TAG = "ExportSpecDetector"

    fun detect(context: Context, session: EditorSession): ExportSpec =
        detect(context, session.videoClips)

    fun detect(context: Context, clips: List<VideoClip>): ExportSpec {
        var detectedWidth: Int? = null
        var detectedHeight: Int? = null
        var detectedFrameRate: Int? = null
        var detectedVideoBitrate: Int? = null
        var detectedAudioSampleRate: Int? = null
        var detectedAudioChannels: Int? = null
        var detectedAudioBitrate: Int? = null

        for (clip in clips) {
            val extractor = MediaExtractor()
            try {
                extractor.setDataSource(context, clip.source, null)

                detectVideoSpec(extractor)?.let { (w, h, fps, bitrate) ->
                    if (detectedWidth == null) {
                        detectedWidth = w
                        detectedHeight = h
                        Log.d(TAG, "Detected source resolution: ${w}x${h}")
                    }
                    if (detectedFrameRate == null && fps != null) {
                        detectedFrameRate = fps
                        Log.d(TAG, "Detected source frameRate: $fps")
                    }
                    if (detectedVideoBitrate == null && bitrate != null) {
                        detectedVideoBitrate = bitrate
                        Log.d(TAG, "Detected source video bitrate: $bitrate")
                    }
                }

                detectAudioSpec(extractor)?.let { (sampleRate, channels, bitrate) ->
                    if (detectedAudioSampleRate == null && sampleRate != null) {
                        detectedAudioSampleRate = sampleRate
                        Log.d(TAG, "Detected source audio sampleRate: $sampleRate")
                    }
                    if (detectedAudioChannels == null && channels != null) {
                        detectedAudioChannels = channels
                        Log.d(TAG, "Detected source audio channels: $channels")
                    }
                    if (detectedAudioBitrate == null && bitrate != null) {
                        detectedAudioBitrate = bitrate
                        Log.d(TAG, "Detected source audio bitrate: $bitrate")
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to parse source spec for ${clip.source}", e)
            } finally {
                extractor.release()
            }
        }

        return ExportSpec(
            width = detectedWidth ?: DEFAULT_WIDTH,
            height = detectedHeight ?: DEFAULT_HEIGHT,
            frameRate = detectedFrameRate ?: DEFAULT_FRAME_RATE,
            videoBitrate = detectedVideoBitrate ?: DEFAULT_VIDEO_BITRATE,
            audioBitrate = detectedAudioBitrate ?: DEFAULT_AUDIO_BITRATE,
            audioSampleRate = detectedAudioSampleRate ?: DEFAULT_AUDIO_SAMPLE_RATE,
            audioChannels = detectedAudioChannels ?: DEFAULT_AUDIO_CHANNELS,
        )
    }

    /** 映像トラックの (width, height, frameRate?, bitrate?) を返す。見つからない場合は null。 */
    private fun detectVideoSpec(extractor: MediaExtractor): VideoSpec? {
        val trackIndex = extractor.findTrack("video/") ?: return null
        val format = extractor.getTrackFormat(trackIndex)

        var width = format.getInteger(MediaFormat.KEY_WIDTH)
        var height = format.getInteger(MediaFormat.KEY_HEIGHT)
        val rotation = format.getIntegerSafe(MediaFormat.KEY_ROTATION)
        if (rotation == 90 || rotation == 270) {
            val tmp = width; width = height; height = tmp
        }

        val fps = format.getIntegerSafe(MediaFormat.KEY_FRAME_RATE)
        val bitrate = format.getIntegerSafe(MediaFormat.KEY_BIT_RATE)
        return VideoSpec(width, height, fps, bitrate)
    }

    /** 音声トラックの (sampleRate?, channels?, bitrate?) を返す。見つからない場合は null。 */
    private fun detectAudioSpec(extractor: MediaExtractor): AudioSpec? {
        val trackIndex = extractor.findTrack("audio/") ?: return null
        val format = extractor.getTrackFormat(trackIndex)

        val sampleRate = format.getIntegerSafe(MediaFormat.KEY_SAMPLE_RATE)
        val channels = format.getIntegerSafe(MediaFormat.KEY_CHANNEL_COUNT)
        val bitrate = format.getIntegerSafe(MediaFormat.KEY_BIT_RATE)
        return AudioSpec(sampleRate, channels, bitrate)
    }

    private fun MediaFormat.getIntegerSafe(key: String): Int? =
        if (containsKey(key)) getInteger(key) else null

    private data class VideoSpec(val width: Int, val height: Int, val frameRate: Int?, val bitrate: Int?)
    private data class AudioSpec(val sampleRate: Int?, val channels: Int?, val bitrate: Int?)
}
