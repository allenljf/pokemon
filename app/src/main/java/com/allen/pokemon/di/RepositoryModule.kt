package com.allen.pokemon.di

import com.allen.pokemon.data.repository.CollectionRepository
import com.allen.pokemon.data.repository.PokemonDetailRepository
import com.allen.pokemon.data.repository.PokemonRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    abstract fun bindCollectionRepository(repository: PokemonRepository): CollectionRepository

    @Binds
    abstract fun bindPokemonDetailRepository(repository: PokemonRepository): PokemonDetailRepository
}
