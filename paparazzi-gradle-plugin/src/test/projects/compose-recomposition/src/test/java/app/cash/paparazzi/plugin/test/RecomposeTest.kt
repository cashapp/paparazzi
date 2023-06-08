package app.cash.paparazzi.plugin.test

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import app.cash.paparazzi.Paparazzi
import org.junit.Rule
import org.junit.Test

class RecomposeTest {
  @get:Rule
  val paparazzi = Paparazzi()

  @Test fun recomposesOnStateChange() {
    paparazzi.snapshot {
      var text by remember { mutableStateOf("Hello") }
      LaunchedEffect(Unit) {
        text = "Hello Paparazzi"
      }

      Box(
        modifier = Modifier
          .fillMaxSize()
          .background(Color.White)
      ) {
        Text(
          modifier = Modifier.align(Alignment.Center),
          text = text
        )
      }
    }
  }
}
