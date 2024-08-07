package app.cash.paparazzi.plugin.test

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.ide.common.rendering.api.SessionParams.RenderingMode
import org.junit.Rule
import org.junit.Test

class ImageHashTest {

  @get:Rule
  val paparazzi = Paparazzi(
    deviceConfig = DeviceConfig.NEXUS_5,
    renderingMode = RenderingMode.SHRINK
  )

  @Test
  fun verticalLineComponentTest() {
    paparazzi.snapshot {
      VerticalLineComponent()
    }
  }

  @Test
  fun horizontalLineComponentTest() {
    paparazzi.snapshot {
      HorizontalLineComponent()
    }
  }

  @Test
  fun squareComponentTest() {
    paparazzi.snapshot {
      SquareComponent()
    }
  }
}

@Composable
fun VerticalLineComponent() {
  Spacer(
    modifier = Modifier
      .width(1.dp)
      .height(4.dp)
      .background(Color.Red)
  )
}

@Composable
fun HorizontalLineComponent() {
  Spacer(
    modifier = Modifier
      .width(4.dp)
      .height(1.dp)
      .background(Color.Red)
  )
}

@Composable
fun SquareComponent() {
  Spacer(
    modifier = Modifier
      .width(2.dp)
      .height(2.dp)
      .background(Color.Red)
  )
}
