package app.cash.paparazzi.sample

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import app.cash.paparazzi.Snapshot
import app.cash.paparazzi.SnapshotHandler
import app.cash.paparazzi.accessibility.AccessibilityRenderExtension
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import java.awt.image.BufferedImage

class ComposeDialogAccessibilityTest {
  private lateinit var renderedImage: BufferedImage

  @get:Rule
  val paparazzi = Paparazzi(
    deviceConfig = DeviceConfig.PIXEL_5.copy(softButtons = false),
    renderExtensions = setOf(AccessibilityRenderExtension()),
    snapshotHandler = object : SnapshotHandler {
      override fun newFrameHandler(snapshot: Snapshot, frameCount: Int, fps: Int) =
        object : SnapshotHandler.FrameHandler {
          override fun handle(image: BufferedImage) {
            renderedImage = image
          }

          override fun close() = Unit
        }

      override fun close() = Unit
    }
  )

  @Test
  fun dialogStaysWithinContentPane() {
    paparazzi.snapshot {
      AlertDialog(
        onDismissRequest = {},
        title = {
          Text(
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            text = "Title"
          )
        },
        text = {
          Text(
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            text = "Subtitle"
          )
        },
        buttons = {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .padding(16.dp)
          ) {
            Button(
              modifier = Modifier.fillMaxWidth(),
              onClick = {}
            ) {
              Text("Continue")
            }
          }
        }
      )
    }

    val detailsPaneX = renderedImage.width * 3 / 4
    val contentPaneX = renderedImage.width / 4
    assertNotEquals(
      "The dialog must render within the content pane",
      renderedImage.getRGB(contentPaneX, renderedImage.height * 3 / 4),
      renderedImage.getRGB(contentPaneX, renderedImage.height / 2)
    )
    assertEquals(
      "The dialog must not extend into the accessibility details pane",
      renderedImage.getRGB(detailsPaneX, renderedImage.height * 3 / 4),
      renderedImage.getRGB(detailsPaneX, renderedImage.height / 2)
    )
  }
}
