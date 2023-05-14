package app.cash.paparazzi.sample

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.ide.common.rendering.api.SessionParams
import org.junit.Rule
import org.junit.Test

class Issue769Test {
  @get:Rule
  val paparazzi = Paparazzi(
    maxPercentDifference = 1.0,
    deviceConfig = DeviceConfig.PIXEL_5.copy(softButtons = false),
    renderingMode = SessionParams.RenderingMode.SHRINK
  )

  @Test
  fun test() {
    paparazzi.snapshot {
      ComposeDialog(
        title = "Title",
        subtitle = "Subtitle"
      )
    }
  }
}
