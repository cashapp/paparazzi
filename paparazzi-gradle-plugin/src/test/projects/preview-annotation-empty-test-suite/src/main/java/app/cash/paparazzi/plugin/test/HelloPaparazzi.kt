package app.cash.paparazzi.plugin.test

import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import app.cash.paparazzi.annotations.Paparazzi

@Paparazzi
@Preview
@Composable
fun HelloPaparazzi() {
  Text("Hello, Paparazzi", style = TextStyle(fontFamily = FontFamily.Cursive), color = Color.White)
}
