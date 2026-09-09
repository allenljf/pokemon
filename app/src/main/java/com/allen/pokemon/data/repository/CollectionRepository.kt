package com.allen.pokemon.data.repository

import com.allen.pokemon.core.model.CapturedPokemon
import com.allen.pokemon.core.model.Pokemon
import kotlinx.coroutines.flow.Flow

interface CollectionRepository {
    fun getPokemonByType(typeName: String): Flow<List<Pokemon>>
    fun getCompletedPokemonIds(): Flow<List<Int>>
    fun getAllTypes(): Flow<List<String>>
    fun getTypeCount(typeName: String): Flow<Int>
    fun getAllCaptures(): Flow<List<CapturedPokemon>>
    suspend fun addCapture(pokemonId: Int): String
    suspend fun removeCapture(captureId: String)
}
