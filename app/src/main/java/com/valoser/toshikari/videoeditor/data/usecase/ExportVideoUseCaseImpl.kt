package com.valoser.toshikari.videoeditor.data.usecase

import android.net.Uri
import com.valoser.toshikari.videoeditor.domain.model.EditorSession
import com.valoser.toshikari.videoeditor.domain.model.ExportProgress
import com.valoser.toshikari.videoeditor.domain.usecase.ExportVideoUseCase
import com.valoser.toshikari.videoeditor.export.ExportPipeline
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * 動画エクスポートUseCaseの実装
 */
class ExportVideoUseCaseImpl @Inject constructor(
    private val exportPipeline: ExportPipeline
) : ExportVideoUseCase {

    override fun export(
        session: EditorSession,
        outputUri: Uri
    ): Flow<ExportProgress> {
        return exportPipeline.export(session, outputUri)
    }
}
