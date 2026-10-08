package dev.tevv.taverntales.ui.info

import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

internal const val REPO_URL = "https://github.com/Tevv01/tavern-tales"

/** A licence text bundled in assets/licenses. */
enum class License(val title: String, val asset: String) {
    Apache("Apache License 2.0", "licenses/apache-2.0.txt"),
    MitSentry("MIT License (Sentry)", "licenses/mit-sentry.txt"),
    OflCinzel("SIL Open Font License 1.1 (Cinzel)", "licenses/ofl-cinzel.txt"),
}

private class Library(val name: String, val by: String, val license: License)

/** Direct dependencies; their own dependencies are AndroidX and Kotlin libraries under the same licences. */
private val LIBRARIES = listOf(
    Library("AndroidX, Jetpack Compose and Material 3", "Google", License.Apache),
    Library("Media3 / ExoPlayer", "Google", License.Apache),
    Library("Material icons", "Google", License.Apache),
    Library("Kotlin, kotlinx.coroutines and kotlinx.serialization", "JetBrains", License.Apache),
    Library("OkHttp", "Square", License.Apache),
    Library("Coil", "Coil contributors", License.Apache),
    Library("Sentry Android SDK", "Sentry (Functional Software, Inc.)", License.MitSentry),
)

@Serializable
private data class SoundCredit(val sound: String, val title: String, val author: String, val license: String, val source: String)

@Composable
fun CreditsScreen(onBack: () -> Unit, onOpenLicense: (License) -> Unit) {
    val context = LocalContext.current
    val sounds = remember {
        runCatching {
            Json.decodeFromString<List<SoundCredit>>(context.assets.open("credits/sounds.json").bufferedReader().readText())
        }.getOrDefault(emptyList())
    }
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_META_DATA).versionName }.getOrNull()
    }
    val open = { url: String -> context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }

    InfoScaffold("Credits & licences", onBack) {
        Section("Tavern Tales") {
            Text("Version ${version ?: "?"}, made by Tevv01.", style = MaterialTheme.typography.bodyMedium)
            LinkRow("Source code on GitHub", REPO_URL, Icons.AutoMirrored.Filled.OpenInNew) { open(REPO_URL) }
        }
        Section("Sounds") {
            Text(
                "Recordings from Freesound, released into the public domain (CC0) by their authors. Tap one to see the original.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            sounds.forEach { credit ->
                LinkRow(
                    title = displayName(credit.sound),
                    subtitle = "“${credit.title}” by ${credit.author} · ${credit.license}",
                    icon = if (credit.source.isNotEmpty()) Icons.AutoMirrored.Filled.OpenInNew else null,
                ) { if (credit.source.isNotEmpty()) open(credit.source) }
            }
        }
        Section("Artwork and font") {
            Text(
                "The scene pictures, title picture and app icon are drawn procedurally for Tavern Tales.",
                style = MaterialTheme.typography.bodyMedium,
            )
            LinkRow("Cinzel font", "The Cinzel Project Authors · ${License.OflCinzel.title}", Icons.Default.ChevronRight) {
                onOpenLicense(License.OflCinzel)
            }
        }
        Section("Open-source software") {
            LIBRARIES.forEach { library ->
                LinkRow(library.name, "${library.by} · ${library.license.title}", Icons.Default.ChevronRight) {
                    onOpenLicense(library.license)
                }
            }
        }
        Section("Trademarks") {
            Text(
                "Philips Hue is a trademark of Signify. Tavern Tales is not affiliated with or endorsed by Signify.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

/**
 * What Tavern Tales sends where, and the crash-report switch. Mirrors the README's Privacy section.
 * [crashReportsEnabled] is null in builds that can't report (debug): the switch is shown greyed out.
 */
@Composable
fun PrivacyScreen(onBack: () -> Unit, crashReportsEnabled: Boolean?, onCrashReportsChange: (Boolean) -> Unit) {
    val context = LocalContext.current
    InfoScaffold("Privacy", onBack) {
        Section("In short") {
            Text(
                "Tavern Tales has no account and no ads, and doesn't track you. Your scenes, sounds, pictures and " +
                    "settings stay on your phone, and in backup files you choose to make.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Section("What goes over the network") {
            Bullet("Your Hue Bridge, directly on your Wi-Fi. If the bridge can't be found there, the app asks Philips Hue's discovery service (discovery.meethue.com) for its local address, as the Hue app does.")
            Bullet("Crash reports, only if you agree (below).")
            Bullet("Bug reports, only when you write and send one yourself (below).")
        }
        Section("Crash reports") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Send crash reports", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Switch(
                    checked = crashReportsEnabled == true,
                    onCheckedChange = onCrashReportsChange,
                    enabled = crashReportsEnabled != null,
                )
            }
            if (crashReportsEnabled == null) {
                Text(
                    "This is a development (debug) build, which never sends crash reports. The switch works in the released app.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                "If the app crashes, a report goes to Sentry, stored in the EU. It contains:",
                style = MaterialTheme.typography.bodyMedium,
            )
            Bullet("the error and where in the app it happened, and the app version;")
            Bullet("the phone model and Android version, plus technical details such as memory, storage, battery level, screen size, network type, and your language and time-zone settings;")
            Bullet("a random ID created when the app was installed, which only lets crashes from the same install be counted together. It isn't linked to you.")
            Text(
                "No name, account, IP address, location, scenes, sounds, pictures or screenshots are included. After a " +
                    "report is sent, the app shows you the complete report, exactly as it was sent.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Section("Bug reports") {
            Text(
                "When you send a bug report from Report a bug, it goes to the same place as crash reports: your " +
                    "message, your email if you give one, the app's recent log if you choose to include it, and the " +
                    "same technical details as a crash report. The log holds technical messages from the app and from " +
                    "Android while it runs, such as connection or playback errors and system warnings; it doesn't " +
                    "contain your name, accounts or files. You review the report, every log line included, before " +
                    "it's sent and can see the complete report afterwards. Sending one doesn't switch on crash reports.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Section("Source code") {
            LinkRow("Tavern Tales on GitHub", REPO_URL, Icons.AutoMirrored.Filled.OpenInNew) {
                context.startActivity(Intent(Intent.ACTION_VIEW, REPO_URL.toUri()))
            }
        }
    }
}

@Composable
fun LicenseScreen(license: License, onBack: () -> Unit) {
    val context = LocalContext.current
    val text = remember(license) {
        runCatching { reflow(context.assets.open(license.asset).bufferedReader().readText()) }.getOrDefault("")
    }
    InfoScaffold(license.title, onBack) {
        Section(null) {
            SelectionContainer {
                Text(text, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun InfoScaffold(title: String, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Scaffold(
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onBackground,
        topBar = {
            TopAppBar(
                title = { Text(title, style = MaterialTheme.typography.headlineSmall) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            content = content,
        )
    }
}

@Composable
internal fun Section(title: String?, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.82f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            title?.let { Text(it, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary) }
            content()
        }
    }
}

@Composable
private fun LinkRow(title: String, subtitle: String?, icon: ImageVector?, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 4.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            subtitle?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        icon?.let { Icon(it, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 8.dp)) }
    }
}

@Composable
private fun Bullet(text: String) {
    Row {
        Text("•", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(end = 8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

/**
 * Licence files are hard-wrapped at about 80 characters, which a phone then wraps again. Joins the
 * lines of each paragraph so the text flows; blank lines stay paragraph breaks.
 */
internal fun reflow(text: String): String =
    text.replace("\r\n", "\n").split(Regex("\n\\s*\n"))
        .map { paragraph -> paragraph.lines().joinToString(" ") { it.trim() }.trim() }
        .filter { it.isNotEmpty() }
        .joinToString("\n\n")

/** "event_fire" -> "Fire (event)", "town_crowd" -> "Town crowd". */
private fun displayName(sound: String): String {
    val event = sound.startsWith("event_")
    val words = sound.removePrefix("event_").replace('_', ' ').replaceFirstChar { it.uppercase() }
    return if (event) "$words (event)" else words
}
