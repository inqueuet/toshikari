package com.valoser.toshikari.videoeditor.core.di

import com.valoser.toshikari.videoeditor.data.session.EditorSessionManagerImpl
import com.valoser.toshikari.videoeditor.data.usecase.EditClipUseCaseImpl
import com.valoser.toshikari.videoeditor.data.usecase.ManageAudioTrackUseCaseImpl
import com.valoser.toshikari.videoeditor.data.usecase.ApplyTransitionUseCaseImpl
import com.valoser.toshikari.videoeditor.domain.session.EditorSessionManager
import com.valoser.toshikari.videoeditor.domain.usecase.EditClipUseCase
import com.valoser.toshikari.videoeditor.domain.usecase.ManageAudioTrackUseCase
import com.valoser.toshikari.videoeditor.domain.usecase.ApplyTransitionUseCase
import com.valoser.toshikari.videoeditor.media.player.PlayerEngine
import com.valoser.toshikari.videoeditor.media.player.PlayerEngineImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 動画編集機能のDIモジュール
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class VideoEditorModule {

    @Binds
    @Singleton
    abstract fun bindEditorSessionManager(
        impl: EditorSessionManagerImpl
    ): EditorSessionManager

    @Binds
    @Singleton
    abstract fun bindEditClipUseCase(
        impl: EditClipUseCaseImpl
    ): EditClipUseCase

    @Binds
    @Singleton
    abstract fun bindManageAudioTrackUseCase(
        impl: ManageAudioTrackUseCaseImpl
    ): ManageAudioTrackUseCase

    @Binds
    @Singleton
    abstract fun bindApplyTransitionUseCase(
        impl: ApplyTransitionUseCaseImpl
    ): ApplyTransitionUseCase

    @Binds
    @Singleton
    abstract fun bindPlayerEngine(
        impl: PlayerEngineImpl
    ): PlayerEngine
}
