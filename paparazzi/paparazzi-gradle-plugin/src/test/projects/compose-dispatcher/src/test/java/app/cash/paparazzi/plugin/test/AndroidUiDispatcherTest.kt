package app.cash.paparazzi.plugin.test

import androidx.compose.runtime.LaunchedEffect
import app.cash.paparazzi.Paparazzi
import org.junit.Rule
import org.junit.Test

class AndroidUiDispatcherTest {
  @get:Rule
  val paparazzi = Paparazzi()

  @Test
  fun androidUiDispatcherResets1() = assertSynchronousLaunchedEffect()

  @Test
  fun androidUiDispatcherResets2() = assertSynchronousLaunchedEffect()

  private fun assertSynchronousLaunchedEffect() {
    var launchedEffect = false
    paparazzi.snapshot {
      LaunchedEffect(Unit) {
        launchedEffect = true
      }
    }
    assert(launchedEffect)
  }
}
