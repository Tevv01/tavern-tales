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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.tevv.taverntales.AppContainer
import dev.tevv.taverntales.CrashReporting
import dev.tevv.taverntales.model.findScene
import dev.tevv.taverntales.ui.home.HomeScreen
import dev.tevv.taverntales.ui.info.BugReportScreen
import dev.tevv.taverntales.ui.info.CreditsScreen
import dev.tevv.taverntales.ui.info.License
import dev.tevv.taverntales.ui.info.LicenseScreen
import dev.tevv.taverntales.ui.info.PrivacyScreen
import dev.tevv.taverntales.ui.info.ReportDialog
import dev.tevv.taverntales.ui.menu.MenuScreen
import dev.tevv.taverntales.ui.home.HomeViewModel
import dev.tevv.taverntales.ui.hue.HueSetupScreen
import dev.tevv.taverntales.ui.hue.HueSetupViewModel
import dev.tevv.taverntales.ui.scene.SceneScreen
import dev.tevv.taverntales.ui.scene.SceneViewModel
import kotlinx.serialization.Serializable

@Serializable
private object MenuRoute

@Serializable
private object HomeRoute

@Serializable
private data class SceneRoute(val sceneId: String)

@Serializable
private object HueRoute

@Serializable
private object CreditsRoute

@Serializable
private object PrivacyRoute

@Serializable
private object BugReportRoute

@Serializable
private data class LicenseRoute(val license: String)

@Composable
fun TavernTalesNavHost(container: AppContainer, crashReporting: CrashReporting) {
    val navController = rememberNavController()
    // Shared so playback errors show up on whichever screen is visible.
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        container.mixer.errors.collect { snackbar.showSnackbar(it) }
    }
    LaunchedEffect(Unit) {
        container.hue.messages.collect { snackbar.showSnackbar(it) }
    }

    val crashReportsEnabled by crashReporting.enabled.collectAsStateWithLifecycle()
    if (crashReporting.available && crashReportsEnabled == null) {
        CrashReportingQuestion(onAnswer = crashReporting::setEnabled)
    }
    val pendingReports by crashReporting.pendingReports.collectAsStateWithLifecycle()
    pendingReports.firstOrNull()?.let { file ->
        ReportDialog(
            title = "Crash report sent",
            intro = "Tavern Tales closed unexpectedly last time. This report was sent to help fix it; it's " +
                "everything that was sent, nothing more.",
            report = remember(file) { crashReporting.readReport(file) },
            onDismiss = { crashReporting.dismissReport(file) },
        )
    }

    NavHost(navController, startDestination = MenuRoute) {
        composable<MenuRoute> {
            val mixer by container.mixer.state.collectAsStateWithLifecycle()
            val library by container.library.library.collectAsStateWithLifecycle()
            MenuScreen(
                nowPlaying = mixer.sceneId?.let { library.findScene(it) }?.takeIf { mixer.playing.isNotEmpty() },
                playingCount = mixer.playing.size,
                onScenes = { navController.navigate(HomeRoute) },
                onCredits = { navController.navigate(CreditsRoute) },
                onPrivacy = { navController.navigate(PrivacyRoute) },
                onReportBug = { navController.navigate(BugReportRoute) },
                onOpenNowPlaying = { navController.navigate(SceneRoute(it.id)) },
                onStop = container.mixer::stopAll,
            )
        }
        composable<CreditsRoute> {
            CreditsScreen(
                onBack = { navController.popBackStack() },
                onOpenLicense = { navController.navigate(LicenseRoute(it.name)) },
            )
        }
        composable<PrivacyRoute> {
            PrivacyScreen(
                onBack = { navController.popBackStack() },
                crashReportsEnabled = if (crashReporting.available) crashReportsEnabled == true else null,
                onCrashReportsChange = crashReporting::setEnabled,
            )
        }
        composable<BugReportRoute> {
            BugReportScreen(
                canSend = crashReporting.available,
                onBack = { navController.popBackStack() },
                readLog = crashReporting::recentLog,
                send = crashReporting::sendBugReport,
            )
        }
        composable<LicenseRoute> { entry ->
            LicenseScreen(License.valueOf(entry.toRoute<LicenseRoute>().license), onBack = { navController.popBackStack() })
        }
        composable<HomeRoute> {
            HomeScreen(
                viewModel = viewModel { HomeViewModel(container.library, container.mixer, container.launcher, container.backgrounds, container.backup, container.preferences) },
                snackbar = snackbar,
                onBack = { navController.popBackStack() },
                onOpenScene = { navController.navigate(SceneRoute(it)) },
                onOpenHueSetup = { navController.navigate(HueRoute) },
                onReportBug = { navController.navigate(BugReportRoute) },
                crashReportsEnabled = if (crashReporting.available) crashReportsEnabled == true else null,
                onCrashReportsChange = crashReporting::setEnabled,
            )
        }
        composable<SceneRoute> { entry ->
            val sceneId = entry.toRoute<SceneRoute>().sceneId
            SceneScreen(
                viewModel = viewModel { SceneViewModel(sceneId, container.library, container.mixer, container.launcher, container.hue, container.backgrounds) },
                snackbar = snackbar,
                onBack = { navController.popBackStack() },
                onOpenHueSetup = { navController.navigate(HueRoute) },
            )
        }
        composable<HueRoute> {
            HueSetupScreen(
                viewModel = viewModel { HueSetupViewModel(container.hue) },
                onBack = { navController.popBackStack() },
            )
        }
    }
}

/** Asked once, on first launch of a build that can report crashes. */
@Composable
private fun CrashReportingQuestion(onAnswer: (Boolean) -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text("Send crash reports?") },
        text = {
            Text(
                "If Tavern Tales crashes, it can send an anonymous report to help fix the problem: what went " +
                    "wrong in the app, your phone model and Android version. Your scenes, sounds and lights are " +
                    "never sent. You can change this any time in the ⋮ menu.",
            )
        },
        confirmButton = { TextButton(onClick = { onAnswer(true) }) { Text("Send reports") } },
        dismissButton = { TextButton(onClick = { onAnswer(false) }) { Text("No thanks") } },
    )
}
