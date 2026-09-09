package com.allen.pokemon.data.local.entity

import androidx.room.Entity
import androidx.room.ColumnInfo
import androidx.room.PrimaryKey

enum class SyncPhaseStatus {
    PENDING,
    RETRYABLE,
    COMPLETE,
    NOT_AVAILABLE,
}

@Entity(tableName = "pokemon")
data class PokemonEntity(
    @PrimaryKey
    val id: Int,
    val name: String,
    val imageUrl: String?,
    @ColumnInfo(defaultValue = "'PENDING'")
    val coreStatus: SyncPhaseStatus = SyncPhaseStatus.PENDING,
)

@Entity(
    tableName = "pokemon_type",
    primaryKeys = ["pokemonId", "typeName"]
)
data class PokemonTypeCrossRef(
    val pokemonId: Int,
    val typeName: String,
)

@Entity(tableName = "species")
data class SpeciesEntity(
    @PrimaryKey
    val id: Int,
    val description: String?,
    val evolvesFromSpeciesId: Int?,
    @ColumnInfo(defaultValue = "0")
    val descriptionFormatVersion: Int = 1,
)

@Entity(tableName = "capture")
data class CaptureEntity(
    @PrimaryKey
    val id: String,
    val pokemonId: Int,
    val capturedAt: Long,
)
