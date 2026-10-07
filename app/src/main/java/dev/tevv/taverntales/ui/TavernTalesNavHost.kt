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
import dev.tevv.taverntales.ui.scene.SceneScreen
import dev.tevv.taverntales.ui.scene.SceneViewModel
import dev.tevv.taverntales.ui.scenes.ScenesScreen
import dev.tevv.taverntales.ui.scenes.ScenesViewModel
import kotlinx.serialization.Serializable

@Serializable
private object ScenesRoute

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

    NavHost(navController, startDestination = ScenesRoute) {
        composable<ScenesRoute> {
            ScenesScreen(
                viewModel = viewModel { ScenesViewModel(container.scenes, container.mixer) },
                snackbar = snackbar,
                onOpenScene = { navController.navigate(SceneRoute(it)) },
            )
        }
        composable<SceneRoute> { entry ->
            val sceneId = entry.toRoute<SceneRoute>().sceneId
            SceneScreen(
                viewModel = viewModel { SceneViewModel(sceneId, container.scenes, container.mixer) },
                snackbar = snackbar,
                onBack = { navController.popBackStack(ScenesRoute, inclusive = false) },
            )
        }
    }
}
