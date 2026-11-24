package app.cash.paparazzi.plugin.test

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import app.cash.paparazzi.accessibility.AccessibilityRenderExtension
import org.junit.Rule
import org.junit.Test

/**
 * A dialog is registered before its Compose root is attached. Accessibility collection from the
 * base window must wait for that root, then include its labels in the captured overlay and legend.
 */
class AccessibilityDialogTest {
  @get:Rule
  val paparazzi = Paparazzi(
    deviceConfig = DeviceConfig.PIXEL_5,
    renderExtensions = setOf(AccessibilityRenderExtension())
  )

  @Test
  fun unsavedChangesDialog() {
    paparazzi.snapshot {
      BasicText("Profile form")
      Dialog(onDismissRequest = {}) {
        Column(Modifier.width(280.dp).background(Color.White).padding(16.dp)) {
          BasicText("Unsaved changes")
          BasicText("Discard your edits?")
          BasicText("Discard", Modifier.clickable(role = Role.Button) {})
          BasicText("Cancel", Modifier.clickable(role = Role.Button) {})
        }
      }
    }
  }

  @Test
  fun fullscreenDialog() {
    paparazzi.snapshot {
      Dialog(
        onDismissRequest = {},
        properties = DialogProperties(usePlatformDefaultWidth = false)
      ) {
        Box(Modifier.fillMaxSize().background(Color.White)) {
          BasicText(
            "Close",
            Modifier.align(Alignment.TopEnd).padding(16.dp).clickable(role = Role.Button) {}
          )
        }
      }
    }
  }
}
