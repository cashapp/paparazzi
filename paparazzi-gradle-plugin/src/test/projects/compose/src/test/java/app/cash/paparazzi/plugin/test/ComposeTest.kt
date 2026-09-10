package app.cash.paparazzi.plugin.test

import androidx.compose.animation.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.Paparazzi
import com.android.ide.common.rendering.api.SessionParams.RenderingMode.SHRINK
import org.junit.Rule
import org.junit.Test
import kotlin.math.roundToInt

class ComposeTest {
  @get:Rule
  val paparazzi = Paparazzi()

  @Test
  fun compose() {
    paparazzi.snapshot {
      HelloPaparazzi()
    }
  }

  @Test
  fun gif() {
    paparazzi.gif(end = 1000L) {
      val color = remember { Animatable(Color.Cyan) }
      LaunchedEffect(Unit) {
        color.animateTo(Color.Magenta, animationSpec = tween(500, easing = LinearEasing))
        color.animateTo(Color.Cyan, animationSpec = tween(500, easing = LinearEasing))
      }

      Box(
        Modifier
          .fillMaxSize()
          .background(Color.White)
      ) {
        Box(
          Modifier
            .align(Alignment.Center)
            .size(120.dp)
            .background(color.value, CircleShape)
        )
      }
    }
  }

  @Test
  fun composeDefaultLayoutParams() {
    paparazzi.snapshot {
      Box(
        modifier = Modifier
          .background(Color.Cyan)
          .fillMaxWidth(),
        contentAlignment = Alignment.Center
      ) {
        Text("Hello")
      }
    }
  }

  @Test
  fun anchoredDraggableAnchorsFromSize() {
    paparazzi.unsafeUpdateConfig(renderingMode = SHRINK)

    paparazzi.snapshot {
      val state = remember { AnchoredDraggableState(initialValue = 1) }

      Box(
        modifier = Modifier
          .size(width = 320.dp, height = 64.dp)
          .background(Color.LightGray)
          .onSizeChanged { size ->
            state.updateAnchors(
              DraggableAnchors {
                0 at 0f
                1 at size.width / 2f
              }
            )
          }
          .anchoredDraggable(
            state = state,
            orientation = Orientation.Horizontal,
            enabled = false
          )
      ) {
        Box(
          modifier = Modifier
            .offset {
              IntOffset(
                x = if (state.offset.isNaN()) 0 else state.offset.roundToInt(),
                y = 0
              )
            }
            .size(width = 160.dp, height = 64.dp)
            .background(Color(0xFF2C6BED))
        )
        Text("left", Modifier.align(Alignment.CenterStart).padding(start = 56.dp))
        Text("right", Modifier.align(Alignment.CenterEnd).padding(end = 56.dp))
      }
    }
  }
}
