package com.valoser.toshikari.videoeditor.core.di

import com.valoser.toshikari.videoeditor.domain.usecase.ExportVideoUseCase
import com.valoser.toshikari.videoeditor.data.usecase.ExportVideoUseCaseImpl
import com.valoser.toshikari.videoeditor.export.ExportPipeline
import com.valoser.toshikari.videoeditor.export.ExportPipelineImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ExportModule {

    @Binds
    @Singleton
    abstract fun bindExportVideoUseCase(
        useCase: ExportVideoUseCaseImpl
    ): ExportVideoUseCase

    @Binds
    @Singleton
    abstract fun bindExportPipeline(
        pipeline: ExportPipelineImpl
    ): ExportPipeline
}
