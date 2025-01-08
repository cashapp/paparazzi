package app.cash.paparazzi.plugin.test

import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import app.cash.paparazzi.annotations.Paparazzi

@Paparazzi
@Preview
@Composable
fun HelloPaparazzi() {
  Text("Hello, Paparazzi!")
}
