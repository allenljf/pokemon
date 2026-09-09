package com.allen.pokemon.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PokemonListResponse(
    val results: List<PokemonListItem>,
)

@Serializable
data class PokemonListItem(
    val name: String,
    val url: String,
)

@Serializable
data class PokemonDetailResponse(
    val id: Int,
    val name: String,
    val types: List<TypeSlot>,
    val sprites: Sprites,
)

@Serializable
data class TypeSlot(
    val type: TypeInfo,
)

@Serializable
data class TypeInfo(
    val name: String,
)

@Serializable
data class Sprites(
    val other: Other?,
)

@Serializable
data class Other(
    @SerialName("official-artwork")
    val officialArtwork: OfficialArtwork?,
)

@Serializable
data class OfficialArtwork(
    val front_default: String?,
)

@Serializable
data class PokemonSpeciesResponse(
    val id: Int,
    val name: String,
    @SerialName("flavor_text_entries")
    val flavorTextEntries: List<FlavorTextEntry>,
    @SerialName("evolves_from_species")
    val evolvesFromSpecies: SpeciesRef?,
)

@Serializable
data class FlavorTextEntry(
    @SerialName("flavor_text")
    val flavorText: String,
    val language: LanguageRef,
)

@Serializable
data class LanguageRef(
    val name: String,
)

@Serializable
data class SpeciesRef(
    val name: String,
    val url: String,
)
