package app.cash.paparazzi.plugin.test

import androidx.compose.material.Text
import androidx.compose.runtime.LaunchedEffect
import app.cash.paparazzi.Paparazzi
import org.junit.Rule
import org.junit.Test

class LaunchedEffectExceptionTest {
  @get:Rule
  val paparazzi = Paparazzi()

  @Test
  fun launchedEffectExceptionPropagates() {
    paparazzi.snapshot {
      LaunchedEffect(Unit) {
        error("Exception thrown in LaunchedEffect")
      }
      Text("Hello")
    }
  }
}
