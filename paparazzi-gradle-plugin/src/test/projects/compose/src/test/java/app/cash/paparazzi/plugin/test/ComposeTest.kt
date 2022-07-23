package app.cash.paparazzi.plugin.test

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.cash.paparazzi.Paparazzi
import org.junit.Rule
import org.junit.Test

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
  fun delayed() {
    paparazzi.gif(start = 500L, end = 500L, fps = 1) {
      val name by remember { mutableStateOf("Noone") }
      LaunchedEffect() {
        name = "Paparazzi"
      }
      HelloPaparazzi()
    }
  }
}
