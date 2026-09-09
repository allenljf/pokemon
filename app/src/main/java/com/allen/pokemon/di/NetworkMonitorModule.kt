package com.allen.pokemon.di

import com.allen.pokemon.data.network.AndroidNetworkMonitor
import com.allen.pokemon.data.network.NetworkMonitor
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class NetworkMonitorModule {
    @Binds
    @Singleton
    abstract fun bindNetworkMonitor(monitor: AndroidNetworkMonitor): NetworkMonitor
}
