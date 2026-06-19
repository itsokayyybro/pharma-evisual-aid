package com.pharma.evisualaid.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.pharma.evisualaid.ui.home.HomeScreen
import com.pharma.evisualaid.ui.presentation.PresentationScreen
import com.pharma.evisualaid.ui.settings.SettingsScreen

object Routes {
    const val HOME = "home"
    const val SETTINGS = "settings"
    const val PRESENTATION = "presentation"
    const val ARG_CATALOG_ID = "catalogId"
    fun presentation(catalogId: String) = "$PRESENTATION/$catalogId"
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Routes.HOME,
        enterTransition = { fadeIn(tween(220)) },
        exitTransition = { fadeOut(tween(180)) }
    ) {
        composable(Routes.HOME) {
            HomeScreen(
                onOpenCatalog = { id -> navController.navigate(Routes.presentation(id)) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) }
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }

        composable(
            route = "${Routes.PRESENTATION}/{${Routes.ARG_CATALOG_ID}}",
            arguments = listOf(navArgument(Routes.ARG_CATALOG_ID) { type = NavType.StringType })
        ) { backStackEntry ->
            val catalogId = backStackEntry.arguments?.getString(Routes.ARG_CATALOG_ID).orEmpty()
            PresentationScreen(
                catalogId = catalogId,
                onExit = { navController.popBackStack() }
            )
        }
    }
}
