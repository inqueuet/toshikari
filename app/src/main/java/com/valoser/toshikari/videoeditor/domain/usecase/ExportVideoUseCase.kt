package com.valoser.toshikari.videoeditor.domain.usecase

import android.net.Uri
import com.valoser.toshikari.videoeditor.domain.model.EditorSession
import com.valoser.toshikari.videoeditor.domain.model.ExportProgress
import kotlinx.coroutines.flow.Flow

/**
 * 動画をエクスポートするユースケース
 */
interface ExportVideoUseCase {
    fun export(
        session: EditorSession,
        outputUri: Uri
    ): Flow<ExportProgress>
}
