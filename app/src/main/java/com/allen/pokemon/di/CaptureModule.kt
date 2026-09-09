package com.allen.pokemon.di

import com.allen.pokemon.data.repository.CaptureClock
import com.allen.pokemon.data.repository.CaptureIdGenerator
import com.allen.pokemon.data.repository.SystemCaptureClock
import com.allen.pokemon.data.repository.UuidCaptureIdGenerator
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class CaptureModule {
    @Binds
    @Singleton
    abstract fun bindCaptureClock(clock: SystemCaptureClock): CaptureClock

    @Binds
    @Singleton
    abstract fun bindCaptureIdGenerator(generator: UuidCaptureIdGenerator): CaptureIdGenerator
}
