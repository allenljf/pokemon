package com.allen.pokemon.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import com.allen.pokemon.data.local.entity.CaptureEntity
import kotlinx.coroutines.flow.Flow

data class CapturedPokemonRow(
    val captureId: String,
    val pokemonId: Int,
    val capturedAt: Long,
    val pokemonName: String,
    val imageUrl: String?,
)

@Dao
interface CaptureDao {
    @Insert
    suspend fun insertCapture(capture: CaptureEntity)

    @Delete
    suspend fun deleteCapture(capture: CaptureEntity)

    @Query("SELECT * FROM capture ORDER BY capturedAt DESC, rowid DESC")
    fun getAllCaptures(): Flow<List<CaptureEntity>>

    @Query("""
        SELECT c.id AS captureId, c.pokemonId, c.capturedAt, p.name AS pokemonName, p.imageUrl
        FROM capture c
        INNER JOIN pokemon p ON p.id = c.pokemonId
        WHERE p.coreStatus = 'COMPLETE'
        ORDER BY c.capturedAt DESC, c.rowid DESC
    """)
    fun getAllCapturesWithPokemon(): Flow<List<CapturedPokemonRow>>

    @Query("SELECT * FROM capture WHERE id = :id")
    suspend fun getCaptureById(id: String): CaptureEntity?

    @Query("SELECT COUNT(*) FROM capture")
    fun getCaptureCount(): Flow<Int>
}
