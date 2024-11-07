package app.cash.paparazzi.plugin.test

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
}
