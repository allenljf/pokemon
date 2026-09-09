package com.allen.pokemon.ui.navigation

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import org.junit.Rule
import org.junit.Test

class PokemonNavigationTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun navigatingToPreEvolutionCreatesDetailHistoryForBackNavigation() {
        lateinit var navController: NavHostController

        composeRule.setContent {
            navController = rememberNavController()
            TestDetailNavHost(navController)
        }

        composeRule.onNodeWithText("Open #5").performClick()
        composeRule.onNodeWithText("Viewing #5").assertExists()

        composeRule.runOnIdle { navController.popBackStack() }

        composeRule.onNodeWithText("Viewing #6").assertExists()
    }
}

@Composable
private fun TestDetailNavHost(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = NavigationRoute.Detail.createRoute(6),
    ) {
        composable(
            route = NavigationRoute.Detail.route,
            arguments = listOf(
                navArgument("pokemonId") {
                    type = NavType.IntType
                },
            ),
        ) { entry ->
            val pokemonId = entry.requirePokemonId()
            Text("Viewing #$pokemonId")
            Button(onClick = { navController.navigateToPokemonDetail(5) }) {
                Text("Open #5")
            }
        }
    }
}

private fun NavBackStackEntry.requirePokemonId(): Int =
    requireNotNull(arguments).getInt("pokemonId")
