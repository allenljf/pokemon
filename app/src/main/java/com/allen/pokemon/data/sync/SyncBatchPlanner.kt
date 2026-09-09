package com.allen.pokemon.data.sync

class SyncBatchPlanner(
    private val maxConcurrency: Int,
) {
    init {
        require(maxConcurrency > 0)
    }

    fun batches(pendingPokemonIds: List<Int>): List<List<Int>> =
        pendingPokemonIds.chunked(maxConcurrency)
}
