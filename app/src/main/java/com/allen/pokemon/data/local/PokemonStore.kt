package com.allen.pokemon.data.local

import androidx.room.withTransaction
import com.allen.pokemon.core.model.PersistedSyncFailure
import com.allen.pokemon.core.model.PokemonCoreSnapshot
import com.allen.pokemon.core.model.PokemonReference
import com.allen.pokemon.data.local.entity.PokemonEntity
import com.allen.pokemon.data.local.entity.PokemonTypeCrossRef
import com.allen.pokemon.data.local.entity.SyncPhaseStatus

interface PokemonStore {
    suspend fun seedPending(references: List<PokemonReference>)
    suspend fun pendingCore(limit: Int): List<Int>
    suspend fun commitCore(snapshot: PokemonCoreSnapshot)
    suspend fun markCoreFailure(id: Int, failure: PersistedSyncFailure)
    suspend fun completedCoreCount(): Int
}

class RoomPokemonStore(
    private val database: PokemonDatabase,
    private val beforeCoreCompletion: suspend () -> Unit = {},
) : PokemonStore {
    override suspend fun seedPending(references: List<PokemonReference>) {
        references.forEach { reference ->
            database.pokemonDao().insertPendingPokemon(
                PokemonEntity(reference.id, reference.name, imageUrl = null),
            )
        }
    }

    override suspend fun pendingCore(limit: Int): List<Int> =
        database.pokemonDao().getPendingCoreIds(limit)

    override suspend fun commitCore(snapshot: PokemonCoreSnapshot) {
        database.withTransaction {
            database.pokemonDao().insertPokemon(
                PokemonEntity(snapshot.id, snapshot.name, snapshot.imageUrl),
            )
            database.pokemonDao().deleteTypeMembershipsForPokemon(snapshot.id)
            snapshot.typeNames.distinct().forEach { typeName ->
                database.pokemonDao().insertPokemonTypeCrossRef(
                    PokemonTypeCrossRef(snapshot.id, typeName),
                )
            }
            beforeCoreCompletion()
            database.pokemonDao().updateCoreStatus(snapshot.id, SyncPhaseStatus.COMPLETE)
        }
    }

    override suspend fun markCoreFailure(id: Int, failure: PersistedSyncFailure) {
        database.withTransaction {
            database.pokemonDao().insertPendingPokemon(PokemonEntity(id, "", imageUrl = null))
            database.pokemonDao().updateCoreStatus(
                id,
                if (failure.isRetryable) SyncPhaseStatus.RETRYABLE else SyncPhaseStatus.NOT_AVAILABLE,
            )
        }
    }

    override suspend fun completedCoreCount(): Int = database.pokemonDao().getCompletedCoreCount()
}
