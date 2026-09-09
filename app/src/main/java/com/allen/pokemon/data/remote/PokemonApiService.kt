package com.allen.pokemon.data.remote

import com.allen.pokemon.data.remote.dto.PokemonDetailResponse
import com.allen.pokemon.data.remote.dto.PokemonListResponse
import com.allen.pokemon.data.remote.dto.PokemonSpeciesResponse
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface PokemonApiService {
    @GET("pokemon")
    suspend fun getPokemonList(
        @Query("limit") limit: Int,
        @Query("offset") offset: Int = 0,
    ): PokemonListResponse

    @GET("pokemon/{id}")
    suspend fun getPokemonDetail(
        @Path("id") id: Int,
    ): PokemonDetailResponse

    @GET("pokemon-species/{id}")
    suspend fun getPokemonSpecies(
        @Path("id") id: Int,
    ): PokemonSpeciesResponse
}
