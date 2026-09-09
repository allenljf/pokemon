package com.allen.pokemon.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.allen.pokemon.data.local.entity.CaptureEntity
import com.allen.pokemon.data.local.entity.PokemonEntity
import com.allen.pokemon.data.local.entity.PokemonTypeCrossRef
import com.allen.pokemon.data.local.entity.SpeciesEntity

@Database(
    entities = [
        PokemonEntity::class,
        PokemonTypeCrossRef::class,
        SpeciesEntity::class,
        CaptureEntity::class,
    ],
    version = 1,
)
@TypeConverters(RoomConverters::class)
abstract class PokemonDatabase : RoomDatabase() {
    abstract fun pokemonDao(): PokemonDao
    abstract fun captureDao(): CaptureDao
    abstract fun speciesDao(): SpeciesDao
}
