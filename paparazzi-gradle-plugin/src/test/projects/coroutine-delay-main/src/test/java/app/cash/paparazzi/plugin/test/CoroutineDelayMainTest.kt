package app.cash.paparazzi.plugin.test

import android.os.SystemClock
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.ComposeView
import app.cash.paparazzi.Paparazzi
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import kotlinx.coroutines.delay

class CoroutineDelayMainTest {
  @get:Rule
  val paparazzi = Paparazzi()

  @Test fun delayUsesMainDispatcher() {
    var start = 0L
    var end = 0L
    paparazzi.gif(
      ComposeView(paparazzi.context).apply {
        setContent {
          start = SystemClock.uptimeMillis()
          LaunchedEffect(Unit) {
            delay(250)
            end = SystemClock.uptimeMillis()
          }
        }
      },
      end = 1000,
      fps = 4
    )

    assertEquals(250L, end - start)
  }
}
