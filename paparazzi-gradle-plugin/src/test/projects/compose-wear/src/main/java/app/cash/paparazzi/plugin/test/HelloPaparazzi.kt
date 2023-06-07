package app.cash.paparazzi.plugin.test

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import kotlin.math.sqrt

@Composable
fun HelloPaparazzi() {
  val text = "Hello, Paparazzi"

  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(Color.DarkGray)
  ) {
    Box(
      Modifier
        .fillMaxSize()
        .insetSquare()
        .background(Color.Blue),
      contentAlignment = Alignment.Center
    ) {
      // TODO stop using compose material
      // and update to Compose 1.2.0, Wear Compose 1.0.0, Kotlin 1.7.0
      Text(text)
    }
  }
}

@Composable
fun Modifier.insetSquare(): Modifier {
  val screenHeightDp = LocalConfiguration.current.screenHeightDp
  val screenWidthDp = LocalConfiguration.current.smallestScreenWidthDp
  val maxSquareEdge = (sqrt(((screenHeightDp * screenWidthDp) / 2).toDouble()))
  return padding(Dp(((screenHeightDp - maxSquareEdge) / 2).toFloat()))
}
