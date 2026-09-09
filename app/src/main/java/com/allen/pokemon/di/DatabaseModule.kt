package com.allen.pokemon.di

import android.content.Context
import androidx.room.Room
import com.allen.pokemon.data.local.PokemonDatabase
import com.allen.pokemon.data.local.PokemonStore
import com.allen.pokemon.data.local.RoomPokemonStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Singleton
    @Provides
    fun providePokemonDatabase(
        @ApplicationContext context: Context,
    ): PokemonDatabase {
        return Room.databaseBuilder(
            context,
            PokemonDatabase::class.java,
            "pokemon_database"
        ).build()
    }

    @Singleton
    @Provides
    fun providePokemonDao(database: PokemonDatabase) = database.pokemonDao()

    @Singleton
    @Provides
    fun provideCaptureDao(database: PokemonDatabase) = database.captureDao()

    @Singleton
    @Provides
    fun provideSpeciesDao(database: PokemonDatabase) = database.speciesDao()

    @Singleton
    @Provides
    fun providePokemonStore(database: PokemonDatabase): PokemonStore = RoomPokemonStore(database)
}
