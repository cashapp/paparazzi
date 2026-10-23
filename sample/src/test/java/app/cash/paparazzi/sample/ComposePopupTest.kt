package app.cash.paparazzi.sample

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.InstantAnimationsRule
import app.cash.paparazzi.Paparazzi
import org.junit.Rule
import org.junit.Test
import kotlinx.coroutines.delay

class ComposePopupTest {
  @get:Rule
  val instantAnimations = InstantAnimationsRule()

  @get:Rule
  val paparazzi = Paparazzi(
    deviceConfig = DeviceConfig.PIXEL_5.copy(softButtons = false)
  )

  @Test
  @OptIn(ExperimentalMaterial3Api::class)
  fun expandedDropdown() {
    val updated = mutableStateOf(false)
    paparazzi.gif(end = 300, fps = 10) {
      LaunchedEffect(Unit) {
        // Change the open menu so stale frames cannot match the golden.
        delay(200)
        updated.value = true
      }
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
                // Focus indications can start on different frames; capture the popup itself.
                CompositionLocalProvider(LocalRippleConfiguration provides null) {
                  for (option in listOf("Option 1", "Option 2", "Option 3")) {
                    DropdownMenuItem(text = { Text(if (updated.value) "$option updated" else option) }, onClick = {})
                  }
                }
              }
            }
          }
        }
      }
    }
  }
}
