package com.allen.pokemon.data.sync

import java.io.IOException
import java.net.UnknownHostException
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

class SyncFailureTest {
    @Test
    fun `maps DNS failures to offline`() {
        assertEquals(
            SyncFailure.Offline,
            SyncFailure.from(UnknownHostException("pokeapi.co")),
        )
    }

    @Test
    fun `maps transport failures to offline`() {
        assertEquals(
            SyncFailure.Offline,
            SyncFailure.from(IOException("timeout")),
        )
    }

    @Test
    fun `maps HTTP failures to a readable API error`() {
        assertEquals(
            SyncFailure.Api("HTTP 503"),
            SyncFailure.from(HttpException(Response.error<Any>(503, "".toResponseBody()))),
        )
    }

    @Test
    fun `maps unexpected throwables to a readable API error`() {
        assertEquals(
            SyncFailure.Api("boom"),
            SyncFailure.from(IllegalStateException("boom")),
        )
    }
}
