package com.allen.pokemon.data.sync

import java.io.IOException
import java.net.UnknownHostException
import retrofit2.HttpException

sealed interface SyncFailure {
    data object Offline : SyncFailure

    data class Api(val message: String) : SyncFailure

    companion object {
        fun from(throwable: Throwable): SyncFailure = when (throwable) {
            is UnknownHostException,
            is IOException,
            -> Offline
            is HttpException -> Api("HTTP ${throwable.code()}")
            else -> Api(throwable.message ?: "Unexpected API error")
        }
    }
}
