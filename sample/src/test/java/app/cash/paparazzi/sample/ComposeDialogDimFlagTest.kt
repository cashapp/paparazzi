package app.cash.paparazzi.sample

import android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogWindowProvider
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import app.cash.paparazzi.Snapshot
import app.cash.paparazzi.SnapshotHandler
import com.android.ide.common.rendering.api.SessionParams.RenderingMode.NORMAL
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.awt.image.BufferedImage

private val BackgroundColor = Color(0xFF2A7F62)
private val ExpectedBackgroundColor = java.awt.Color(0xFF2A7F62.toInt(), true)

// Confirms a dialog that clears FLAG_DIM_BEHIND leaves the content behind it visible.
class ComposeDialogDimFlagTest {
  @get:Rule
  val paparazzi = Paparazzi(
    deviceConfig = DeviceConfig.PIXEL_5.copy(softButtons = false),
    renderingMode = NORMAL,
    snapshotHandler = PixelAssertingSnapshotHandler { image ->
      val topLeftPixel = java.awt.Color(image.getRGB(8, 8), true)

      assertEquals(ExpectedBackgroundColor.red, topLeftPixel.red)
      assertEquals(ExpectedBackgroundColor.green, topLeftPixel.green)
      assertEquals(ExpectedBackgroundColor.blue, topLeftPixel.blue)
    }
  )

  @Test
  fun clearingFlagDimBehindRevealsContentBehindDialog() {
    paparazzi.snapshot {
      DialogWithoutDimRepro()
    }
  }
}

@Composable
private fun DialogWithoutDimRepro() {
  Box(Modifier.fillMaxSize().background(BackgroundColor)) {
    Dialog(onDismissRequest = {}) {
      val dialogWindow = (LocalView.current.parent as? DialogWindowProvider)?.window
      DisposableEffect(dialogWindow) {
        dialogWindow?.clearFlags(FLAG_DIM_BEHIND)
        onDispose {}
      }

      Box(Modifier.size(120.dp).background(Color.Red))
    }
  }
}

private class PixelAssertingSnapshotHandler(
  private val assertImage: (BufferedImage) -> Unit
) : SnapshotHandler {
  override fun newFrameHandler(snapshot: Snapshot, frameCount: Int, fps: Int): SnapshotHandler.FrameHandler {
    return object : SnapshotHandler.FrameHandler {
      override fun handle(image: BufferedImage) {
        assertImage(image)
      }

      override fun close() = Unit
    }
  }

  override fun close() = Unit
}
