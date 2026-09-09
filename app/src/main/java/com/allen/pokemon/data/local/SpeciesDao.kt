package com.allen.pokemon.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.allen.pokemon.data.local.entity.SpeciesEntity

@Dao
interface SpeciesDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSpecies(species: SpeciesEntity)

    @Query("SELECT * FROM species WHERE id = :id")
    suspend fun getSpeciesById(id: Int): SpeciesEntity?
}
