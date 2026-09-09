package com.allen.pokemon.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

interface NetworkMonitor {
    val isOnline: StateFlow<Boolean>

    /**
     * A real request failed although Android still considered the default network
     * validated. Ask the system to verify it again instead of waiting for a TCP
     * timeout or a later capability callback.
     */
    fun reportNetworkFailure() = Unit
}

/** Application-lifetime monitor that only reports networks validated for internet access. */
@Singleton
class AndroidNetworkMonitor @Inject constructor(
    @ApplicationContext context: Context,
) : NetworkMonitor {
    private val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
    private val _isOnline = MutableStateFlow(currentNetworkIsValidated())

    override val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    init {
        connectivityManager.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            // onCapabilitiesChanged follows onAvailable and is the ordered, current
            // source of truth for network capabilities.
            override fun onAvailable(network: Network) = Unit

            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                _isOnline.value = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            }

            override fun onLost(network: Network) {
                _isOnline.value = false
            }

            override fun onUnavailable() {
                _isOnline.value = false
            }
        })
    }

    override fun reportNetworkFailure() {
        synchronized(this) {
            // Several concurrent Retrofit calls can fail from the same outage. Only
            // the first one should transition the app to offline and trigger a
            // system revalidation; repeated reports otherwise flap the UI.
            if (!_isOnline.value) return
            _isOnline.value = false
            connectivityManager.reportNetworkConnectivity(connectivityManager.activeNetwork, false)
        }
    }

    private fun currentNetworkIsValidated(): Boolean =
        connectivityManager.activeNetwork?.let(connectivityManager::getNetworkCapabilities)
            ?.let { capabilities ->
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            } == true
}

/** Keeps unit-test callers independent from Android connectivity services. */
object AlwaysOnlineNetworkMonitor : NetworkMonitor {
    override val isOnline: StateFlow<Boolean> = MutableStateFlow(true)
}
