package dev.tevv.taverntales.ui.menu

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.TheaterComedy
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.tevv.taverntales.R
import dev.tevv.taverntales.model.Scene

/** The title screen the app opens on: the way into the scenes, plus credits, privacy and bug reports. */
@Composable
fun MenuScreen(
    nowPlaying: Scene?,
    playingCount: Int,
    onScenes: () -> Unit,
    onCredits: () -> Unit,
    onPrivacy: () -> Unit,
    onReportBug: () -> Unit,
    onOpenNowPlaying: (Scene) -> Unit,
    onStop: () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(R.drawable.bg_title),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        // Darkens the sky a little behind the title and the street behind the buttons.
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = 0.35f),
                    0.28f to Color.Transparent,
                    0.6f to Color.Transparent,
                    1f to Color.Black.copy(alpha = 0.88f),
                ),
            ),
        )
        Column(
            Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(48.dp))
            Text(
                "Tavern Tales",
                style = MaterialTheme.typography.displayMedium.copy(
                    shadow = Shadow(color = Color(0xFFFF9A3A).copy(alpha = 0.55f), blurRadius = 28f),
                ),
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                "Ambience, effects and lights for your table",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )
            Spacer(Modifier.weight(1f))

            if (nowPlaying != null) {
                NowPlaying(nowPlaying, playingCount, onOpen = { onOpenNowPlaying(nowPlaying) }, onStop = onStop)
                Spacer(Modifier.height(16.dp))
            }
            Button(
                onClick = onScenes,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth().height(64.dp),
            ) {
                Icon(Icons.Default.TheaterComedy, contentDescription = null, modifier = Modifier.size(26.dp))
                Text("Scenes", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 12.dp))
            }
            Spacer(Modifier.height(12.dp))
            // Equal heights, also when large text wraps one label onto two lines.
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max)) {
                MenuTile(Icons.AutoMirrored.Filled.MenuBook, "Credits", onCredits, Modifier.weight(1f).fillMaxHeight())
                MenuTile(Icons.Default.PrivacyTip, "Privacy", onPrivacy, Modifier.weight(1f).fillMaxHeight())
                MenuTile(Icons.Default.BugReport, "Report a bug", onReportBug, Modifier.weight(1f).fillMaxHeight())
            }
        }
    }
}

/** A smaller menu button: an icon over a short label. */
@Composable
private fun MenuTile(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.Black.copy(alpha = 0.35f), contentColor = Color.White),
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 12.dp),
        modifier = modifier,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
            // Two lines, so "Report a bug" still fits with large text.
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

@Composable
private fun NowPlaying(scene: Scene, playingCount: Int, onOpen: () -> Unit, onStop: () -> Unit) {
    Surface(
        onClick = onOpen,
        shape = RoundedCornerShape(16.dp),
        color = Color.Black.copy(alpha = 0.55f),
        contentColor = Color.White,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp)) {
            Icon(Icons.Default.GraphicEq, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text("Now playing", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.7f))
                Text(
                    "${scene.name} · " + if (playingCount == 1) "1 sound" else "$playingCount sounds",
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            FilledTonalIconButton(onClick = onStop) { Icon(Icons.Default.Stop, contentDescription = "Stop") }
        }
    }
}
