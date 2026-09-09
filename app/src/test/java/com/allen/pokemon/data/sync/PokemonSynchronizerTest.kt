package com.allen.pokemon.data.sync

import com.allen.pokemon.core.model.PokemonReference
import com.allen.pokemon.testing.ControllablePokemonSyncRemote
import com.allen.pokemon.testing.FakePokemonStore
import java.io.IOException
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

class PokemonSynchronizerTest {
    @Test
    fun `never has more than five suspended detail fetches and fetches every successful id once`() = runBlocking {
        val ids = (1..151).toList()
        val store = FakePokemonStore()
        val remote = ControllablePokemonSyncRemote(ids.map { PokemonReference(it, "pokemon-$it") })
        val synchronizer = PokemonSynchronizer(remote, store)

        val sync = async { synchronizer.syncMissing() }
        repeat(5) { remote.started.receive() }
        assertEquals(5, remote.peakInFlight())

        ids.forEach(remote::allow)
        sync.await()

        assertTrue(remote.peakInFlight() <= 5)
        assertEquals(ids.toSet(), remote.fetchedIds())
        assertEquals(ids.toSet(), store.completedIds())
    }

    @Test
    fun `simultaneous callers do not duplicate a detail request`() = runBlocking {
        val store = FakePokemonStore()
        val remote = ControllablePokemonSyncRemote(listOf(PokemonReference(25, "pikachu")))
        val synchronizer = PokemonSynchronizer(remote, store)

        val first = async { synchronizer.syncMissing() }
        remote.started.receive()
        val second = async { synchronizer.syncMissing() }
        remote.allow(25)
        first.await()
        second.await()

        assertEquals(setOf(25), remote.fetchedIds())
        assertEquals(setOf(25), store.completedIds())
    }

    @Test
    fun `cancellation leaves an uncommitted id pending`() = runBlocking {
        val store = FakePokemonStore()
        val remote = ControllablePokemonSyncRemote(listOf(PokemonReference(7, "squirtle")))
        val synchronizer = PokemonSynchronizer(remote, store)

        val sync = async { synchronizer.syncMissing() }
        remote.started.receive()
        sync.cancelAndJoin()

        assertEquals(setOf(7), store.pendingIds())
        assertTrue(store.completedIds().isEmpty())
    }

    @Test
    fun `retryable failure remains eligible for a later synchronizer run`() = runBlocking {
        val store = FakePokemonStore()
        val references = listOf(PokemonReference(4, "charmander"))
        val firstRemote = ControllablePokemonSyncRemote(references).also { it.fail(4, IOException("offline")) }
        val first = PokemonSynchronizer(firstRemote, store)

        val firstRun = async { first.syncMissing() }
        firstRemote.started.receive()
        firstRemote.allow(4)
        firstRun.await()

        val secondRemote = ControllablePokemonSyncRemote(references)
        val secondRun = async { PokemonSynchronizer(secondRemote, store).syncMissing() }
        secondRemote.started.receive()
        secondRemote.allow(4)
        secondRun.await()

        assertEquals(setOf(4), store.completedIds())
    }

    @Test
    fun `server error is retried on a later run while a client error is terminal`() = runBlocking {
        val references = listOf(PokemonReference(1, "bulbasaur"), PokemonReference(2, "ivysaur"))
        val store = FakePokemonStore()
        val firstRemote = ControllablePokemonSyncRemote(references).also {
            it.fail(1, HttpException(Response.error<Any>(503, "".toResponseBody())))
            it.fail(2, HttpException(Response.error<Any>(400, "".toResponseBody())))
        }
        val firstRun = async { PokemonSynchronizer(firstRemote, store).syncMissing() }
        firstRemote.started.receive()
        firstRemote.started.receive()
        firstRemote.allow(1)
        firstRemote.allow(2)
        firstRun.await()

        assertTrue(1 in store.pendingIds())
        assertFalse(2 in store.pendingIds())

        val secondRemote = ControllablePokemonSyncRemote(references)
        val secondRun = async { PokemonSynchronizer(secondRemote, store).syncMissing() }
        secondRemote.started.receive()
        secondRemote.allow(1)
        secondRun.await()

        assertEquals(setOf(1), secondRemote.fetchedIds())
        assertEquals(setOf(1), store.completedIds())
    }

    @Test
    fun `offline core failure stops dispatching additional pending ids`() = runBlocking {
        val ids = (1..10).toList()
        val store = FakePokemonStore()
        val remote = ControllablePokemonSyncRemote(ids.map { PokemonReference(it, "pokemon-$it") }).also {
            it.fail(1, IOException("offline"))
        }
        val synchronizer = PokemonSynchronizer(remote, store)

        val sync = async { synchronizer.syncMissing() }
        repeat(5) { remote.started.receive() }
        remote.allow(1)
        withTimeout(1_000) { synchronizer.syncState.filter { it.failure == SyncFailure.Offline }.first() }
        (2..10).forEach(remote::allow)

        assertNull(withTimeoutOrNull(250) { remote.started.receive() })
        sync.await()
        assertEquals(setOf(1, 6, 7, 8, 9, 10), store.pendingIds())
    }

    @Test
    fun `terminal failure is not fetched by a later synchronizer run`() = runBlocking {
        val store = FakePokemonStore()
        val references = listOf(PokemonReference(150, "mewtwo"))
        val firstRemote = ControllablePokemonSyncRemote(references).also {
            it.fail(150, HttpException(Response.error<Any>(404, "".toResponseBody())))
        }
        val firstRun = async { PokemonSynchronizer(firstRemote, store).syncMissing() }
        firstRemote.started.receive()
        firstRemote.allow(150)
        firstRun.await()

        val secondRemote = ControllablePokemonSyncRemote(references)
        val secondRun = async { PokemonSynchronizer(secondRemote, store).syncMissing() }
        secondRun.await()

        assertTrue(secondRemote.fetchedIds().isEmpty())
        assertFalse(150 in store.pendingIds())
    }

    @Test
    fun `relaunch requests only incomplete ids from the shared persisted store`() = runBlocking {
        val store = FakePokemonStore()
        val firstRemote = ControllablePokemonSyncRemote(listOf(PokemonReference(1, "bulbasaur")))
        val firstRun = async { PokemonSynchronizer(firstRemote, store).syncMissing() }
        firstRemote.started.receive()
        firstRemote.allow(1)
        firstRun.await()

        val relaunchedRemote = ControllablePokemonSyncRemote(
            listOf(PokemonReference(1, "bulbasaur"), PokemonReference(2, "ivysaur")),
        )
        val secondRun = async { PokemonSynchronizer(relaunchedRemote, store).syncMissing() }
        assertEquals(2, relaunchedRemote.started.receive())
        relaunchedRemote.allow(2)
        secondRun.await()

        assertEquals(setOf(2), relaunchedRemote.fetchedIds())
    }

    @Test
    fun `progress is based on committed records rather than launched requests`() = runBlocking {
        val store = FakePokemonStore()
        val remote = ControllablePokemonSyncRemote(listOf(PokemonReference(9, "blastoise")))
        val synchronizer = PokemonSynchronizer(remote, store)

        val sync = async { synchronizer.syncMissing() }
        remote.started.receive()
        assertEquals(0, synchronizer.syncState.value.progress)
        remote.allow(9)
        sync.await()

        assertEquals(1, synchronizer.syncState.value.progress)
    }

    @Test
    fun `dismiss failure retains committed progress`() = runBlocking {
        val store = FakePokemonStore()
        val remote = ControllablePokemonSyncRemote(listOf(PokemonReference(6, "charizard"))).also {
            it.fail(6, IOException("offline"))
        }
        val synchronizer = PokemonSynchronizer(remote, store)

        val sync = async { synchronizer.syncMissing() }
        remote.started.receive()
        remote.allow(6)
        sync.await()
        synchronizer.dismissFailure()

        assertEquals(0, synchronizer.syncState.value.progress)
        assertEquals(null, synchronizer.syncState.value.failure)
    }

    @Test
    fun `index failure is published as a completed non-syncing state`() = runBlocking {
        val remote = ControllablePokemonSyncRemote(emptyList()).also { it.failIndex(IOException("offline")) }
        val synchronizer = PokemonSynchronizer(remote, FakePokemonStore())

        synchronizer.syncMissing()

        assertEquals(SyncFailure.Offline, synchronizer.syncState.value.failure)
        assertFalse(synchronizer.syncState.value.isSyncing)
    }

    @Test
    fun `a concurrent successful commit cannot erase a record failure`() = runBlocking {
        val store = FakePokemonStore().also { it.blockCompletedCountOn(call = 2) }
        val remote = ControllablePokemonSyncRemote(
            listOf(PokemonReference(1, "bulbasaur"), PokemonReference(2, "ivysaur")),
        ).also { it.fail(2, IOException("offline")) }
        val synchronizer = PokemonSynchronizer(remote, store)

        val sync = async { synchronizer.syncMissing() }
        assertEquals(setOf(1, 2), setOf(remote.started.receive(), remote.started.receive()))
        remote.allow(1)
        assertEquals(2, store.completedCountStarted.receive())
        remote.allow(2)
        store.allowBlockedCompletedCount()
        sync.await()

        assertEquals(SyncFailure.Offline, synchronizer.syncState.value.failure)
        assertEquals(1, synchronizer.syncState.value.progress)
    }

    @Test
    fun `dismissing a failure cannot be undone by an in-flight state update`() = runBlocking {
        val store = FakePokemonStore().also { it.blockCompletedCountOn(call = 3) }
        val remote = ControllablePokemonSyncRemote(
            listOf(PokemonReference(1, "bulbasaur"), PokemonReference(2, "ivysaur")),
        ).also {
            it.fail(1, IOException("offline"))
        }
        val synchronizer = PokemonSynchronizer(remote, store)

        val sync = async { synchronizer.syncMissing() }
        assertEquals(setOf(1, 2), setOf(remote.started.receive(), remote.started.receive()))
        remote.allow(1)
        remote.allow(2)
        assertEquals(3, store.completedCountStarted.receive())
        val dismiss = async { synchronizer.dismissFailure() }
        store.allowBlockedCompletedCount()
        sync.await()
        dismiss.await()

        assertEquals(null, synchronizer.syncState.value.failure)
    }
}
