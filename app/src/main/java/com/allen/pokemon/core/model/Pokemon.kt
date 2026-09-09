package com.allen.pokemon.core.model

data class Pokemon(
    val id: Int,
    val name: String,
    val types: List<String>,
    val imageUrl: String?,
)

data class PokemonDetail(
    val id: Int,
    val name: String,
    val types: List<String>,
    val imageUrl: String?,
    val description: String?,
    val evolvesFromSpeciesId: Int? = null,
)

data class Capture(
    val id: String,
    val pokemonId: Int,
    val capturedAt: Long,
)

data class CapturedPokemon(
    val captureId: String,
    val pokemonId: Int,
    val capturedAt: Long,
    val pokemonName: String,
    val imageUrl: String?,
)

data class PokemonReference(
    val id: Int,
    val name: String,
)

data class PokemonCoreSnapshot(
    val id: Int,
    val name: String,
    val imageUrl: String?,
    val typeNames: List<String>,
)

data class PersistedSyncFailure(
    val isRetryable: Boolean,
)
