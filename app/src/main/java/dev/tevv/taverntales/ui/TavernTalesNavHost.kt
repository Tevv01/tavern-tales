package dev.tevv.taverntales.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import dev.tevv.taverntales.AppContainer
import dev.tevv.taverntales.ui.home.HomeScreen
import dev.tevv.taverntales.ui.home.HomeViewModel
import dev.tevv.taverntales.ui.scene.SceneScreen
import dev.tevv.taverntales.ui.scene.SceneViewModel
import kotlinx.serialization.Serializable

@Serializable
private object HomeRoute

@Serializable
private data class SceneRoute(val sceneId: String)

@Composable
fun TavernTalesNavHost(container: AppContainer) {
    val navController = rememberNavController()
    // Shared so playback errors show up on whichever screen is visible.
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        container.mixer.errors.collect { snackbar.showSnackbar(it) }
    }

    NavHost(navController, startDestination = HomeRoute) {
        composable<HomeRoute> {
            HomeScreen(
                viewModel = viewModel { HomeViewModel(container.library, container.mixer, container.backgrounds) },
                snackbar = snackbar,
                onOpenScene = { navController.navigate(SceneRoute(it)) },
            )
        }
        composable<SceneRoute> { entry ->
            val sceneId = entry.toRoute<SceneRoute>().sceneId
            SceneScreen(
                viewModel = viewModel { SceneViewModel(sceneId, container.library, container.mixer, container.backgrounds) },
                snackbar = snackbar,
                onBack = { navController.popBackStack(HomeRoute, inclusive = false) },
            )
        }
    }
}
