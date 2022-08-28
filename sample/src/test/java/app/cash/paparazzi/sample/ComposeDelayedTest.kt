package app.cash.paparazzi.sample

import androidx.compose.foundation.layout.Box
import androidx.compose.material.Text
import androidx.compose.runtime.*
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import org.junit.Rule
import org.junit.Test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ComposeDelayedTest {
  @get:Rule
  val paparazzi = Paparazzi(
    deviceConfig = DeviceConfig.PIXEL,
  )

  @Test
  fun Delay() {
    var state by mutableStateOf(0)

    paparazzi.snapshot(ready = {
      state > 25
    }) {
      Box {
          Text("$state")
      }
      SideEffect {
        println("Side Effect $state")
      }
      LaunchedEffect(Unit) {
        launch(Dispatchers.Default) {
          while (state < 50) {
            delay(10)
            state++
          }
        }
      }
    }
  }
}
