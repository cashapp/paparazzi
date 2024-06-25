package app.cash.paparazzi.sample

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Composable
@Preview
fun SimpleBoxAlphaRepro() {
  Column(
    Modifier
      .background(Color.White)
      .fillMaxSize()
  ) {
    Box(
      Modifier
        .fillMaxSize()
        .padding(48.dp)
        .background(
          color = Color.Black.copy(alpha = 0.5f)
        )
    )
  }
}

@Composable
@Preview
fun SimpleBoxAlphaRepro2() {
  Column(
    Modifier
      .background(Color.White)
      .fillMaxSize()
  ) {
    Box(
      Modifier
        .fillMaxSize()
        .padding(48.dp)
        .background(
          brush = SolidColor(Color.Black),
          alpha = 0.5f
        )
    )
  }
}
