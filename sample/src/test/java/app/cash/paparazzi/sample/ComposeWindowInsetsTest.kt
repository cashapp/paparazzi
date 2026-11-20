package app.cash.paparazzi.sample

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.ide.common.rendering.api.SessionParams.RenderingMode.NORMAL
import com.android.resources.ScreenOrientation.LANDSCAPE
import org.junit.Rule
import org.junit.Test

/** Snapshot regression for https://github.com/cashapp/paparazzi/issues/2501. */
class ComposeWindowInsetsTest {
  @get:Rule
  val paparazzi = Paparazzi(
    deviceConfig = DeviceConfig.PIXEL_9_PRO_XL,
    theme = "android:Theme.Material.Light.NoActionBar",
    renderingMode = NORMAL,
    showSystemUi = true,
    useDeviceResolution = true
  )

  @Test
  fun safeDrawingWithSystemUiOnRepeatedSnapshots() {
    paparazzi.snapshot(name = "first") { InsetsContent() }
    paparazzi.snapshot(name = "second") { InsetsContent() }
  }

  @Test
  fun safeDrawingWithLandscapeSystemUi() {
    paparazzi.unsafeUpdateConfig(deviceConfig = DeviceConfig.PIXEL_9_PRO_XL.copy(orientation = LANDSCAPE))
    paparazzi.snapshot { InsetsContent() }
  }

  @Test
  fun safeDrawingWithDarkSystemUi() {
    paparazzi.unsafeUpdateConfig(theme = "android:Theme.Material.NoActionBar")
    paparazzi.snapshot { InsetsContent() }
  }

  @Test
  fun safeDrawingWithTranslucentSystemUi() {
    paparazzi.unsafeUpdateConfig(theme = "android:Theme.Material.Light.NoActionBar.TranslucentDecor")
    paparazzi.snapshot { InsetsContent() }
  }
}

class ComposeWindowInsetsWithoutSystemUiTest {
  @get:Rule
  val paparazzi = Paparazzi(
    deviceConfig = DeviceConfig.PIXEL_9_PRO_XL,
    theme = "android:Theme.Material.Light.NoActionBar",
    showSystemUi = false,
    useDeviceResolution = true
  )

  @Test
  fun safeDrawingWithoutSystemUi() {
    paparazzi.snapshot { InsetsContent() }
  }
}

@Composable
private fun InsetsContent() {
  Box(Modifier.fillMaxSize().background(Color.Red)) {
    Text(
      "UNSAFE TOP INSET",
      modifier = Modifier.align(Alignment.TopCenter).windowInsetsTopHeight(WindowInsets.safeDrawing),
      color = Color.White
    )
    Box(Modifier.safeDrawingPadding().fillMaxSize()) {
      Box(
        Modifier.fillMaxSize().background(Color.Green).border(2.dp, Color.Blue),
        contentAlignment = Alignment.Center
      ) {
        Text("Safe drawing area")
      }
    }
  }
}
