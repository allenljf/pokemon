package com.allen.pokemon.data.sync

import com.allen.pokemon.core.model.PersistedSyncFailure
import com.allen.pokemon.core.model.PokemonCoreSnapshot
import com.allen.pokemon.core.model.PokemonReference
import com.allen.pokemon.data.local.PokemonStore
import com.allen.pokemon.data.network.AlwaysOnlineNetworkMonitor
import com.allen.pokemon.data.network.NetworkMonitor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import retrofit2.HttpException
import javax.inject.Inject
import javax.inject.Singleton

interface PokemonSyncRemote {
    suspend fun fetchIndex(): List<PokemonReference>
    suspend fun fetchCore(id: Int): PokemonCoreSnapshot
}

data class PokemonSyncState(
    val progress: Int = 0,
    val total: Int = TOTAL_POKEMON,
    val isSyncing: Boolean = false,
    val failure: SyncFailure? = null,
) {
    companion object {
        const val TOTAL_POKEMON = 151
    }
}

@Singleton
class PokemonSynchronizer @Inject constructor(
    private val remote: PokemonSyncRemote,
    private val store: PokemonStore,
    private val networkMonitor: NetworkMonitor = AlwaysOnlineNetworkMonitor,
) {
    private val runMutex = Mutex()
    private val stateMutex = Mutex()
    private val _syncState = MutableStateFlow(PokemonSyncState())
    val syncState: StateFlow<PokemonSyncState> = _syncState.asStateFlow()

    suspend fun syncMissing() = runMutex.withLock {
        try {
            val references = remote.fetchIndex()
            store.seedPending(references)
            updateState(total = references.size, isSyncing = true, clearFailure = true)
            val work = Channel<Int>(Channel.UNLIMITED)
            store.pendingCore(Int.MAX_VALUE).forEach { work.send(it) }
            work.close()
            val offlineDetected = AtomicBoolean(false)

            coroutineScope {
                repeat(MAX_IN_FLIGHT) {
                    launch {
                        for (id in work) {
                            if (offlineDetected.get()) break
                            if (syncOne(id) && offlineDetected.compareAndSet(false, true)) {
                                networkMonitor.reportNetworkFailure()
                            }
                        }
                    }
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            val failure = SyncFailure.from(error)
            if (failure == SyncFailure.Offline) networkMonitor.reportNetworkFailure()
            updateState(isSyncing = false, failure = failure)
        } finally {
            withContext(NonCancellable) {
                updateState(isSyncing = false)
            }
        }
    }

    suspend fun dismissFailure() {
        stateMutex.withLock {
            _syncState.value = _syncState.value.copy(failure = null)
        }
    }

    suspend fun dismissOfflineFailure() {
        stateMutex.withLock {
            if (_syncState.value.failure == SyncFailure.Offline) {
                _syncState.value = _syncState.value.copy(failure = null)
            }
        }
    }

    /** Returns true when the current sync run should stop dispatching more work. */
    private suspend fun syncOne(id: Int): Boolean {
        try {
            store.commitCore(remote.fetchCore(id))
            updateState()
            return false
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            store.markCoreFailure(id, PersistedSyncFailure(isRetryable = error.isRetryable()))
            val failure = SyncFailure.from(error)
            updateState(failure = failure)
            return failure == SyncFailure.Offline
        }
    }

    private suspend fun updateState(
        total: Int? = null,
        isSyncing: Boolean? = null,
        failure: SyncFailure? = null,
        clearFailure: Boolean = false,
    ) {
        stateMutex.withLock {
            val current = _syncState.value
            _syncState.emit(
                PokemonSyncState(
                    progress = store.completedCoreCount(),
                    total = total ?: current.total,
                    isSyncing = isSyncing ?: current.isSyncing,
                    failure = when {
                        clearFailure -> null
                        failure != null -> failure
                        else -> current.failure
                    },
                ),
            )
        }
    }

    private fun Throwable.isRetryable(): Boolean =
        (this as? HttpException)?.code()?.let { code -> code == 429 || code >= 500 } ?: true

    private companion object {
        const val MAX_IN_FLIGHT = 5
    }
}
