package com.allen.pokemon.testing

import com.allen.pokemon.core.model.PersistedSyncFailure
import com.allen.pokemon.core.model.PokemonCoreSnapshot
import com.allen.pokemon.core.model.PokemonReference
import com.allen.pokemon.data.local.PokemonStore
import com.allen.pokemon.data.sync.PokemonSyncRemote
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.channels.Channel

class FakePokemonStore : PokemonStore {
    private val references = ConcurrentHashMap<Int, PokemonReference>()
    private val pending = ConcurrentHashMap.newKeySet<Int>()
    private val completed = ConcurrentHashMap.newKeySet<Int>()
    private val terminal = ConcurrentHashMap.newKeySet<Int>()
    private val completedCountCalls = AtomicInteger(0)
    private var blockedCompletedCountCall: Int? = null
    val completedCountStarted = Channel<Int>(Channel.UNLIMITED)
    private val completedCountPermits = Channel<Unit>(Channel.UNLIMITED)

    override suspend fun seedPending(references: List<PokemonReference>) {
        references.forEach { reference ->
            this.references.putIfAbsent(reference.id, reference)
            if (reference.id !in completed && reference.id !in terminal) pending += reference.id
        }
    }

    override suspend fun pendingCore(limit: Int): List<Int> = pending.sorted().take(limit)

    override suspend fun commitCore(snapshot: PokemonCoreSnapshot) {
        pending -= snapshot.id
        completed += snapshot.id
    }

    override suspend fun markCoreFailure(id: Int, failure: PersistedSyncFailure) {
        if (!failure.isRetryable) {
            pending -= id
            terminal += id
        }
    }

    override suspend fun completedCoreCount(): Int {
        val call = completedCountCalls.incrementAndGet()
        if (call == blockedCompletedCountCall) {
            completedCountStarted.send(call)
            completedCountPermits.receive()
        }
        return completed.size
    }

    fun pendingIds(): Set<Int> = pending.toSet()

    fun completedIds(): Set<Int> = completed.toSet()

    fun blockCompletedCountOn(call: Int) {
        blockedCompletedCountCall = call
    }

    fun allowBlockedCompletedCount() {
        check(completedCountPermits.trySend(Unit).isSuccess)
    }
}

class ControllablePokemonSyncRemote(
    private val references: List<PokemonReference>,
) : PokemonSyncRemote {
    val started = Channel<Int>(Channel.UNLIMITED)
    private val permitsById = ConcurrentHashMap<Int, Channel<Unit>>()
    private val active = AtomicInteger(0)
    private val maximumActive = AtomicInteger(0)
    private val fetches = ConcurrentHashMap.newKeySet<Int>()
    private val failures = ConcurrentHashMap<Int, Throwable>()
    private var indexFailure: Throwable? = null

    override suspend fun fetchIndex(): List<PokemonReference> {
        indexFailure?.let { throw it }
        return references
    }

    override suspend fun fetchCore(id: Int): PokemonCoreSnapshot {
        val nowActive = active.incrementAndGet()
        maximumActive.updateAndGet { maxOf(it, nowActive) }
        started.send(id)
        try {
            permitsById.getOrPut(id) { Channel(Channel.UNLIMITED) }.receive()
            failures[id]?.let { throw it }
            fetches += id
            return PokemonCoreSnapshot(id, "pokemon-$id", null, listOf("normal"))
        } finally {
            active.decrementAndGet()
        }
    }

    fun allow(id: Int) {
        check(permitsById.getOrPut(id) { Channel(Channel.UNLIMITED) }.trySend(Unit).isSuccess)
    }

    fun fail(id: Int, error: Throwable) {
        failures[id] = error
    }

    fun failIndex(error: Throwable) {
        indexFailure = error
    }

    fun peakInFlight(): Int = maximumActive.get()

    fun fetchedIds(): Set<Int> = fetches.toSet()
}
