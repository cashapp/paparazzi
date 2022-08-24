package app.cash.paparazzi.sample

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.widget.ConstraintLayout
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import app.cash.paparazzi.ShrinkDirection
import com.android.ide.common.rendering.api.SessionParams
import org.junit.Rule
import org.junit.Test

class ShrinkDirectionTest {

  @get:Rule
  val paparazzi = Paparazzi(
    deviceConfig = DeviceConfig.NEXUS_5.copy(softButtons = false),
    renderingMode = SessionParams.RenderingMode.SHRINK
  )

  @Test
  fun `shrinks xml layout to wrap_view`() {
    val launch = paparazzi.inflate<ConstraintLayout>(R.layout.display)
    paparazzi.snapshot(launch)
  }

  @Test
  fun `shrinks xml layout to width wrap_view`() {
    paparazzi.unsafeUpdateConfig(shrinkDirection = ShrinkDirection.HORIZONTAL)
    val launch = paparazzi.inflate<ConstraintLayout>(R.layout.display)
    paparazzi.snapshot(launch)
  }

  @Test
  fun `shrinks xml layout to height wrap_view`() {
    paparazzi.unsafeUpdateConfig(shrinkDirection = ShrinkDirection.VERTICAL)
    val launch = paparazzi.inflate<ConstraintLayout>(R.layout.display)
    paparazzi.snapshot(launch)
  }

  @Test
  fun `shrinks composable to wrap view`() {
    paparazzi.snapshot {
      Box(
        modifier = Modifier
          .wrapContentSize()
          .padding(8.dp)
          .border(2.dp, Color.White)
          .padding(8.dp)
      ) {
        Text(text = "Paparazzi rocks!")
      }
    }
  }

  @Test
  fun `shrinks composable to width wrap view`() {
    paparazzi.unsafeUpdateConfig(shrinkDirection = ShrinkDirection.HORIZONTAL)
    paparazzi.snapshot {
      Box(
        modifier = Modifier
          .wrapContentSize()
          .padding(8.dp)
          .border(2.dp, Color.White)
          .padding(8.dp)
      ) {
        Text(text = "Paparazzi rocks!")
      }
    }
  }

  @Test
  fun `shrinks composable to height wrap view`() {
    paparazzi.unsafeUpdateConfig(shrinkDirection = ShrinkDirection.VERTICAL)
    paparazzi.snapshot {
      Box(
        modifier = Modifier
          .wrapContentSize()
          .padding(8.dp)
          .border(2.dp, Color.White)
          .padding(8.dp)
      ) {
        Text(text = "Paparazzi rocks!")
      }
    }
  }
}
