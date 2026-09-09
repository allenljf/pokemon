package com.allen.pokemon.ui.navigation

import android.graphics.Rect
import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.allen.pokemon.feature.collection.CollectionScreen
import com.allen.pokemon.feature.detail.DetailScreen

sealed class NavigationRoute(val route: String) {
    object Collection : NavigationRoute("collection")
    object Detail : NavigationRoute("detail/{pokemonId}") {
        fun createRoute(pokemonId: Int) = "detail/$pokemonId"
    }
}

@Composable
fun PokemonNavHost(
    navController: NavHostController = rememberNavController(),
    startDestination: String = NavigationRoute.Collection.route,
    hingeBounds: Rect? = null,
    collectionContent: (@Composable ((Int) -> Unit) -> Unit)? = null,
    detailContent: (@Composable (() -> Unit) -> Unit)? = null,
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
    ) {
        composable(NavigationRoute.Collection.route) {
            var lastNavigationAt by remember { mutableLongStateOf(0L) }
            val openDetail = { pokemonId: Int ->
                val now = SystemClock.elapsedRealtime()
                val isCollectionVisible =
                    navController.currentDestination?.route == NavigationRoute.Collection.route
                if (isCollectionVisible && now - lastNavigationAt >= DETAIL_NAVIGATION_THROTTLE_MS) {
                    lastNavigationAt = now
                    navController.navigate(NavigationRoute.Detail.createRoute(pokemonId)) {
                        launchSingleTop = true
                    }
                }
            }
            collectionContent?.invoke(openDetail) ?: CollectionScreen(
                onNavigateToDetail = openDetail,
                hingeBounds = hingeBounds,
            )
        }

        composable(
            NavigationRoute.Detail.route,
            arguments = listOf(
                navArgument("pokemonId") {
                    type = NavType.IntType
                }
            ),
        ) {
            val navigateBack: () -> Unit = { navController.popBackStack(); Unit }
            var lastPokemonNavigationAt by remember { mutableLongStateOf(0L) }
            val openPokemon: (Int) -> Unit = { pokemonId ->
                val now = SystemClock.elapsedRealtime()
                if (now - lastPokemonNavigationAt >= DETAIL_NAVIGATION_THROTTLE_MS) {
                    lastPokemonNavigationAt = now
                    navController.navigate(NavigationRoute.Detail.createRoute(pokemonId)) {
                        launchSingleTop = true
                    }
                }
            }
            detailContent?.invoke(navigateBack) ?: DetailScreen(
                onBackClick = navigateBack,
                onNavigateToPokemon = openPokemon,
                hingeBounds = hingeBounds,
            )
        }
    }
}

private const val DETAIL_NAVIGATION_THROTTLE_MS = 500L
