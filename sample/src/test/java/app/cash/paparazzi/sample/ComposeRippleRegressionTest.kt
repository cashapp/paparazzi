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
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.Density
import org.junit.Test
import org.junit.runner.Description
import org.junit.runners.model.Statement
import kotlinx.coroutines.delay

class ComposeRippleRegressionTest {
  @Test
  fun rippleStartsAfterAnEarlierGif() {
    // https://github.com/cashapp/paparazzi/issues/2516: an earlier GIF must not delay a ripple
    // beyond the end of the next GIF, even when each GIF has its own render session.
    recordGif(name = "earlier", end = 2_000L) { Box(Modifier.size(100.dp)) }

    recordGif(name = "ripple", end = 1_500L) {
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
  }

  private fun recordGif(name: String, end: Long, content: @Composable () -> Unit) {
    val paparazzi = Paparazzi(
      deviceConfig = DeviceConfig(
        screenWidth = 100,
        screenHeight = 100,
        xdpi = 160,
        ydpi = 160,
        density = Density.MEDIUM,
        softButtons = false
      ),
      // Ripple sparkle rasterization differs slightly across platforms (<0.04% in CI).
      // A missing ripple still differs by over 11% at its peak.
      maxPercentDifference = 0.1,
      useDeviceResolution = true
    )
    // Exercise separate test lifecycles in the same JVM without depending on method order.
    paparazzi.apply(
      object : Statement() {
        override fun evaluate() {
          paparazzi.gif(name = name, end = end, composable = content)
        }
      },
      Description.createTestDescription(javaClass, "rippleStartsAfterAnEarlierGif")
    ).evaluate()
  }
}
