package app.cash.paparazzi.plugin.test

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
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
  fun composeDefaultLayoutParams() {
    paparazzi.snapshot {
      Box(
        modifier = Modifier
          .background(Color.Cyan)
          .fillMaxWidth(),
        contentAlignment = Alignment.Center
      ) {
        Text("Hello")
      }
    }
  }

  @Test
  fun animation() {
    val view = ComposeView(paparazzi.context).apply {
      setContent { SimpleAnimation() }
    }

    paparazzi.gif(view, fps = 120)
    paparazzi.gif(view, name = "start-end", fps = 2, end = 500)
    paparazzi.gif(view, name = "middle-anim", start = 200, fps = 60)
    paparazzi.snapshot(view = view, offsetMillis = 1, name = "1ms")
    paparazzi.snapshot(view = view, offsetMillis = 100, name = "100ms")
    paparazzi.snapshot(view = view, offsetMillis = 200, name = "200ms")
    paparazzi.snapshot(view = view, offsetMillis = 300, name = "300ms")
    paparazzi.snapshot(view = view, offsetMillis = 400, name = "400ms")
    paparazzi.snapshot(view = view, offsetMillis = 500, name = "500ms")
  }
}
