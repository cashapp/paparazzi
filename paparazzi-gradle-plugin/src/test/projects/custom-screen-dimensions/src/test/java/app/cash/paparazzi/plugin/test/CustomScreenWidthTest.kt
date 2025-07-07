package app.cash.paparazzi.plugin.test

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import org.junit.Rule
import org.junit.Test

class CustomScreenWidthTest {

  @get:Rule
  val paparazzi = Paparazzi(
    deviceConfig = DeviceConfig.NEXUS_5.copy(screenWidth = 300)
  )

  @Test
  fun view() {
    paparazzi.snapshot(paparazzi.inflate(R.layout.launch))
  }

  @Test
  fun compose() {
    paparazzi.snapshot {
      Box(modifier = Modifier.size(100.dp).background(Color.Red)) {
        Text("Hello, Paparazzi")
      }
    }
  }
}
