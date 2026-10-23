package app.cash.paparazzi.sample

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import app.cash.paparazzi.Snapshot
import app.cash.paparazzi.SnapshotHandler
import com.android.resources.Density
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.Description
import org.junit.runners.model.Statement
import java.awt.image.BufferedImage
import kotlinx.coroutines.delay

class ComposeRippleRegressionTest {
  @Test
  fun rippleStartsAfterAnEarlierGif() {
    // https://github.com/cashapp/paparazzi/issues/2516: an earlier GIF must not delay a ripple
    // beyond the end of the next GIF, even when each GIF has its own render session.
    captureGif(end = 2_000L) { Box(Modifier.size(100.dp)) }

    val colors = captureGif(end = 1_500L) {
      val interactions = remember { MutableInteractionSource() }
      Box(
        Modifier
          .size(100.dp)
          .background(Color.Black)
          .clickable(interactions, indication = ripple(color = Color.White), onClick = {})
      )
      LaunchedEffect(Unit) {
        delay(1_000L)
        val press = PressInteraction.Press(Offset(50f, 50f))
        interactions.emit(press)
        delay(50L)
        interactions.emit(PressInteraction.Release(press))
      }
    }

    assertEquals(46, colors.size)
    assertTrue("The box must be black before the press", colors.take(30).all { it == Color.Black.toArgb() })
    // At 30 fps these frames cover 1.1-1.3 seconds, shortly after the press. Only the ripple
    // changes the box's pixels; no text or other state change can satisfy this assertion.
    assertTrue(
      "The ripple must start on the new GIF's timeline",
      colors.slice(33..39).any {
        it != Color.Black.toArgb()
      }
    )
  }

  private fun captureGif(end: Long, content: @Composable () -> Unit): List<Int> {
    val colors = mutableListOf<Int>()
    val handler = object : SnapshotHandler {
      override fun newFrameHandler(snapshot: Snapshot, frameCount: Int, fps: Int): SnapshotHandler.FrameHandler =
        object : SnapshotHandler.FrameHandler {
          override fun handle(image: BufferedImage) {
            colors += image.getRGB(image.width / 2, image.height / 2)
          }

          override fun close() = Unit
        }

      override fun close() = Unit
    }
    val paparazzi = Paparazzi(
      deviceConfig = DeviceConfig(
        screenWidth = 100,
        screenHeight = 100,
        xdpi = 160,
        ydpi = 160,
        density = Density.MEDIUM,
        softButtons = false
      ),
      snapshotHandler = handler,
      useDeviceResolution = true
    )
    // Exercise separate test lifecycles in the same JVM without depending on method order.
    paparazzi.apply(
      object : Statement() {
        override fun evaluate() {
          paparazzi.gif(end = end, composable = content)
        }
      },
      Description.createTestDescription(javaClass, "rippleStartsAfterAnEarlierGif")
    ).evaluate()
    return colors
  }
}
