package dev.tevv.taverntales.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.tevv.taverntales.model.LightSetup
import dev.tevv.taverntales.model.LightSlot
import dev.tevv.taverntales.model.SoundEvent
import dev.tevv.taverntales.model.SoundLayer
import dev.tevv.taverntales.ui.components.ChoiceDialog
import dev.tevv.taverntales.ui.components.ColorChoice
import dev.tevv.taverntales.ui.info.BugReportScreen
import dev.tevv.taverntales.ui.scene.EventPad
import dev.tevv.taverntales.ui.scene.LayerCard
import dev.tevv.taverntales.ui.scene.LightsSheet
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * What screen readers (TalkBack) are told about the controls: names, roles, states and action
 * labels. Checks Compose's semantics directly, which is what TalkBack reads.
 */
@RunWith(AndroidJUnit4::class)
class AccessibilityTest {
    @get:Rule
    val compose = createComposeRule()

    private fun stateIs(value: String) = SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, value)
    private fun roleIs(role: Role) = SemanticsMatcher.expectValue(SemanticsProperties.Role, role)
    private fun clickLabelIs(label: String) =
        SemanticsMatcher("click label is $label") { it.config.getOrNull(SemanticsActions.OnClick)?.label == label }
    private val isSlider = SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)

    @Test
    fun layerVolumeSliderSaysWhichSoundItControls() {
        compose.setContent {
            LayerCard(SoundLayer("l", "Rain", "asset:///x.ogg", loop = true, autoPlay = false), false, {}, {}, {}, {}, {}, {})
        }
        compose.onNodeWithContentDescription("Volume of Rain").assert(isSlider)
        compose.onNodeWithContentDescription("More options").performClick()
        // The card also shows "Loop" as a tag; the menu item is the clickable one.
        compose.onNode(hasText("Loop") and hasClickAction()).assert(stateIs("On"))
        compose.onNode(hasText("Start with scene") and hasClickAction()).assert(stateIs("Off"))
    }

    @Test
    fun eventPadIsAPlayButtonThatSaysWhenItsPlaying() {
        compose.setContent {
            EventPad(SoundEvent("e", "Thunder", "asset:///x.ogg"), isPlaying = true, onPlay = {}, onEdit = {})
        }
        compose.onNodeWithText("Thunder")
            .assert(roleIs(Role.Button))
            .assert(clickLabelIs("Play"))
            .assert(stateIs("Playing"))
            .assert(SemanticsMatcher("long press edits") { it.config.getOrNull(SemanticsActions.OnLongClick)?.label == "Edit Thunder" })
    }

    @Test
    fun colourChoicesAreNamedSelectableAndBigEnoughToTap() {
        compose.setContent {
            Row(Modifier.selectableGroup()) {
                ColorChoice(Color.Yellow, "Gold", selected = true, onClick = {}, size = 32.dp)
                ColorChoice(Color.Blue, "Azure", selected = false, onClick = {}, size = 32.dp)
            }
        }
        compose.onNodeWithContentDescription("Gold").assertIsSelected().assert(roleIs(Role.RadioButton))
            .assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
        compose.onNodeWithContentDescription("Azure").assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, false))
    }

    @Test
    fun choiceDialogReadsEachOptionOnceAsARadioButton() {
        compose.setContent {
            ChoiceDialog("Move to collection", listOf("Essentials", "Nights"), { it }, selected = "Essentials", onPick = {}, onDismiss = {})
        }
        compose.onAllNodesWithText("Essentials").assertCountEquals(1)
        compose.onNodeWithText("Essentials").assertIsSelected().assert(roleIs(Role.RadioButton))
    }

    @Test
    fun includeLogIsOneCheckbox() {
        compose.setContent { BugReportScreen(canSend = true, onBack = {}, readLog = { "" }, send = { _, _, _ -> "" }) }
        val row = compose.onNode(hasText("Include the app's log") and SemanticsMatcher.keyIsDefined(SemanticsProperties.ToggleableState))
        row.assert(roleIs(Role.Checkbox)).assert(SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.Off))
        row.performClick()
        row.assert(SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.On))
    }

    @Test
    fun lightEditorControlsAreLabelled() {
        compose.setContent {
            LightsSheet(
                lighting = LightSetup(slots = listOf(LightSlot("#FFC27A", effect = "candle"), LightSlot("#2B3A8C")), motion = 0.5f),
                lights = null,
                room = "Living room",
                hueScenes = null,
                onLoadHueScenes = {},
                onSetLighting = {},
                onLinkHueScene = {},
                onChooseRoom = {},
                onDismiss = {},
            )
        }
        compose.onNodeWithContentDescription("Colour 1, Candle effect").assert(clickLabelIs("Change"))
        compose.onNodeWithContentDescription("Colour 2").assert(clickLabelIs("Change"))
        compose.onNodeWithContentDescription("Brightness").assert(isSlider)
        compose.onNode(hasContentDescription("Movement") and isSlider).assert(stateIs("gentle"))
    }
}
