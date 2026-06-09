package app.cash.paparazzi.plugin.test

import android.graphics.Insets
import androidx.compose.animation.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.ide.common.rendering.api.SessionParams.RenderingMode.SHRINK
import org.junit.Rule
import org.junit.Test
import kotlin.math.roundToInt
import android.view.WindowInsets as ViewWindowInsets

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
  fun syntheticWindowInsets() {
    paparazzi.unsafeUpdateConfig(deviceConfig = DeviceConfig.PIXEL_5)

    paparazzi.snapshot {
      SyntheticSystemBarInsets {
        Box(Modifier.fillMaxSize()) {
          InsetAwareScreen()
          StatusBar(modifier = Modifier.align(Alignment.TopCenter))
          NavigationBar(modifier = Modifier.align(Alignment.BottomCenter))
        }
      }
    }
  }

  @Test
  fun syntheticWindowInsetsWithPopupOverlay() {
    paparazzi.unsafeUpdateConfig(deviceConfig = DeviceConfig.PIXEL_5)

    paparazzi.snapshot {
      SyntheticSystemBarInsets {
        Box(Modifier.fillMaxSize()) {
          InsetAwareScreen()
        }
        // Both bars share one Popup: layoutlib drops the first window and NPEs at teardown when
        // a composition hosts two popup windows.
        Popup {
          Box(Modifier.fillMaxSize()) {
            StatusBar(modifier = Modifier.align(Alignment.TopCenter))
            NavigationBar(modifier = Modifier.align(Alignment.BottomCenter))
          }
        }
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

  /** Dispatches synthetic status and navigation bar insets to the root view. */
  @Composable
  private fun SyntheticSystemBarInsets(content: @Composable () -> Unit) {
    val hostView = LocalView.current
    val density = LocalDensity.current
    val statusBarHeightPx = density.run { STATUS_BAR_HEIGHT.roundToPx() }
    val navigationBarHeightPx = density.run { NAVIGATION_BAR_HEIGHT.roundToPx() }

    LaunchedEffect(statusBarHeightPx, navigationBarHeightPx) {
      val insets = ViewWindowInsets.Builder()
        .setInsets(ViewWindowInsets.Type.statusBars(), Insets.of(0, statusBarHeightPx, 0, 0))
        .setInsets(ViewWindowInsets.Type.navigationBars(), Insets.of(0, 0, 0, navigationBarHeightPx))
        .build()
      hostView.rootView.dispatchApplyWindowInsets(insets)
    }

    content()
  }

  @Composable
  private fun InsetAwareScreen() {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .background(Color.White)
    ) {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(64.dp)
          .background(Color(0xFF1C1C1E)),
        contentAlignment = Alignment.Center
      ) {
        Text("Title", color = Color.White)
      }
      Box(
        modifier = Modifier
          .fillMaxSize()
          .padding(WindowInsets.navigationBars.asPaddingValues())
          .background(Color(0xFFE7F3ED)),
        contentAlignment = Alignment.BottomCenter
      ) {
        Text("Bottom content must remain above nav", color = Color.Black)
      }
    }
  }

  @Composable
  private fun StatusBar(modifier: Modifier) {
    Box(
      modifier = modifier
        .fillMaxWidth()
        .height(STATUS_BAR_HEIGHT)
        .background(Color.Black)
    )
  }

  @Composable
  private fun NavigationBar(modifier: Modifier) {
    Row(
      modifier = modifier
        .fillMaxWidth()
        .height(NAVIGATION_BAR_HEIGHT)
        .background(Color.White),
      horizontalArrangement = Arrangement.SpaceEvenly,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Box(Modifier.size(16.dp).background(Color.Black))
      Box(Modifier.size(16.dp).background(Color.Black))
      Box(Modifier.size(16.dp).background(Color.Black))
    }
  }

  private companion object {
    val STATUS_BAR_HEIGHT = 62.dp
    val NAVIGATION_BAR_HEIGHT = 48.dp
  }
}
