package com.valoser.toshikari.videoeditor.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ExportOptionsTest {

    @Test
    fun `standard 1080p uses expected bitrates`() {
        val options = ExportOptions(
            resolution = Resolution.HD1080,
            compression = ExportCompression.STANDARD
        )

        assertEquals(1920, options.width)
        assertEquals(1080, options.height)
        assertEquals(12_000_000, options.videoBitrate)
        assertEquals(192_000, options.audioBitrate)
    }

    @Test
    fun `high compression lowers bitrate`() {
        val options = ExportOptions(
            resolution = Resolution.HD1080,
            compression = ExportCompression.HIGH
        )

        assertEquals(7_200_000, options.videoBitrate)
        assertEquals(128_000, options.audioBitrate)
    }

    @Test
    fun `low compression raises bitrate`() {
        val options = ExportOptions(
            resolution = Resolution.HD720,
            compression = ExportCompression.LOW
        )

        assertEquals(12_000_000, options.videoBitrate)
        assertEquals(256_000, options.audioBitrate)
    }
}
