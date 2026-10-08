package dev.tevv.taverntales.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * True when the window is wide enough for side-by-side panes: tablets, and phones in landscape.
 * Phones held upright are narrower than this.
 */
@Composable
fun isWideWindow(): Boolean = windowWidth() >= WIDE_WINDOW

/** True when the window is wider than it is tall. */
@Composable
fun isLandscapeWindow(): Boolean = LocalWindowInfo.current.containerSize.let { it.width > it.height }

@Composable
private fun windowWidth(): Dp = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp() }

private val WIDE_WINDOW = 700.dp

/** The widest that reading text and forms should get, so lines stay readable on tablets. */
val READABLE_WIDTH = 680.dp
