package app.cash.paparazzi.sample

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.InstantAnimationsRule
import app.cash.paparazzi.Paparazzi
import org.junit.Rule
import org.junit.Test

class ComposePopupTest {
  @get:Rule
  val instantAnimations = InstantAnimationsRule()

  @get:Rule
  val paparazzi = Paparazzi(
    deviceConfig = DeviceConfig.PIXEL_5.copy(softButtons = false)
  )

  @Test
  fun expandedDropdown() {
    paparazzi.gif(end = 300, fps = 10) {
      MaterialTheme {
        Surface {
          Box(Modifier.fillMaxSize().padding(24.dp)) {
            Box {
              Button(onClick = {}) {
                Text("Options")
              }
              DropdownMenu(
                expanded = true,
                onDismissRequest = {},
                modifier = Modifier.defaultMinSize(minWidth = 192.dp),
                shape = RoundedCornerShape(12.dp)
              ) {
                for (option in listOf("Option 1", "Option 2", "Option 3")) {
                  DropdownMenuItem(text = { Text(option) }, onClick = {})
                }
              }
            }
          }
        }
      }
    }
  }
}
