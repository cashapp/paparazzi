package app.cash.paparazzi.plugin.test

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.Paparazzi
import org.junit.Rule
import org.junit.Test
import kotlin.math.ceil
import kotlin.math.sin
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class ComposeTest {
  @get:Rule
  val paparazzi = Paparazzi(
    useDeviceResolution = true
  )

  @Test
  fun compose() {
    paparazzi.snapshot {
      SineGraph()
    }
  }

  @Composable
  fun SineGraph(
    modifier: Modifier = Modifier,
    animationProgress: State<Float> = infiniteAnimation(duration = 2.seconds)
  ) {
    val wave = SineWave(
      width = 4.dp,
      wavelength = 125.dp,
      amplitude = 12.dp,
      color = Color.Gray
    )
    Spacer(
      modifier
        .clipToBounds()
        .drawWithCache {
          val path = with(wave) {
            toPath(
              bounds = Rect(
                left = -wave.wavelength.toPx(),
                right = size.width,
                top = 0f,
                bottom = size.height
              )
            )
          }
          val pathStyle = Stroke(
            width = wave.width.toPx(),
            pathEffect = PathEffect.cornerPathEffect(radius = wave.wavelength.toPx()) // For smoother waves.
          )
          onDrawBehind {
            translate(left = animationProgress.value * wave.wavelength.toPx()) {
              drawPath(
                path = path,
                color = wave.color,
                style = pathStyle
              )
            }
          }
        }
    )
  }

  data class SineWave(
    val width: Dp,
    val wavelength: Dp,
    val amplitude: Dp,
    val color: Color
  ) {
    fun Density.toPath(bounds: Rect): Path {
      val waveStart = bounds.left
      val waveEnd = bounds.right

      val segmentsPerWavelength = 10
      val segmentWidth = wavelength.toPx() / segmentsPerWavelength
      val numOfPoints = ceil((waveEnd - waveStart) / segmentWidth).toInt() + 1

      return Path().also { path ->
        var pointX = waveStart
        (0..numOfPoints).map { point ->
          val proportionOfWavelength = (pointX - waveStart) / wavelength.toPx()
          val radiansX = proportionOfWavelength * (2f * Math.PI.toFloat())
          val offsetY = bounds.center.y + sin(radiansX) * amplitude.toPx()

          when (point) {
            0 -> path.moveTo(pointX, offsetY)
            else -> path.lineTo(pointX, offsetY)
          }
          pointX = (pointX + segmentWidth).coerceAtMost(waveEnd)
        }
      }
    }
  }

  @Composable
  private fun infiniteAnimation(duration: Duration): State<Float> {
    return rememberInfiniteTransition().animateFloat(
      initialValue = 0f,
      targetValue = 1f,
      animationSpec = infiniteRepeatable(
        animation = tween(duration.inWholeMilliseconds.toInt(), easing = LinearEasing),
        repeatMode = RepeatMode.Restart
      )
    )
  }
}
