package com.allen.pokemon.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.allen.pokemon.data.local.entity.SyncPhaseStatus
import com.allen.pokemon.data.local.entity.PokemonEntity
import com.allen.pokemon.data.local.entity.PokemonTypeCrossRef
import kotlinx.coroutines.flow.Flow

@Dao
interface PokemonDao {
    @Upsert
    suspend fun insertPokemon(pokemon: PokemonEntity)

    // IGNORE preserves both committed rows and previous retry decisions on reseeding.
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPendingPokemon(pokemon: PokemonEntity)

    @Query("SELECT * FROM pokemon WHERE id = :id")
    suspend fun getSyncRecord(id: Int): PokemonEntity?

    @Query("SELECT id FROM pokemon WHERE coreStatus IN ('PENDING', 'RETRYABLE') ORDER BY id LIMIT :limit")
    suspend fun getPendingCoreIds(limit: Int): List<Int>

    @Query("UPDATE pokemon SET coreStatus = :status WHERE id = :id")
    suspend fun updateCoreStatus(id: Int, status: SyncPhaseStatus)

    @Query("SELECT COUNT(*) FROM pokemon WHERE coreStatus = 'COMPLETE'")
    suspend fun getCompletedCoreCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPokemonTypeCrossRef(crossRef: PokemonTypeCrossRef)

    @Query("DELETE FROM pokemon_type WHERE pokemonId = :pokemonId")
    suspend fun deleteTypeMembershipsForPokemon(pokemonId: Int)

    @Query("SELECT * FROM pokemon WHERE id = :id AND coreStatus = 'COMPLETE'")
    suspend fun getPokemonById(id: Int): PokemonEntity?

    @Query("""
        SELECT pt.typeName FROM pokemon_type pt
        INNER JOIN pokemon p ON p.id = pt.pokemonId
        WHERE pt.pokemonId = :pokemonId AND p.coreStatus = 'COMPLETE'
        ORDER BY pt.typeName ASC
    """)
    suspend fun getTypesByPokemonId(pokemonId: Int): List<String>

    @Query("SELECT * FROM pokemon WHERE coreStatus = 'COMPLETE' ORDER BY id")
    fun getAllPokemon(): Flow<List<PokemonEntity>>

    @Query("""
        SELECT DISTINCT p.* FROM pokemon p
        INNER JOIN pokemon_type pt ON p.id = pt.pokemonId
        WHERE pt.typeName = :typeName AND p.coreStatus = 'COMPLETE'
        ORDER BY p.id
    """)
    fun getPokemonByType(typeName: String): Flow<List<PokemonEntity>>

    @Query("""
        SELECT DISTINCT pt.typeName FROM pokemon_type pt
        INNER JOIN pokemon p ON p.id = pt.pokemonId
        WHERE p.coreStatus = 'COMPLETE'
        ORDER BY pt.typeName ASC
    """)
    fun getAllTypes(): Flow<List<String>>

    @Query("""
        SELECT COUNT(*) FROM pokemon_type pt
        INNER JOIN pokemon p ON p.id = pt.pokemonId
        WHERE pt.typeName = :typeName AND p.coreStatus = 'COMPLETE'
    """)
    fun getTypeCount(typeName: String): Flow<Int>

    @Query("""
        SELECT COUNT(*) FROM pokemon_type pt
        INNER JOIN pokemon p ON p.id = pt.pokemonId
        WHERE pt.typeName = :typeName AND p.coreStatus = 'COMPLETE'
    """)
    suspend fun getTypeCountNow(typeName: String): Int
}
