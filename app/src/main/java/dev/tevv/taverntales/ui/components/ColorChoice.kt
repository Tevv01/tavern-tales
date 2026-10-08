package dev.tevv.taverntales.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * One colour in a picker: a [size] circle inside a 48dp touch target, read out by screen readers as
 * [name] with its selected state. Put choices in a container with `Modifier.selectableGroup()`.
 */
@Composable
fun ColorChoice(color: Color, name: String, selected: Boolean, onClick: () -> Unit, size: Dp = 40.dp) {
    Box(
        Modifier
            .size(48.dp)
            .clip(CircleShape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = name },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(size)
                .clip(CircleShape)
                .background(color)
                .border(
                    if (selected) 3.dp else 1.dp,
                    if (selected) Color.White else Color.White.copy(alpha = 0.25f),
                    CircleShape,
                ),
        )
    }
}
