package app.cash.paparazzi.sample

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material.Icon
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import org.junit.Rule
import org.junit.Test

class Issue454 {
  @get:Rule
  val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_3)

  @Test
  fun reproduction() {
    paparazzi.snapshot {
      Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        val sizeModifier = Modifier.size(16.dp)
        Icon(
          painter = painterResource(id = R.drawable.camera),
          contentDescription = null,
          modifier = sizeModifier,
        )
        Icon(
          painter = painterResource(id = R.drawable.camera),
          contentDescription = null,
          modifier = sizeModifier,
          tint = Color.Magenta,
        )
      }
    }
  }
}
